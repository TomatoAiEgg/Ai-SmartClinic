package com.example.airegistration.gateway.controller;

import com.example.airegistration.gateway.dto.TraceAuditResponse;
import com.example.airegistration.gateway.service.TraceAuditQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/traces")
public class TraceAuditController {

    private final TraceAuditQueryService queryService;

    public TraceAuditController(TraceAuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{traceId}/audits")
    public Mono<TraceAuditResponse> queryTraceAudits(@PathVariable String traceId,
                                                     @RequestParam(required = false) Integer limit) {
        return queryService.queryTrace(traceId, limit);
    }
}
