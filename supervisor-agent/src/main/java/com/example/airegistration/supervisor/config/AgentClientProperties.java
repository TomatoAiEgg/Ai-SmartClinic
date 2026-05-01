package com.example.airegistration.supervisor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agents")
public class AgentClientProperties {

    private Endpoint triage = new Endpoint("");
    private Endpoint registration = new Endpoint("");
    private Endpoint guide = new Endpoint("");

    public Endpoint getTriage() {
        return triage;
    }

    public void setTriage(Endpoint triage) {
        this.triage = triage == null ? new Endpoint("") : triage;
    }

    public Endpoint getRegistration() {
        return registration;
    }

    public void setRegistration(Endpoint registration) {
        this.registration = registration == null ? new Endpoint("") : registration;
    }

    public Endpoint getGuide() {
        return guide;
    }

    public void setGuide(Endpoint guide) {
        this.guide = guide == null ? new Endpoint("") : guide;
    }

    public static class Endpoint {
        private String baseUrl;

        public Endpoint() {
        }

        public Endpoint(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
