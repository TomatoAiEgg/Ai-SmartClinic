package com.example.airegistration.gateway.controller;

import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import com.example.airegistration.dto.RegistrationSearchResponse;
import com.example.airegistration.gateway.client.RegistrationClient;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/registrations")
public class GatewayRegistrationController {

    private final RegistrationClient registrationClient;

    public GatewayRegistrationController(RegistrationClient registrationClient) {
        this.registrationClient = registrationClient;
    }

    @GetMapping
    public Mono<RegistrationSearchResponse> search(Principal principal,
                                                   @RequestParam(required = false) String clinicDate,
                                                   @RequestParam(required = false) String departmentCode,
                                                   @RequestParam(required = false) String doctorId,
                                                   @RequestParam(required = false) String status) {
        String userId = requireUserId(principal);
        return registrationClient.search(new RegistrationSearchRequest(
                userId,
                clinicDate,
                departmentCode,
                doctorId,
                status
        ));
    }

    @GetMapping("/{registrationId}")
    public Mono<RegistrationResult> query(@PathVariable String registrationId, Principal principal) {
        return registrationClient.query(registrationId, requireUserId(principal));
    }

    private String requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalStateException("登录状态无效，请重新登录。");
        }
        return principal.getName();
    }
}
