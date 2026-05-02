package com.example.airegistration.knowledge.service.parser;

import com.example.airegistration.rag.core.KnowledgeChunkInput;
import java.util.List;
import java.util.Map;

public interface KnowledgeDocumentParser {

    boolean supports(String documentType);

    List<KnowledgeChunkInput> parse(String title,
                                    String rawContent,
                                    Map<String, Object> metadata,
                                    int chunkMaxChars,
                                    int chunkOverlapChars);
}
