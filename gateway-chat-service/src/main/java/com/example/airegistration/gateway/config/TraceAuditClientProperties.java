package com.example.airegistration.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.trace-audit")
public class TraceAuditClientProperties {

    private String registrationBaseUrl = "http://localhost:10103";
    private String scheduleBaseUrl = "http://localhost:10102";
    private String knowledgeBaseUrl = "http://localhost:10200";

    public String getRegistrationBaseUrl() {
        return registrationBaseUrl;
    }

    public void setRegistrationBaseUrl(String registrationBaseUrl) {
        this.registrationBaseUrl = registrationBaseUrl;
    }

    public String getScheduleBaseUrl() {
        return scheduleBaseUrl;
    }

    public void setScheduleBaseUrl(String scheduleBaseUrl) {
        this.scheduleBaseUrl = scheduleBaseUrl;
    }

    public String getKnowledgeBaseUrl() {
        return knowledgeBaseUrl;
    }

    public void setKnowledgeBaseUrl(String knowledgeBaseUrl) {
        this.knowledgeBaseUrl = knowledgeBaseUrl;
    }
}
