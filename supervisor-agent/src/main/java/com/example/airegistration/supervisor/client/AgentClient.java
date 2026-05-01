package com.example.airegistration.supervisor.client;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.supervisor.config.AgentClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(AgentClientProperties.class)
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);

    private final WebClient triageClient;
    private final WebClient registrationClient;
    private final WebClient guideClient;

    public AgentClient(WebClient.Builder webClientBuilder, AgentClientProperties properties) {
        this.triageClient = webClientBuilder.baseUrl(properties.getTriage().getBaseUrl()).build();
        this.registrationClient = webClientBuilder.baseUrl(properties.getRegistration().getBaseUrl()).build();
        this.guideClient = webClientBuilder.baseUrl(properties.getGuide().getBaseUrl()).build();
    }

    public Mono<ChatResponse> callTriage(ChatRequest request) {
        return callAgent("triage-agent", triageClient, "/api/triage", request);
    }

    public Mono<ChatResponse> callRegistration(ChatRequest request) {
        return callAgent("registration-agent", registrationClient, "/api/registration", request);
    }

    public Mono<ChatResponse> callGuide(ChatRequest request) {
        return callAgent("guide-agent", guideClient, "/api/guide", request);
    }

    private Mono<ChatResponse> callAgent(String target, WebClient client, String path, ChatRequest request) {
        log.info("[supervisor->{}] request trace_id={} chat_id={} user_id={} path={}",
                target,
                request.traceId(),
                request.chatId(),
                request.userId(),
                path);
        return client.post()
                .uri(path)
                .header(TraceIdSupport.TRACE_HEADER, request.traceId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .doOnSuccess(response -> log.info(
                        "[supervisor->{}] response trace_id={} chat_id={} route={} confirmation={}",
                        target,
                        request.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation()
                ))
                .doOnError(error -> log.warn(
                        "[supervisor->{}] failed trace_id={} chat_id={} reason={}",
                        target,
                        request.traceId(),
                        request.chatId(),
                        error.getMessage()
                ));
    }
}
