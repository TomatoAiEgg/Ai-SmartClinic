package com.example.airegistration.gateway.controller;

import com.example.airegistration.dto.PatientCreateRequest;
import com.example.airegistration.dto.PatientListResponse;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.gateway.client.PatientClient;
import com.example.airegistration.gateway.dto.PatientCreatePayload;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/patients")
public class GatewayPatientController {

    private final PatientClient patientClient;

    public GatewayPatientController(PatientClient patientClient) {
        this.patientClient = patientClient;
    }

    @GetMapping
    public Mono<PatientListResponse> list(Principal principal) {
        return patientClient.list(requireUserId(principal));
    }

    @PostMapping
    public Mono<PatientSummary> create(@RequestBody PatientCreatePayload payload, Principal principal) {
        return patientClient.create(new PatientCreateRequest(
                requireUserId(principal),
                payload.name(),
                payload.idType(),
                payload.idNumber(),
                payload.phone(),
                payload.relationCode(),
                payload.defaultPatient()
        ));
    }

    @PostMapping("/{patientId}/default")
    public Mono<PatientSummary> setDefault(@PathVariable String patientId, Principal principal) {
        return patientClient.setDefault(requireUserId(principal), patientId);
    }

    private String requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalStateException("登录状态无效，请重新登录。");
        }
        return principal.getName();
    }
}
