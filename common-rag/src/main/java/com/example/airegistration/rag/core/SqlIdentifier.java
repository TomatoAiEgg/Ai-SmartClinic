package com.example.airegistration.rag.core;

import java.util.regex.Pattern;

final class SqlIdentifier {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private SqlIdentifier() {
    }

    static String require(String value, String field) {
        String identifier = optional(value, field);
        if (identifier == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return identifier;
    }

    static String optional(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String identifier = value.trim();
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(field + " is not a safe SQL identifier: " + value);
        }
        return identifier;
    }
}
