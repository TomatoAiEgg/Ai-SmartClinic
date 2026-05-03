package com.example.airegistration.schedulemcp.service;

import com.example.airegistration.schedulemcp.dto.ScheduleInventoryAuditLogView;
import com.example.airegistration.schedulemcp.entity.ClinicSlotInventoryAuditLogEntity;
import com.example.airegistration.schedulemcp.repository.ScheduleInventoryAuditRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScheduleInventoryAuditQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ScheduleInventoryAuditRepository auditRepository;

    public ScheduleInventoryAuditQueryService(ScheduleInventoryAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public List<ScheduleInventoryAuditLogView> listAuditLogs(String operationId,
                                                             String traceId,
                                                             String departmentCode,
                                                             String doctorId,
                                                             String clinicDate,
                                                             String operationType,
                                                             Boolean success,
                                                             Integer limit) {
        int boundedLimit = boundLimit(limit);
        String normalizedDepartmentCode = upperOrNull(departmentCode);
        String normalizedOperationType = upperOrNull(operationType);
        return auditRepository.listAuditLogs(
                        nullIfBlank(operationId),
                        nullIfBlank(traceId),
                        normalizedDepartmentCode,
                        nullIfBlank(doctorId),
                        nullIfBlank(clinicDate),
                        normalizedOperationType,
                        success,
                        boundedLimit
                )
                .stream()
                .map(this::toView)
                .toList();
    }

    private int boundLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private String upperOrNull(String value) {
        String normalized = nullIfBlank(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ScheduleInventoryAuditLogView toView(ClinicSlotInventoryAuditLogEntity entity) {
        return new ScheduleInventoryAuditLogView(
                entity.getAuditId(),
                entity.getOperationType(),
                entity.getTraceId(),
                entity.getDepartmentCode(),
                entity.getDoctorId(),
                entity.getClinicDate(),
                entity.getStartTime(),
                entity.getOperationId(),
                entity.getOperationSource(),
                entity.getSuccess(),
                entity.getReason(),
                entity.getRemainingBefore(),
                entity.getRemainingAfter(),
                entity.getSourceService(),
                entity.getCreatedAt()
        );
    }
}
