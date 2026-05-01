package com.example.airegistration.registrationmcp.controller;

import com.example.airegistration.dto.RegistrationCancelRequest;
import com.example.airegistration.dto.RegistrationCommand;
import com.example.airegistration.dto.RegistrationRescheduleRequest;
import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import com.example.airegistration.registrationmcp.service.RegistrationLedgerUseCase;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class RegistrationMcpTools {

    private final RegistrationLedgerUseCase registrationLedgerUseCase;

    public RegistrationMcpTools(RegistrationLedgerUseCase registrationLedgerUseCase) {
        this.registrationLedgerUseCase = registrationLedgerUseCase;
    }

    @Tool(name = "registration-create", description = "Create a hospital registration after the patient confirms the booking.")
    public String createRegistration(
            @ToolParam(description = "User ID.") String userId,
            @ToolParam(description = "Patient ID.") String patientId,
            @ToolParam(description = "Department code.") String departmentCode,
            @ToolParam(description = "Doctor ID.") String doctorId,
            @ToolParam(description = "Clinic date.") String clinicDate,
            @ToolParam(description = "Start time.") String startTime,
            @ToolParam(description = "Explicit confirmation flag.") boolean confirmed) {
        RegistrationResult result = registrationLedgerUseCase.create(new RegistrationCommand(userId, patientId, departmentCode, doctorId, clinicDate, startTime, confirmed));
        return formatResult(result);
    }

    @Tool(name = "registration-query", description = "Query a registration by its registration ID.")
    public String queryRegistration(@ToolParam(description = "Registration ID.") String registrationId) {
        return formatResult(registrationLedgerUseCase.query(registrationId));
    }

    @Tool(name = "registration-search", description = "Search registrations by user and optional filters.")
    public String searchRegistrations(
            @ToolParam(description = "User ID.") String userId,
            @ToolParam(description = "Clinic date, optional.") String clinicDate,
            @ToolParam(description = "Department code, optional.") String departmentCode,
            @ToolParam(description = "Doctor ID, optional.") String doctorId,
            @ToolParam(description = "Registration status, optional.") String status) {
        List<RegistrationResult> results = registrationLedgerUseCase.search(
                new RegistrationSearchRequest(userId, clinicDate, departmentCode, doctorId, status));
        if (results.isEmpty()) {
            return "records=0";
        }
        return results.stream()
                .map(this::formatResult)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("records=0");
    }

    @Tool(name = "registration-cancel", description = "Cancel a registration after explicit confirmation.")
    public String cancelRegistration(
            @ToolParam(description = "Registration ID.") String registrationId,
            @ToolParam(description = "User ID.") String userId,
            @ToolParam(description = "Explicit confirmation flag.") boolean confirmed,
            @ToolParam(description = "Cancellation reason.") String reason) {
        RegistrationResult result = registrationLedgerUseCase.cancel(
                new RegistrationCancelRequest(registrationId, userId, confirmed, reason));
        return formatResult(result);
    }

    @Tool(name = "registration-reschedule", description = "Reschedule a registration after explicit confirmation.")
    public String rescheduleRegistration(
            @ToolParam(description = "Registration ID.") String registrationId,
            @ToolParam(description = "User ID.") String userId,
            @ToolParam(description = "New clinic date.") String clinicDate,
            @ToolParam(description = "New start time.") String startTime,
            @ToolParam(description = "Explicit confirmation flag.") boolean confirmed) {
        RegistrationResult result = registrationLedgerUseCase.reschedule(
                new RegistrationRescheduleRequest(registrationId, userId, clinicDate, startTime, confirmed));
        return formatResult(result);
    }

    private String formatResult(RegistrationResult result) {
        return "registrationId=%s,status=%s,patientId=%s,departmentCode=%s,doctorId=%s,clinicDate=%s,startTime=%s,message=%s"
                .formatted(
                        result.registrationId(),
                        result.status(),
                        result.patientId(),
                        result.departmentCode(),
                        result.doctorId(),
                        result.clinicDate(),
                        result.startTime(),
                        result.message()
                );
    }
}
