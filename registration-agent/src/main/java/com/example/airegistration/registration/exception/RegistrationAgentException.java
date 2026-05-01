package com.example.airegistration.registration.exception;

import com.example.airegistration.dto.ApiError;

public class RegistrationAgentException extends RuntimeException {

    private final ApiError error;
    private final String source;

    public RegistrationAgentException(ApiError error, String source) {
        super(error.message());
        this.error = error;
        this.source = source;
    }

    public ApiError error() {
        return error;
    }

    public String source() {
        return source;
    }
}
