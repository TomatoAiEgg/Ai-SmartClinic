package com.example.airegistration.knowledge.controller;

import com.example.airegistration.knowledge.dto.KnowledgeIngestApiRequest;
import com.example.airegistration.knowledge.service.KnowledgeIngestRequestMapper;
import com.example.airegistration.rag.core.KnowledgeIngestRequest;
import com.example.airegistration.rag.core.KnowledgeIngestResult;
import com.example.airegistration.rag.service.KnowledgeIngestService;
import com.example.airegistration.support.TraceIdSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeIngestController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestController.class);

    private final KnowledgeIngestRequestMapper requestMapper;
    private final KnowledgeIngestService ingestService;

    public KnowledgeIngestController(KnowledgeIngestRequestMapper requestMapper,
                                     KnowledgeIngestService ingestService) {
        this.requestMapper = requestMapper;
        this.ingestService = ingestService;
    }

    @PostMapping("/ingest")
    public Mono<KnowledgeIngestResult> ingest(
            @RequestBody KnowledgeIngestApiRequest request,
            @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        KnowledgeIngestRequest ingestRequest = requestMapper.toCoreRequest(request);
        log.info("[knowledge] ingest inbound trace_id={} namespace={} source_id={} document_count={}",
                traceId,
                ingestRequest.namespace(),
                ingestRequest.sourceId(),
                ingestRequest.documents().size());
        return Mono.fromCallable(() -> ingestService.ingest(ingestRequest))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> log.info(
                        "[knowledge] ingest outbound trace_id={} job_id={} status={} document_count={} chunk_count={}",
                        traceId,
                        result.jobId(),
                        result.status(),
                        result.documentCount(),
                        result.chunkCount()
                ));
    }
}
