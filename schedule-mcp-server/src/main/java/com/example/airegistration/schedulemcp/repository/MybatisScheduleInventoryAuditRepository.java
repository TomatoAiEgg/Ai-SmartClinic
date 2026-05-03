package com.example.airegistration.schedulemcp.repository;

import com.example.airegistration.schedulemcp.entity.ClinicSlotInventoryAuditLogEntity;
import com.example.airegistration.schedulemcp.entity.ScheduleInventoryAuditRecord;
import com.example.airegistration.schedulemcp.mapper.ClinicSlotInventoryAuditLogMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisScheduleInventoryAuditRepository implements ScheduleInventoryAuditRepository {

    private static final String SOURCE_SERVICE = "schedule-mcp-server";

    private final ClinicSlotInventoryAuditLogMapper mapper;

    public MybatisScheduleInventoryAuditRepository(ClinicSlotInventoryAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(ScheduleInventoryAuditRecord record) {
        mapper.insertAudit(toEntity(record));
    }

    @Override
    public boolean hasSuccessfulOperation(String operationType, String operationId, String operationSource) {
        if (operationId == null || operationId.isBlank()
                || operationSource == null || operationSource.isBlank()) {
            return false;
        }
        return mapper.countSuccessfulOperation(operationType, operationId, operationSource) > 0;
    }

    private ClinicSlotInventoryAuditLogEntity toEntity(ScheduleInventoryAuditRecord record) {
        ClinicSlotInventoryAuditLogEntity entity = new ClinicSlotInventoryAuditLogEntity();
        entity.setOperationType(record.operationType());
        entity.setTraceId(record.traceId());
        entity.setDepartmentCode(record.departmentCode());
        entity.setDoctorId(record.doctorId());
        entity.setClinicDate(record.clinicDate());
        entity.setStartTime(record.startTime());
        entity.setOperationId(record.operationId());
        entity.setOperationSource(record.operationSource());
        entity.setSuccess(record.success());
        entity.setReason(record.reason());
        entity.setRemainingBefore(record.remainingBefore());
        entity.setRemainingAfter(record.remainingAfter());
        entity.setSourceService(SOURCE_SERVICE);
        return entity;
    }
}
