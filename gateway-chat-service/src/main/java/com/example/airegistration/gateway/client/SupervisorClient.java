package com.example.airegistration.gateway.client;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.gateway.config.SupervisorClientProperties;
import com.example.airegistration.support.TraceIdSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(SupervisorClientProperties.class)
public class SupervisorClient {

    private static final Logger log = LoggerFactory.getLogger(SupervisorClient.class);

    private final WebClient supervisorClient;

    public SupervisorClient(WebClient.Builder webClientBuilder, SupervisorClientProperties properties) {
        this.supervisorClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    public Mono<ChatResponse> route(ChatRequest request) {
        log.info("[gateway->supervisor] request trace_id={} chat_id={} user_id={}",
                request.traceId(),
                request.chatId(),
                request.userId());
        return supervisorClient.post()
                .uri("/api/route")
                .header(TraceIdSupport.TRACE_HEADER, request.traceId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .doOnSuccess(response -> log.info(
                        "[gateway->supervisor] response trace_id={} chat_id={} route={} confirmation={}",
                        request.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation()
                ))
                .doOnError(error -> log.warn(
                        "[gateway->supervisor] failed trace_id={} chat_id={} reason={}",
                        request.traceId(),
                        request.chatId(),
                        error.getMessage()
                ));
    }
}
