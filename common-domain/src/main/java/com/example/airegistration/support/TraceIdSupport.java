package com.example.airegistration.support;

import com.example.airegistration.dto.ChatRequest;
import java.util.UUID;

public final class TraceIdSupport {

    public static final String TRACE_HEADER = "X-Trace-Id";

    private TraceIdSupport() {
    }

    public static ChatRequest ensureTraceId(ChatRequest request) {
        if (request == null) {
            return null;
        }
        if (hasText(request.traceId())) {
            return request;
        }
        return request.withTraceId(generateTraceId());
    }

    public static String ensureTraceId(String traceId) {
        return hasText(traceId) ? traceId.trim() : generateTraceId();
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static String preview(String message) {
        if (!hasText(message)) {
            return "<blank>";
        }
        String normalized = message.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 48 ? normalized : normalized.substring(0, 48) + "...";
    }

    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
