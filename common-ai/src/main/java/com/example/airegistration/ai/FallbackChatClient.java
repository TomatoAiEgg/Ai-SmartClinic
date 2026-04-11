package com.example.airegistration.ai;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
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
        AiModelFallbackProperties.ModelRoute route = properties.getChat();
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
                    boolean retryCurrentModel = attempt < attempts && isTransientCandidate(ex);
                    if (retryCurrentModel) {
                        log.warn("AI chat model [{}] transient failure, retry {}/{}. reason={}",
                                model, attempt, attempts - 1, exceptionSummary(ex));
                        continue;
                    }

                    if (isExhaustedCandidate(ex)) {
                        markExhausted(model, route);
                    }
                    if (!route.isFallbackEnabled() || !hasAvailableNextModel(models, index) || !isFallbackCandidate(ex)) {
                        throw ex;
                    }
                    String nextModel = nextAvailableModel(models, index);
                    log.warn("AI chat model [{}] failed, fallback to [{}]. reason={}",
                            model, nextModel, exceptionSummary(ex));
                    break;
                }
            }
        }

        throw lastFailure == null ? new IllegalStateException("No AI model candidate configured.") : lastFailure;
    }

    public ChatResponse callResponse(Prompt prompt) {
        return call(prompt).response();
    }

    public String callText(String userMessage) {
        return call(userMessage).content();
    }

    private List<String> modelCandidates() {
        Set<String> models = new LinkedHashSet<>();
        AiModelFallbackProperties.ModelRoute route = properties.getChat();
        addModels(models, route.getDefaultModel());
        route.getFallbackModels().forEach(model -> addModels(models, model));

        if (models.isEmpty() && chatModel.getDefaultOptions() != null
                && StringUtils.hasText(chatModel.getDefaultOptions().getModel())) {
            models.add(chatModel.getDefaultOptions().getModel());
        }

        if (models.isEmpty()) {
            throw new IllegalStateException("No AI model candidate configured. Set app.ai.models or DASHSCOPE_MODELS.");
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
        AiModelFallbackProperties.ModelRoute route = properties.getChat();
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
        if (!properties.getChat().isFallbackEnabled()) {
            return false;
        }
        String summary = exceptionSummary(ex).toLowerCase(Locale.ROOT);
        return properties.getFallbackErrorKeywords().stream()
                .filter(StringUtils::hasText)
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(summary::contains);
    }

    private boolean isExhaustedCandidate(RuntimeException ex) {
        String summary = exceptionSummary(ex).toLowerCase(Locale.ROOT);
        return properties.getExhaustedErrorKeywords().stream()
                .filter(StringUtils::hasText)
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(summary::contains);
    }

    private boolean isTransientCandidate(RuntimeException ex) {
        String summary = exceptionSummary(ex).toLowerCase(Locale.ROOT);
        return properties.getTransientErrorKeywords().stream()
                .filter(StringUtils::hasText)
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(summary::contains);
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

    private void markExhausted(String model, AiModelFallbackProperties.ModelRoute route) {
        Instant exhaustedUntil = Instant.now().plus(route.getExhaustedTtl());
        exhaustedUntilByModel.put(model, exhaustedUntil);
        log.warn("AI chat model [{}] marked exhausted until [{}].", model, exhaustedUntil);
    }

    private String exceptionSummary(Throwable throwable) {
        StringJoiner summary = new StringJoiner(" | ");
        Throwable current = throwable;
        while (current != null) {
            summary.add(current.getClass().getSimpleName() + ": " + current.getMessage());
            current = current.getCause();
        }
        return summary.toString();
    }
}
