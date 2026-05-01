package com.example.airegistration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentWorkflowDefinitionTest {

    @Test
    void shouldRenderMermaidWorkflow() {
        AgentWorkflowDefinition definition = new AgentWorkflowDefinition(
                "workflow-sample",
                "Sample workflow",
                "registration",
                List.of(
                        new AgentWorkflowNode("start", "Start", "classify", AgentPattern.ROUTING_AGENT, true, false, false),
                        new AgentWorkflowNode("tool", "Tool", "write", AgentPattern.DETERMINISTIC_TOOL, false, true, true)
                ),
                List.of(new AgentWorkflowEdge("start", "tool", "create"))
        );

        assertThat(definition.toMermaid())
                .contains("flowchart TD")
                .contains("start -->|create| tool");
    }

    @Test
    void shouldRejectEdgesPointingToUnknownNodes() {
        assertThatThrownBy(() -> new AgentWorkflowDefinition(
                "workflow-sample",
                "Sample workflow",
                "registration",
                List.of(new AgentWorkflowNode("start", "Start", "classify", AgentPattern.ROUTING_AGENT, true, false, false)),
                List.of(new AgentWorkflowEdge("start", "missing", "always"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("edge target does not exist");
    }
}
