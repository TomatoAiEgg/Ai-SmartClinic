package com.example.airegistration.registrationmcp.repository;

import com.example.airegistration.registrationmcp.entity.RegistrationAuditRecord;

public interface RegistrationAuditRepository {

    void append(RegistrationAuditRecord record);
}
