package com.example.airegistration.agent;

import java.util.Objects;

public record AgentWorkflowNode(
        String id,
        String name,
        String businessCapability,
        AgentPattern pattern,
        boolean aiDriven,
        boolean toolCalling,
        boolean humanApprovalRequired
) {
    public AgentWorkflowNode {
        id = requireText(id, "id");
        name = requireText(name, "name");
        businessCapability = requireText(businessCapability, "businessCapability");
        pattern = Objects.requireNonNull(pattern, "pattern");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
