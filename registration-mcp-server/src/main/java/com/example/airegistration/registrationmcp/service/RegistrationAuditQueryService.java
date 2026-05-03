package com.example.airegistration.registrationmcp.service;

import com.example.airegistration.registrationmcp.dto.RegistrationAuditLogView;
import com.example.airegistration.registrationmcp.entity.RegistrationAuditLogEntity;
import com.example.airegistration.registrationmcp.repository.RegistrationAuditRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RegistrationAuditQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final RegistrationAuditRepository auditRepository;

    public RegistrationAuditQueryService(RegistrationAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public List<RegistrationAuditLogView> listAuditLogs(String registrationId,
                                                        String operatorUserId,
                                                        String chatId,
                                                        String traceId,
                                                        String operationType,
                                                        Boolean success,
                                                        Integer limit) {
        return auditRepository.listAuditLogs(
                        nullIfBlank(registrationId),
                        nullIfBlank(operatorUserId),
                        nullIfBlank(chatId),
                        nullIfBlank(traceId),
                        upperOrNull(operationType),
                        success,
                        boundLimit(limit)
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

    private RegistrationAuditLogView toView(RegistrationAuditLogEntity entity) {
        return new RegistrationAuditLogView(
                entity.getAuditId(),
                entity.getRegistrationId(),
                entity.getOperationType(),
                entity.getOperatorUserId(),
                entity.getChatId(),
                entity.getSourceService(),
                entity.getSuccess(),
                entity.getReason(),
                entity.getTraceId(),
                entity.getRequestPayload(),
                entity.getResponsePayload(),
                entity.getBeforeSnapshot(),
                entity.getAfterSnapshot(),
                entity.getCreatedAt()
        );
    }
}
