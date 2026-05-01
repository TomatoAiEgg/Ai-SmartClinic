package com.example.airegistration.rag.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RagSearchSpec(
        String corpusName,
        String tableName,
        String idColumn,
        String titleColumn,
        String contentColumn,
        String embeddingColumn,
        String namespaceColumn,
        String enabledColumn,
        String metadataColumn,
        Map<String, String> attributeColumns,
        List<String> extraWhereClauses
) {
    public RagSearchSpec {
        corpusName = requireText(corpusName, "corpusName");
        tableName = SqlIdentifier.require(tableName, "tableName");
        idColumn = SqlIdentifier.require(idColumn, "idColumn");
        titleColumn = SqlIdentifier.require(titleColumn, "titleColumn");
        contentColumn = SqlIdentifier.require(contentColumn, "contentColumn");
        embeddingColumn = SqlIdentifier.require(embeddingColumn, "embeddingColumn");
        namespaceColumn = SqlIdentifier.require(namespaceColumn, "namespaceColumn");
        enabledColumn = SqlIdentifier.require(enabledColumn, "enabledColumn");
        metadataColumn = SqlIdentifier.optional(metadataColumn, "metadataColumn");
        attributeColumns = validateAttributeColumns(attributeColumns);
        extraWhereClauses = List.copyOf(extraWhereClauses == null ? List.of() : extraWhereClauses);
    }

    public boolean hasMetadataColumn() {
        return metadataColumn != null && !metadataColumn.isBlank();
    }

    private static Map<String, String> validateAttributeColumns(Map<String, String> columns) {
        Map<String, String> validated = new LinkedHashMap<>();
        if (columns == null) {
            return validated;
        }
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            String key = requireText(entry.getKey(), "attribute key");
            validated.put(key, SqlIdentifier.require(entry.getValue(), "attribute column " + key));
        }
        return validated;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
