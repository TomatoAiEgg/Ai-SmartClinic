package com.example.airegistration.dto;

import java.util.List;

public record PatientListResponse(List<PatientSummary> patients) {

    public PatientListResponse {
        patients = patients == null ? List.of() : List.copyOf(patients);
    }
}
