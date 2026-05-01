package com.example.airegistration.patientmcp.repository;

import com.example.airegistration.dto.PatientCreateRequest;
import com.example.airegistration.dto.PatientSummary;
import java.util.List;

public interface PatientDirectoryRepository {

    PatientSummary findDefaultByUserId(String userId);

    List<PatientSummary> findByUserId(String userId);

    PatientSummary createForUser(PatientCreateRequest request);

    PatientSummary setDefault(String userId, String patientId);
}
