package com.example.airegistration.agent;

import java.util.List;
import java.util.Map;

public record AgentCapability(
        String agentName,
        String description,
        List<String> supportedRoutes,
        List<String> inputSchemas,
        List<String> outputSchemas,
        List<AgentPattern> patterns,
        Map<String, Object> metadata
) {
    public AgentCapability {
        agentName = requireText(agentName, "agentName");
        description = description == null ? "" : description.trim();
        supportedRoutes = List.copyOf(supportedRoutes == null ? List.of() : supportedRoutes);
        inputSchemas = List.copyOf(inputSchemas == null ? List.of() : inputSchemas);
        outputSchemas = List.copyOf(outputSchemas == null ? List.of() : outputSchemas);
        patterns = List.copyOf(patterns == null ? List.of() : patterns);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
