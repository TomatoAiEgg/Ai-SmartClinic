package com.example.airegistration.schedulemcp.repository;

import com.example.airegistration.schedulemcp.entity.ClinicSlotInventoryAuditLogEntity;
import com.example.airegistration.schedulemcp.entity.ScheduleInventoryAuditRecord;
import com.example.airegistration.schedulemcp.mapper.ClinicSlotInventoryAuditLogMapper;
import java.util.List;
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

    @Override
    public List<ClinicSlotInventoryAuditLogEntity> listAuditLogs(String operationId,
                                                                 String traceId,
                                                                 String departmentCode,
                                                                 String doctorId,
                                                                 String clinicDate,
                                                                 String operationType,
                                                                 Boolean success,
                                                                 int limit) {
        return mapper.selectAuditLogs(
                nullIfBlank(operationId),
                nullIfBlank(traceId),
                nullIfBlank(departmentCode),
                nullIfBlank(doctorId),
                nullIfBlank(clinicDate),
                nullIfBlank(operationType),
                success,
                Math.max(1, limit)
        );
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

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
