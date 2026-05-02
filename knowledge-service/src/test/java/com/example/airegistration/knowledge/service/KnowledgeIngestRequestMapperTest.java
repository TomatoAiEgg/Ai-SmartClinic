package com.example.airegistration.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.airegistration.ai.config.AiModelFallbackProperties;
import com.example.airegistration.ai.config.AiModelRouteProperties;
import com.example.airegistration.knowledge.config.KnowledgeIngestProperties;
import com.example.airegistration.knowledge.dto.KnowledgeChunkPayload;
import com.example.airegistration.knowledge.dto.KnowledgeDocumentPayload;
import com.example.airegistration.knowledge.dto.KnowledgeIngestApiRequest;
import com.example.airegistration.rag.core.KnowledgeIngestRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeIngestRequestMapperTest {

    private final KnowledgeIngestRequestMapper mapper = new KnowledgeIngestRequestMapper(
            modelProperties(),
            ingestProperties()
    );

    @Test
    void shouldBuildCoreRequestAndAutoChunkRawDocument() {
        KnowledgeIngestApiRequest request = new KnowledgeIngestApiRequest(
                "default-triage-knowledge",
                "triage-seed",
                "Triage Seed",
                null,
                null,
                220,
                20,
                List.of(new KnowledgeDocumentPayload(
                        null,
                        "respiratory-cough",
                        "Respiratory Cough",
                        "TRIAGE",
                        "Cough Triage",
                        "v1",
                        "Cough with fever should consider respiratory department first. ".repeat(8),
                        Map.of("departmentCode", "RESP", "departmentName", "Respiratory", "emergency", false),
                        List.of()
                )),
                Map.of("operator", "seed")
        );

        KnowledgeIngestRequest core = mapper.toCoreRequest(request);

        assertThat(core.embeddingModel()).isEqualTo("text-embedding-v2");
        assertThat(core.embeddingDimensions()).isEqualTo(1536);
        assertThat(core.metadata()).containsEntry("operator", "seed");
        assertThat(core.documents()).hasSize(1);
        assertThat(core.documents().get(0).chunks()).isNotEmpty();
        assertThat(core.documents().get(0).chunks().get(0).metadata())
                .containsEntry("departmentCode", "RESP")
                .containsEntry("chunker", "simple-text");
    }

    @Test
    void shouldMapExplicitChunksAndAllowChunkMetadataOverride() {
        KnowledgeIngestApiRequest request = new KnowledgeIngestApiRequest(
                "default-guide-knowledge",
                "guide-seed",
                "Guide Seed",
                "custom-embedding",
                1024,
                null,
                null,
                List.of(new KnowledgeDocumentPayload(
                        null,
                        null,
                        null,
                        "GUIDE",
                        "Guide Doc",
                        "v1",
                        null,
                        Map.of("sourceId", "guide-doc", "sourceName", "Guide Doc"),
                        List.of(new KnowledgeChunkPayload(
                                3,
                                "FAQ",
                                "Floor",
                                "The clinic is on floor 2.",
                                7,
                                Map.of("sourceName", "Floor Guide")
                        ))
                )),
                Map.of()
        );

        KnowledgeIngestRequest core = mapper.toCoreRequest(request);

        assertThat(core.embeddingModel()).isEqualTo("custom-embedding");
        assertThat(core.documents().get(0).rawContent()).isEqualTo("The clinic is on floor 2.");
        assertThat(core.documents().get(0).chunks().get(0).chunkIndex()).isEqualTo(3);
        assertThat(core.documents().get(0).chunks().get(0).metadata())
                .containsEntry("sourceId", "guide-doc")
                .containsEntry("sourceName", "Floor Guide");
    }

    @Test
    void shouldRejectDuplicateChunkIndex() {
        KnowledgeIngestApiRequest request = new KnowledgeIngestApiRequest(
                "default-guide-knowledge",
                "guide-seed",
                "Guide Seed",
                null,
                null,
                null,
                null,
                List.of(new KnowledgeDocumentPayload(
                        null,
                        null,
                        null,
                        "GUIDE",
                        "Guide Doc",
                        "v1",
                        null,
                        Map.of(),
                        List.of(
                                new KnowledgeChunkPayload(0, "TEXT", "", "first", null, Map.of()),
                                new KnowledgeChunkPayload(0, "TEXT", "", "second", null, Map.of())
                        )
                )),
                Map.of()
        );

        assertThatThrownBy(() -> mapper.toCoreRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate chunkIndex");
    }

    private static AiModelFallbackProperties modelProperties() {
        AiModelRouteProperties embedding = new AiModelRouteProperties();
        embedding.setDefaultModel("text-embedding-v2");
        embedding.setDimensions(1536);
        AiModelFallbackProperties properties = new AiModelFallbackProperties();
        properties.setEmbedding(embedding);
        return properties;
    }

    private static KnowledgeIngestProperties ingestProperties() {
        KnowledgeIngestProperties properties = new KnowledgeIngestProperties();
        properties.setChunkMaxChars(900);
        properties.setChunkOverlapChars(120);
        return properties;
    }
}
