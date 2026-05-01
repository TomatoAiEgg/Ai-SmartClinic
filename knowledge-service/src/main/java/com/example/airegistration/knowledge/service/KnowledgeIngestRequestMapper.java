package com.example.airegistration.knowledge.service;

import com.example.airegistration.ai.config.AiModelFallbackProperties;
import com.example.airegistration.knowledge.config.KnowledgeIngestProperties;
import com.example.airegistration.knowledge.dto.KnowledgeChunkPayload;
import com.example.airegistration.knowledge.dto.KnowledgeDocumentPayload;
import com.example.airegistration.knowledge.dto.KnowledgeIngestApiRequest;
import com.example.airegistration.rag.core.KnowledgeChunkInput;
import com.example.airegistration.rag.core.KnowledgeDocumentInput;
import com.example.airegistration.rag.core.KnowledgeIngestRequest;
import com.example.airegistration.rag.core.SimpleTextChunker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIngestRequestMapper {

    private final AiModelFallbackProperties modelProperties;
    private final KnowledgeIngestProperties ingestProperties;

    public KnowledgeIngestRequestMapper(AiModelFallbackProperties modelProperties,
                                        KnowledgeIngestProperties ingestProperties) {
        this.modelProperties = modelProperties;
        this.ingestProperties = ingestProperties;
    }

    public KnowledgeIngestRequest toCoreRequest(KnowledgeIngestApiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String namespace = requireText(request.namespace(), "namespace");
        String sourceId = requireText(request.sourceId(), "sourceId");
        String sourceName = defaultText(request.sourceName(), sourceId, "sourceName");
        String embeddingModel = defaultText(
                request.embeddingModel(),
                modelProperties.getEmbedding().getDefaultModel(),
                "embeddingModel"
        );
        int embeddingDimensions = defaultPositive(
                request.embeddingDimensions(),
                modelProperties.getEmbedding().getDimensions(),
                "embeddingDimensions"
        );
        int chunkMaxChars = defaultPositive(
                request.chunkMaxChars(),
                ingestProperties.getChunkMaxChars(),
                "chunkMaxChars"
        );
        int chunkOverlapChars = defaultNonNegative(
                request.chunkOverlapChars(),
                ingestProperties.getChunkOverlapChars(),
                "chunkOverlapChars"
        );

        List<KnowledgeDocumentInput> documents = mapDocuments(
                request.documents(),
                namespace,
                sourceId,
                sourceName,
                chunkMaxChars,
                chunkOverlapChars
        );

        return new KnowledgeIngestRequest(
                namespace,
                sourceId,
                sourceName,
                embeddingModel,
                embeddingDimensions,
                documents,
                request.metadata()
        );
    }

    private List<KnowledgeDocumentInput> mapDocuments(List<KnowledgeDocumentPayload> documents,
                                                      String namespace,
                                                      String sourceId,
                                                      String sourceName,
                                                      int chunkMaxChars,
                                                      int chunkOverlapChars) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("documents must not be empty");
        }
        List<KnowledgeDocumentInput> mapped = new ArrayList<>();
        for (KnowledgeDocumentPayload document : documents) {
            mapped.add(mapDocument(document, namespace, sourceId, sourceName, chunkMaxChars, chunkOverlapChars));
        }
        return mapped;
    }

    private KnowledgeDocumentInput mapDocument(KnowledgeDocumentPayload document,
                                               String namespace,
                                               String sourceId,
                                               String sourceName,
                                               int chunkMaxChars,
                                               int chunkOverlapChars) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        String documentNamespace = defaultText(document.namespace(), namespace, "document.namespace");
        String documentSourceId = defaultText(document.sourceId(), sourceId, "document.sourceId");
        String documentSourceName = defaultText(document.sourceName(), sourceName, "document.sourceName");
        String rawContent = defaultText(document.rawContent(), joinChunkContent(document.chunks()), "document.rawContent");
        String title = defaultText(document.title(), documentSourceName, "document.title");
        List<KnowledgeChunkInput> chunks = mapChunks(
                document,
                rawContent,
                chunkMaxChars,
                chunkOverlapChars
        );

        return new KnowledgeDocumentInput(
                documentNamespace,
                documentSourceId,
                documentSourceName,
                document.documentType(),
                title,
                document.version(),
                rawContent,
                document.metadata(),
                chunks
        );
    }

    private List<KnowledgeChunkInput> mapChunks(KnowledgeDocumentPayload document,
                                                String rawContent,
                                                int chunkMaxChars,
                                                int chunkOverlapChars) {
        if (document.chunks().isEmpty()) {
            return SimpleTextChunker.chunk(rawContent, chunkMaxChars, chunkOverlapChars)
                    .stream()
                    .map(chunk -> new KnowledgeChunkInput(
                            chunk.chunkIndex(),
                            chunk.chunkType(),
                            chunk.title(),
                            chunk.content(),
                            chunk.tokenCount(),
                            mergeMetadata(document.metadata(), chunk.metadata())
                    ))
                    .toList();
        }

        List<KnowledgeChunkInput> mapped = new ArrayList<>();
        Set<Integer> indexes = new HashSet<>();
        for (int position = 0; position < document.chunks().size(); position++) {
            KnowledgeChunkPayload chunk = document.chunks().get(position);
            if (chunk == null) {
                throw new IllegalArgumentException("chunk must not be null");
            }
            int chunkIndex = chunk.chunkIndex() == null ? position : chunk.chunkIndex();
            if (!indexes.add(chunkIndex)) {
                throw new IllegalArgumentException("duplicate chunkIndex: " + chunkIndex);
            }
            mapped.add(new KnowledgeChunkInput(
                    chunkIndex,
                    chunk.chunkType(),
                    chunk.title(),
                    chunk.content(),
                    chunk.tokenCount(),
                    mergeMetadata(document.metadata(), chunk.metadata())
            ));
        }
        return mapped;
    }

    private String joinChunkContent(List<KnowledgeChunkPayload> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        List<String> content = new ArrayList<>();
        for (KnowledgeChunkPayload chunk : chunks) {
            if (chunk != null && hasText(chunk.content())) {
                content.add(chunk.content().trim());
            }
        }
        return String.join("\n\n", content);
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> base, Map<String, Object> overrides) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (overrides != null) {
            merged.putAll(overrides);
        }
        return Map.copyOf(merged);
    }

    private String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private String defaultText(String value, String fallback, String field) {
        return hasText(value) ? value.trim() : requireText(fallback, field);
    }

    private int defaultPositive(Integer value, Integer fallback, String field) {
        int resolved = value == null ? fallback == null ? 0 : fallback : value;
        if (resolved <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return resolved;
    }

    private int defaultNonNegative(Integer value, Integer fallback, String field) {
        int resolved = value == null ? fallback == null ? 0 : fallback : value;
        if (resolved < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return resolved;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
