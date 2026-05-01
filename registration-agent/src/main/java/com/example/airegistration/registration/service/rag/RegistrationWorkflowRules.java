package com.example.airegistration.registration.service.rag;

import java.util.List;

public record RegistrationWorkflowRules(List<String> ruleIds,
                                        boolean previewBeforeWrite,
                                        List<String> createRequiredFields,
                                        boolean requireRegistrationId,
                                        List<String> rescheduleRequiredFields,
                                        boolean allowDepartmentChangeOnReschedule,
                                        boolean allowDoctorChangeOnReschedule) {

    public RegistrationWorkflowRules {
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
        createRequiredFields = createRequiredFields == null ? List.of() : List.copyOf(createRequiredFields);
        rescheduleRequiredFields =
                rescheduleRequiredFields == null ? List.of() : List.copyOf(rescheduleRequiredFields);
    }
}
