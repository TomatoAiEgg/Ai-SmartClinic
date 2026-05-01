package com.example.airegistration.registration.service.workflow;

import com.example.airegistration.agent.AgentPattern;
import com.example.airegistration.agent.AgentWorkflowDefinition;
import com.example.airegistration.agent.AgentWorkflowEdge;
import com.example.airegistration.agent.AgentWorkflowNode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RegistrationWorkflowDefinition {

    public AgentWorkflowDefinition definition() {
        return new AgentWorkflowDefinition(
                "registration-agent-v1",
                "Registration agent state graph",
                "registration",
                nodes(),
                edges()
        );
    }

    private List<AgentWorkflowNode> nodes() {
        return List.of(
                node("classify_intent", "Classify intent", "registration.intent", AgentPattern.ROUTING_AGENT, true, false, false),
                node("extract_slots", "Extract fields", "registration.slot_extraction", AgentPattern.STATE_GRAPH, true, false, false),
                node("load_patient", "Load patient", "patient.default_profile", AgentPattern.DETERMINISTIC_TOOL, false, true, false),
                node("resolve_slot", "Resolve slot", "schedule.resolve_or_recommend", AgentPattern.DETERMINISTIC_TOOL, false, true, false),
                node("query_order", "Query order", "registration.query", AgentPattern.DETERMINISTIC_TOOL, false, true, false),
                node("build_preview", "Build preview", "registration.preview", AgentPattern.HUMAN_IN_THE_LOOP, false, false, true),
                node("execute_write", "Execute write", "registration.confirmed_write", AgentPattern.DETERMINISTIC_TOOL, false, true, true),
                node("compensate", "Compensate", "registration.rollback_or_release", AgentPattern.DETERMINISTIC_TOOL, false, true, false),
                node("reply", "Reply", "registration.patient_reply", AgentPattern.STATE_GRAPH, true, false, false)
        );
    }

    private List<AgentWorkflowEdge> edges() {
        return List.of(
                edge("classify_intent", "query_order", "QUERY|CANCEL|RESCHEDULE"),
                edge("classify_intent", "extract_slots", "CREATE"),
                edge("extract_slots", "load_patient", "create requires patient"),
                edge("load_patient", "resolve_slot", "patient loaded"),
                edge("resolve_slot", "build_preview", "write not confirmed"),
                edge("query_order", "build_preview", "cancel/reschedule not confirmed"),
                edge("build_preview", "reply", "requires user approval"),
                edge("resolve_slot", "execute_write", "create confirmed"),
                edge("query_order", "execute_write", "cancel/reschedule confirmed"),
                edge("execute_write", "compensate", "downstream failure"),
                edge("execute_write", "reply", "success"),
                edge("compensate", "reply", "compensated or warning")
        );
    }

    private AgentWorkflowNode node(String id,
                                   String name,
                                   String businessCapability,
                                   AgentPattern pattern,
                                   boolean aiDriven,
                                   boolean toolCalling,
                                   boolean humanApprovalRequired) {
        return new AgentWorkflowNode(id, name, businessCapability, pattern, aiDriven, toolCalling, humanApprovalRequired);
    }

    private AgentWorkflowEdge edge(String source, String target, String condition) {
        return new AgentWorkflowEdge(source, target, condition);
    }
}
