package com.example.airegistration.triage;

import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class TriageAgentController {

    private final TriageAgentService triageAgentService;

    public TriageAgentController(TriageAgentService triageAgentService) {
        this.triageAgentService = triageAgentService;
    }

    @PostMapping("/triage")
    public Mono<ChatResponse> handle(@RequestBody ChatRequest request) {
        return triageAgentService.handle(request);
    }
}
