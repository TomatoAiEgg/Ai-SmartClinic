package com.example.airegistration.ai.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.example.airegistration.ai.config.AiModelFallbackProperties;
import com.example.airegistration.ai.config.AiModelRouteProperties;
import com.example.airegistration.ai.dto.FallbackChatResult;
import com.example.airegistration.ai.enums.AiModelFailureType;
import com.example.airegistration.ai.enums.AiModelStatusCode;
import com.example.airegistration.ai.exception.AiModelFallbackException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

public class FallbackChatClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackChatClient.class);

    private final ChatModel chatModel;
    private final AiModelFallbackProperties properties;
    private final Map<String, Instant> exhaustedUntilByModel = new ConcurrentHashMap<>();

    public FallbackChatClient(ChatModel chatModel, AiModelFallbackProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    public FallbackChatResult call(String userMessage) {
        return call(new Prompt(userMessage));
    }

    public FallbackChatResult call(Prompt prompt) {
        AiModelRouteProperties route = properties.getChat();
        List<String> models = modelCandidates();
        RuntimeException lastFailure = null;

        for (int index = 0; index < models.size(); index++) {
            String model = models.get(index);
            if (isModelExhausted(model)) {
                continue;
            }

            int attempts = route.getTransientRetries() + 1;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    ChatResponse response = chatModel.call(promptWithModel(prompt, model));
                    return new FallbackChatResult(model, index + 1, response);
                } catch (RuntimeException ex) {
                    lastFailure = ex;
                    if (attempt < attempts && isTransientCandidate(ex)) {
                        log.warn("Chat model [{}] transient failure, retry {}/{}. failureTypes={}",
                                model, attempt, attempts - 1, AiModelFailureType.classify(ex));
                        continue;
                    }

                    if (isExhaustedCandidate(ex)) {
                        markExhausted(model, route);
                    }
                    if (!route.isFallbackEnabled() || !hasAvailableNextModel(models, index) || !isFallbackCandidate(ex)) {
                        throw ex;
                    }
                    String nextModel = nextAvailableModel(models, index);
                    log.warn("Chat model [{}] failed, fallback to [{}]. failureTypes={}",
                            model, nextModel, AiModelFailureType.classify(ex));
                    break;
                }
            }
        }

        throw lastFailure == null ? new AiModelFallbackException(AiModelStatusCode.CHAT_MODEL_NOT_CONFIGURED) : lastFailure;
    }

    public ChatResponse callResponse(Prompt prompt) {
        return call(prompt).response();
    }

    public String callText(String userMessage) {
        return call(userMessage).content();
    }

    private List<String> modelCandidates() {
        Set<String> models = new LinkedHashSet<>();
        AiModelRouteProperties route = properties.getChat();
        addModels(models, route.getDefaultModel());
        route.getFallbackModels().forEach(model -> addModels(models, model));

        if (models.isEmpty() && chatModel.getDefaultOptions() != null
                && StringUtils.hasText(chatModel.getDefaultOptions().getModel())) {
            models.add(chatModel.getDefaultOptions().getModel());
        }

        if (models.isEmpty()) {
            throw new AiModelFallbackException(AiModelStatusCode.CHAT_MODEL_NOT_CONFIGURED);
        }
        return new ArrayList<>(models);
    }

    private void addModels(Set<String> models, String configuredModel) {
        if (!StringUtils.hasText(configuredModel)) {
            return;
        }
        Arrays.stream(configuredModel.split("[,;]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(models::add);
    }

    private Prompt promptWithModel(Prompt prompt, String model) {
        AiModelRouteProperties route = properties.getChat();
        DashScopeChatOptions options;
        if (prompt.getOptions() instanceof DashScopeChatOptions dashScopeOptions) {
            options = DashScopeChatOptions.fromOptions(dashScopeOptions);
        } else {
            options = DashScopeChatOptions.builder().build();
        }

        options.setModel(model);
        if (options.getTemperature() == null && route.getTemperature() != null) {
            options.setTemperature(route.getTemperature());
        }
        if (options.getMaxTokens() == null && route.getMaxTokens() != null) {
            options.setMaxTokens(route.getMaxTokens());
        }
        if (route.getEnableSearch() != null) {
            options.setEnableSearch(route.getEnableSearch());
        }

        return new Prompt(new ArrayList<>(prompt.getInstructions()), options);
    }

    private boolean isFallbackCandidate(RuntimeException ex) {
        return properties.getChat().isFallbackEnabled()
                && matchesAnyConfiguredType(AiModelFailureType.classify(ex), properties.getFallbackFailureTypes());
    }

    private boolean isExhaustedCandidate(RuntimeException ex) {
        return matchesAnyConfiguredType(AiModelFailureType.classify(ex), properties.getExhaustedFailureTypes());
    }

    private boolean isTransientCandidate(RuntimeException ex) {
        return matchesAnyConfiguredType(AiModelFailureType.classify(ex), properties.getTransientFailureTypes());
    }

    private boolean matchesAnyConfiguredType(EnumSet<AiModelFailureType> failureTypes,
                                             Set<AiModelFailureType> configuredTypes) {
        return configuredTypes.stream().anyMatch(failureTypes::contains);
    }

    private boolean hasAvailableNextModel(List<String> models, int currentIndex) {
        return nextAvailableModel(models, currentIndex) != null;
    }

    private String nextAvailableModel(List<String> models, int currentIndex) {
        for (int index = currentIndex + 1; index < models.size(); index++) {
            if (!isModelExhausted(models.get(index))) {
                return models.get(index);
            }
        }
        return null;
    }

    private boolean isModelExhausted(String model) {
        Instant exhaustedUntil = exhaustedUntilByModel.get(model);
        if (exhaustedUntil == null) {
            return false;
        }
        if (Instant.now().isAfter(exhaustedUntil)) {
            exhaustedUntilByModel.remove(model);
            return false;
        }
        return true;
    }

    private void markExhausted(String model, AiModelRouteProperties route) {
        Instant exhaustedUntil = Instant.now().plus(route.getExhaustedTtl());
        exhaustedUntilByModel.put(model, exhaustedUntil);
        log.warn("Chat model [{}] marked exhausted until [{}].", model, exhaustedUntil);
    }
}
