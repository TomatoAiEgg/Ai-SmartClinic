package com.example.airegistration.domain;

public record PatientSummary(String patientId, String userId, String name, String idType, String maskedIdNumber, String maskedPhone) {
}
