package com.example.airegistration.rag.core;

import java.util.regex.Pattern;

final class SqlProjectionExpression {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern SAFE_JSON_TEXT_EXPRESSION = Pattern.compile(
            "[a-zA-Z_][a-zA-Z0-9_]*\\s*->>\\s*'[a-zA-Z_][a-zA-Z0-9_]*'"
    );

    private SqlProjectionExpression() {
    }

    static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String expression = value.trim();
        if (!SAFE_IDENTIFIER.matcher(expression).matches()
                && !SAFE_JSON_TEXT_EXPRESSION.matcher(expression).matches()) {
            throw new IllegalArgumentException(field + " is not a safe SQL projection expression: " + value);
        }
        return expression;
    }
}
