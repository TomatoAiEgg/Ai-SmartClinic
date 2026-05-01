package com.example.airegistration.agent;

import java.util.Map;

public record AgentResponseEnvelope(
        String route,
        String message,
        Map<String, Object> structuredData,
        boolean requiresConfirmation,
        String confirmationId,
        String nextAction,
        AgentExecutionMeta executionMeta
) {
    public AgentResponseEnvelope {
        route = requireText(route, "route");
        message = message == null ? "" : message.trim();
        structuredData = Map.copyOf(structuredData == null ? Map.of() : structuredData);
        confirmationId = confirmationId == null ? "" : confirmationId.trim();
        nextAction = nextAction == null ? "" : nextAction.trim();
        executionMeta = executionMeta == null ? AgentExecutionMeta.empty(route) : executionMeta;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
