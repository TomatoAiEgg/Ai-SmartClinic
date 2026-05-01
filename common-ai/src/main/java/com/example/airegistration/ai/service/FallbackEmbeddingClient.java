package com.example.airegistration.ai.service;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.example.airegistration.ai.config.AiModelFallbackProperties;
import com.example.airegistration.ai.config.AiModelRouteProperties;
import com.example.airegistration.ai.dto.FallbackEmbeddingResult;
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
        AiModelRouteProperties route = properties.getEmbedding();
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
                    if (attempt < attempts && isTransientCandidate(ex)) {
                        log.warn("Embedding model [{}] transient failure, retry {}/{}. failureTypes={}",
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
                    log.warn("Embedding model [{}] failed, fallback to [{}]. failureTypes={}",
                            model, nextModel, AiModelFailureType.classify(ex));
                    break;
                }
            }
        }

        throw lastFailure == null ? new AiModelFallbackException(AiModelStatusCode.EMBEDDING_MODEL_NOT_CONFIGURED) : lastFailure;
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
        AiModelRouteProperties route = properties.getEmbedding();
        addModels(models, route.getDefaultModel());
        route.getFallbackModels().forEach(model -> addModels(models, model));

        if (models.isEmpty()) {
            throw new AiModelFallbackException(AiModelStatusCode.EMBEDDING_MODEL_NOT_CONFIGURED);
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
        AiModelRouteProperties route = properties.getEmbedding();
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
        return properties.getEmbedding().isFallbackEnabled()
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
        log.warn("Embedding model [{}] marked exhausted until [{}].", model, exhaustedUntil);
    }
}
