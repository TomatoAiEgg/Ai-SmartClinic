package com.example.airegistration.registration.service.rag;

import com.example.airegistration.ai.service.FallbackEmbeddingClient;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.enums.RegistrationReplyScene;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RegistrationPolicyRagService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationPolicyRagService.class);

    private final RegistrationPolicyMapper mapper;
    private final ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String namespace;
    private final int topK;
    private final double minScore;

    public RegistrationPolicyRagService(RegistrationPolicyMapper mapper,
                                        ObjectProvider<FallbackEmbeddingClient> embeddingClientProvider,
                                        ObjectMapper objectMapper,
                                        @Value("${app.ai.registration-policy-rag.enabled:true}") boolean enabled,
                                        @Value("${app.ai.registration-policy-rag.namespace:default-registration-policy}") String namespace,
                                        @Value("${app.ai.registration-policy-rag.top-k:4}") int topK,
                                        @Value("${app.ai.registration-policy-rag.min-score:0.55}") double minScore) {
        this.mapper = mapper;
        this.embeddingClientProvider = embeddingClientProvider;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.namespace = namespace;
        this.topK = Math.max(1, topK);
        this.minScore = minScore;
    }

    public Map<String, Object> buildContext(ChatRequest request,
                                            RegistrationReplyScene scene,
                                            boolean requiresConfirmation,
                                            Map<String, Object> data) {
        String query = queryText(request, scene, requiresConfirmation, data);
        String actionTag = actionTag(scene, data);
        if (!enabled || query.isBlank()) {
            return emptyContext(query, actionTag);
        }

        FallbackEmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            log.info("[registration-policy-rag] embedding client unavailable trace_id={} chat_id={}",
                    request.traceId(),
                    request.chatId());
            return emptyContext(query, actionTag);
        }

        try {
            String queryEmbedding = toVectorLiteral(embeddingClient.embed(query));
            List<RegistrationPolicyHit> hits = mapper.search(namespace, actionTag, queryEmbedding, topK, minScore);
            RegistrationPolicyHit best = hits.isEmpty() ? null : hits.get(0);
            log.info("[registration-policy-rag] retrieval completed trace_id={} chat_id={} namespace={} action_tag={} top_k={} min_score={} hit_count={} best_policy={} best_score={}",
                    request.traceId(),
                    request.chatId(),
                    namespace,
                    actionTag,
                    topK,
                    minScore,
                    hits.size(),
                    best == null ? null : best.getPolicyId(),
                    best == null ? null : best.getScore());
            return contextWithMetadata(query, actionTag, hits);
        } catch (RuntimeException ex) {
            log.warn("[registration-policy-rag] retrieval failed, use empty evidence trace_id={} chat_id={} error={}",
                    request.traceId(),
                    request.chatId(),
                    ex.getMessage());
            return emptyContext(query, actionTag);
        }
    }

    static String queryText(ChatRequest request,
                            RegistrationReplyScene scene,
                            boolean requiresConfirmation,
                            Map<String, Object> data) {
        String message = request.message() == null ? "" : request.message().trim();
        return """
                user_message: %s
                scene: %s
                action: %s
                requires_confirmation: %s
                structured_data: %s
                """.formatted(message, scene.name(), actionTag(scene, data), requiresConfirmation, data).strip();
    }

    static Map<String, Object> emptyContext(String query, String actionTag) {
        return context(query, actionTag, List.of());
    }

    private static Map<String, Object> context(String query, String actionTag, List<RegistrationPolicyHit> hits) {
        return Map.of(
                "source", "registration-policy-rag",
                "retriever", "pgvector",
                "query", query,
                "actionTag", actionTag,
                "matchCount", hits.size(),
                "policyIds", hits.stream().map(RegistrationPolicyHit::getPolicyId).toList(),
                "referenceText", buildReferenceText(hits),
                "knowledgeSources", hits.stream()
                        .collect(LinkedHashMap::new,
                                (sources, hit) -> sources.putIfAbsent(
                                        hit.getSourceId(),
                                        Map.of(
                                                "sourceId", hit.getSourceId(),
                                                "sourceName", hit.getSourceName()
                                        )
                                ),
                                LinkedHashMap::putAll)
                        .values()
                        .stream()
                        .toList(),
                "snippets", hits.stream()
                        .map(hit -> Map.of(
                                "policyId", hit.getPolicyId(),
                                "sourceId", hit.getSourceId(),
                                "sourceName", hit.getSourceName(),
                                "documentId", hit.getDocumentId(),
                                "policyType", hit.getPolicyType(),
                                "actionTag", hit.getActionTag(),
                                "title", hit.getTitle(),
                                "content", hit.getContent(),
                                "score", hit.getScore()
                        ))
                        .toList()
        );
    }

    private Map<String, Object> contextWithMetadata(String query, String actionTag, List<RegistrationPolicyHit> hits) {
        return Map.of(
                "source", "registration-policy-rag",
                "retriever", "pgvector",
                "query", query,
                "actionTag", actionTag,
                "matchCount", hits.size(),
                "policyIds", hits.stream().map(RegistrationPolicyHit::getPolicyId).toList(),
                "referenceText", buildReferenceText(hits),
                "knowledgeSources", hits.stream()
                        .collect(LinkedHashMap::new,
                                (sources, hit) -> sources.putIfAbsent(
                                        hit.getSourceId(),
                                        Map.of(
                                                "sourceId", hit.getSourceId(),
                                                "sourceName", hit.getSourceName()
                                        )
                                ),
                                LinkedHashMap::putAll)
                        .values()
                        .stream()
                        .toList(),
                "snippets", hits.stream()
                        .map(hit -> Map.of(
                                "policyId", hit.getPolicyId(),
                                "sourceId", hit.getSourceId(),
                                "sourceName", hit.getSourceName(),
                                "documentId", hit.getDocumentId(),
                                "policyType", hit.getPolicyType(),
                                "actionTag", hit.getActionTag(),
                                "title", hit.getTitle(),
                                "content", hit.getContent(),
                                "score", hit.getScore(),
                                "metadata", metadata(hit)
                        ))
                        .toList()
        );
    }

    private static String buildReferenceText(List<RegistrationPolicyHit> hits) {
        if (hits.isEmpty()) {
            return "No registration policy evidence was retrieved. Use deterministic workflow guardrails and structured business data only; do not invent hospital policies.";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < hits.size(); index++) {
            RegistrationPolicyHit hit = hits.get(index);
            if (index > 0) {
                builder.append(System.lineSeparator());
            }
            builder.append(index + 1)
                    .append(". [")
                    .append(hit.getPolicyId())
                    .append("] ")
                    .append(hit.getSourceName())
                    .append(" - ")
                    .append(hit.getTitle())
                    .append(": ")
                    .append(hit.getContent());
        }
        return builder.toString();
    }

    private static String actionTag(RegistrationReplyScene scene, Map<String, Object> data) {
        Object actionValue = data.get("action");
        if (actionValue != null) {
            String action = String.valueOf(actionValue).trim().toUpperCase(Locale.ROOT);
            if (!action.isBlank()) {
                return switch (action) {
                    case "CREATE", "QUERY", "CANCEL", "RESCHEDULE" -> action;
                    default -> inferIntentFromScene(scene).name();
                };
            }
        }
        return inferIntentFromScene(scene).name();
    }

    private static RegistrationIntent inferIntentFromScene(RegistrationReplyScene scene) {
        String name = scene.name();
        if (name.startsWith("CREATE")) {
            return RegistrationIntent.CREATE;
        }
        if (name.startsWith("QUERY")) {
            return RegistrationIntent.QUERY;
        }
        if (name.startsWith("CANCEL") || name.equals("SLOT_RELEASE_FAILED")) {
            return RegistrationIntent.CANCEL;
        }
        if (name.startsWith("RESCHEDULE") || name.equals("OLD_SLOT_RELEASE_FAILED")) {
            return RegistrationIntent.RESCHEDULE;
        }
        return RegistrationIntent.QUERY;
    }

    private Map<String, Object> metadata(RegistrationPolicyHit hit) {
        if (hit.getMetadataJson() == null || hit.getMetadataJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(hit.getMetadataJson(), new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("[registration-policy-rag] invalid metadata ignored policy_id={} error={}",
                    hit.getPolicyId(),
                    ex.getMessage());
            return Map.of();
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding[index]);
        }
        builder.append(']');
        return builder.toString();
    }
}
