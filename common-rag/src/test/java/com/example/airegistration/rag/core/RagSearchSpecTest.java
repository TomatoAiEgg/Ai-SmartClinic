package com.example.airegistration.rag.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagSearchSpecTest {

    @Test
    void shouldValidateSqlIdentifiers() {
        assertThatThrownBy(() -> new RagSearchSpec(
                "test",
                "knowledge;drop table users",
                "id",
                "title",
                "content",
                "embedding",
                "namespace",
                "enabled",
                null,
                Map.of(),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowSafeMetadataProjectionExpressions() {
        RagSearchSpec spec = new RagSearchSpec(
                "test",
                "knowledge_chunk",
                "id",
                "title",
                "content",
                "embedding",
                "namespace",
                "enabled",
                "metadata",
                Map.of("departmentCode", "metadata ->> 'departmentCode'"),
                null
        );

        assertThat(spec.attributeColumns()).containsEntry("departmentCode", "metadata ->> 'departmentCode'");
    }

    @Test
    void shouldRejectUnsafeProjectionExpressions() {
        assertThatThrownBy(() -> new RagSearchSpec(
                "test",
                "knowledge_chunk",
                "id",
                "title",
                "content",
                "embedding",
                "namespace",
                "enabled",
                "metadata",
                Map.of("bad", "metadata ->> 'x'; drop table knowledge_chunk"),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRenderVectorLiteral() {
        assertThat(VectorLiteral.from(new float[]{0.1F, 0.2F})).isEqualTo("[0.1,0.2]");
    }

    @Test
    void shouldCreateOverlappingTextChunks() {
        String content = "a".repeat(260) + "b".repeat(260);

        var chunks = SimpleTextChunker.chunk(content, 300, 50);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
        assertThat(chunks.get(0).content()).hasSize(300);
        assertThat(chunks.get(1).content()).hasSize(270);
        assertThat(chunks.get(0).metadata()).containsEntry("chunker", "simple-text");
    }

    @Test
    void shouldAllowNullHitAttributesFromOptionalMetadataColumns() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("departmentCode", null);

        RagSearchHit hit = new RagSearchHit("id", "title", "content", "{}", 0.8D, attributes);

        assertThat(hit.attributes()).containsEntry("departmentCode", null);
    }
}
