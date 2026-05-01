package com.example.airegistration.registration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mcp")
public class McpClientProperties {

    private Endpoint patient = new Endpoint("");
    private Endpoint schedule = new Endpoint("");
    private Endpoint registration = new Endpoint("");

    public Endpoint getPatient() {
        return patient;
    }

    public void setPatient(Endpoint patient) {
        this.patient = patient == null ? new Endpoint("") : patient;
    }

    public Endpoint getSchedule() {
        return schedule;
    }

    public void setSchedule(Endpoint schedule) {
        this.schedule = schedule == null ? new Endpoint("") : schedule;
    }

    public Endpoint getRegistration() {
        return registration;
    }

    public void setRegistration(Endpoint registration) {
        this.registration = registration == null ? new Endpoint("") : registration;
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
