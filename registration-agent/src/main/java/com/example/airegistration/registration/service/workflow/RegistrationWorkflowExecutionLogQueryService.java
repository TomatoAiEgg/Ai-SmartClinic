package com.example.airegistration.registration.service.workflow;

import com.example.airegistration.registration.dto.RegistrationWorkflowExecutionLogView;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RegistrationWorkflowExecutionLogQueryService {

    private final ObjectProvider<RegistrationWorkflowExecutionLogRepository> repositoryProvider;

    public RegistrationWorkflowExecutionLogQueryService(
            ObjectProvider<RegistrationWorkflowExecutionLogRepository> repositoryProvider) {
        this.repositoryProvider = repositoryProvider;
    }

    public List<RegistrationWorkflowExecutionLogView> listLogs(String traceId,
                                                               String chatId,
                                                               String executionId,
                                                               String confirmationId,
                                                               String intent,
                                                               String status,
                                                               Integer limit) {
        RegistrationWorkflowExecutionLogRepository repository = repositoryProvider.getIfAvailable();
        if (repository == null) {
            return List.of();
        }
        return repository.listLogs(
                nullIfBlank(traceId),
                nullIfBlank(chatId),
                nullIfBlank(executionId),
                nullIfBlank(confirmationId),
                nullIfBlank(intent),
                nullIfBlank(status),
                limit
        );
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
