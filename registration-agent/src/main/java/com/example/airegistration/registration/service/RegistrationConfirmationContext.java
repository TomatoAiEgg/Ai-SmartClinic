package com.example.airegistration.registration.service;

import com.example.airegistration.registration.service.workflow.RegistrationWorkflowCheckpoint;
import java.util.Map;

public record RegistrationConfirmationContext(
        String confirmationId,
        String action,
        String userId,
        String chatId,
        Map<String, Object> data,
        RegistrationWorkflowCheckpoint checkpoint
) {
    public RegistrationConfirmationContext {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public RegistrationConfirmationContext(String confirmationId,
                                           String action,
                                           String userId,
                                           String chatId,
                                           Map<String, Object> data) {
        this(confirmationId, action, userId, chatId, data, null);
    }
}
