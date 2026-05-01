package com.example.airegistration.dto;

public record RegistrationQueryRequest(String registrationId, String userId) {

    public RegistrationQueryRequest(String registrationId) {
        this(registrationId, null);
    }
}
