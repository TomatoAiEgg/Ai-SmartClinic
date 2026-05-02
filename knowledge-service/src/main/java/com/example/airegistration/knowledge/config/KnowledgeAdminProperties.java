package com.example.airegistration.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.knowledge-admin")
public class KnowledgeAdminProperties {

    private String token = "";

    private String headerName = "X-Knowledge-Admin-Token";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token.trim();
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName == null || headerName.isBlank()
                ? "X-Knowledge-Admin-Token"
                : headerName.trim();
    }
}
