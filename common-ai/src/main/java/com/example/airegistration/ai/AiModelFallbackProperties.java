package com.example.airegistration.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.service.model-router")
public class AiModelFallbackProperties {

    private ModelRoute chat = new ModelRoute();

    private ModelRoute embedding = new ModelRoute();

    private Set<String> fallbackErrorKeywords = new LinkedHashSet<>(List.of(
            "429",
            "rate limit",
            "too many requests",
            "throttl",
            "quota",
            "exceeded",
            "insufficient",
            "timeout",
            "timed out",
            "502",
            "503",
            "504",
            "service unavailable",
            "server error",
            "try again"
    ));

    private Set<String> exhaustedErrorKeywords = new LinkedHashSet<>(List.of(
            "429",
            "quota",
            "exceeded",
            "insufficient",
            "billing",
            "balance",
            "rate limit",
            "too many requests"
    ));

    private Set<String> transientErrorKeywords = new LinkedHashSet<>(List.of(
            "timeout",
            "timed out",
            "connection reset",
            "connection refused",
            "502",
            "503",
            "504",
            "service unavailable",
            "server error",
            "try again"
    ));

    public ModelRoute getChat() {
        return chat;
    }

    public void setChat(ModelRoute chat) {
        this.chat = chat == null ? new ModelRoute() : chat;
    }

    public ModelRoute getEmbedding() {
        return embedding;
    }

    public void setEmbedding(ModelRoute embedding) {
        this.embedding = embedding == null ? new ModelRoute() : embedding;
    }

    public Set<String> getFallbackErrorKeywords() {
        return fallbackErrorKeywords;
    }

    public void setFallbackErrorKeywords(Set<String> fallbackErrorKeywords) {
        this.fallbackErrorKeywords = fallbackErrorKeywords == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(fallbackErrorKeywords);
    }

    public Set<String> getExhaustedErrorKeywords() {
        return exhaustedErrorKeywords;
    }

    public void setExhaustedErrorKeywords(Set<String> exhaustedErrorKeywords) {
        this.exhaustedErrorKeywords = exhaustedErrorKeywords == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(exhaustedErrorKeywords);
    }

    public Set<String> getTransientErrorKeywords() {
        return transientErrorKeywords;
    }

    public void setTransientErrorKeywords(Set<String> transientErrorKeywords) {
        this.transientErrorKeywords = transientErrorKeywords == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(transientErrorKeywords);
    }

    public static class ModelRoute {

        private String defaultModel;

        private List<String> fallbackModels = new ArrayList<>();

        private boolean fallbackEnabled = true;

        private Duration exhaustedTtl = Duration.ofHours(1);

        private int transientRetries = 1;

        private Double temperature;

        private Integer maxTokens;

        private Boolean enableSearch;

        private Integer dimensions;

        private String textType;

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public List<String> getFallbackModels() {
            return fallbackModels;
        }

        public void setFallbackModels(List<String> fallbackModels) {
            this.fallbackModels = fallbackModels == null ? new ArrayList<>() : new ArrayList<>(fallbackModels);
        }

        public boolean isFallbackEnabled() {
            return fallbackEnabled;
        }

        public void setFallbackEnabled(boolean fallbackEnabled) {
            this.fallbackEnabled = fallbackEnabled;
        }

        public Duration getExhaustedTtl() {
            return exhaustedTtl;
        }

        public void setExhaustedTtl(Duration exhaustedTtl) {
            this.exhaustedTtl = exhaustedTtl == null ? Duration.ofHours(1) : exhaustedTtl;
        }

        public int getTransientRetries() {
            return transientRetries;
        }

        public void setTransientRetries(int transientRetries) {
            this.transientRetries = Math.max(transientRetries, 0);
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Boolean getEnableSearch() {
            return enableSearch;
        }

        public void setEnableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
        }

        public Integer getDimensions() {
            return dimensions;
        }

        public void setDimensions(Integer dimensions) {
            this.dimensions = dimensions;
        }

        public String getTextType() {
            return textType;
        }

        public void setTextType(String textType) {
            this.textType = textType;
        }
    }
}
