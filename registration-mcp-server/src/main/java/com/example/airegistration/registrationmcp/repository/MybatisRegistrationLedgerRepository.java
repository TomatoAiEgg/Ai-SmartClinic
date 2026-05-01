package com.example.airegistration.registrationmcp.repository;

import com.example.airegistration.enums.RegistrationStatus;
import com.example.airegistration.registrationmcp.entity.RegistrationOrderEntity;
import com.example.airegistration.registrationmcp.entity.RegistrationRecord;
import com.example.airegistration.registrationmcp.mapper.RegistrationOrderMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisRegistrationLedgerRepository implements RegistrationLedgerRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final RegistrationOrderMapper registrationOrderMapper;

    public MybatisRegistrationLedgerRepository(RegistrationOrderMapper registrationOrderMapper) {
        this.registrationOrderMapper = registrationOrderMapper;
    }

    @Override
    public void save(RegistrationRecord record) {
        RegistrationOrderEntity entity = toEntity(record);
        try {
            registrationOrderMapper.upsert(entity);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to save registration_order in PostgreSQL.", ex);
        }
    }

    @Override
    public Optional<RegistrationRecord> findById(String registrationId) {
        try {
            return Optional.ofNullable(registrationOrderMapper.selectByRegistrationId(registrationId))
                    .map(this::toRecord);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to query registration_order in PostgreSQL.", ex);
        }
    }

    @Override
    public Optional<RegistrationRecord> findByExternalRequestId(String externalRequestId) {
        if (externalRequestId == null || externalRequestId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(registrationOrderMapper.selectByExternalRequestId(externalRequestId))
                    .map(this::toRecord);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to query registration_order by external_request_id in PostgreSQL.", ex);
        }
    }

    @Override
    public List<RegistrationRecord> findAll() {
        try {
            return registrationOrderMapper.selectAllOrders().stream()
                    .map(this::toRecord)
                    .toList();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to search registration_order in PostgreSQL.", ex);
        }
    }

    private RegistrationOrderEntity toEntity(RegistrationRecord record) {
        RegistrationOrderEntity entity = new RegistrationOrderEntity();
        entity.setRegistrationId(record.registrationId());
        entity.setUserId(record.userId());
        entity.setPatientId(record.patientId());
        entity.setDepartmentCode(record.departmentCode());
        entity.setDoctorId(record.doctorId());
        entity.setClinicDate(LocalDate.parse(record.clinicDate()));
        entity.setStartTime(LocalTime.parse(record.startTime(), TIME_FORMATTER));
        entity.setStatus(record.status().code());
        entity.setConfirmationRequired(false);
        entity.setSourceChannel("MINIAPP");
        entity.setChatId(record.chatId());
        entity.setExternalRequestId(record.externalRequestId());
        entity.setCancelledAt(record.status() == RegistrationStatus.CANCELLED ? OffsetDateTime.now() : null);
        return entity;
    }

    private RegistrationRecord toRecord(RegistrationOrderEntity entity) {
        return new RegistrationRecord(
                entity.getRegistrationId(),
                entity.getUserId(),
                entity.getPatientId(),
                entity.getDepartmentCode(),
                entity.getDoctorId(),
                entity.getClinicDate().toString(),
                entity.getStartTime().format(TIME_FORMATTER),
                RegistrationStatus.valueOf(entity.getStatus()),
                entity.getExternalRequestId(),
                entity.getChatId()
        );
    }
}
