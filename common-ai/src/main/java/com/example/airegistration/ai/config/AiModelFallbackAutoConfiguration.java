package com.example.airegistration.ai.config;

import com.example.airegistration.ai.service.AiChatClient;
import com.example.airegistration.ai.service.FallbackChatClient;
import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AiModelFallbackProperties.class)
public class AiModelFallbackAutoConfiguration {

    @Bean
    @ConditionalOnClass(ChatModel.class)
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean
    public FallbackChatClient fallbackChatClient(ChatModel chatModel, AiModelFallbackProperties properties) {
        return new FallbackChatClient(chatModel, properties);
    }

    @Bean
    @ConditionalOnBean(FallbackChatClient.class)
    @ConditionalOnMissingBean
    public AiChatClient aiChatClient(FallbackChatClient fallbackChatClient) {
        return new AiChatClient(fallbackChatClient);
    }

    @Bean
    @ConditionalOnClass(EmbeddingModel.class)
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnMissingBean
    public FallbackEmbeddingClient fallbackEmbeddingClient(EmbeddingModel embeddingModel,
                                                           AiModelFallbackProperties properties) {
        return new FallbackEmbeddingClient(embeddingModel, properties);
    }
}
