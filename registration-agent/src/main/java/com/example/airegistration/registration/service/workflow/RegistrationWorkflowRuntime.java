package com.example.airegistration.registration.service.workflow;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationIntent;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

public interface RegistrationWorkflowRuntime {

    String WORKFLOW_ID = "registration-agent-v1";

    RegistrationWorkflowCheckpoint start(ChatRequest request, RegistrationIntent intent);

    RegistrationWorkflowCheckpoint resume(ChatRequest request,
                                          RegistrationWorkflowCheckpoint checkpoint,
                                          String currentNode);

    Mono<Void> record(ChatRequest request, RegistrationWorkflowCheckpoint checkpoint);

    Map<String, Object> traceData(RegistrationWorkflowCheckpoint checkpoint);

    static RegistrationWorkflowRuntime noop() {
        return new RegistrationWorkflowRuntime() {
            @Override
            public RegistrationWorkflowCheckpoint start(ChatRequest request, RegistrationIntent intent) {
                return RegistrationWorkflowCheckpoint.started(WORKFLOW_ID, intent, inputSnapshot(request));
            }

            @Override
            public RegistrationWorkflowCheckpoint resume(ChatRequest request,
                                                         RegistrationWorkflowCheckpoint checkpoint,
                                                         String currentNode) {
                RegistrationWorkflowCheckpoint source = checkpoint == null ? start(request, RegistrationIntent.CREATE) : checkpoint;
                return source.advance(currentNode, "", "RESUMED", Map.of("confirmationId", source.confirmationId()));
            }

            @Override
            public Mono<Void> record(ChatRequest request, RegistrationWorkflowCheckpoint checkpoint) {
                return Mono.empty();
            }

            @Override
            public Map<String, Object> traceData(RegistrationWorkflowCheckpoint checkpoint) {
                return traceMap(checkpoint);
            }
        };
    }

    private static Map<String, Object> inputSnapshot(ChatRequest request) {
        if (request == null) {
            return Map.of();
        }
        return Map.of(
                "traceId", safeText(request.traceId()),
                "chatId", safeText(request.chatId()),
                "userId", safeText(request.userId()),
                "messageLength", request.message() == null ? 0 : request.message().length(),
                "metadataKeys", String.join(",", request.metadata().keySet())
        );
    }

    private static Map<String, Object> traceMap(RegistrationWorkflowCheckpoint checkpoint) {
        if (checkpoint == null) {
            return Map.of();
        }
        List<Map<String, Object>> events = checkpoint.events().stream()
                .map(event -> Map.<String, Object>of(
                        "nodeId", event.nodeId(),
                        "status", event.status(),
                        "occurredAt", event.occurredAt().toString(),
                        "data", event.data()
                ))
                .toList();
        return Map.of(
                "workflowId", checkpoint.workflowId(),
                "executionId", checkpoint.executionId(),
                "confirmationId", checkpoint.confirmationId(),
                "intent", checkpoint.intent().name(),
                "currentNode", checkpoint.currentNode(),
                "nextNode", checkpoint.nextNode(),
                "events", events
        );
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}
