package com.example.airegistration.ai.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AiModelRouteProperties {

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
