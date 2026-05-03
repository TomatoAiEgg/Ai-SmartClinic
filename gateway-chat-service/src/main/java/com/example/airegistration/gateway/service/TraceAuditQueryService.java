package com.example.airegistration.gateway.service;

import com.example.airegistration.gateway.client.TraceAuditClient;
import com.example.airegistration.gateway.dto.TraceAuditResponse;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Service;

@Service
public class TraceAuditQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final TraceAuditClient traceAuditClient;

    public TraceAuditQueryService(TraceAuditClient traceAuditClient) {
        this.traceAuditClient = traceAuditClient;
    }

    public Mono<TraceAuditResponse> queryTrace(String traceId, Integer limit) {
        if (traceId == null || traceId.isBlank()) {
            return Mono.error(new IllegalArgumentException("traceId must not be blank"));
        }
        return traceAuditClient.queryTrace(traceId.trim(), boundLimit(limit));
    }

    private int boundLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
