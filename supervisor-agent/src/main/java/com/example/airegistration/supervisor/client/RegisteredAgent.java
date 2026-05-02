package com.example.airegistration.supervisor.client;

import com.example.airegistration.agent.AgentCapability;
import com.example.airegistration.enums.AgentRoute;
import java.util.Objects;

public record RegisteredAgent(
        AgentRoute route,
        String agentName,
        String baseUrl,
        String legacyPath,
        AgentCapability capability
) {
    public RegisteredAgent {
        route = Objects.requireNonNull(route, "route must not be null");
        agentName = hasText(agentName) ? agentName.trim() : defaultAgentName(route);
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        legacyPath = requirePath(legacyPath);
    }

    public RegisteredAgent withCapability(AgentCapability capability) {
        if (capability == null) {
            return this;
        }
        return new RegisteredAgent(route, capability.agentName(), baseUrl, legacyPath, capability);
    }

    private static String defaultAgentName(AgentRoute route) {
        return route.name().toLowerCase().replace('_', '-') + "-agent";
    }

    private static String requirePath(String path) {
        if (!hasText(path)) {
            throw new IllegalArgumentException("legacyPath must not be blank");
        }
        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
