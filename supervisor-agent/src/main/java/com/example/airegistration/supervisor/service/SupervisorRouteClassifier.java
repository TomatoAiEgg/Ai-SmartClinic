package com.example.airegistration.supervisor.service;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.dto.AiChatResult;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.service.policy.SupervisorRoutePolicy;
import com.example.airegistration.supervisor.service.prompt.SupervisorPromptService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class SupervisorRouteClassifier {

    private static final Logger log = LoggerFactory.getLogger(SupervisorRouteClassifier.class);
    private static final double MIN_CONFIDENCE = 0.65D;

    private final SupervisorRoutePolicy routePolicy;
    private final SupervisorPromptService promptService;
    private final ObjectProvider<AiChatClient> aiChatClientProvider;
    private final ObjectMapper objectMapper;
    private final boolean llmEnabled;

    public SupervisorRouteClassifier(SupervisorRoutePolicy routePolicy,
                                     SupervisorPromptService promptService,
                                     ObjectProvider<AiChatClient> aiChatClientProvider,
                                     ObjectMapper objectMapper,
                                     @Value("${app.ai.supervisor-routing.llm-enabled:true}") boolean llmEnabled) {
        this.routePolicy = routePolicy;
        this.promptService = promptService;
        this.aiChatClientProvider = aiChatClientProvider;
        this.objectMapper = objectMapper;
        this.llmEnabled = llmEnabled;
    }

    public Mono<RouteDecision> determineRoute(ChatRequest request) {
        AgentRoute ruleRoute = routePolicy.determineRoute(request);
        if (!shouldUseModel(request, ruleRoute)) {
            log.info("[supervisor-ai] use rule route trace_id={} chat_id={} route={} reason={}",
                    request.traceId(),
                    request.chatId(),
                    ruleRoute,
                    ruleBypassReason(request, ruleRoute));
            return Mono.just(RouteDecision.rule(ruleRoute, ruleBypassReason(request, ruleRoute)));
        }

        AiChatClient aiChatClient = aiChatClientProvider.getIfAvailable();
        if (aiChatClient == null) {
            log.info("[supervisor-ai] AI chat client unavailable, use rule route trace_id={} chat_id={} route={}",
                    request.traceId(),
                    request.chatId(),
                    ruleRoute);
            return Mono.just(RouteDecision.rule(ruleRoute, "ai_client_unavailable"));
        }

        return Mono.fromCallable(() -> classifyWithModel(aiChatClient, request, ruleRoute))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("[supervisor-ai] route classification failed, use rule route trace_id={} chat_id={} rule_route={} error={}",
                            request.traceId(),
                            request.chatId(),
                            ruleRoute,
                            ex.getMessage());
                    return Mono.just(RouteDecision.fallback(ruleRoute, null, 0D,
                            "classification_failed: " + ex.getMessage(), "", 0));
                });
    }

    private RouteDecision classifyWithModel(AiChatClient aiChatClient, ChatRequest request, AgentRoute ruleRoute) {
        String systemPrompt = promptService.routeSystemPrompt();
        String userPrompt = promptService.routeUserPrompt(request, ruleRoute);
        log.debug("[supervisor-ai] routing prompt trace_id={} chat_id={} system_prompt={} user_prompt={}",
                request.traceId(),
                request.chatId(),
                systemPrompt,
                userPrompt);

        AiChatResult result = aiChatClient.call(AiChatRequest.builder("supervisor.route.classify")
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .attributes(Map.of(
                        "agent", "SUPERVISOR",
                        "chatId", request.chatId(),
                        "traceId", request.traceId(),
                        "ruleRoute", ruleRoute.name()
                ))
                .build());

        RoutePayload payload = parsePayload(result.content());
        AgentRoute modelRoute = parseRoute(payload.route());
        double confidence = payload.confidence() == null ? 0D : payload.confidence();
        if (modelRoute == null || confidence < MIN_CONFIDENCE) {
            log.info("[supervisor-ai] low confidence, use rule route trace_id={} chat_id={} model={} model_route={} confidence={} rule_route={} reason={}",
                    request.traceId(),
                    request.chatId(),
                    result.model(),
                    modelRoute,
                    confidence,
                    ruleRoute,
                    payload.reason());
            return RouteDecision.fallback(ruleRoute, modelRoute, confidence,
                    "low_confidence_or_invalid_route: " + safeReason(payload.reason()),
                    result.model(),
                    result.attempt());
        }

        if (shouldProtectRuleRoute(ruleRoute, modelRoute)) {
            log.info("[supervisor-ai] protected rule route, use rule route trace_id={} chat_id={} model={} model_route={} confidence={} rule_route={} reason={}",
                    request.traceId(),
                    request.chatId(),
                    result.model(),
                    modelRoute,
                    confidence,
                    ruleRoute,
                    payload.reason());
            return RouteDecision.fallback(ruleRoute, modelRoute, confidence,
                    "protected_triage_conflict: " + safeReason(payload.reason()),
                    result.model(),
                    result.attempt());
        }

        log.info("[supervisor-ai] route classified trace_id={} chat_id={} model={} attempt={} model_route={} confidence={} rule_route={} reason={}",
                request.traceId(),
                request.chatId(),
                result.model(),
                result.attempt(),
                modelRoute,
                confidence,
                ruleRoute,
                payload.reason());
        return RouteDecision.llm(ruleRoute, modelRoute, confidence, safeReason(payload.reason()),
                result.model(), result.attempt());
    }

    private boolean shouldUseModel(ChatRequest request, AgentRoute ruleRoute) {
        return llmEnabled
                && ruleRoute != AgentRoute.HUMAN_REVIEW
                && !hasExplicitAction(request)
                && hasText(request.message());
    }

    private String ruleBypassReason(ChatRequest request, AgentRoute ruleRoute) {
        if (!llmEnabled) {
            return "llm_disabled";
        }
        if (ruleRoute == AgentRoute.HUMAN_REVIEW) {
            return "human_review_safety_rule";
        }
        if (hasExplicitAction(request)) {
            return "explicit_action";
        }
        if (!hasText(request.message())) {
            return "blank_message";
        }
        return "rule_only";
    }

    private boolean hasExplicitAction(ChatRequest request) {
        String action = request.metadata().get("action");
        return hasText(action);
    }

    private boolean shouldProtectRuleRoute(AgentRoute ruleRoute, AgentRoute modelRoute) {
        return ruleRoute == AgentRoute.TRIAGE
                && modelRoute != AgentRoute.TRIAGE
                && modelRoute != AgentRoute.HUMAN_REVIEW;
    }

    private RoutePayload parsePayload(String content) {
        String json = extractJson(content);
        try {
            return objectMapper.readValue(json, RoutePayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot parse supervisor route JSON: " + content, ex);
        }
    }

    private String extractJson(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Supervisor route result is empty");
        }
        String text = content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Supervisor route result is not JSON: " + content);
        }
        return text.substring(start, end + 1);
    }

    private AgentRoute parseRoute(String route) {
        if (!hasText(route)) {
            return null;
        }
        try {
            return AgentRoute.valueOf(route.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeReason(String reason) {
        if (!hasText(reason)) {
            return "";
        }
        String normalized = reason.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RoutePayload(String route, Double confidence, String reason) {
    }
}
