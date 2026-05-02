package com.example.airegistration.registrationmcp.repository;

import com.example.airegistration.registrationmcp.entity.RegistrationAuditLogEntity;
import com.example.airegistration.registrationmcp.entity.RegistrationAuditRecord;
import com.example.airegistration.registrationmcp.mapper.RegistrationAuditLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisRegistrationAuditRepository implements RegistrationAuditRepository {

    private static final String SOURCE_SERVICE = "registration-mcp-server";

    private final RegistrationAuditLogMapper registrationAuditLogMapper;
    private final ObjectMapper objectMapper;

    public MybatisRegistrationAuditRepository(RegistrationAuditLogMapper registrationAuditLogMapper,
                                              ObjectMapper objectMapper) {
        this.registrationAuditLogMapper = registrationAuditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(RegistrationAuditRecord record) {
        try {
            registrationAuditLogMapper.insertAudit(toEntity(record));
        } catch (JsonProcessingException | RuntimeException ex) {
            throw new IllegalStateException("Failed to append registration audit log.", ex);
        }
    }

    private RegistrationAuditLogEntity toEntity(RegistrationAuditRecord record) throws JsonProcessingException {
        RegistrationAuditLogEntity entity = new RegistrationAuditLogEntity();
        entity.setRegistrationId(record.registrationId());
        entity.setOperationType(record.operationType());
        entity.setOperatorUserId(record.operatorUserId());
        entity.setChatId(record.chatId());
        entity.setTraceId(record.traceId());
        entity.setSourceService(SOURCE_SERVICE);
        entity.setSuccess(record.success());
        entity.setReason(record.reason());
        entity.setRequestPayload(objectMapper.writeValueAsString(record.requestPayload()));
        entity.setResponsePayload(objectMapper.writeValueAsString(record.responsePayload()));
        entity.setBeforeSnapshot(objectMapper.writeValueAsString(record.beforeSnapshot()));
        entity.setAfterSnapshot(objectMapper.writeValueAsString(record.afterSnapshot()));
        return entity;
    }
}
