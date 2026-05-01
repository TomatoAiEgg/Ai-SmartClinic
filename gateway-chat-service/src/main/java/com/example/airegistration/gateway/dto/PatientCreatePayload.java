package com.example.airegistration.gateway.dto;

public record PatientCreatePayload(
        String name,
        String idType,
        String idNumber,
        String phone,
        String relationCode,
        boolean defaultPatient
) {
}
