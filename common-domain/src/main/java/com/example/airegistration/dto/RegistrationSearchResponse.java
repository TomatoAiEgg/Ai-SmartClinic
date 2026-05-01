package com.example.airegistration.dto;

import java.util.List;

public record RegistrationSearchResponse(List<RegistrationResult> records) {

    public RegistrationSearchResponse {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
