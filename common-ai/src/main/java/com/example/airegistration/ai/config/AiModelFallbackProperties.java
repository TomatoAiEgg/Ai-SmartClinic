package com.example.airegistration.ai.config;

import com.example.airegistration.ai.enums.AiModelFailureType;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.service.model-router")
public class AiModelFallbackProperties {

    private AiModelRouteProperties chat = new AiModelRouteProperties();

    private AiModelRouteProperties embedding = new AiModelRouteProperties();

    private Set<AiModelFailureType> fallbackFailureTypes = EnumSet.of(
            AiModelFailureType.RATE_LIMITED,
            AiModelFailureType.QUOTA_EXHAUSTED,
            AiModelFailureType.TIMEOUT,
            AiModelFailureType.SERVICE_UNAVAILABLE,
            AiModelFailureType.SERVER_ERROR,
            AiModelFailureType.CONNECTION_ERROR
    );

    private Set<AiModelFailureType> exhaustedFailureTypes = EnumSet.of(
            AiModelFailureType.RATE_LIMITED,
            AiModelFailureType.QUOTA_EXHAUSTED
    );

    private Set<AiModelFailureType> transientFailureTypes = EnumSet.of(
            AiModelFailureType.TIMEOUT,
            AiModelFailureType.SERVICE_UNAVAILABLE,
            AiModelFailureType.SERVER_ERROR,
            AiModelFailureType.CONNECTION_ERROR
    );

    public AiModelRouteProperties getChat() {
        return chat;
    }

    public void setChat(AiModelRouteProperties chat) {
        this.chat = chat == null ? new AiModelRouteProperties() : chat;
    }

    public AiModelRouteProperties getEmbedding() {
        return embedding;
    }

    public void setEmbedding(AiModelRouteProperties embedding) {
        this.embedding = embedding == null ? new AiModelRouteProperties() : embedding;
    }

    public Set<AiModelFailureType> getFallbackFailureTypes() {
        return fallbackFailureTypes;
    }

    public void setFallbackFailureTypes(Set<AiModelFailureType> fallbackFailureTypes) {
        this.fallbackFailureTypes = normalizeTypes(fallbackFailureTypes);
    }

    public Set<AiModelFailureType> getExhaustedFailureTypes() {
        return exhaustedFailureTypes;
    }

    public void setExhaustedFailureTypes(Set<AiModelFailureType> exhaustedFailureTypes) {
        this.exhaustedFailureTypes = normalizeTypes(exhaustedFailureTypes);
    }

    public Set<AiModelFailureType> getTransientFailureTypes() {
        return transientFailureTypes;
    }

    public void setTransientFailureTypes(Set<AiModelFailureType> transientFailureTypes) {
        this.transientFailureTypes = normalizeTypes(transientFailureTypes);
    }

    private Set<AiModelFailureType> normalizeTypes(Set<AiModelFailureType> types) {
        return types == null || types.isEmpty()
                ? EnumSet.noneOf(AiModelFailureType.class)
                : EnumSet.copyOf(types);
    }
}
