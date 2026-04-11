package com.example.airegistration.registrationmcp;

import com.example.airegistration.domain.RegistrationCancelRequest;
import com.example.airegistration.domain.RegistrationCommand;
import com.example.airegistration.domain.RegistrationResult;
import com.example.airegistration.domain.RegistrationRescheduleRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class RegistrationMcpTools {

    private final RegistrationLedgerService registrationLedgerService;

    public RegistrationMcpTools(RegistrationLedgerService registrationLedgerService) {
        this.registrationLedgerService = registrationLedgerService;
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
        RegistrationResult result = registrationLedgerService.create(new RegistrationCommand(userId, patientId, departmentCode, doctorId, clinicDate, startTime, confirmed));
        return formatResult(result);
    }

    @Tool(name = "registration-query", description = "Query a registration by its registration ID.")
    public String queryRegistration(@ToolParam(description = "Registration ID.") String registrationId) {
        return formatResult(registrationLedgerService.query(registrationId));
    }

    @Tool(name = "registration-cancel", description = "Cancel a registration after explicit confirmation.")
    public String cancelRegistration(
            @ToolParam(description = "Registration ID.") String registrationId,
            @ToolParam(description = "User ID.") String userId,
            @ToolParam(description = "Explicit confirmation flag.") boolean confirmed,
            @ToolParam(description = "Cancellation reason.") String reason) {
        RegistrationResult result = registrationLedgerService.cancel(
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
        RegistrationResult result = registrationLedgerService.reschedule(
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
