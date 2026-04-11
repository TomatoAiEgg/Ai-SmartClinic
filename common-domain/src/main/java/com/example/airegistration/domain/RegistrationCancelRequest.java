package com.example.airegistration.domain;

public record RegistrationCancelRequest(
        String registrationId,
        String userId,
        boolean confirmed,
        String reason
) {
}
