package com.example.airegistration.gateway.client;

import com.example.airegistration.gateway.config.TraceAuditClientProperties;
import com.example.airegistration.gateway.dto.AuditSection;
import com.example.airegistration.gateway.dto.KnowledgeRetrievalLogView;
import com.example.airegistration.gateway.dto.RegistrationAuditLogView;
import com.example.airegistration.gateway.dto.RegistrationWorkflowExecutionLogView;
import com.example.airegistration.gateway.dto.ScheduleInventoryAuditLogView;
import com.example.airegistration.gateway.dto.TraceAuditResponse;
import com.example.airegistration.support.TraceIdSupport;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(TraceAuditClientProperties.class)
public class TraceAuditClient {

    private static final Logger log = LoggerFactory.getLogger(TraceAuditClient.class);
    private static final ParameterizedTypeReference<List<RegistrationAuditLogView>> REGISTRATION_AUDIT_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<RegistrationWorkflowExecutionLogView>> REGISTRATION_WORKFLOW_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<ScheduleInventoryAuditLogView>> SCHEDULE_AUDIT_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<KnowledgeRetrievalLogView>> KNOWLEDGE_LOG_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient registrationClient;
    private final WebClient registrationAgentClient;
    private final WebClient scheduleClient;
    private final WebClient knowledgeClient;

    public TraceAuditClient(WebClient.Builder webClientBuilder, TraceAuditClientProperties properties) {
        this.registrationClient = webClientBuilder.baseUrl(properties.getRegistrationBaseUrl()).build();
        this.registrationAgentClient = webClientBuilder.baseUrl(properties.getRegistrationAgentBaseUrl()).build();
        this.scheduleClient = webClientBuilder.baseUrl(properties.getScheduleBaseUrl()).build();
        this.knowledgeClient = webClientBuilder.baseUrl(properties.getKnowledgeBaseUrl()).build();
    }

    public Mono<TraceAuditResponse> queryTrace(String traceId, int limit) {
        Mono<AuditSection<RegistrationWorkflowExecutionLogView>> workflowExecutions = fetchRegistrationWorkflowExecutions(traceId, limit);
        Mono<AuditSection<RegistrationAuditLogView>> registrationAudits = fetchRegistrationAudits(traceId, limit);
        Mono<AuditSection<ScheduleInventoryAuditLogView>> scheduleAudits = fetchScheduleAudits(traceId, limit);
        Mono<AuditSection<KnowledgeRetrievalLogView>> knowledgeLogs = fetchKnowledgeLogs(traceId, limit);
        return Mono.zip(workflowExecutions, registrationAudits, scheduleAudits, knowledgeLogs)
                .map(tuple -> new TraceAuditResponse(traceId, tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()));
    }

    private Mono<AuditSection<RegistrationWorkflowExecutionLogView>> fetchRegistrationWorkflowExecutions(String traceId, int limit) {
        return registrationAgentClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/registration/workflow-executions")
                        .queryParam("traceId", traceId)
                        .queryParam("limit", limit)
                        .build())
                .header(TraceIdSupport.TRACE_HEADER, traceId)
                .retrieve()
                .bodyToMono(REGISTRATION_WORKFLOW_LIST)
                .map(records -> AuditSection.success("registration-agent", records))
                .onErrorResume(ex -> fallback("registration-agent", traceId, ex));
    }

    private Mono<AuditSection<RegistrationAuditLogView>> fetchRegistrationAudits(String traceId, int limit) {
        return registrationClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/mcp/registrations/audits")
                        .queryParam("traceId", traceId)
                        .queryParam("limit", limit)
                        .build())
                .header(TraceIdSupport.TRACE_HEADER, traceId)
                .retrieve()
                .bodyToMono(REGISTRATION_AUDIT_LIST)
                .map(records -> AuditSection.success("registration-mcp-server", records))
                .onErrorResume(ex -> fallback("registration-mcp-server", traceId, ex));
    }

    private Mono<AuditSection<ScheduleInventoryAuditLogView>> fetchScheduleAudits(String traceId, int limit) {
        return scheduleClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/mcp/schedules/inventory-audits")
                        .queryParam("traceId", traceId)
                        .queryParam("limit", limit)
                        .build())
                .header(TraceIdSupport.TRACE_HEADER, traceId)
                .retrieve()
                .bodyToMono(SCHEDULE_AUDIT_LIST)
                .map(records -> AuditSection.success("schedule-mcp-server", records))
                .onErrorResume(ex -> fallback("schedule-mcp-server", traceId, ex));
    }

    private Mono<AuditSection<KnowledgeRetrievalLogView>> fetchKnowledgeLogs(String traceId, int limit) {
        return knowledgeClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/knowledge/retrieval-logs")
                        .queryParam("traceId", traceId)
                        .queryParam("limit", limit)
                        .build())
                .header(TraceIdSupport.TRACE_HEADER, traceId)
                .retrieve()
                .bodyToMono(KNOWLEDGE_LOG_LIST)
                .map(records -> AuditSection.success("knowledge-service", records))
                .onErrorResume(ex -> fallback("knowledge-service", traceId, ex));
    }

    private <T> Mono<AuditSection<T>> fallback(String source, String traceId, Throwable ex) {
        log.warn("[gateway] trace audit section failed trace_id={} source={} reason={}",
                traceId,
                source,
                ex.getMessage());
        return Mono.just(AuditSection.failure(source, ex.getMessage()));
    }
}
