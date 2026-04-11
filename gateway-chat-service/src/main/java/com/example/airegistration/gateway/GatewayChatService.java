package com.example.airegistration.gateway;

import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class GatewayChatService {

    private final WebClient supervisorClient;

    public GatewayChatService(WebClient.Builder webClientBuilder,
                              @Value("${app.supervisor.base-url}") String supervisorBaseUrl) {
        this.supervisorClient = webClientBuilder.baseUrl(supervisorBaseUrl).build();
    }

    public Mono<ChatResponse> forward(ChatRequest request) {
        return supervisorClient.post()
                .uri("/api/route")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponse.class);
    }
}
