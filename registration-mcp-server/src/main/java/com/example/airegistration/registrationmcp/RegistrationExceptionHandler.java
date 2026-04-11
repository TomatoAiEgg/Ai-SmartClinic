package com.example.airegistration.registrationmcp;

import com.example.airegistration.domain.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RegistrationExceptionHandler {

    @ExceptionHandler(RegistrationOperationException.class)
    public ResponseEntity<ApiError> handleRegistrationOperationException(RegistrationOperationException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "REQUIRES_CONFIRMATION", "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        ApiError error = new ApiError(ex.getCode(), ex.getMessage(), ex.getDetails());
        return ResponseEntity.status(status).body(error);
    }
}
