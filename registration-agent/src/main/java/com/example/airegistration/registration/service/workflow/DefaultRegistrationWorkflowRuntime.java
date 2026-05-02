package com.example.airegistration.registration.service.workflow;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationIntent;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DefaultRegistrationWorkflowRuntime implements RegistrationWorkflowRuntime {

    private static final Logger log = LoggerFactory.getLogger(DefaultRegistrationWorkflowRuntime.class);

    private final ObjectProvider<RegistrationWorkflowExecutionLogRepository> repositoryProvider;

    public DefaultRegistrationWorkflowRuntime(ObjectProvider<RegistrationWorkflowExecutionLogRepository> repositoryProvider) {
        this.repositoryProvider = repositoryProvider;
    }

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
        RegistrationWorkflowExecutionLogRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null || checkpoint == null || checkpoint.events().isEmpty()) {
            return Mono.empty();
        }
        RegistrationWorkflowTraceEvent event = checkpoint.events().get(checkpoint.events().size() - 1);
        return repository.save(request, checkpoint, event)
                .doOnError(ex -> log.warn(
                        "[registration] workflow log write failed trace_id={} chat_id={} execution_id={} node={} error={}",
                        request == null ? null : request.traceId(),
                        request == null ? null : request.chatId(),
                        checkpoint.executionId(),
                        event.nodeId(),
                        ex.getMessage()
                ))
                .onErrorResume(ex -> Mono.empty());
    }

    @Override
    public Map<String, Object> traceData(RegistrationWorkflowCheckpoint checkpoint) {
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

    private Map<String, Object> inputSnapshot(ChatRequest request) {
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

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
