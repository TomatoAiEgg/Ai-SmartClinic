package com.example.airegistration.schedulemcp.repository;

import com.example.airegistration.schedulemcp.entity.ScheduleInventoryAuditRecord;

public interface ScheduleInventoryAuditRepository {

    void append(ScheduleInventoryAuditRecord record);

    default boolean hasSuccessfulOperation(String operationType, String operationId, String operationSource) {
        return false;
    }
}
