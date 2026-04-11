package com.example.airegistration.gateway;

import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class GatewayChatController {

    private final GatewayChatService gatewayChatService;

    public GatewayChatController(GatewayChatService gatewayChatService) {
        this.gatewayChatService = gatewayChatService;
    }

    @PostMapping("/chat")
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        return gatewayChatService.forward(request);
    }
}
