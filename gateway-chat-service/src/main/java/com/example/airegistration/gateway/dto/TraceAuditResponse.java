package com.example.airegistration.gateway.dto;

public record TraceAuditResponse(
        String traceId,
        AuditSection<RegistrationAuditLogView> registrationAudits,
        AuditSection<ScheduleInventoryAuditLogView> scheduleInventoryAudits,
        AuditSection<KnowledgeRetrievalLogView> knowledgeRetrievalLogs
) {
}
