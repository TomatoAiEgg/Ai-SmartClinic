package com.example.airegistration.rag.config;

import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import com.example.airegistration.rag.service.KnowledgeIngestService;
import com.example.airegistration.rag.service.PgvectorRagSearchService;
import com.example.airegistration.rag.service.RagRetrievalLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@AutoConfiguration(after = {DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnClass(NamedParameterJdbcOperations.class)
public class RagAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(NamedParameterJdbcOperations.class)
    public NamedParameterJdbcTemplate ragNamedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public RagRetrievalLogRepository ragRetrievalLogRepository(NamedParameterJdbcOperations jdbcOperations,
                                                               ObjectMapper objectMapper) {
        return new RagRetrievalLogRepository(jdbcOperations, objectMapper);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public PgvectorRagSearchService pgvectorRagSearchService(NamedParameterJdbcOperations jdbcOperations,
                                                             ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider,
                                                             ObjectProvider<RagRetrievalLogRepository> logRepositoryProvider) {
        return new PgvectorRagSearchService(jdbcOperations, embeddingClientProvider, logRepositoryProvider);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public KnowledgeIngestService knowledgeIngestService(NamedParameterJdbcOperations jdbcOperations,
                                                         ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider,
                                                         ObjectMapper objectMapper) {
        return new KnowledgeIngestService(jdbcOperations, embeddingClientProvider, objectMapper);
    }
}
