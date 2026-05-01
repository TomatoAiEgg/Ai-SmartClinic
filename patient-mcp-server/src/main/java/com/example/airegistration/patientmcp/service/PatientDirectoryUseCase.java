package com.example.airegistration.patientmcp.service;

import com.example.airegistration.dto.PatientCreateRequest;
import com.example.airegistration.dto.PatientSummary;
import java.util.List;

public interface PatientDirectoryUseCase {

    PatientSummary getDefaultPatient(String userId);

    List<PatientSummary> listPatients(String userId);

    PatientSummary createPatient(PatientCreateRequest request);

    PatientSummary setDefaultPatient(String userId, String patientId);
}
