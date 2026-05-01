package com.example.airegistration.dto;

public record PatientSummary(
        String patientId,
        String userId,
        String name,
        String idType,
        String maskedIdNumber,
        String maskedPhone,
        String relationCode,
        boolean defaultPatient
) {

    public PatientSummary(String patientId,
                          String userId,
                          String name,
                          String idType,
                          String maskedIdNumber,
                          String maskedPhone) {
        this(patientId, userId, name, idType, maskedIdNumber, maskedPhone, "SELF", false);
    }
}
