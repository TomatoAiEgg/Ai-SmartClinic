package com.example.airegistration.gateway.client;

import com.example.airegistration.dto.RegistrationQueryRequest;
import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import com.example.airegistration.dto.RegistrationSearchResponse;
import com.example.airegistration.gateway.config.RegistrationClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(RegistrationClientProperties.class)
public class RegistrationClient {

    private final WebClient registrationClient;

    public RegistrationClient(WebClient.Builder webClientBuilder, RegistrationClientProperties properties) {
        this.registrationClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    public Mono<RegistrationSearchResponse> search(RegistrationSearchRequest request) {
        return registrationClient.post()
                .uri("/api/mcp/registrations/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RegistrationSearchResponse.class);
    }

    public Mono<RegistrationResult> query(String registrationId, String userId) {
        return registrationClient.post()
                .uri("/api/mcp/registrations/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationQueryRequest(registrationId, userId))
                .retrieve()
                .bodyToMono(RegistrationResult.class);
    }
}
