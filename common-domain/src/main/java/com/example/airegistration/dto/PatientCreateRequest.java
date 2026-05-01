package com.example.airegistration.dto;

public record PatientCreateRequest(
        String userId,
        String name,
        String idType,
        String idNumber,
        String phone,
        String relationCode,
        boolean defaultPatient
) {
}
