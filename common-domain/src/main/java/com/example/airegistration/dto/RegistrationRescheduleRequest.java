package com.example.airegistration.dto;

public record RegistrationRescheduleRequest(
        String registrationId,
        String userId,
        String clinicDate,
        String startTime,
        boolean confirmed
) {
}
