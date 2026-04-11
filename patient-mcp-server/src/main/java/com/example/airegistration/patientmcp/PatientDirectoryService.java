package com.example.airegistration.patientmcp;

import com.example.airegistration.domain.PatientSummary;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PatientDirectoryService {

    private final Map<String, PatientSummary> mockPatients = Map.of(
            "user-001", new PatientSummary("patient-001", "user-001", "Alex Chen", "ID_CARD", "110***********1234", "138****0001"),
            "user-002", new PatientSummary("patient-002", "user-002", "Jamie Lin", "ID_CARD", "310***********5678", "138****0002")
    );

    public PatientSummary getDefaultPatient(String userId) {
        return mockPatients.getOrDefault(userId, new PatientSummary("patient-demo", userId, "Demo Patient", "ID_CARD", "320***********0000", "138****9999"));
    }
}
