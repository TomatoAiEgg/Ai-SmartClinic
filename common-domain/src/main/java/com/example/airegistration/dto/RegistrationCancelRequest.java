package com.example.airegistration.dto;

public record RegistrationCancelRequest(
        String registrationId,
        String userId,
        boolean confirmed,
        String reason
) {
}
