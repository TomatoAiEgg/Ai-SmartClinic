package com.example.airegistration.triage.service.orchestrator;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ChatResponse;
import com.example.airegistration.dto.DepartmentSuggestion;
import com.example.airegistration.triage.enums.TriageReplyScene;
import com.example.airegistration.triage.service.TriageDepartmentClassifier;
import com.example.airegistration.triage.service.TriageReplyService;
import com.example.airegistration.triage.service.TriageUseCase;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TriageOrchestratorService implements TriageUseCase {

    private static final Logger log = LoggerFactory.getLogger(TriageOrchestratorService.class);

    private final TriageDepartmentClassifier departmentClassifier;
    private final TriageReplyService replyService;

    public TriageOrchestratorService(TriageDepartmentClassifier departmentClassifier, TriageReplyService replyService) {
        this.departmentClassifier = departmentClassifier;
        this.replyService = replyService;
    }

    @Override
    public Mono<ChatResponse> handle(ChatRequest request) {
        return departmentClassifier.suggestDepartment(request)
                .flatMap(suggestion -> reply(request, suggestion));
    }

    private Mono<ChatResponse> reply(ChatRequest request, DepartmentSuggestion suggestion) {
        log.info("[triage] suggestion decided trace_id={} chat_id={} department_code={} emergency={} reason={}",
                request.traceId(),
                request.chatId(),
                suggestion.departmentCode(),
                suggestion.emergency(),
                suggestion.reason());
        Map<String, Object> data = Map.of(
                "departmentCode", suggestion.departmentCode(),
                "departmentName", suggestion.departmentName(),
                "emergency", suggestion.emergency(),
                "reason", suggestion.reason()
        );
        return replyService.reply(request, TriageReplyScene.DEPARTMENT_SUGGESTION, data);
    }
}
