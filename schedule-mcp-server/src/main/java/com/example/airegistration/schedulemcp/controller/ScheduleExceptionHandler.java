package com.example.airegistration.schedulemcp.controller;

import com.example.airegistration.dto.ApiError;
import com.example.airegistration.schedulemcp.exception.ScheduleOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ScheduleExceptionHandler {

    @ExceptionHandler(ScheduleOperationException.class)
    public ResponseEntity<ApiError> handleScheduleOperationException(ScheduleOperationException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getErrorCode().httpStatus());
        ApiError error = new ApiError(ex.getErrorCode(), ex.getMessage(), ex.getDetails());
        return ResponseEntity.status(status).body(error);
    }
}
