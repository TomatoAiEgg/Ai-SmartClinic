package com.example.airegistration.patientmcp;

import com.example.airegistration.domain.PatientSummary;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class PatientMcpTools {

    private final PatientDirectoryService patientDirectoryService;

    public PatientMcpTools(PatientDirectoryService patientDirectoryService) {
        this.patientDirectoryService = patientDirectoryService;
    }

    @Tool(name = "patient-get-default-patient", description = "Return the default patient profile bound to a user.")
    public String getDefaultPatient(@ToolParam(description = "The user ID bound to the patient profile.") String userId) {
        PatientSummary patient = patientDirectoryService.getDefaultPatient(userId);
        return "patientId=%s,name=%s,idType=%s,maskedId=%s,maskedPhone=%s"
                .formatted(patient.patientId(), patient.name(), patient.idType(), patient.maskedIdNumber(), patient.maskedPhone());
    }
}
