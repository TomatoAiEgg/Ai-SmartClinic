package com.example.airegistration.gateway.exception;

import com.example.airegistration.dto.ApiError;
import com.example.airegistration.enums.ApiErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice(basePackages = "com.example.airegistration.gateway")
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(WechatLoginException.class)
    public ResponseEntity<ApiError> handleWechatLoginException(WechatLoginException ex) {
        ApiErrorCode errorCode = ex.getErrorCode();
        log.warn("[gateway-error] wechat login failed, code={}, message={}", errorCode.code(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.valueOf(errorCode.httpStatus()))
                .body(new ApiError(errorCode, ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex) {
        log.warn("[gateway-error] authentication failed, message={}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(ApiErrorCode.UNAUTHORIZED, "登录状态无效，请重新登录。", Map.of()));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> handleRemoteResponseException(WebClientResponseException ex) {
        ApiError error = readRemoteError(ex);
        log.warn("[gateway-error] remote service failed, status={}, message={}",
                ex.getStatusCode().value(),
                error.message());
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(error);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex) {
        log.warn("[gateway-error] bad request, message={}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(ApiErrorCode.INVALID_REQUEST, ex.getMessage(), Map.of()));
    }

    private ApiError readRemoteError(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            try {
                return objectMapper.readValue(body, ApiError.class);
            } catch (Exception ignored) {
                return new ApiError(ApiErrorCode.REMOTE_ERROR, body, Map.of());
            }
        }
        return new ApiError(
                ApiErrorCode.REMOTE_ERROR,
                "下游服务调用失败：HTTP " + ex.getStatusCode().value(),
                Map.of()
        );
    }
}
