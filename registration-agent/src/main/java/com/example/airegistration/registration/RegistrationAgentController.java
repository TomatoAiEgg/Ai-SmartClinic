package com.example.airegistration.registration;

import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class RegistrationAgentController {

    private final RegistrationAgentService registrationAgentService;

    public RegistrationAgentController(RegistrationAgentService registrationAgentService) {
        this.registrationAgentService = registrationAgentService;
    }

    @PostMapping("/registration")
    public Mono<ChatResponse> handle(@RequestBody ChatRequest request) {
        return registrationAgentService.handle(request);
    }
}
