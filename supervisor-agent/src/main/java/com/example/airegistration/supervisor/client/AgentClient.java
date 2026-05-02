package com.example.airegistration.supervisor.client;

import com.example.airegistration.agent.AgentEnvelopeMapper;
import com.example.airegistration.agent.AgentRequestEnvelope;
import com.example.airegistration.agent.AgentResponseEnvelope;
import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.supervisor.config.AgentClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(AgentClientProperties.class)
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);
    private static final String EXECUTE_PATH = "/api/agent/execute";

    private final WebClient.Builder webClientBuilder;
    private final AgentRegistry agentRegistry;

    @Autowired
    public AgentClient(WebClient.Builder webClientBuilder, AgentRegistry agentRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.agentRegistry = agentRegistry;
    }

    public AgentClient(WebClient.Builder webClientBuilder, AgentClientProperties properties) {
        this(webClientBuilder, AgentRegistry.fromProperties(properties));
    }

    public Mono<ChatResponse> callTriage(ChatRequest request) {
        return callAgent(AgentRoute.TRIAGE, request);
    }

    public Mono<ChatResponse> callRegistration(ChatRequest request) {
        return callAgent(AgentRoute.REGISTRATION, request);
    }

    public Mono<ChatResponse> callGuide(ChatRequest request) {
        return callAgent(AgentRoute.GUIDE, request);
    }

    public Mono<ChatResponse> call(AgentRoute route, ChatRequest request) {
        return callAgent(route, request);
    }

    private Mono<ChatResponse> callAgent(AgentRoute route, ChatRequest request) {
        return Mono.defer(() -> {
            RegisteredAgent agent = agentRegistry.requireAgent(route);
            WebClient client = webClientBuilder.clone()
                    .baseUrl(agent.baseUrl())
                    .build();
            log.info("[supervisor->{}] request trace_id={} chat_id={} user_id={} path={}",
                    agent.agentName(),
                    request.traceId(),
                    request.chatId(),
                    request.userId(),
                    EXECUTE_PATH);
            return callUnifiedAgent(agent, client, request)
                    .onErrorResume(error -> shouldFallbackToLegacy(error), error -> {
                        log.warn("[supervisor->{}] unified endpoint unavailable, fallback legacy trace_id={} chat_id={} legacy_path={} reason={}",
                                agent.agentName(),
                                request.traceId(),
                                request.chatId(),
                                agent.legacyPath(),
                                error.getMessage());
                        return callLegacyAgent(agent, client, request);
                    });
        });
    }

    private Mono<ChatResponse> callUnifiedAgent(RegisteredAgent agent, WebClient client, ChatRequest request) {
        AgentRequestEnvelope envelope = AgentEnvelopeMapper.toAgentRequest(request);
        return client.post()
                .uri(EXECUTE_PATH)
                .header(TraceIdSupport.TRACE_HEADER, request.traceId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(envelope)
                .retrieve()
                .bodyToMono(AgentResponseEnvelope.class)
                .map(response -> AgentEnvelopeMapper.toChatResponse(request, response))
                .doOnSuccess(response -> log.info(
                        "[supervisor->{}] response trace_id={} chat_id={} route={} confirmation={} protocol=agent_execute",
                        agent.agentName(),
                        request.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation()
                ))
                .doOnError(error -> log.warn(
                        "[supervisor->{}] failed trace_id={} chat_id={} protocol=agent_execute reason={}",
                        agent.agentName(),
                        request.traceId(),
                        request.chatId(),
                        error.getMessage()
                ));
    }

    private Mono<ChatResponse> callLegacyAgent(RegisteredAgent agent, WebClient client, ChatRequest request) {
        return client.post()
                .uri(agent.legacyPath())
                .header(TraceIdSupport.TRACE_HEADER, request.traceId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class)
                .doOnSuccess(response -> log.info(
                        "[supervisor->{}] response trace_id={} chat_id={} route={} confirmation={}",
                        agent.agentName(),
                        request.traceId(),
                        response.chatId(),
                        response.route(),
                        response.requiresConfirmation()
                ))
                .doOnError(error -> log.warn(
                        "[supervisor->{}] failed trace_id={} chat_id={} protocol=legacy reason={}",
                        agent.agentName(),
                        request.traceId(),
                        request.chatId(),
                        error.getMessage()
                ));
    }

    private boolean shouldFallbackToLegacy(Throwable error) {
        if (!(error instanceof WebClientResponseException responseException)) {
            return false;
        }
        HttpStatus status = HttpStatus.resolve(responseException.getStatusCode().value());
        return status == HttpStatus.NOT_FOUND
                || status == HttpStatus.METHOD_NOT_ALLOWED
                || status == HttpStatus.UNSUPPORTED_MEDIA_TYPE;
    }
}
