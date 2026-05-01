package com.example.airegistration.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.knowledge-ingest")
public class KnowledgeIngestProperties {

    private int chunkMaxChars = 900;

    private int chunkOverlapChars = 120;

    public int getChunkMaxChars() {
        return chunkMaxChars;
    }

    public void setChunkMaxChars(int chunkMaxChars) {
        this.chunkMaxChars = Math.max(200, chunkMaxChars);
    }

    public int getChunkOverlapChars() {
        return chunkOverlapChars;
    }

    public void setChunkOverlapChars(int chunkOverlapChars) {
        this.chunkOverlapChars = Math.max(0, chunkOverlapChars);
    }
}
