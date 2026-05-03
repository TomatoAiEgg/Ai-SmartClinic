package com.example.airegistration.schedulemcp.service;

import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.dto.SlotSummary;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.schedulemcp.entity.ScheduleInventoryAuditRecord;
import com.example.airegistration.schedulemcp.entity.ScheduleSlotInventory;
import com.example.airegistration.schedulemcp.entity.ScheduleSlotKey;
import com.example.airegistration.schedulemcp.exception.ScheduleOperationException;
import com.example.airegistration.schedulemcp.repository.ScheduleInventoryAuditRepository;
import com.example.airegistration.schedulemcp.repository.ScheduleSlotRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScheduleCatalogApplicationService implements ScheduleCatalogUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScheduleCatalogApplicationService.class);

    private final ScheduleSlotRepository scheduleSlotRepository;
    private final ScheduleInventoryAuditRepository auditRepository;

    public ScheduleCatalogApplicationService(ScheduleSlotRepository scheduleSlotRepository) {
        this(scheduleSlotRepository, record -> {
        });
    }

    @Autowired
    public ScheduleCatalogApplicationService(ScheduleSlotRepository scheduleSlotRepository,
                                             ScheduleInventoryAuditRepository auditRepository) {
        this.scheduleSlotRepository = scheduleSlotRepository;
        this.auditRepository = auditRepository == null ? record -> {
        } : auditRepository;
    }

    @Override
    public SlotSummary recommend(String departmentCode) {
        String normalizedDepartmentCode = normalizeRequired("departmentCode", departmentCode).toUpperCase(Locale.ROOT);
        return scheduleSlotRepository.findAll().stream()
                .filter(slot -> slot.departmentCode().equals(normalizedDepartmentCode))
                .filter(slot -> slot.remainingSlots() > 0)
                .sorted(slotComparator())
                .findFirst()
                .map(ScheduleSlotInventory::toSummary)
                .orElseThrow(() -> new ScheduleOperationException(
                        ApiErrorCode.NOT_FOUND,
                        "No available slot found.",
                        Map.of("departmentCode", normalizedDepartmentCode)
                ));
    }

    @Override
    public List<SlotSummary> search(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        return scheduleSlotRepository.findAll().stream()
                .filter(slot -> normalizedKeyword.isEmpty() || slot.matchesKeyword(normalizedKeyword))
                .sorted(slotComparator())
                .map(ScheduleSlotInventory::toSummary)
                .toList();
    }

    @Override
    public SlotSummary resolve(ScheduleSlotRequest request) {
        return getRequiredSlot(request).toSummary();
    }

    @Override
    public SlotSummary reserve(ScheduleSlotRequest request, String traceId) {
        return mutateWithAudit("RESERVE", request, traceId, scheduleSlotRepository::reserve);
    }

    @Override
    public SlotSummary release(ScheduleSlotRequest request, String traceId) {
        return mutateWithAudit("RELEASE", request, traceId, scheduleSlotRepository::release);
    }

    private SlotSummary mutateWithAudit(String operationType,
                                        ScheduleSlotRequest request,
                                        String traceId,
                                        SlotMutation mutation) {
        ScheduleSlotKey key = toRequiredSlotKey(request);
        Integer remainingBefore = scheduleSlotRepository.findByKey(key)
                .map(ScheduleSlotInventory::remainingSlots)
                .orElse(null);
        try {
            SlotSummary result = mutation.execute(key);
            appendAudit(ScheduleInventoryAuditRecord.success(
                    operationType,
                    traceId,
                    request.operationId(),
                    request.operationSource(),
                    key,
                    remainingBefore,
                    result.remainingSlots()
            ));
            return result;
        } catch (RuntimeException ex) {
            appendAudit(ScheduleInventoryAuditRecord.failure(
                    operationType,
                    traceId,
                    request.operationId(),
                    request.operationSource(),
                    key,
                    remainingBefore,
                    ex.getMessage()
            ));
            throw ex;
        }
    }

    private void appendAudit(ScheduleInventoryAuditRecord record) {
        try {
            auditRepository.append(record);
        } catch (RuntimeException ex) {
            log.warn("[schedule-mcp] inventory audit append failed operation={} trace_id={} department_code={} doctor_id={} clinic_date={} start_time={}",
                    record.operationType(),
                    record.traceId(),
                    record.departmentCode(),
                    record.doctorId(),
                    record.clinicDate(),
                    record.startTime(),
                    ex);
        }
    }

    private ScheduleSlotInventory getRequiredSlot(ScheduleSlotRequest request) {
        ScheduleSlotKey key = toRequiredSlotKey(request);
        return scheduleSlotRepository.findByKey(key)
                .orElseThrow(() -> new ScheduleOperationException(
                        ApiErrorCode.NOT_FOUND,
                        "Slot does not exist.",
                        Map.of(
                                "departmentCode", key.departmentCode(),
                                "doctorId", key.doctorId(),
                                "clinicDate", key.clinicDate(),
                                "startTime", key.startTime()
                        )
                ));
    }

    private ScheduleSlotKey toRequiredSlotKey(ScheduleSlotRequest request) {
        if (request == null) {
            throw new ScheduleOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Slot request cannot be null.",
                    Map.of("field", "request")
            );
        }
        String departmentCode = normalizeRequired("departmentCode", request.departmentCode()).toUpperCase(Locale.ROOT);
        String doctorId = normalizeRequired("doctorId", request.doctorId());
        String clinicDate = normalizeRequired("clinicDate", request.clinicDate());
        String startTime = normalizeRequired("startTime", request.startTime());
        return new ScheduleSlotKey(departmentCode, doctorId, clinicDate, startTime);
    }

    private Comparator<ScheduleSlotInventory> slotComparator() {
        return Comparator.comparing(ScheduleSlotInventory::clinicDate)
                .thenComparing(ScheduleSlotInventory::startTime)
                .thenComparing(ScheduleSlotInventory::departmentCode);
    }

    private String normalizeRequired(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ScheduleOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Missing required field: " + field,
                    Map.of("field", field)
            );
        }
        return value.trim();
    }

    @FunctionalInterface
    private interface SlotMutation {
        SlotSummary execute(ScheduleSlotKey key);
    }
}
