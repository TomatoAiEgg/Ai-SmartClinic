package com.example.airegistration.registrationmcp;

import com.example.airegistration.domain.RegistrationCancelRequest;
import com.example.airegistration.domain.RegistrationCommand;
import com.example.airegistration.domain.RegistrationRescheduleRequest;
import com.example.airegistration.domain.RegistrationResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RegistrationLedgerService {

    private final Map<String, RegistrationRecord> registrations = new ConcurrentHashMap<>();

    public RegistrationResult create(RegistrationCommand command) {
        String userId = normalizeRequired("userId", command.userId());
        String patientId = normalizeRequired("patientId", command.patientId());
        String departmentCode = normalizeRequired("departmentCode", command.departmentCode()).toUpperCase();
        String doctorId = normalizeRequired("doctorId", command.doctorId());
        String clinicDate = normalizeRequired("clinicDate", command.clinicDate());
        String startTime = normalizeRequired("startTime", command.startTime());
        ensureConfirmed(command.confirmed(), "create");

        String registrationId = "REG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        RegistrationRecord record = new RegistrationRecord(
                registrationId,
                userId,
                patientId,
                departmentCode,
                doctorId,
                clinicDate,
                startTime,
                "BOOKED"
        );
        registrations.put(registrationId, record);
        return toResult(record, "Registration created successfully.");
    }

    public RegistrationResult query(String registrationId) {
        RegistrationRecord record = getRequiredRecord(registrationId);
        return toResult(record, "Registration found.");
    }

    public RegistrationResult cancel(RegistrationCancelRequest request) {
        String registrationId = normalizeRequired("registrationId", request.registrationId()).toUpperCase();
        String userId = normalizeRequired("userId", request.userId());
        ensureConfirmed(request.confirmed(), "cancel");

        RegistrationRecord record = getRequiredRecord(registrationId);
        ensureOwner(record, userId);
        ensureCancelable(record);
        record.status = "CANCELLED";

        String reason = isBlank(request.reason()) ? "No reason provided." : request.reason().trim();
        return toResult(record, "Registration cancelled successfully. Reason: " + reason);
    }

    public RegistrationResult reschedule(RegistrationRescheduleRequest request) {
        String registrationId = normalizeRequired("registrationId", request.registrationId()).toUpperCase();
        String userId = normalizeRequired("userId", request.userId());
        String clinicDate = normalizeRequired("clinicDate", request.clinicDate());
        String startTime = normalizeRequired("startTime", request.startTime());
        ensureConfirmed(request.confirmed(), "reschedule");

        RegistrationRecord record = getRequiredRecord(registrationId);
        ensureOwner(record, userId);
        ensureReschedulable(record, clinicDate, startTime);
        record.clinicDate = clinicDate;
        record.startTime = startTime;
        record.status = "RESCHEDULED";

        return toResult(record, "Registration rescheduled successfully.");
    }

    private RegistrationRecord getRequiredRecord(String registrationId) {
        String normalizedRegistrationId = normalizeRequired("registrationId", registrationId).toUpperCase();
        RegistrationRecord record = registrations.get(normalizedRegistrationId);
        if (record == null) {
            throw new RegistrationOperationException(
                    "NOT_FOUND",
                    "No registration exists for the given ID.",
                    Map.of("registrationId", normalizedRegistrationId)
            );
        }
        return record;
    }

    private void ensureOwner(RegistrationRecord record, String userId) {
        if (!record.userId.equals(userId.trim())) {
            throw new RegistrationOperationException(
                    "INVALID_REQUEST",
                    "The registration does not belong to the given user.",
                    Map.of("registrationId", record.registrationId, "userId", userId)
            );
        }
    }

    private void ensureCancelable(RegistrationRecord record) {
        if ("CANCELLED".equals(record.status)) {
            throw new RegistrationOperationException(
                    "INVALID_REQUEST",
                    "The registration has already been cancelled.",
                    Map.of("registrationId", record.registrationId, "status", record.status)
            );
        }
    }

    private void ensureReschedulable(RegistrationRecord record, String clinicDate, String startTime) {
        if ("CANCELLED".equals(record.status)) {
            throw new RegistrationOperationException(
                    "INVALID_REQUEST",
                    "Cancelled registrations cannot be rescheduled.",
                    Map.of("registrationId", record.registrationId, "status", record.status)
            );
        }
        if (record.clinicDate.equals(clinicDate) && record.startTime.equals(startTime)) {
            throw new RegistrationOperationException(
                    "INVALID_REQUEST",
                    "The requested schedule is the same as the current registration.",
                    Map.of(
                            "registrationId", record.registrationId,
                            "clinicDate", clinicDate,
                            "startTime", startTime
                    )
            );
        }
    }

    private void ensureConfirmed(boolean confirmed, String action) {
        if (!confirmed) {
            throw new RegistrationOperationException(
                    "REQUIRES_CONFIRMATION",
                    "The " + action + " action requires explicit confirmation.",
                    Map.of("action", action)
            );
        }
    }

    private String normalizeRequired(String field, String value) {
        if (isBlank(value)) {
            throw new RegistrationOperationException(
                    "INVALID_REQUEST",
                    "Required field is missing: " + field,
                    Map.of("field", field)
            );
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private RegistrationResult toResult(RegistrationRecord record, String message) {
        return new RegistrationResult(
                record.registrationId,
                record.status,
                message,
                record.patientId,
                record.departmentCode,
                record.doctorId,
                record.clinicDate,
                record.startTime
        );
    }

    private static final class RegistrationRecord {
        private final String registrationId;
        private final String userId;
        private final String patientId;
        private final String departmentCode;
        private final String doctorId;
        private String clinicDate;
        private String startTime;
        private String status;

        private RegistrationRecord(String registrationId,
                                   String userId,
                                   String patientId,
                                   String departmentCode,
                                   String doctorId,
                                   String clinicDate,
                                   String startTime,
                                   String status) {
            this.registrationId = registrationId;
            this.userId = userId;
            this.patientId = patientId;
            this.departmentCode = departmentCode;
            this.doctorId = doctorId;
            this.clinicDate = clinicDate;
            this.startTime = startTime;
            this.status = status;
        }
    }
}
