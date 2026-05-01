package com.example.airegistration.gateway.exception;

import com.example.airegistration.enums.ApiErrorCode;

public class WechatLoginException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public WechatLoginException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WechatLoginException(ApiErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }
}
