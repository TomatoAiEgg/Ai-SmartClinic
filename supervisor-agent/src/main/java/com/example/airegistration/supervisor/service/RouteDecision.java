package com.example.airegistration.supervisor.service;

import com.example.airegistration.enums.AgentRoute;

public record RouteDecision(
        AgentRoute route,
        RouteDecisionSource source,
        AgentRoute ruleRoute,
        AgentRoute modelRoute,
        double confidence,
        String reason,
        String model,
        int attempt
) {

    public static RouteDecision rule(AgentRoute route, String reason) {
        return new RouteDecision(route, RouteDecisionSource.RULE, route, null, 1.0D, reason, "", 0);
    }

    public static RouteDecision llm(AgentRoute ruleRoute,
                                    AgentRoute modelRoute,
                                    double confidence,
                                    String reason,
                                    String model,
                                    int attempt) {
        return new RouteDecision(modelRoute, RouteDecisionSource.LLM, ruleRoute, modelRoute,
                confidence, reason, model, attempt);
    }

    public static RouteDecision fallback(AgentRoute ruleRoute,
                                         AgentRoute modelRoute,
                                         double confidence,
                                         String reason,
                                         String model,
                                         int attempt) {
        return new RouteDecision(ruleRoute, RouteDecisionSource.FALLBACK, ruleRoute, modelRoute,
                confidence, reason, model, attempt);
    }
}
