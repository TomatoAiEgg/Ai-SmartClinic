package com.example.airegistration.schedulemcp.repository;

import com.example.airegistration.schedulemcp.entity.ScheduleInventoryAuditRecord;
import com.example.airegistration.schedulemcp.entity.ClinicSlotInventoryAuditLogEntity;
import java.util.List;

public interface ScheduleInventoryAuditRepository {

    void append(ScheduleInventoryAuditRecord record);

    default boolean hasSuccessfulOperation(String operationType, String operationId, String operationSource) {
        return false;
    }

    default List<ClinicSlotInventoryAuditLogEntity> listAuditLogs(String operationId,
                                                                  String traceId,
                                                                  String departmentCode,
                                                                  String doctorId,
                                                                  String clinicDate,
                                                                  String operationType,
                                                                  Boolean success,
                                                                  int limit) {
        return List.of();
    }
}
