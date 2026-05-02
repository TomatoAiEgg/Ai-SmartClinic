package com.example.airegistration.knowledge.controller;

import com.example.airegistration.knowledge.dto.KnowledgeChunkView;
import com.example.airegistration.knowledge.dto.KnowledgeDocumentStatusUpdateRequest;
import com.example.airegistration.knowledge.dto.KnowledgeDocumentView;
import com.example.airegistration.knowledge.dto.KnowledgeIngestJobView;
import com.example.airegistration.knowledge.dto.KnowledgeRetrievalLogView;
import com.example.airegistration.knowledge.service.KnowledgeAdminService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeAdminService adminService;

    public KnowledgeAdminController(KnowledgeAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/jobs")
    public Mono<List<KnowledgeIngestJobView>> listJobs(
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) Integer limit) {
        return Mono.fromCallable(() -> adminService.listJobs(namespace, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/jobs/{jobId}")
    public Mono<KnowledgeIngestJobView> getJob(@PathVariable UUID jobId) {
        return Mono.fromCallable(() -> adminService.getJob(jobId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/documents")
    public Mono<List<KnowledgeDocumentView>> listDocuments(
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        return Mono.fromCallable(() -> adminService.listDocuments(namespace, status, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/documents/{documentId}")
    public Mono<KnowledgeDocumentView> getDocument(@PathVariable UUID documentId) {
        return Mono.fromCallable(() -> adminService.getDocument(documentId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/documents/{documentId}/status")
    public Mono<KnowledgeDocumentView> updateDocumentStatus(
            @PathVariable UUID documentId,
            @RequestBody KnowledgeDocumentStatusUpdateRequest request) {
        return Mono.fromCallable(() -> adminService.updateDocumentStatus(
                        documentId,
                        request == null ? null : request.status()
                ))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/chunks")
    public Mono<List<KnowledgeChunkView>> listChunks(
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer limit) {
        return Mono.fromCallable(() -> adminService.listChunks(namespace, enabled, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/retrieval-logs")
    public Mono<List<KnowledgeRetrievalLogView>> listRetrievalLogs(
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) Integer limit) {
        return Mono.fromCallable(() -> adminService.listRetrievalLogs(namespace, limit))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
