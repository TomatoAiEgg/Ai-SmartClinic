package com.example.airegistration.supervisor;

import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class SupervisorAgentController {

    private final SupervisorRoutingService supervisorRoutingService;

    public SupervisorAgentController(SupervisorRoutingService supervisorRoutingService) {
        this.supervisorRoutingService = supervisorRoutingService;
    }

    @PostMapping("/route")
    public Mono<ChatResponse> route(@RequestBody ChatRequest request) {
        return supervisorRoutingService.route(request);
    }
}
