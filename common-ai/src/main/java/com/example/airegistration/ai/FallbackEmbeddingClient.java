package com.example.airegistration.ai;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
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
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.StringUtils;

public class FallbackEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackEmbeddingClient.class);

    private final EmbeddingModel embeddingModel;

    private final AiModelFallbackProperties properties;

    private final Map<String, Instant> exhaustedUntilByModel = new ConcurrentHashMap<>();

    public FallbackEmbeddingClient(EmbeddingModel embeddingModel, AiModelFallbackProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    public FallbackEmbeddingResult call(List<String> texts) {
        return call(new EmbeddingRequest(texts, null));
    }

    public FallbackEmbeddingResult call(EmbeddingRequest request) {
        AiModelFallbackProperties.ModelRoute route = properties.getEmbedding();
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
                    EmbeddingResponse response = embeddingModel.call(requestWithModel(request, model));
                    return new FallbackEmbeddingResult(model, index + 1, response);
                } catch (RuntimeException ex) {
                    lastFailure = ex;
                    boolean retryCurrentModel = attempt < attempts && isTransientCandidate(ex);
                    if (retryCurrentModel) {
                        log.warn("AI embedding model [{}] transient failure, retry {}/{}. reason={}",
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
                    log.warn("AI embedding model [{}] failed, fallback to [{}]. reason={}",
                            model, nextModel, exceptionSummary(ex));
                    break;
                }
            }
        }

        throw lastFailure == null ? new IllegalStateException("No AI embedding model candidate configured.") : lastFailure;
    }

    public List<float[]> embed(List<String> texts) {
        return call(texts).embeddings();
    }

    public float[] embed(String text) {
        List<float[]> embeddings = embed(List.of(text));
        return embeddings.isEmpty() ? new float[0] : embeddings.get(0);
    }

    private List<String> modelCandidates() {
        Set<String> models = new LinkedHashSet<>();
        AiModelFallbackProperties.ModelRoute route = properties.getEmbedding();
        addModels(models, route.getDefaultModel());
        route.getFallbackModels().forEach(model -> addModels(models, model));

        if (models.isEmpty()) {
            throw new IllegalStateException("No AI embedding model candidate configured. Set ai.service.model-router.embedding.");
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

    private EmbeddingRequest requestWithModel(EmbeddingRequest request, String model) {
        AiModelFallbackProperties.ModelRoute route = properties.getEmbedding();
        DashScopeEmbeddingOptions.Builder builder = DashScopeEmbeddingOptions.builder().withModel(model);
        if (route.getDimensions() != null) {
            builder.withDimensions(route.getDimensions());
        }
        if (StringUtils.hasText(route.getTextType())) {
            builder.withTextType(route.getTextType());
        }
        return new EmbeddingRequest(new ArrayList<>(request.getInstructions()), builder.build());
    }

    private boolean isFallbackCandidate(RuntimeException ex) {
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
        log.warn("AI embedding model [{}] marked exhausted until [{}].", model, exhaustedUntil);
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
