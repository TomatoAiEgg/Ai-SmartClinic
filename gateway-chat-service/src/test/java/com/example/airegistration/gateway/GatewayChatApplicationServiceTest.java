package com.example.airegistration.gateway;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.gateway.client.SupervisorClient;
import com.example.airegistration.gateway.service.GatewayChatApplicationService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayChatApplicationServiceTest {

    @Test
    void shouldOnlyForwardChatRequestToSupervisor() {
        SupervisorClient supervisorClient = mock(SupervisorClient.class);
        GatewayChatApplicationService service = new GatewayChatApplicationService(supervisorClient);
        ChatRequest request = new ChatRequest("chat-1", "user-1", "我要挂号", Map.of());
        ChatResponse routedResponse = new ChatResponse("chat-1", AgentRoute.REGISTRATION, "已路由", false, Map.of());
        when(supervisorClient.route(request)).thenReturn(Mono.just(routedResponse));

        ChatResponse response = service.chat(request).block();

        assertThat(response).isEqualTo(routedResponse);
        verify(supervisorClient).route(request);
    }
}
