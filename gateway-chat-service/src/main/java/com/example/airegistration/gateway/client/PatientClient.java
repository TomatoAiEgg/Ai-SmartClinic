package com.example.airegistration.gateway.client;

import com.example.airegistration.dto.PatientCreateRequest;
import com.example.airegistration.dto.PatientListRequest;
import com.example.airegistration.dto.PatientListResponse;
import com.example.airegistration.dto.PatientSetDefaultRequest;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.gateway.config.PatientClientProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(PatientClientProperties.class)
public class PatientClient {

    private final WebClient patientClient;

    public PatientClient(WebClient.Builder webClientBuilder, PatientClientProperties properties) {
        this.patientClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    public Mono<PatientListResponse> list(String userId) {
        return patientClient.post()
                .uri("/api/mcp/patients/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PatientListRequest(userId))
                .retrieve()
                .bodyToMono(PatientListResponse.class);
    }

    public Mono<PatientSummary> create(PatientCreateRequest request) {
        return patientClient.post()
                .uri("/api/mcp/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PatientSummary.class);
    }

    public Mono<PatientSummary> setDefault(String userId, String patientId) {
        return patientClient.post()
                .uri("/api/mcp/patients/default/set")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PatientSetDefaultRequest(userId, patientId))
                .retrieve()
                .bodyToMono(PatientSummary.class);
    }
}
