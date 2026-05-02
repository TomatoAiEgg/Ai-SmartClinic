package com.example.airegistration.knowledge.service.parser;

import com.example.airegistration.rag.core.KnowledgeChunkInput;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentParserRegistry {

    private final List<KnowledgeDocumentParser> parsers;
    private final KnowledgeDocumentParser fallbackParser;

    public KnowledgeDocumentParserRegistry() {
        this(defaultParsers());
    }

    @Autowired
    public KnowledgeDocumentParserRegistry(List<KnowledgeDocumentParser> parsers) {
        this.parsers = List.copyOf(parsers == null || parsers.isEmpty() ? defaultParsers() : parsers);
        this.fallbackParser = this.parsers.stream()
                .filter(parser -> parser instanceof TextKnowledgeDocumentParser)
                .findFirst()
                .orElseGet(TextKnowledgeDocumentParser::new);
    }

    public static KnowledgeDocumentParserRegistry defaultRegistry() {
        return new KnowledgeDocumentParserRegistry(defaultParsers());
    }

    public List<KnowledgeChunkInput> parse(String documentType,
                                           String title,
                                           String rawContent,
                                           Map<String, Object> metadata,
                                           int chunkMaxChars,
                                           int chunkOverlapChars) {
        KnowledgeDocumentParser parser = parsers.stream()
                .filter(candidate -> candidate.supports(documentType))
                .findFirst()
                .orElse(fallbackParser);
        return parser.parse(title, rawContent, metadata, chunkMaxChars, chunkOverlapChars);
    }

    private static List<KnowledgeDocumentParser> defaultParsers() {
        return List.of(new MarkdownKnowledgeDocumentParser(), new TextKnowledgeDocumentParser());
    }
}
