package com.example.airegistration.ai;

import com.example.airegistration.ai.config.AiModelFallbackProperties;
import com.example.airegistration.ai.config.AiModelRouteProperties;
import com.example.airegistration.ai.dto.FallbackChatResult;
import com.example.airegistration.ai.dto.FallbackEmbeddingResult;
import com.example.airegistration.ai.enums.AiModelFailureType;
import com.example.airegistration.ai.enums.AiModelStatusCode;
import com.example.airegistration.ai.exception.AiModelFallbackException;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiModelInfrastructureTest {

    @Test
    void routePropertiesShouldNormalizeUnsafeValues() {
        AiModelRouteProperties route = new AiModelRouteProperties();

        route.setFallbackModels(null);
        route.setTransientRetries(-10);
        route.setExhaustedTtl(null);

        assertThat(route.getFallbackModels()).isEmpty();
        assertThat(route.getTransientRetries()).isZero();
        assertThat(route.getExhaustedTtl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void fallbackPropertiesShouldNormalizeNullRoutesAndFailureTypes() {
        AiModelFallbackProperties properties = new AiModelFallbackProperties();

        properties.setChat(null);
        properties.setEmbedding(null);
        properties.setFallbackFailureTypes(null);

        assertThat(properties.getChat()).isNotNull();
        assertThat(properties.getEmbedding()).isNotNull();
        assertThat(properties.getFallbackFailureTypes()).isEmpty();
    }

    @Test
    void failureTypeShouldClassifyCommonProviderFailures() {
        EnumSet<AiModelFailureType> quota = AiModelFailureType.classify(
                new RuntimeException("insufficient quota, billing required"));
        EnumSet<AiModelFailureType> timeout = AiModelFailureType.classify(
                new RuntimeException("request timed out with 504"));
        EnumSet<AiModelFailureType> unknown = AiModelFailureType.classify(
                new RuntimeException("unexpected response"));

        assertThat(quota).contains(AiModelFailureType.QUOTA_EXHAUSTED);
        assertThat(timeout).contains(AiModelFailureType.TIMEOUT);
        assertThat(unknown).containsExactly(AiModelFailureType.UNKNOWN);
    }

    @Test
    void modelStatusCodeShouldUseNumericStatusAndMessage() {
        AiModelFallbackException exception = new AiModelFallbackException(
                AiModelStatusCode.CHAT_MODEL_NOT_CONFIGURED);

        assertThat(exception.getStatusCode()).isEqualTo(AiModelStatusCode.CHAT_MODEL_NOT_CONFIGURED);
        assertThat(exception.getStatusCode().statusCode()).isEqualTo(500);
        assertThat(AiModelStatusCode.MODEL_CALL_FAILED.statusCode()).isEqualTo(503);
        assertThat(exception.getMessage()).isEqualTo(AiModelStatusCode.CHAT_MODEL_NOT_CONFIGURED.message());
    }

    @Test
    void resultObjectsShouldBeNullSafe() {
        FallbackChatResult chatResult = new FallbackChatResult("model-a", 1, null);
        FallbackEmbeddingResult embeddingResult = new FallbackEmbeddingResult("embedding-a", 1, null);

        assertThat(chatResult.content()).isEmpty();
        assertThat(embeddingResult.embeddings()).isEqualTo(List.of());
    }
}
