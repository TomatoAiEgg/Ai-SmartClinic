package com.example.airegistration.registrationmcp;

import java.util.Map;

public class RegistrationOperationException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public RegistrationOperationException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
