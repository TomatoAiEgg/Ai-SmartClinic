package com.example.airegistration.registration.service.workflow;

import com.example.airegistration.dto.ChatRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Repository
@ConditionalOnProperty(
        prefix = "app.registration.workflow",
        name = "execution-log-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RegistrationWorkflowExecutionLogRepository {

    private final NamedParameterJdbcOperations jdbcOperations;
    private final ObjectMapper objectMapper;

    public RegistrationWorkflowExecutionLogRepository(NamedParameterJdbcOperations jdbcOperations,
                                                      ObjectMapper objectMapper) {
        this.jdbcOperations = jdbcOperations;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> save(ChatRequest request,
                           RegistrationWorkflowCheckpoint checkpoint,
                           RegistrationWorkflowTraceEvent event) {
        return Mono.fromRunnable(() -> jdbcOperations.update("""
                INSERT INTO registration_workflow_execution_log (
                    execution_id, confirmation_id, trace_id, chat_id, user_id,
                    workflow_id, intent, node_id, status, payload
                )
                VALUES (
                    :executionId, :confirmationId, :traceId, :chatId, :userId,
                    :workflowId, :intent, :nodeId, :status, CAST(:payload AS jsonb)
                )
                """, new MapSqlParameterSource()
                .addValue("executionId", checkpoint.executionId())
                .addValue("confirmationId", blankToNull(checkpoint.confirmationId()))
                .addValue("traceId", request == null ? null : blankToNull(request.traceId()))
                .addValue("chatId", request == null ? null : blankToNull(request.chatId()))
                .addValue("userId", request == null ? null : blankToNull(request.userId()))
                .addValue("workflowId", checkpoint.workflowId())
                .addValue("intent", checkpoint.intent().name())
                .addValue("nodeId", event.nodeId())
                .addValue("status", event.status())
                .addValue("payload", toJson(Map.of(
                        "event", event,
                        "currentNode", checkpoint.currentNode(),
                        "nextNode", checkpoint.nextNode(),
                        "inputSnapshot", checkpoint.inputSnapshot(),
                        "toolResultSnapshot", checkpoint.toolResultSnapshot()
                )))))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            String message = ex.getMessage() == null ? "unknown" : ex.getMessage().replace("\"", "'");
            return "{\"serializationError\":\"" + message + "\"}";
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
