package com.example.airegistration.dto;

public record RegistrationSearchRequest(
        String userId,
        String clinicDate,
        String departmentCode,
        String doctorId,
        String status
) {
}
