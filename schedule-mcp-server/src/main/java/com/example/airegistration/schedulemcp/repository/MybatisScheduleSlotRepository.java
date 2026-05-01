package com.example.airegistration.schedulemcp.repository;

import com.example.airegistration.dto.SlotSummary;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.schedulemcp.entity.ScheduleSlotInventory;
import com.example.airegistration.schedulemcp.entity.ScheduleSlotKey;
import com.example.airegistration.schedulemcp.entity.ScheduleSlotView;
import com.example.airegistration.schedulemcp.exception.ScheduleOperationException;
import com.example.airegistration.schedulemcp.mapper.ClinicSlotMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisScheduleSlotRepository implements ScheduleSlotRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ClinicSlotMapper clinicSlotMapper;

    public MybatisScheduleSlotRepository(ClinicSlotMapper clinicSlotMapper) {
        this.clinicSlotMapper = clinicSlotMapper;
    }

    @Override
    public List<ScheduleSlotInventory> findAll() {
        try {
            return clinicSlotMapper.selectActiveSlots()
                    .stream()
                    .map(ScheduleSlotView::toInventory)
                    .toList();
        } catch (RuntimeException ex) {
            throw databaseException("Failed to search clinic slots in PostgreSQL.", ex);
        }
    }

    @Override
    public Optional<ScheduleSlotInventory> findByKey(ScheduleSlotKey key) {
        try {
            ScheduleSlotView slot = clinicSlotMapper.selectActiveSlot(
                    key.departmentCode(),
                    key.doctorId(),
                    parseClinicDate(key),
                    parseStartTime(key)
            );
            return Optional.ofNullable(slot).map(ScheduleSlotView::toInventory);
        } catch (ScheduleOperationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw databaseException("Failed to query clinic slot in PostgreSQL.", ex);
        }
    }

    @Override
    public SlotSummary reserve(ScheduleSlotKey key) {
        return executeMutation(
                key,
                "Slot is not available.",
                () -> clinicSlotMapper.reserveSlot(
                        key.departmentCode(),
                        key.doctorId(),
                        parseClinicDate(key),
                        parseStartTime(key)
                )
        );
    }

    @Override
    public SlotSummary release(ScheduleSlotKey key) {
        return executeMutation(
                key,
                "Slot does not exist.",
                () -> clinicSlotMapper.releaseSlot(
                        key.departmentCode(),
                        key.doctorId(),
                        parseClinicDate(key),
                        parseStartTime(key)
                )
        );
    }

    private SlotSummary executeMutation(ScheduleSlotKey key, String notFoundMessage, SlotMutation mutation) {
        try {
            ScheduleSlotView slot = mutation.execute();
            if (slot != null) {
                return slot.toInventory().toSummary();
            }
        } catch (ScheduleOperationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw databaseException("Failed to update clinic slot in PostgreSQL.", ex);
        }

        throw new ScheduleOperationException(
                ApiErrorCode.NOT_FOUND,
                notFoundMessage,
                slotDetails(key)
        );
    }

    private LocalDate parseClinicDate(ScheduleSlotKey key) {
        try {
            return LocalDate.parse(key.clinicDate());
        } catch (DateTimeParseException ex) {
            throw invalidKey(key);
        }
    }

    private LocalTime parseStartTime(ScheduleSlotKey key) {
        try {
            return LocalTime.parse(key.startTime(), TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw invalidKey(key);
        }
    }

    private ScheduleOperationException invalidKey(ScheduleSlotKey key) {
        return new ScheduleOperationException(
                ApiErrorCode.INVALID_REQUEST,
                "Invalid clinic date or start time.",
                slotDetails(key)
        );
    }

    private ScheduleOperationException databaseException(String message, RuntimeException ex) {
        return new ScheduleOperationException(
                ApiErrorCode.INTERNAL_ERROR,
                message,
                Map.of("cause", ex.getMessage())
        );
    }

    private Map<String, Object> slotDetails(ScheduleSlotKey key) {
        return Map.of(
                "departmentCode", key.departmentCode(),
                "doctorId", key.doctorId(),
                "clinicDate", key.clinicDate(),
                "startTime", key.startTime()
        );
    }

    @FunctionalInterface
    private interface SlotMutation {
        ScheduleSlotView execute();
    }
}
