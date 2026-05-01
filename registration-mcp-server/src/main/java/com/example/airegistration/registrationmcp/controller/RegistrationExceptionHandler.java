package com.example.airegistration.registrationmcp.controller;

import com.example.airegistration.dto.ApiError;
import com.example.airegistration.registrationmcp.exception.RegistrationOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RegistrationExceptionHandler {

    @ExceptionHandler(RegistrationOperationException.class)
    public ResponseEntity<ApiError> handleRegistrationOperationException(RegistrationOperationException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getErrorCode().httpStatus());
        ApiError error = new ApiError(ex.getErrorCode(), ex.getMessage(), ex.getDetails());
        return ResponseEntity.status(status).body(error);
    }
}
