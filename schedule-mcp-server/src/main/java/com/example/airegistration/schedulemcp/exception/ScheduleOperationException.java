package com.example.airegistration.schedulemcp.exception;

import com.example.airegistration.enums.ApiErrorCode;
import java.util.Map;

public class ScheduleOperationException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final Map<String, Object> details;

    public ScheduleOperationException(ApiErrorCode errorCode, String message, Map<String, Object> details) {
        super(message == null || message.isBlank() ? errorCode.message() : message);
        this.errorCode = errorCode;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public int getCode() {
        return errorCode.code();
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
