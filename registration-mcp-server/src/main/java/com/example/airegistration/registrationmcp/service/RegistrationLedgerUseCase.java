package com.example.airegistration.registrationmcp.service;

import com.example.airegistration.dto.RegistrationCancelRequest;
import com.example.airegistration.dto.RegistrationCommand;
import com.example.airegistration.dto.RegistrationRescheduleRequest;
import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import java.util.List;

public interface RegistrationLedgerUseCase {

    default RegistrationResult create(RegistrationCommand command) {
        return create(command, null);
    }

    RegistrationResult create(RegistrationCommand command, String traceId);

    default RegistrationResult query(String registrationId) {
        return query(registrationId, null, null);
    }

    default RegistrationResult query(String registrationId, String userId) {
        return query(registrationId, userId, null);
    }

    RegistrationResult query(String registrationId, String userId, String traceId);

    default List<RegistrationResult> search(RegistrationSearchRequest request) {
        return search(request, null);
    }

    List<RegistrationResult> search(RegistrationSearchRequest request, String traceId);

    default RegistrationResult cancel(RegistrationCancelRequest request) {
        return cancel(request, null);
    }

    RegistrationResult cancel(RegistrationCancelRequest request, String traceId);

    default RegistrationResult reschedule(RegistrationRescheduleRequest request) {
        return reschedule(request, null);
    }

    RegistrationResult reschedule(RegistrationRescheduleRequest request, String traceId);
}
