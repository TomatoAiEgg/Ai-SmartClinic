package com.example.airegistration.registration.service.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.enums.RegistrationReplyScene;
import com.example.airegistration.registration.service.RegistrationFlowPolicy;
import com.example.airegistration.registration.service.RegistrationReplyService;
import com.example.airegistration.registration.service.RegistrationSlotExtractor;
import com.example.airegistration.registration.service.rag.RegistrationRuleService;
import com.example.airegistration.registration.service.rag.RegistrationWorkflowRules;
import com.example.airegistration.registration.service.tool.RegistrationToolService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

class RegistrationWorkflowServiceTest {

    @Test
    void shouldGuideSymptomOnlyCreateRequestToTriageBeforeRegistration() {
        RegistrationFlowPolicy flowPolicy = new RegistrationFlowPolicy(new RegistrationSlotExtractor());
        RegistrationReplyService replyService = Mockito.mock(RegistrationReplyService.class);
        RegistrationRuleService ruleService = Mockito.mock(RegistrationRuleService.class);
        RegistrationToolService toolService = Mockito.mock(RegistrationToolService.class);
        RegistrationWorkflowService workflowService =
                new RegistrationWorkflowService(flowPolicy, replyService, ruleService, toolService);
        ChatRequest request = new ChatRequest(
                "chat-1",
                "user-1",
                "Can I book an appointment by describing my condition",
                Map.of("action", "create")
        );

        when(ruleService.resolveWorkflowRules(eq(request), eq(RegistrationIntent.CREATE), anyMap()))
                .thenReturn(new RegistrationWorkflowRules(
                        java.util.List.of("rule-create-patient-and-slot"),
                        false,
                        java.util.List.of("departmentCode"),
                        false,
                        java.util.List.of(),
                        false,
                        false
                ));
        when(replyService.reply(eq(request), eq(RegistrationReplyScene.CREATE_MISSING_DEPARTMENT), anyBoolean(), anyMap()))
                .thenAnswer(invocation -> Mono.just(new ChatResponse(
                        request.chatId(),
                        AgentRoute.REGISTRATION,
                        "triage-first",
                        false,
                        invocation.<Map<String, Object>>getArgument(3)
                )));

        ChatResponse response = workflowService.handle(request, RegistrationIntent.CREATE).block();

        assertThat(response).isNotNull();
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.data())
                .containsEntry("action", "create")
                .containsEntry("requiredField", "departmentCode")
                .containsEntry("requiredAction", "triage")
                .containsEntry("suggestedRoute", "TRIAGE")
                .containsEntry("acceptsSymptomDescription", true)
                .containsEntry("nextStep", "describeSymptoms");
        verify(toolService, never()).fetchDefaultPatient(any(), any());
        verify(toolService, never()).previewCreateSlot(any(), any(), any());
    }
}
