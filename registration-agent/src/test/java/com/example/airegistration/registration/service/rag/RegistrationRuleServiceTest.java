package com.example.airegistration.registration.service.rag;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.enums.RegistrationReplyScene;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationRuleServiceTest {

    @Test
    void shouldComposeDeterministicGuardrailsAndRetrievedPolicyEvidence() {
        RegistrationPolicyRagService policyRagService = mock(RegistrationPolicyRagService.class);
        when(policyRagService.buildContext(any(), eq(RegistrationReplyScene.CREATE_PREVIEW), anyBoolean(), anyMap()))
                .thenReturn(Map.of(
                        "policyIds", List.of("policy-confirm-001"),
                        "referenceText", "Policy evidence: write action requires confirmation.",
                        "matchCount", 1
                ));
        RegistrationRuleService service = new RegistrationRuleService(policyRagService);

        Map<String, Object> context = service.buildContext(
                new ChatRequest("chat-1", "user-1", "book respiratory clinic", Map.of()),
                RegistrationReplyScene.CREATE_PREVIEW,
                true,
                Map.of("action", "create", "previewed", true, "departmentCode", "RESP")
        );

        @SuppressWarnings("unchecked")
        List<String> ruleIds = (List<String>) context.get("ruleIds");
        assertThat(ruleIds)
                .contains("guard-write-confirmation-gate", "guard-create-patient-and-slot", "guard-response-fact-guard");
        assertThat(context.get("policyIds")).isEqualTo(List.of("policy-confirm-001"));
        assertThat(context.get("ruleText").toString())
                .contains("Deterministic workflow guardrails")
                .contains("Policy evidence: write action requires confirmation.");
    }

    @Test
    void shouldResolveWorkflowRulesForCreate() {
        RegistrationRuleService service = new RegistrationRuleService((RegistrationPolicyRagService) null);

        RegistrationWorkflowRules workflowRules = service.resolveWorkflowRules(
                new ChatRequest("chat-1", "user-1", "book appointment", Map.of()),
                RegistrationIntent.CREATE,
                Map.of("action", "create")
        );

        assertThat(workflowRules.ruleIds()).contains("guard-write-confirmation-gate", "guard-create-patient-and-slot");
        assertThat(workflowRules.previewBeforeWrite()).isTrue();
        assertThat(workflowRules.createRequiredFields()).containsExactly("departmentCode");
        assertThat(workflowRules.requireRegistrationId()).isFalse();
    }

    @Test
    void shouldResolveWorkflowRulesForReschedule() {
        RegistrationRuleService service = new RegistrationRuleService((RegistrationPolicyRagService) null);

        RegistrationWorkflowRules workflowRules = service.resolveWorkflowRules(
                new ChatRequest("chat-3", "user-3", "reschedule to tomorrow afternoon", Map.of()),
                RegistrationIntent.RESCHEDULE,
                Map.of("action", "reschedule")
        );

        assertThat(workflowRules.ruleIds()).contains("guard-write-confirmation-gate", "guard-reschedule-scope");
        assertThat(workflowRules.previewBeforeWrite()).isTrue();
        assertThat(workflowRules.requireRegistrationId()).isTrue();
        assertThat(workflowRules.rescheduleRequiredFields()).containsExactly("clinicDate", "startTime");
        assertThat(workflowRules.allowDepartmentChangeOnReschedule()).isFalse();
        assertThat(workflowRules.allowDoctorChangeOnReschedule()).isFalse();
    }
}
