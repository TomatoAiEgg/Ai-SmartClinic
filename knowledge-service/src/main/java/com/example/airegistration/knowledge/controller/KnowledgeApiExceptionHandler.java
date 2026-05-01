package com.example.airegistration.knowledge.controller;

import com.example.airegistration.dto.ApiError;
import com.example.airegistration.enums.ApiErrorCode;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = KnowledgeIngestController.class)
public class KnowledgeApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidRequest(IllegalArgumentException ex) {
        return new ApiError(ApiErrorCode.INVALID_REQUEST, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError unavailable(IllegalStateException ex) {
        return new ApiError(ApiErrorCode.REMOTE_ERROR, ex.getMessage(), Map.of());
    }
}
