package com.example.airegistration.registrationmcp.service;

import com.example.airegistration.dto.RegistrationCancelRequest;
import com.example.airegistration.dto.RegistrationCommand;
import com.example.airegistration.dto.RegistrationRescheduleRequest;
import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import java.util.List;

public interface RegistrationLedgerUseCase {

    RegistrationResult create(RegistrationCommand command);

    RegistrationResult query(String registrationId);

    RegistrationResult query(String registrationId, String userId);

    List<RegistrationResult> search(RegistrationSearchRequest request);

    RegistrationResult cancel(RegistrationCancelRequest request);

    RegistrationResult reschedule(RegistrationRescheduleRequest request);
}
