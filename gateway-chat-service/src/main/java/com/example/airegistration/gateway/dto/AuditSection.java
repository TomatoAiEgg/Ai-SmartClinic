package com.example.airegistration.gateway.dto;

import java.util.List;

public record AuditSection<T>(
        String source,
        List<T> records,
        String error
) {
    public AuditSection {
        records = records == null ? List.of() : List.copyOf(records);
    }

    public static <T> AuditSection<T> success(String source, List<T> records) {
        return new AuditSection<>(source, records, null);
    }

    public static <T> AuditSection<T> failure(String source, String error) {
        return new AuditSection<>(source, List.of(), error);
    }
}
