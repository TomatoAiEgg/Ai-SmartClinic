package com.example.airegistration.registrationmcp.repository;

import com.example.airegistration.registrationmcp.entity.RegistrationAuditLogEntity;
import com.example.airegistration.registrationmcp.entity.RegistrationAuditRecord;
import java.util.List;

public interface RegistrationAuditRepository {

    void append(RegistrationAuditRecord record);

    default List<RegistrationAuditLogEntity> listAuditLogs(String registrationId,
                                                           String operatorUserId,
                                                           String chatId,
                                                           String traceId,
                                                           String operationType,
                                                           Boolean success,
                                                           int limit) {
        return List.of();
    }
}
