package com.example.airegistration.registration.service.workflow;

import com.example.airegistration.registration.enums.RegistrationIntent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public record RegistrationWorkflowCheckpoint(
        String workflowId,
        String executionId,
        String confirmationId,
        RegistrationIntent intent,
        String currentNode,
        String nextNode,
        Map<String, Object> inputSnapshot,
        Map<String, Object> toolResultSnapshot,
        List<RegistrationWorkflowTraceEvent> events
) {
    public RegistrationWorkflowCheckpoint {
        workflowId = requireText(workflowId, "workflowId");
        executionId = executionId == null || executionId.isBlank() ? nextExecutionId() : executionId.trim();
        confirmationId = confirmationId == null ? "" : confirmationId.trim();
        intent = intent == null ? RegistrationIntent.CREATE : intent;
        currentNode = requireText(currentNode, "currentNode");
        nextNode = nextNode == null ? "" : nextNode.trim();
        inputSnapshot = copyMap(inputSnapshot);
        toolResultSnapshot = copyMap(toolResultSnapshot);
        events = List.copyOf(events == null ? List.of() : events);
    }

    public static RegistrationWorkflowCheckpoint started(String workflowId,
                                                         RegistrationIntent intent,
                                                         Map<String, Object> inputSnapshot) {
        return new RegistrationWorkflowCheckpoint(
                workflowId,
                nextExecutionId(),
                "",
                intent,
                "classify_intent",
                "extract_slots",
                inputSnapshot,
                Map.of(),
                List.of(new RegistrationWorkflowTraceEvent(
                        "classify_intent",
                        "SUCCEEDED",
                        null,
                        Map.of("intent", intent.name())
                ))
        );
    }

    public RegistrationWorkflowCheckpoint advance(String currentNode,
                                                  String nextNode,
                                                  String status,
                                                  Map<String, Object> eventData) {
        List<RegistrationWorkflowTraceEvent> nextEvents = new ArrayList<>(events);
        nextEvents.add(new RegistrationWorkflowTraceEvent(currentNode, status, null, eventData));
        return new RegistrationWorkflowCheckpoint(
                workflowId,
                executionId,
                confirmationId,
                intent,
                currentNode,
                nextNode,
                inputSnapshot,
                toolResultSnapshot,
                nextEvents
        );
    }

    public RegistrationWorkflowCheckpoint withConfirmation(String confirmationId,
                                                           String currentNode,
                                                           String nextNode,
                                                           Map<String, Object> toolResultSnapshot) {
        List<RegistrationWorkflowTraceEvent> nextEvents = new ArrayList<>(events);
        nextEvents.add(new RegistrationWorkflowTraceEvent(
                currentNode,
                "WAITING_CONFIRMATION",
                null,
                Map.of("confirmationId", confirmationId)
        ));
        return new RegistrationWorkflowCheckpoint(
                workflowId,
                executionId,
                confirmationId,
                intent,
                currentNode,
                nextNode,
                inputSnapshot,
                toolResultSnapshot,
                nextEvents
        );
    }

    public RegistrationWorkflowCheckpoint withEvent(String nodeId, String status, Map<String, Object> eventData) {
        return advance(nodeId, nextNode, status, eventData);
    }

    private static String nextExecutionId() {
        return "REG-WF-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Map.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
