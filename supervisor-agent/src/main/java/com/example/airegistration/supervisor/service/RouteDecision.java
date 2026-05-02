package com.example.airegistration.supervisor.service;

import com.example.airegistration.enums.AgentRoute;
import java.util.List;
import java.util.Map;

public record RouteDecision(
        AgentRoute route,
        RouteDecisionSource source,
        AgentRoute ruleRoute,
        AgentRoute modelRoute,
        double confidence,
        String reason,
        String model,
        int attempt,
        String targetAgent,
        List<String> requiredSlots,
        Map<String, String> handoffMetadata,
        String safetyLevel
) {
    public RouteDecision {
        targetAgent = hasText(targetAgent) ? targetAgent.trim() : targetAgent(route);
        requiredSlots = List.copyOf(requiredSlots == null ? List.of() : requiredSlots);
        handoffMetadata = Map.copyOf(handoffMetadata == null ? Map.of() : handoffMetadata);
        safetyLevel = hasText(safetyLevel) ? safetyLevel.trim() : safetyLevel(route);
    }

    public static RouteDecision rule(AgentRoute route, String reason) {
        return new RouteDecision(route, RouteDecisionSource.RULE, route, null, 1.0D, reason, "", 0,
                targetAgent(route), List.of(), Map.of(), safetyLevel(route));
    }

    public static RouteDecision llm(AgentRoute ruleRoute,
                                    AgentRoute modelRoute,
                                    double confidence,
                                    String reason,
                                    String model,
                                    int attempt) {
        return new RouteDecision(modelRoute, RouteDecisionSource.LLM, ruleRoute, modelRoute,
                confidence, reason, model, attempt, targetAgent(modelRoute), List.of(), Map.of(),
                safetyLevel(modelRoute));
    }

    public static RouteDecision fallback(AgentRoute ruleRoute,
                                         AgentRoute modelRoute,
                                         double confidence,
                                         String reason,
                                         String model,
                                         int attempt) {
        return new RouteDecision(ruleRoute, RouteDecisionSource.FALLBACK, ruleRoute, modelRoute,
                confidence, reason, model, attempt, targetAgent(ruleRoute), List.of(), Map.of(),
                safetyLevel(ruleRoute));
    }

    public RouteDecision withHandoffMetadata(Map<String, String> metadata) {
        return new RouteDecision(route, source, ruleRoute, modelRoute, confidence, reason, model, attempt,
                targetAgent, requiredSlots, metadata, safetyLevel);
    }

    private static String targetAgent(AgentRoute route) {
        if (route == null) {
            return "";
        }
        return switch (route) {
            case TRIAGE -> "triage-agent";
            case REGISTRATION -> "registration-agent";
            case GUIDE -> "guide-agent";
            case HUMAN_REVIEW -> "human-review";
        };
    }

    private static String safetyLevel(AgentRoute route) {
        return route == AgentRoute.HUMAN_REVIEW ? "HIGH" : "STANDARD";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
