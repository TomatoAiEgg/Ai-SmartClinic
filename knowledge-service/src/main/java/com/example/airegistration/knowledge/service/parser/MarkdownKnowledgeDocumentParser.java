package com.example.airegistration.knowledge.service.parser;

import com.example.airegistration.rag.core.KnowledgeChunkInput;
import com.example.airegistration.rag.core.SimpleTextChunker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarkdownKnowledgeDocumentParser extends TextKnowledgeDocumentParser {

    @Override
    public boolean supports(String documentType) {
        String normalized = documentType == null ? "" : documentType.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("MARKDOWN") || normalized.equals("MD");
    }

    @Override
    public List<KnowledgeChunkInput> parse(String title,
                                           String rawContent,
                                           Map<String, Object> metadata,
                                           int chunkMaxChars,
                                           int chunkOverlapChars) {
        List<MarkdownSection> sections = sections(rawContent, title);
        if (sections.isEmpty()) {
            return super.parse(title, rawContent, metadata, chunkMaxChars, chunkOverlapChars);
        }
        List<KnowledgeChunkInput> chunks = new ArrayList<>();
        for (MarkdownSection section : sections) {
            List<KnowledgeChunkInput> sectionChunks = SimpleTextChunker.chunk(
                    section.content(),
                    chunkMaxChars,
                    chunkOverlapChars
            );
            for (KnowledgeChunkInput chunk : sectionChunks) {
                Map<String, Object> parserMetadata = new LinkedHashMap<>();
                parserMetadata.put("parser", "markdown");
                parserMetadata.put("contentFormat", "markdown");
                parserMetadata.put("heading", section.heading());
                parserMetadata.put("headingLevel", section.level());
                parserMetadata.put("sectionIndex", section.index());
                chunks.add(new KnowledgeChunkInput(
                        chunks.size(),
                        "MARKDOWN_SECTION",
                        section.heading(),
                        chunk.content(),
                        chunk.tokenCount(),
                        mergeMetadata(metadata, chunk.metadata(), parserMetadata)
                ));
            }
        }
        return List.copyOf(chunks);
    }

    private List<MarkdownSection> sections(String rawContent, String fallbackTitle) {
        if (rawContent == null || rawContent.isBlank()) {
            return List.of();
        }
        String[] lines = rawContent.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<MarkdownSection> sections = new ArrayList<>();
        String currentHeading = firstText(fallbackTitle, "Document");
        int currentLevel = 0;
        StringBuilder currentContent = new StringBuilder();
        int sectionIndex = 0;
        boolean sawHeading = false;
        for (String line : lines) {
            Heading heading = heading(line);
            if (heading != null) {
                if (currentContent.toString().trim().length() > 0) {
                    sections.add(new MarkdownSection(sectionIndex++, currentLevel, currentHeading, currentContent.toString()));
                    currentContent.setLength(0);
                }
                currentHeading = heading.text();
                currentLevel = heading.level();
                sawHeading = true;
                continue;
            }
            currentContent.append(line).append('\n');
        }
        if (currentContent.toString().trim().length() > 0) {
            sections.add(new MarkdownSection(sectionIndex, currentLevel, currentHeading, currentContent.toString()));
        }
        return sawHeading ? sections : List.of();
    }

    private Heading heading(String line) {
        if (line == null || !line.startsWith("#")) {
            return null;
        }
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        if (level == 0 || level > 6 || level >= line.length() || line.charAt(level) != ' ') {
            return null;
        }
        String text = line.substring(level).trim();
        return text.isBlank() ? null : new Heading(level, text);
    }

    private record Heading(int level, String text) {
    }

    private record MarkdownSection(int index, int level, String heading, String content) {
    }
}
