package com.example.airegistration.registration.service.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.airegistration.agent.AgentPattern;
import org.junit.jupiter.api.Test;

class RegistrationWorkflowDefinitionTest {

    @Test
    void shouldExposeEnterpriseWorkflowDefinition() {
        var definition = new RegistrationWorkflowDefinition().definition();

        assertThat(definition.ownerBusinessModule()).isEqualTo("registration");
        assertThat(definition.nodes())
                .anySatisfy(node -> {
                    assertThat(node.id()).isEqualTo("build_preview");
                    assertThat(node.pattern()).isEqualTo(AgentPattern.HUMAN_IN_THE_LOOP);
                    assertThat(node.humanApprovalRequired()).isTrue();
                })
                .anySatisfy(node -> {
                    assertThat(node.id()).isEqualTo("execute_write");
                    assertThat(node.toolCalling()).isTrue();
                    assertThat(node.humanApprovalRequired()).isTrue();
                });
        assertThat(definition.toMermaid()).contains("flowchart TD");
    }
}
