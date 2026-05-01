package com.example.airegistration.registration.service;

import com.example.airegistration.ai.dto.AiChatRequest;
import com.example.airegistration.ai.dto.AiChatResult;
import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class RegistrationIntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(RegistrationIntentClassifier.class);
    private static final double MIN_CONFIDENCE = 0.65D;

    private final RegistrationFlowPolicy flowPolicy;
    private final ObjectProvider<AiChatClient> aiChatClientProvider;
    private final ObjectMapper objectMapper;
    private final boolean llmEnabled;

    public RegistrationIntentClassifier(RegistrationFlowPolicy flowPolicy,
                                        ObjectProvider<AiChatClient> aiChatClientProvider,
                                        ObjectMapper objectMapper,
                                        @Value("${app.ai.registration-intent.llm-enabled:true}") boolean llmEnabled) {
        this.flowPolicy = flowPolicy;
        this.aiChatClientProvider = aiChatClientProvider;
        this.objectMapper = objectMapper;
        this.llmEnabled = llmEnabled;
    }

    public Mono<RegistrationIntent> determineIntent(ChatRequest request) {
        RegistrationIntent ruleIntent = flowPolicy.determineIntent(request);
        if (!llmEnabled || hasExplicitAction(request) || flowPolicy.isBlank(request.message())) {
            return Mono.just(ruleIntent);
        }

        AiChatClient aiChatClient = aiChatClientProvider.getIfAvailable();
        if (aiChatClient == null) {
            log.debug("[registration-ai] AI chat client is unavailable, use rule intent. chat_id={} rule_intent={}",
                    request.chatId(),
                    ruleIntent);
            return Mono.just(ruleIntent);
        }

        return Mono.fromCallable(() -> classifyWithModel(aiChatClient, request, ruleIntent))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("[registration-ai] intent classification failed, use rule intent. chat_id={} rule_intent={} error={}",
                            request.chatId(),
                            ruleIntent,
                            ex.getMessage());
                    return Mono.just(ruleIntent);
                });
    }

    private RegistrationIntent classifyWithModel(AiChatClient aiChatClient,
                                                 ChatRequest request,
                                                 RegistrationIntent ruleIntent) {
        AiChatResult result = aiChatClient.call(AiChatRequest.builder("registration.intent.classify")
                .systemPrompt(systemPrompt())
                .userPrompt(userPrompt(request))
                .attributes(Map.of(
                        "agent", "REGISTRATION",
                        "chatId", request.chatId(),
                        "ruleIntent", ruleIntent.name()
                ))
                .build());
        IntentPayload payload = parsePayload(result.content());
        RegistrationIntent modelIntent = parseIntent(payload.intent());
        double confidence = payload.confidence() == null ? 0D : payload.confidence();

        if (modelIntent == null || confidence < MIN_CONFIDENCE) {
            log.info("[registration-ai] low confidence, use rule intent. chat_id={} model={} model_intent={} confidence={} rule_intent={}",
                    request.chatId(),
                    result.model(),
                    modelIntent,
                    confidence,
                    ruleIntent);
            return ruleIntent;
        }

        log.info("[registration-ai] intent classified chat_id={} model={} attempt={} model_intent={} confidence={} rule_intent={} reason={}",
                request.chatId(),
                result.model(),
                result.attempt(),
                modelIntent,
                confidence,
                ruleIntent,
                payload.reason());
        return modelIntent;
    }

    private String systemPrompt() {
        return """
                你是医院智能挂号系统的意图分类器。
                任务：判断用户在挂号业务里的操作意图。
                只允许输出一行 JSON，不要 Markdown，不要解释。

                intent 只能取以下枚举：
                - CREATE：用户想新建挂号、预约医生、找号源、挂某个科室
                - QUERY：用户想查询已有挂号结果、预约结果、挂号记录、订单状态
                - CANCEL：用户想取消挂号、退号
                - RESCHEDULE：用户想改约、换时间

                输出格式：
                {"intent":"QUERY","confidence":0.95,"reason":"用户想查询挂号结果"}
                """;
    }

    private String userPrompt(ChatRequest request) {
        return """
                用户输入：%s
                metadata：%s
                """.formatted(request.message(), request.metadata());
    }

    private IntentPayload parsePayload(String content) {
        String json = extractJson(content);
        try {
            return objectMapper.readValue(json, IntentPayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("无法解析大模型意图 JSON：" + content, ex);
        }
    }

    private String extractJson(String content) {
        if (content == null) {
            throw new IllegalArgumentException("大模型意图结果为空");
        }
        String text = content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("大模型意图结果不是 JSON：" + content);
        }
        return text.substring(start, end + 1);
    }

    private RegistrationIntent parseIntent(String intent) {
        if (intent == null || intent.isBlank()) {
            return null;
        }
        try {
            return RegistrationIntent.valueOf(intent.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean hasExplicitAction(ChatRequest request) {
        return !flowPolicy.isBlank(request.metadata().get("action"));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IntentPayload(String intent, Double confidence, String reason) {
    }
}
