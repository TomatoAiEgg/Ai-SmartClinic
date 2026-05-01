package com.example.airegistration.patientmcp.controller;

import com.example.airegistration.dto.ApiError;
import com.example.airegistration.patientmcp.exception.PatientOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PatientExceptionHandler {

    @ExceptionHandler(PatientOperationException.class)
    public ResponseEntity<ApiError> handlePatientOperationException(PatientOperationException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getErrorCode().httpStatus());
        ApiError error = new ApiError(ex.getErrorCode(), ex.getMessage(), ex.getDetails());
        return ResponseEntity.status(status).body(error);
    }
}
