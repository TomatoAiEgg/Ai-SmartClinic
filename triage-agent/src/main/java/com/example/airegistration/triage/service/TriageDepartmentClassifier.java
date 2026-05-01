package com.example.airegistration.triage.service;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.dto.AiChatResult;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.DepartmentSuggestion;
import com.example.airegistration.triage.service.policy.TriagePolicy;
import com.example.airegistration.triage.service.prompt.TriagePromptService;
import com.example.airegistration.triage.service.rag.TriageRagContext;
import com.example.airegistration.triage.service.rag.TriageRagService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class TriageDepartmentClassifier {

    private static final Logger log = LoggerFactory.getLogger(TriageDepartmentClassifier.class);
    private static final double MIN_CONFIDENCE = 0.65D;
    private static final Map<String, String> ALLOWED_DEPARTMENTS = Map.of(
            "RESP", "呼吸内科",
            "GI", "消化内科",
            "DERM", "皮肤科",
            "PED", "儿科",
            "GYN", "妇科",
            "OPH", "眼科",
            "NEURO", "神经内科",
            "GEN", "全科医学科",
            "ER", "急诊"
    );

    private final TriagePolicy triagePolicy;
    private final TriagePromptService promptService;
    private final ObjectProvider<AiChatClient> aiChatClientProvider;
    private final ObjectProvider<TriageRagService> ragServiceProvider;
    private final ObjectMapper objectMapper;
    private final boolean llmEnabled;
    private final boolean ragEnrichSpecialtyRule;

    public TriageDepartmentClassifier(TriagePolicy triagePolicy,
                                      TriagePromptService promptService,
                                      ObjectProvider<AiChatClient> aiChatClientProvider,
                                      ObjectProvider<TriageRagService> ragServiceProvider,
                                      ObjectMapper objectMapper,
                                      @Value("${app.ai.triage-department.llm-enabled:true}") boolean llmEnabled,
                                      @Value("${app.ai.triage-rag.enrich-specialty-rule:false}") boolean ragEnrichSpecialtyRule) {
        this.triagePolicy = triagePolicy;
        this.promptService = promptService;
        this.aiChatClientProvider = aiChatClientProvider;
        this.ragServiceProvider = ragServiceProvider;
        this.objectMapper = objectMapper;
        this.llmEnabled = llmEnabled;
        this.ragEnrichSpecialtyRule = ragEnrichSpecialtyRule;
    }

    public Mono<DepartmentSuggestion> suggestDepartment(ChatRequest request) {
        DepartmentSuggestion ruleSuggestion = triagePolicy.suggestDepartment(request.message());
        if (shouldUseRuleImmediately(request, ruleSuggestion)) {
            log.info("[triage-ai] use rule suggestion trace_id={} chat_id={} department_code={} reason={}",
                    request.traceId(),
                    request.chatId(),
                    ruleSuggestion.departmentCode(),
                    bypassReason(request, ruleSuggestion));
            return Mono.just(ruleSuggestion);
        }

        return retrieveRagContext(request)
                .flatMap(ragContext -> suggestWithContext(request, ruleSuggestion, ragContext));
    }

    private Mono<DepartmentSuggestion> suggestWithContext(ChatRequest request,
                                                          DepartmentSuggestion ruleSuggestion,
                                                          TriageRagContext ragContext) {
        if (!shouldUseModel(request, ruleSuggestion, ragContext)) {
            DepartmentSuggestion fallback = bestFallback(ruleSuggestion, ragContext);
            log.info("[triage-ai] use non-model suggestion trace_id={} chat_id={} department_code={} rag_hits={} reason={}",
                    request.traceId(),
                    request.chatId(),
                    fallback.departmentCode(),
                    ragContext.hits().size(),
                    fallback == ruleSuggestion ? bypassReason(request, ruleSuggestion) : "rag_fallback");
            return Mono.just(fallback);
        }

        AiChatClient aiChatClient = aiChatClientProvider.getIfAvailable();
        if (aiChatClient == null) {
            DepartmentSuggestion fallback = bestFallback(ruleSuggestion, ragContext);
            log.info("[triage-ai] AI chat client unavailable, use fallback suggestion trace_id={} chat_id={} department_code={} rag_hits={}",
                    request.traceId(),
                    request.chatId(),
                    fallback.departmentCode(),
                    ragContext.hits().size());
            return Mono.just(fallback);
        }

        return Mono.fromCallable(() -> classifyWithModel(aiChatClient, request, ruleSuggestion, ragContext))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    DepartmentSuggestion fallback = bestFallback(ruleSuggestion, ragContext);
                    log.warn("[triage-ai] department classification failed, use fallback suggestion trace_id={} chat_id={} fallback_department={} rule_department={} rag_hits={} error={}",
                            request.traceId(),
                            request.chatId(),
                            fallback.departmentCode(),
                            ruleSuggestion.departmentCode(),
                            ragContext.hits().size(),
                            ex.getMessage());
                    return Mono.just(fallback);
                });
    }

    private Mono<TriageRagContext> retrieveRagContext(ChatRequest request) {
        TriageRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            return Mono.just(TriageRagContext.empty());
        }
        return ragService.retrieve(request);
    }

    private DepartmentSuggestion classifyWithModel(AiChatClient aiChatClient,
                                                   ChatRequest request,
                                                   DepartmentSuggestion ruleSuggestion,
                                                   TriageRagContext ragContext) {
        AiChatResult result = aiChatClient.call(AiChatRequest.builder("triage.department.classify")
                .systemPrompt(promptService.departmentSystemPrompt())
                .userPrompt(promptService.departmentUserPrompt(request, suggestionData(ruleSuggestion), ragContext.toPromptData()))
                .attributes(Map.of(
                        "agent", "TRIAGE",
                        "chatId", request.chatId(),
                        "traceId", request.traceId(),
                        "ruleDepartment", ruleSuggestion.departmentCode(),
                        "ragHitCount", ragContext.hits().size()
                ))
                .build());

        DepartmentPayload payload = parsePayload(result.content());
        String departmentCode = normalizeCode(payload.departmentCode());
        double confidence = payload.confidence() == null ? 0D : payload.confidence();
        if (departmentCode == null || confidence < MIN_CONFIDENCE) {
            DepartmentSuggestion fallback = bestFallback(ruleSuggestion, ragContext);
            log.info("[triage-ai] low confidence, use fallback suggestion trace_id={} chat_id={} model={} model_department={} confidence={} fallback_department={} rule_department={} rag_hits={} reason={}",
                    request.traceId(),
                    request.chatId(),
                    result.model(),
                    payload.departmentCode(),
                    confidence,
                    fallback.departmentCode(),
                    ruleSuggestion.departmentCode(),
                    ragContext.hits().size(),
                    payload.reason());
            return fallback;
        }

        String departmentName = ALLOWED_DEPARTMENTS.get(departmentCode);
        boolean emergency = "ER".equals(departmentCode) || Boolean.TRUE.equals(payload.emergency());
        DepartmentSuggestion suggestion = new DepartmentSuggestion(
                departmentCode,
                departmentName,
                emergency,
                safeReason(payload.reason())
        );
        log.info("[triage-ai] department classified trace_id={} chat_id={} model={} attempt={} model_department={} confidence={} rule_department={} rag_hits={} evidence_ids={} reason={}",
                request.traceId(),
                request.chatId(),
                result.model(),
                result.attempt(),
                departmentCode,
                confidence,
                ruleSuggestion.departmentCode(),
                ragContext.hits().size(),
                payload.evidenceIds(),
                payload.reason());
        return suggestion;
    }

    private boolean shouldUseRuleImmediately(ChatRequest request, DepartmentSuggestion ruleSuggestion) {
        return !hasText(request.message())
                || ruleSuggestion.emergency()
                || (!ragEnrichSpecialtyRule && !"GEN".equals(ruleSuggestion.departmentCode()));
    }

    private boolean shouldUseModel(ChatRequest request, DepartmentSuggestion ruleSuggestion, TriageRagContext ragContext) {
        return llmEnabled
                && hasText(request.message())
                && !ruleSuggestion.emergency()
                && ("GEN".equals(ruleSuggestion.departmentCode()) || ragContext.hasHits());
    }

    private String bypassReason(ChatRequest request, DepartmentSuggestion ruleSuggestion) {
        if (!llmEnabled) {
            return "llm_disabled";
        }
        if (!hasText(request.message())) {
            return "blank_message";
        }
        if (ruleSuggestion.emergency()) {
            return "emergency_rule";
        }
        if (!"GEN".equals(ruleSuggestion.departmentCode())) {
            return "specialty_rule";
        }
        return "rule_only";
    }

    private Map<String, Object> suggestionData(DepartmentSuggestion suggestion) {
        return Map.of(
                "departmentCode", suggestion.departmentCode(),
                "departmentName", suggestion.departmentName(),
                "emergency", suggestion.emergency(),
                "reason", suggestion.reason()
        );
    }

    private DepartmentSuggestion bestFallback(DepartmentSuggestion ruleSuggestion, TriageRagContext ragContext) {
        DepartmentSuggestion ragSuggestion = ragContext.bestSuggestion(MIN_CONFIDENCE);
        if (ragSuggestion != null && ("GEN".equals(ruleSuggestion.departmentCode()) || ragSuggestion.emergency())) {
            return ragSuggestion;
        }
        return ruleSuggestion;
    }

    private DepartmentPayload parsePayload(String content) {
        String json = extractJson(content);
        try {
            return objectMapper.readValue(json, DepartmentPayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot parse triage department JSON: " + content, ex);
        }
    }

    private String extractJson(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Triage department result is empty");
        }
        String text = content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Triage department result is not JSON: " + content);
        }
        return text.substring(start, end + 1);
    }

    private String normalizeCode(String departmentCode) {
        if (!hasText(departmentCode)) {
            return null;
        }
        String normalized = departmentCode.trim().toUpperCase();
        return ALLOWED_DEPARTMENTS.containsKey(normalized) ? normalized : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeReason(String reason) {
        if (!hasText(reason)) {
            return "模型根据用户描述和RAG证据给出科室建议。";
        }
        String normalized = reason.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DepartmentPayload(String departmentCode, String departmentName, Boolean emergency,
                                     Double confidence, String reason, List<String> evidenceIds) {
    }
}
