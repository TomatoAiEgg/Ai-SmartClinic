package com.example.airegistration.schedulemcp.entity;

public record ScheduleInventoryAuditRecord(
        String operationType,
        String traceId,
        String departmentCode,
        String doctorId,
        String clinicDate,
        String startTime,
        String operationId,
        String operationSource,
        boolean success,
        String reason,
        Integer remainingBefore,
        Integer remainingAfter
) {

    public static ScheduleInventoryAuditRecord success(String operationType,
                                                       String traceId,
                                                       String operationId,
                                                       String operationSource,
                                                       ScheduleSlotKey key,
                                                       Integer remainingBefore,
                                                       Integer remainingAfter) {
        return new ScheduleInventoryAuditRecord(
                operationType,
                normalize(traceId),
                key.departmentCode(),
                key.doctorId(),
                key.clinicDate(),
                key.startTime(),
                normalize(operationId),
                normalize(operationSource),
                true,
                "ok",
                remainingBefore,
                remainingAfter
        );
    }

    public static ScheduleInventoryAuditRecord failure(String operationType,
                                                       String traceId,
                                                       String operationId,
                                                       String operationSource,
                                                       ScheduleSlotKey key,
                                                       Integer remainingBefore,
                                                       String reason) {
        return new ScheduleInventoryAuditRecord(
                operationType,
                normalize(traceId),
                key.departmentCode(),
                key.doctorId(),
                key.clinicDate(),
                key.startTime(),
                normalize(operationId),
                normalize(operationSource),
                false,
                normalize(reason),
                remainingBefore,
                null
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
