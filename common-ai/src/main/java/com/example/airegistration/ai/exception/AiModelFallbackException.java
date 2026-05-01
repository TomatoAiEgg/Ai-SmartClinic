package com.example.airegistration.ai.exception;

import com.example.airegistration.ai.enums.AiModelStatusCode;

public class AiModelFallbackException extends IllegalStateException {

    private final AiModelStatusCode statusCode;

    public AiModelFallbackException(AiModelStatusCode statusCode) {
        super(statusCode.message());
        this.statusCode = statusCode;
    }

    public AiModelStatusCode getStatusCode() {
        return statusCode;
    }
}
