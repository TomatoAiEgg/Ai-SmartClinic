package com.example.airegistration.dto;

import com.example.airegistration.enums.ApiErrorCode;
import java.util.Map;

public record ApiError(int code, String message, Map<String, Object> details) {

    public ApiError {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ApiError(ApiErrorCode errorCode, Map<String, Object> details) {
        this(errorCode, errorCode.message(), details);
    }

    public ApiError(ApiErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode.code(), normalizeMessage(errorCode, message), details);
    }

    private static String normalizeMessage(ApiErrorCode errorCode, String message) {
        return message == null || message.isBlank() ? errorCode.message() : message;
    }
}
