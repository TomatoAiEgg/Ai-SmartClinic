package com.example.airegistration.registration.service;

import com.example.airegistration.dto.ApiError;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.exception.RegistrationAgentException;
import com.example.airegistration.registration.service.workflow.RegistrationWorkflowCheckpoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RedisRegistrationConfirmationService implements RegistrationConfirmationService {

    private static final String SOURCE = "registration-agent";
    private static final String CONTEXT_PREFIX = "ai-smartclinic:registration:confirmation:";
    private static final String USED_PREFIX = "ai-smartclinic:registration:confirmation-used:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisRegistrationConfirmationService(ReactiveStringRedisTemplate redisTemplate,
                                                ObjectMapper objectMapper,
                                                @Value("${app.confirmation.ttl:PT10M}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Mono<String> save(ChatRequest request, RegistrationIntent intent, Map<String, Object> data) {
        return save(request, intent, data, null);
    }

    @Override
    public Mono<String> save(ChatRequest request,
                             RegistrationIntent intent,
                             Map<String, Object> data,
                             RegistrationWorkflowCheckpoint checkpoint) {
        String confirmationId = nextConfirmationId();
        RegistrationWorkflowCheckpoint storedCheckpoint = checkpoint == null
                ? null
                : checkpoint.withConfirmation(confirmationId, "build_preview", "execute_write", data);
        RegistrationConfirmationContext context = new RegistrationConfirmationContext(
                confirmationId,
                expectedAction(intent),
                request.userId(),
                request.chatId(),
                data,
                storedCheckpoint
        );
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(context))
                .flatMap(payload -> redisTemplate.opsForValue().set(contextKey(confirmationId), payload, ttl))
                .flatMap(saved -> saved
                        ? Mono.just(confirmationId)
                        : invalid("确认上下文保存失败，请重新生成挂号预览。", Map.of("confirmationId", confirmationId)));
    }

    @Override
    public Mono<RegistrationConfirmationContext> consume(ChatRequest request, RegistrationIntent intent) {
        String confirmationId = normalizeText(request.metadata().get("confirmationId"));
        if (confirmationId.isEmpty()) {
            return invalid("确认上下文缺失，请重新生成挂号预览后再确认。", Map.of("requiredField", "confirmationId"));
        }

        return redisTemplate.opsForValue()
                .get(contextKey(confirmationId))
                .switchIfEmpty(invalid("确认上下文已过期，请重新生成挂号预览。", Map.of("confirmationId", confirmationId)))
                .flatMap(payload -> parse(payload, confirmationId))
                .flatMap(context -> validateContext(context, request, intent))
                .flatMap(context -> markUsed(confirmationId)
                        .flatMap(used -> used
                                ? redisTemplate.delete(contextKey(confirmationId)).thenReturn(context)
                                : invalid("该确认请求已经处理，请勿重复提交。", Map.of("confirmationId", confirmationId))));
    }

    private Mono<RegistrationConfirmationContext> parse(String payload, String confirmationId) {
        try {
            return Mono.just(objectMapper.readValue(payload, RegistrationConfirmationContext.class));
        } catch (JsonProcessingException ex) {
            return invalid("确认上下文格式不正确，请重新生成挂号预览。", Map.of(
                    "confirmationId", confirmationId,
                    "cause", ex.getMessage()
            ));
        }
    }

    private Mono<RegistrationConfirmationContext> validateContext(RegistrationConfirmationContext context,
                                                                  ChatRequest request,
                                                                  RegistrationIntent intent) {
        if (!normalizeText(context.userId()).equals(request.userId())) {
            return invalid("确认上下文不属于当前用户。", Map.of("confirmationId", context.confirmationId()));
        }
        String expectedAction = expectedAction(intent);
        if (!expectedAction.equalsIgnoreCase(normalizeText(context.action()))) {
            return invalid("确认动作与预览动作不一致。", Map.of(
                    "confirmationId", context.confirmationId(),
                    "expectedAction", expectedAction,
                    "actualAction", context.action()
            ));
        }
        return Mono.just(context);
    }

    private Mono<Boolean> markUsed(String confirmationId) {
        return redisTemplate.opsForValue().setIfAbsent(usedKey(confirmationId), "1", ttl);
    }

    private <T> Mono<T> invalid(String message, Map<String, Object> details) {
        return Mono.error(new RegistrationAgentException(
                new ApiError(ApiErrorCode.INVALID_REQUEST, message, details),
                SOURCE
        ));
    }

    private String nextConfirmationId() {
        return "CONF-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private String expectedAction(RegistrationIntent intent) {
        return intent.name().toLowerCase(Locale.ROOT);
    }

    private String contextKey(String confirmationId) {
        return CONTEXT_PREFIX + confirmationId;
    }

    private String usedKey(String confirmationId) {
        return USED_PREFIX + confirmationId;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
