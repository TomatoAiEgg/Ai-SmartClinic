package com.example.airegistration.domain;

public record RegistrationRescheduleRequest(
        String registrationId,
        String userId,
        String clinicDate,
        String startTime,
        boolean confirmed
) {
}
