package com.example.airegistration.registrationmcp.repository;

import com.example.airegistration.registrationmcp.entity.RegistrationRecord;
import java.util.List;
import java.util.Optional;

public interface RegistrationLedgerRepository {

    void save(RegistrationRecord record);

    Optional<RegistrationRecord> findById(String registrationId);

    Optional<RegistrationRecord> findByExternalRequestId(String externalRequestId);

    List<RegistrationRecord> findAll();
}
