package com.example.airegistration.domain;

public record RegistrationResult(
        String registrationId,
        String status,
        String message,
        String patientId,
        String departmentCode,
        String doctorId,
        String clinicDate,
        String startTime
) {
}
