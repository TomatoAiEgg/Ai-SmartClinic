package com.example.airegistration.registration.service.workflow;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.dto.RegistrationWorkflowExecutionLogView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

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

    public List<RegistrationWorkflowExecutionLogView> listLogs(String traceId,
                                                               String chatId,
                                                               String executionId,
                                                               String confirmationId,
                                                               String intent,
                                                               String status,
                                                               Integer limit) {
        QueryParts query = new QueryParts("""
                SELECT id, execution_id, confirmation_id, trace_id, chat_id, user_id,
                       workflow_id, intent, node_id, status, CAST(payload AS text) AS payload_json,
                       created_at
                FROM registration_workflow_execution_log
                WHERE 1 = 1
                """);
        query.addTextFilter("trace_id", traceId);
        query.addTextFilter("chat_id", chatId);
        query.addTextFilter("execution_id", executionId);
        query.addTextFilter("confirmation_id", confirmationId);
        query.addTextFilter("intent", upperOrNull(intent));
        query.addTextFilter("status", upperOrNull(status));
        query.sql.append("ORDER BY created_at DESC LIMIT :limit");
        query.parameters.addValue("limit", safeLimit(limit));
        return jdbcOperations.query(query.sql.toString(), query.parameters, this::toView);
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

    private String upperOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private int safeLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_LIMIT : limit;
        if (resolved <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(resolved, MAX_LIMIT);
    }

    private RegistrationWorkflowExecutionLogView toView(ResultSet rs, int rowNum) throws SQLException {
        return new RegistrationWorkflowExecutionLogView(
                rs.getObject("id", UUID.class),
                rs.getString("execution_id"),
                rs.getString("confirmation_id"),
                rs.getString("trace_id"),
                rs.getString("chat_id"),
                rs.getString("user_id"),
                rs.getString("workflow_id"),
                rs.getString("intent"),
                rs.getString("node_id"),
                rs.getString("status"),
                rs.getString("payload_json"),
                instant(rs, "created_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static final class QueryParts {
        private final StringBuilder sql;
        private final MapSqlParameterSource parameters = new MapSqlParameterSource();

        private QueryParts(String baseSql) {
            this.sql = new StringBuilder(baseSql);
        }

        private void addTextFilter(String column, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            String parameterName = "p" + parameters.getValues().size();
            sql.append("AND ").append(column).append(" = :").append(parameterName).append(' ');
            parameters.addValue(parameterName, value.trim());
        }
    }
}
