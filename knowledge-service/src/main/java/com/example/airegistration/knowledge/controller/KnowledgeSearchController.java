package com.example.airegistration.knowledge.controller;

import com.example.airegistration.knowledge.dto.KnowledgeSearchApiRequest;
import com.example.airegistration.knowledge.service.KnowledgeSearchService;
import com.example.airegistration.rag.core.RagSearchResult;
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
public class KnowledgeSearchController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchController.class);

    private final KnowledgeSearchService searchService;

    public KnowledgeSearchController(KnowledgeSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/search")
    public Mono<RagSearchResult> search(
            @RequestBody KnowledgeSearchApiRequest request,
            @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId,
            @RequestHeader(value = "X-Chat-Id", required = false) String chatId) {
        log.info("[knowledge] search inbound trace_id={} chat_id={} namespace={} top_k={}",
                traceId,
                chatId,
                request == null ? null : request.namespace(),
                request == null ? null : request.topK());
        return Mono.fromCallable(() -> searchService.search(request, traceId, chatId))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> log.info(
                        "[knowledge] search outbound trace_id={} chat_id={} namespace={} status={} hit_count={}",
                        traceId,
                        chatId,
                        result.namespace(),
                        result.status(),
                        result.hits().size()
                ));
    }
}
