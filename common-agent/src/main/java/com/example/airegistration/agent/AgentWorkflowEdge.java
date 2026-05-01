package com.example.airegistration.agent;

public record AgentWorkflowEdge(String source, String target, String condition) {
    public AgentWorkflowEdge {
        source = requireText(source, "source");
        target = requireText(target, "target");
        condition = condition == null || condition.isBlank() ? "always" : condition.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
