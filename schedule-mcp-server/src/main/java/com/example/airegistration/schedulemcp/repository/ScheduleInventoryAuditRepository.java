package com.example.airegistration.schedulemcp.repository;

import com.example.airegistration.schedulemcp.entity.ScheduleInventoryAuditRecord;

public interface ScheduleInventoryAuditRepository {

    void append(ScheduleInventoryAuditRecord record);
}
