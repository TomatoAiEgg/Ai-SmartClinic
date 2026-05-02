package com.example.airegistration.agent;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AgentEnvelopeMapper {

    private AgentEnvelopeMapper() {
    }

    public static ChatRequest toChatRequest(AgentRequestEnvelope envelope) {
        return new ChatRequest(
                envelope.chatId(),
                envelope.userId(),
                envelope.message(),
                toStringMetadata(envelope.metadata()),
                envelope.traceId()
        );
    }

    public static AgentRequestEnvelope toAgentRequest(ChatRequest request) {
        return new AgentRequestEnvelope(
                request.traceId(),
                request.chatId(),
                request.userId(),
                request.message(),
                toObjectMetadata(request.metadata()),
                Map.of()
        );
    }

    public static AgentResponseEnvelope toAgentResponse(ChatResponse response,
                                                        String agentName,
                                                        long latencyMs) {
        Map<String, Object> structuredData = response.data();
        return new AgentResponseEnvelope(
                response.route().name(),
                response.message(),
                structuredData,
                response.requiresConfirmation(),
                stringValue(firstPresent(structuredData, "confirmationId", "confirmation_id")),
                stringValue(firstPresent(structuredData, "confirmationAction", "nextAction", "action")),
                executionMeta(agentName, latencyMs, structuredData)
        );
    }

    public static ChatResponse toChatResponse(ChatRequest request, AgentResponseEnvelope response) {
        return new ChatResponse(
                request.chatId(),
                parseRoute(response.route()),
                response.message(),
                response.requiresConfirmation(),
                response.structuredData()
        );
    }

    private static AgentExecutionMeta executionMeta(String agentName,
                                                    long latencyMs,
                                                    Map<String, Object> structuredData) {
        return new AgentExecutionMeta(
                agentName,
                stringValue(firstPresent(structuredData, "model", "aiModel")),
                latencyMs,
                booleanValue(firstPresent(structuredData, "fallbackUsed", "aiFallbackUsed")),
                retryCount(structuredData),
                evidenceIds(structuredData),
                attributes(structuredData)
        );
    }

    private static Map<String, String> toStringMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> converted = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (hasText(key) && value != null) {
                converted.put(key.trim(), stringValue(value));
            }
        });
        return Map.copyOf(converted);
    }

    private static Map<String, Object> toObjectMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (hasText(key) && value != null) {
                converted.put(key.trim(), value);
            }
        });
        return Map.copyOf(converted);
    }

    private static AgentRoute parseRoute(String route) {
        if (!hasText(route)) {
            return AgentRoute.HUMAN_REVIEW;
        }
        try {
            return AgentRoute.valueOf(route.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AgentRoute.HUMAN_REVIEW;
        }
    }

    private static Object firstPresent(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            if (data.containsKey(key)) {
                return data.get(key);
            }
        }
        return null;
    }

    private static int retryCount(Map<String, Object> data) {
        Object retryCount = firstPresent(data, "retryCount", "aiRetryCount");
        if (retryCount instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        Object attempt = firstPresent(data, "attempt", "aiAttempt");
        if (attempt instanceof Number number) {
            return Math.max(0, number.intValue() - 1);
        }
        return 0;
    }

    private static List<String> evidenceIds(Map<String, Object> data) {
        List<String> ids = new ArrayList<>();
        appendValues(ids, firstPresent(data, "evidenceIds", "citations", "policyIds", "ruleIds"));
        return List.copyOf(ids);
    }

    private static void appendValues(List<String> target, Object value) {
        if (value instanceof Collection<?> values) {
            values.stream()
                    .map(AgentEnvelopeMapper::stringValue)
                    .filter(AgentEnvelopeMapper::hasText)
                    .forEach(target::add);
            return;
        }
        String text = stringValue(value);
        if (hasText(text)) {
            target.add(text);
        }
    }

    private static Map<String, Object> attributes(Map<String, Object> data) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("dataKeys", List.copyOf(data.keySet()));

        Map<String, Object> rag = new LinkedHashMap<>();
        putIfPresent(rag, data, "source");
        putIfPresent(rag, data, "retriever");
        putIfPresent(rag, data, "matchCount");
        putIfPresent(rag, data, "citations");
        if (!rag.isEmpty()) {
            attributes.put("ragSummary", Map.copyOf(rag));
        }

        if (data.containsKey("confirmationId") || data.containsKey("confirmationAction")) {
            Map<String, Object> confirmation = new LinkedHashMap<>();
            putIfPresent(confirmation, data, "confirmationId");
            putIfPresent(confirmation, data, "confirmationAction");
            attributes.put("confirmationContext", Map.copyOf(confirmation));
        }

        return Map.copyOf(attributes);
    }

    private static void putIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(stringValue(value));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
