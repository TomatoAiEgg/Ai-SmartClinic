package com.example.airegistration.knowledge.service;

import com.example.airegistration.knowledge.dto.KnowledgeSearchApiRequest;
import com.example.airegistration.rag.core.RagSearchRequest;
import com.example.airegistration.rag.core.RagSearchResult;
import com.example.airegistration.rag.core.RagSearchSpec;
import com.example.airegistration.rag.service.PgvectorRagSearchService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService {

    private static final RagSearchSpec SEARCH_SPEC = new RagSearchSpec(
            "knowledge-service",
            "knowledge_chunk",
            "id",
            "title",
            "content",
            "embedding",
            "namespace",
            "enabled",
            "metadata",
            attributeColumns(),
            List.of()
    );

    private final PgvectorRagSearchService ragSearchService;

    public KnowledgeSearchService(PgvectorRagSearchService ragSearchService) {
        this.ragSearchService = ragSearchService;
    }

    public RagSearchResult search(KnowledgeSearchApiRequest request, String traceId, String chatId) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String namespace = requireText(request.namespace(), "namespace");
        String query = requireText(request.query(), "query");
        int topK = request.topK() == null ? 5 : Math.max(1, request.topK());
        double minScore = request.minScore() == null ? 0D : request.minScore();
        return ragSearchService.search(SEARCH_SPEC, new RagSearchRequest(
                traceId,
                chatId,
                namespace,
                query,
                topK,
                minScore,
                request.parameters()
        ));
    }

    private static Map<String, String> attributeColumns() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("documentId", "document_id");
        attributes.put("chunkType", "chunk_type");
        attributes.put("sourceId", "metadata ->> 'sourceId'");
        attributes.put("sourceName", "metadata ->> 'sourceName'");
        attributes.put("departmentCode", "metadata ->> 'departmentCode'");
        attributes.put("departmentName", "metadata ->> 'departmentName'");
        attributes.put("actionTag", "metadata ->> 'actionTag'");
        attributes.put("policyType", "metadata ->> 'policyType'");
        attributes.put("topic", "metadata ->> 'topic'");
        attributes.put("embeddingModel", "embedding_model");
        attributes.put("embeddingDimensions", "embedding_dimensions");
        return attributes;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
