package com.example.airegistration.guide;

import com.example.airegistration.domain.ChatRequest;
import com.example.airegistration.domain.ChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class GuideAgentController {

    private final GuideAgentService guideAgentService;

    public GuideAgentController(GuideAgentService guideAgentService) {
        this.guideAgentService = guideAgentService;
    }

    @PostMapping("/guide")
    public Mono<ChatResponse> handle(@RequestBody ChatRequest request) {
        return guideAgentService.handle(request);
    }
}
