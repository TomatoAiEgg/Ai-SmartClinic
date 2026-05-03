package com.example.airegistration.gateway.dto;

import java.time.OffsetDateTime;

public record ScheduleInventoryAuditLogView(
        Long auditId,
        String operationType,
        String traceId,
        String departmentCode,
        String doctorId,
        String clinicDate,
        String startTime,
        String operationId,
        String operationSource,
        Boolean success,
        String reason,
        Integer remainingBefore,
        Integer remainingAfter,
        String sourceService,
        OffsetDateTime createdAt
) {
}
