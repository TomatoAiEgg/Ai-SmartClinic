package com.example.airegistration.guide;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.guide.service.GuideApplicationService;
import com.example.airegistration.guide.service.orchestrator.GuideOrchestratorService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuideApplicationServiceTest {

    @Test
    void shouldKeepGuideConsultationInsideGuideAgent() {
        GuideOrchestratorService orchestratorService = mock(GuideOrchestratorService.class);
        ChatRequest request = new ChatRequest("chat-1", "user-1", "医保材料需要什么", Map.of());
        ChatResponse routedResponse = new ChatResponse(
                request.chatId(),
                AgentRoute.GUIDE,
                "导诊回复",
                false,
                Map.of("source", "guide-agent", "scope", "clinic guidance")
        );
        when(orchestratorService.handle(request)).thenReturn(Mono.just(routedResponse));

        GuideApplicationService service = new GuideApplicationService(orchestratorService);

        ChatResponse response = service.handle(request).block();

        assertThat(response).isEqualTo(routedResponse);
        verify(orchestratorService).handle(request);
    }
}
