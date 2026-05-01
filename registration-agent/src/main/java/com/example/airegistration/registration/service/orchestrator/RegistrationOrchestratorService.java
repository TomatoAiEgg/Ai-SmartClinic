package com.example.airegistration.registration.service.orchestrator;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.registration.service.RegistrationAgentUseCase;
import com.example.airegistration.registration.service.RegistrationIntentClassifier;
import com.example.airegistration.registration.service.workflow.RegistrationWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RegistrationOrchestratorService implements RegistrationAgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrationOrchestratorService.class);

    private final RegistrationIntentClassifier intentClassifier;
    private final RegistrationWorkflowService workflowService;

    public RegistrationOrchestratorService(RegistrationIntentClassifier intentClassifier,
                                           RegistrationWorkflowService workflowService) {
        this.intentClassifier = intentClassifier;
        this.workflowService = workflowService;
    }

    @Override
    public Mono<ChatResponse> handle(ChatRequest request) {
        return intentClassifier.determineIntent(request)
                .flatMap(intent -> {
                    log.info("[registration] intent decided trace_id={} chat_id={} user_id={} intent={} message_length={} metadata_keys={}",
                            request.traceId(),
                            request.chatId(),
                            request.userId(),
                            intent,
                            request.message() == null ? 0 : request.message().length(),
                            request.metadata().keySet());
                    return workflowService.handle(request, intent);
                });
    }
}
