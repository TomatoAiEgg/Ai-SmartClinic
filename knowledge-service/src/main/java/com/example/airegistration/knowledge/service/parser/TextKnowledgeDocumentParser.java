package com.example.airegistration.knowledge.service.parser;

import com.example.airegistration.rag.core.KnowledgeChunkInput;
import com.example.airegistration.rag.core.SimpleTextChunker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TextKnowledgeDocumentParser implements KnowledgeDocumentParser {

    @Override
    public boolean supports(String documentType) {
        String normalized = normalize(documentType);
        return normalized.isBlank()
                || normalized.equals("TEXT")
                || normalized.equals("TXT")
                || normalized.equals("GUIDE")
                || normalized.equals("TRIAGE")
                || normalized.equals("POLICY")
                || normalized.equals("REGISTRATION_POLICY");
    }

    @Override
    public List<KnowledgeChunkInput> parse(String title,
                                           String rawContent,
                                           Map<String, Object> metadata,
                                           int chunkMaxChars,
                                           int chunkOverlapChars) {
        return SimpleTextChunker.chunk(rawContent, chunkMaxChars, chunkOverlapChars)
                .stream()
                .map(chunk -> new KnowledgeChunkInput(
                        chunk.chunkIndex(),
                        chunk.chunkType(),
                        firstText(chunk.title(), title),
                        chunk.content(),
                        chunk.tokenCount(),
                        mergeMetadata(metadata, chunk.metadata(), Map.of(
                                "parser", "text",
                                "contentFormat", "txt"
                        ))
                ))
                .toList();
    }

    protected Map<String, Object> mergeMetadata(Map<String, Object> first,
                                                Map<String, Object> second,
                                                Map<String, Object> third) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (first != null) {
            merged.putAll(first);
        }
        if (second != null) {
            merged.putAll(second);
        }
        if (third != null) {
            merged.putAll(third);
        }
        return Map.copyOf(merged);
    }

    protected String firstText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback == null ? "" : fallback.trim() : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
