package com.example.airegistration.registrationmcp.service;

import com.example.airegistration.dto.RegistrationCancelRequest;
import com.example.airegistration.dto.RegistrationCommand;
import com.example.airegistration.dto.RegistrationRescheduleRequest;
import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.enums.RegistrationStatus;
import com.example.airegistration.registrationmcp.entity.RegistrationAuditRecord;
import com.example.airegistration.registrationmcp.entity.RegistrationRecord;
import com.example.airegistration.registrationmcp.exception.RegistrationOperationException;
import com.example.airegistration.registrationmcp.repository.RegistrationAuditRepository;
import com.example.airegistration.registrationmcp.repository.RegistrationLedgerRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegistrationLedgerApplicationService implements RegistrationLedgerUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegistrationLedgerApplicationService.class);
    private static final String OPERATION_CREATE = "CREATE";
    private static final String OPERATION_QUERY = "QUERY";
    private static final String OPERATION_CANCEL = "CANCEL";
    private static final String OPERATION_RESCHEDULE = "RESCHEDULE";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final RegistrationLedgerRepository registrationLedgerRepository;
    private final RegistrationAuditRepository registrationAuditRepository;

    public RegistrationLedgerApplicationService(RegistrationLedgerRepository registrationLedgerRepository) {
        this(registrationLedgerRepository, record -> {
        });
    }

    @Autowired
    public RegistrationLedgerApplicationService(RegistrationLedgerRepository registrationLedgerRepository,
                                                RegistrationAuditRepository registrationAuditRepository) {
        this.registrationLedgerRepository = registrationLedgerRepository;
        this.registrationAuditRepository = registrationAuditRepository == null ? record -> {
        } : registrationAuditRepository;
    }

    @Override
    public RegistrationResult create(RegistrationCommand command) {
        Map<String, Object> requestPayload = commandPayload(command);
        String registrationId = null;
        String operatorUserId = command == null ? null : nullIfBlank(command.userId());
        String chatId = command == null ? null : nullIfBlank(command.chatId());

        try {
            ensureRequestPresent(command, "command");
            String userId = normalizeRequired("userId", command.userId());
            String patientId = normalizeRequired("patientId", command.patientId());
            String departmentCode = normalizeRequired("departmentCode", command.departmentCode()).toUpperCase(Locale.ROOT);
            String doctorId = normalizeRequired("doctorId", command.doctorId());
            String clinicDate = normalizeRequired("clinicDate", command.clinicDate());
            String startTime = normalizeRequired("startTime", command.startTime());
            String externalRequestId = nullIfBlank(command.externalRequestId());
            operatorUserId = userId;
            chatId = nullIfBlank(command.chatId());
            ensureConfirmed(command.confirmed(), "create");
            ensureFutureSlot(clinicDate, startTime);

            if (externalRequestId != null) {
                Optional<RegistrationRecord> existing = registrationLedgerRepository.findByExternalRequestId(externalRequestId);
                if (existing.isPresent()) {
                    RegistrationRecord record = existing.get();
                    ensureSameCreateRequest(record, userId, patientId, departmentCode, doctorId, clinicDate, startTime);
                    RegistrationResult result = toResult(record, "Registration created successfully.");
                    appendAudit(new RegistrationAuditRecord(
                            record.registrationId(),
                            OPERATION_CREATE,
                            userId,
                            chatId,
                            true,
                            "idempotent-hit",
                            requestPayload,
                            resultPayload(result),
                            recordSnapshot(record),
                            recordSnapshot(record)
                    ));
                    return result;
                }
            }

            registrationId = "REG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            RegistrationRecord record = new RegistrationRecord(
                    registrationId,
                    userId,
                    patientId,
                    departmentCode,
                    doctorId,
                    clinicDate,
                    startTime,
                    RegistrationStatus.BOOKED,
                    externalRequestId,
                    chatId
            );

            try {
                registrationLedgerRepository.save(record);
            } catch (RuntimeException ex) {
                if (externalRequestId != null) {
                    Optional<RegistrationRecord> existing = registrationLedgerRepository.findByExternalRequestId(externalRequestId);
                    if (existing.isPresent()) {
                        RegistrationRecord existingRecord = existing.get();
                        ensureSameCreateRequest(existingRecord, userId, patientId, departmentCode, doctorId, clinicDate, startTime);
                        RegistrationResult result = toResult(existingRecord, "Registration created successfully.");
                        appendAudit(new RegistrationAuditRecord(
                                existingRecord.registrationId(),
                                OPERATION_CREATE,
                                userId,
                                chatId,
                                true,
                                "idempotent-race-hit",
                                requestPayload,
                                resultPayload(result),
                                recordSnapshot(existingRecord),
                                recordSnapshot(existingRecord)
                        ));
                        return result;
                    }
                }
                throw ex;
            }

            RegistrationResult result = toResult(record, "Registration created successfully.");
            appendAudit(new RegistrationAuditRecord(
                    registrationId,
                    OPERATION_CREATE,
                    userId,
                    chatId,
                    true,
                    "created",
                    requestPayload,
                    resultPayload(result),
                    Map.of(),
                    recordSnapshot(record)
            ));
            return result;
        } catch (RuntimeException ex) {
            appendFailureAudit(OPERATION_CREATE, registrationId, operatorUserId, chatId, requestPayload, ex);
            throw ex;
        }
    }

    @Override
    public RegistrationResult query(String registrationId) {
        return query(registrationId, null);
    }

    @Override
    public RegistrationResult query(String registrationId, String userId) {
        Map<String, Object> requestPayload = new HashMap<>();
        putIfPresent(requestPayload, "registrationId", registrationId);
        putIfPresent(requestPayload, "userId", userId);
        String operatorUserId = nullIfBlank(userId);

        try {
            RegistrationRecord record = getRequiredRecord(registrationId);
            if (!isBlank(userId)) {
                ensureOwner(record, userId);
            }
            RegistrationResult result = toResult(record, "Registration found.");
            appendAudit(new RegistrationAuditRecord(
                    record.registrationId(),
                    OPERATION_QUERY,
                    operatorUserId,
                    record.chatId(),
                    true,
                    "query-by-id",
                    requestPayload,
                    resultPayload(result),
                    Map.of(),
                    recordSnapshot(record)
            ));
            return result;
        } catch (RuntimeException ex) {
            appendFailureAudit(OPERATION_QUERY, nullIfBlank(registrationId), operatorUserId, null, requestPayload, ex);
            throw ex;
        }
    }

    @Override
    public List<RegistrationResult> search(RegistrationSearchRequest request) {
        Map<String, Object> requestPayload = searchPayload(request);
        String operatorUserId = request == null ? null : nullIfBlank(request.userId());

        try {
            ensureRequestPresent(request, "request");
            String userId = normalizeRequired("userId", request.userId());
            String clinicDate = normalizeOptional(request.clinicDate());
            String departmentCode = normalizeOptional(request.departmentCode());
            if (!isBlank(departmentCode)) {
                departmentCode = departmentCode.toUpperCase(Locale.ROOT);
            }
            String doctorId = normalizeOptional(request.doctorId());
            String status = normalizeOptional(request.status());
            if (!isBlank(status)) {
                status = status.toUpperCase(Locale.ROOT);
            }

            String finalDepartmentCode = departmentCode;
            String finalStatus = status;
            List<RegistrationResult> results = registrationLedgerRepository.findAll().stream()
                    .filter(record -> record.userId().equals(userId))
                    .filter(record -> isBlank(clinicDate) || record.clinicDate().equals(clinicDate))
                    .filter(record -> isBlank(finalDepartmentCode) || record.departmentCode().equals(finalDepartmentCode))
                    .filter(record -> isBlank(doctorId) || record.doctorId().equalsIgnoreCase(doctorId))
                    .filter(record -> isBlank(finalStatus) || effectiveStatus(record).code().equalsIgnoreCase(finalStatus))
                    .sorted(Comparator.comparing(RegistrationRecord::clinicDate)
                            .thenComparing(RegistrationRecord::startTime)
                            .thenComparing(RegistrationRecord::registrationId))
                    .map(record -> toResult(record, "Registration found."))
                    .toList();
            appendAudit(new RegistrationAuditRecord(
                    null,
                    OPERATION_QUERY,
                    userId,
                    null,
                    true,
                    "search",
                    requestPayload,
                    searchResponsePayload(results),
                    Map.of(),
                    Map.of()
            ));
            return results;
        } catch (RuntimeException ex) {
            appendFailureAudit(OPERATION_QUERY, null, operatorUserId, null, requestPayload, ex);
            throw ex;
        }
    }

    @Override
    public RegistrationResult cancel(RegistrationCancelRequest request) {
        Map<String, Object> requestPayload = cancelPayload(request);
        String registrationId = request == null ? null : nullIfBlank(request.registrationId());
        String operatorUserId = request == null ? null : nullIfBlank(request.userId());

        try {
            ensureRequestPresent(request, "request");
            registrationId = normalizeRequired("registrationId", request.registrationId()).toUpperCase(Locale.ROOT);
            String userId = normalizeRequired("userId", request.userId());
            operatorUserId = userId;
            ensureConfirmed(request.confirmed(), "cancel");

            RegistrationRecord record = getRequiredRecord(registrationId);
            ensureOwner(record, userId);
            ensureCancelable(record);
            ensureNotExpired(record, "cancel");
            Map<String, Object> beforeSnapshot = recordSnapshot(record);
            record.cancel();
            registrationLedgerRepository.save(record);

            String reason = isBlank(request.reason()) ? "not_provided" : request.reason().trim();
            RegistrationResult result = toResult(record, "Registration cancelled successfully, reason: " + reason);
            appendAudit(new RegistrationAuditRecord(
                    registrationId,
                    OPERATION_CANCEL,
                    userId,
                    record.chatId(),
                    true,
                    reason,
                    requestPayload,
                    resultPayload(result),
                    beforeSnapshot,
                    recordSnapshot(record)
            ));
            return result;
        } catch (RuntimeException ex) {
            appendFailureAudit(OPERATION_CANCEL, registrationId, operatorUserId, null, requestPayload, ex);
            throw ex;
        }
    }

    @Override
    public RegistrationResult reschedule(RegistrationRescheduleRequest request) {
        Map<String, Object> requestPayload = reschedulePayload(request);
        String registrationId = request == null ? null : nullIfBlank(request.registrationId());
        String operatorUserId = request == null ? null : nullIfBlank(request.userId());

        try {
            ensureRequestPresent(request, "request");
            registrationId = normalizeRequired("registrationId", request.registrationId()).toUpperCase(Locale.ROOT);
            String userId = normalizeRequired("userId", request.userId());
            String clinicDate = normalizeRequired("clinicDate", request.clinicDate());
            String startTime = normalizeRequired("startTime", request.startTime());
            operatorUserId = userId;
            ensureConfirmed(request.confirmed(), "reschedule");
            ensureFutureSlot(clinicDate, startTime);

            RegistrationRecord record = getRequiredRecord(registrationId);
            ensureOwner(record, userId);
            ensureReschedulable(record, clinicDate, startTime);
            ensureNotExpired(record, "reschedule");
            Map<String, Object> beforeSnapshot = recordSnapshot(record);
            record.reschedule(clinicDate, startTime);
            registrationLedgerRepository.save(record);

            RegistrationResult result = toResult(record, "Registration rescheduled successfully.");
            appendAudit(new RegistrationAuditRecord(
                    registrationId,
                    OPERATION_RESCHEDULE,
                    userId,
                    record.chatId(),
                    true,
                    "rescheduled",
                    requestPayload,
                    resultPayload(result),
                    beforeSnapshot,
                    recordSnapshot(record)
            ));
            return result;
        } catch (RuntimeException ex) {
            appendFailureAudit(OPERATION_RESCHEDULE, registrationId, operatorUserId, null, requestPayload, ex);
            throw ex;
        }
    }

    private RegistrationRecord getRequiredRecord(String registrationId) {
        String normalizedRegistrationId = normalizeRequired("registrationId", registrationId).toUpperCase(Locale.ROOT);
        return registrationLedgerRepository.findById(normalizedRegistrationId)
                .orElseThrow(() -> new RegistrationOperationException(
                        ApiErrorCode.NOT_FOUND,
                        "Registration record does not exist.",
                        Map.of("registrationId", normalizedRegistrationId)
                ));
    }

    private void ensureRequestPresent(Object request, String field) {
        if (request == null) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Request object cannot be null.",
                    Map.of("field", field)
            );
        }
    }

    private void ensureOwner(RegistrationRecord record, String userId) {
        if (!record.userId().equals(userId.trim())) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Registration record does not belong to current user.",
                    Map.of("registrationId", record.registrationId(), "userId", userId)
            );
        }
    }

    private void ensureCancelable(RegistrationRecord record) {
        if (record.status() == RegistrationStatus.CANCELLED) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Registration is already cancelled.",
                    Map.of("registrationId", record.registrationId(), "status", record.status().code())
            );
        }
    }

    private void ensureReschedulable(RegistrationRecord record, String clinicDate, String startTime) {
        if (record.status() == RegistrationStatus.CANCELLED) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Cancelled registration cannot be rescheduled.",
                    Map.of("registrationId", record.registrationId(), "status", record.status().code())
            );
        }
        if (record.clinicDate().equals(clinicDate) && record.startTime().equals(startTime)) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Target slot is the same as current slot.",
                    Map.of(
                            "registrationId", record.registrationId(),
                            "clinicDate", clinicDate,
                            "startTime", startTime
                    )
            );
        }
    }

    private void ensureFutureSlot(String clinicDate, String startTime) {
        LocalDateTime appointmentTime = parseAppointmentTime(clinicDate, startTime);
        if (!appointmentTime.isAfter(LocalDateTime.now())) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Appointment slot must be in the future.",
                    Map.of("clinicDate", clinicDate, "startTime", startTime)
            );
        }
    }

    private void ensureNotExpired(RegistrationRecord record, String action) {
        if (effectiveStatus(record) == RegistrationStatus.EXPIRED) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Expired registration cannot be " + action + ".",
                    Map.of("registrationId", record.registrationId(), "status", RegistrationStatus.EXPIRED.code())
            );
        }
    }

    private RegistrationResult toResult(RegistrationRecord record, String message) {
        return new RegistrationResult(
                record.registrationId(),
                effectiveStatus(record).code(),
                message,
                record.patientId(),
                record.departmentCode(),
                record.doctorId(),
                record.clinicDate(),
                record.startTime()
        );
    }

    private RegistrationStatus effectiveStatus(RegistrationRecord record) {
        if (record.status() != RegistrationStatus.BOOKED && record.status() != RegistrationStatus.RESCHEDULED) {
            return record.status();
        }
        return parseAppointmentTime(record.clinicDate(), record.startTime()).isBefore(LocalDateTime.now())
                ? RegistrationStatus.EXPIRED
                : record.status();
    }

    private LocalDateTime parseAppointmentTime(String clinicDate, String startTime) {
        try {
            return LocalDateTime.of(LocalDate.parse(clinicDate), LocalTime.parse(startTime, TIME_FORMATTER));
        } catch (DateTimeParseException ex) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Invalid clinic date or start time.",
                    Map.of("clinicDate", clinicDate, "startTime", startTime)
            );
        }
    }

    private void ensureSameCreateRequest(RegistrationRecord record,
                                         String userId,
                                         String patientId,
                                         String departmentCode,
                                         String doctorId,
                                         String clinicDate,
                                         String startTime) {
        boolean samePayload = record.userId().equals(userId)
                && record.patientId().equals(patientId)
                && record.departmentCode().equals(departmentCode)
                && record.doctorId().equals(doctorId)
                && record.clinicDate().equals(clinicDate)
                && record.startTime().equals(startTime);
        if (!samePayload) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "External request id already belongs to another registration payload.",
                    Map.of("registrationId", record.registrationId())
            );
        }
    }

    private void ensureConfirmed(boolean confirmed, String action) {
        if (!confirmed) {
            throw new RegistrationOperationException(
                    ApiErrorCode.REQUIRES_CONFIRMATION,
                    "Operation requires explicit user confirmation.",
                    Map.of("action", action)
            );
        }
    }

    private String normalizeRequired(String field, String value) {
        if (isBlank(value)) {
            throw new RegistrationOperationException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Required parameter is missing: " + field,
                    Map.of("field", field)
            );
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return isBlank(value) ? "" : value.trim();
    }

    private String nullIfBlank(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void appendAudit(RegistrationAuditRecord record) {
        try {
            registrationAuditRepository.append(record);
        } catch (RuntimeException ex) {
            log.warn("Failed to append registration audit log. operation={} registration_id={}",
                    record.operationType(),
                    record.registrationId(),
                    ex);
        }
    }

    private void appendFailureAudit(String operationType,
                                    String registrationId,
                                    String operatorUserId,
                                    String chatId,
                                    Map<String, Object> requestPayload,
                                    RuntimeException ex) {
        appendAudit(new RegistrationAuditRecord(
                registrationId,
                operationType,
                operatorUserId,
                chatId,
                false,
                errorMessage(ex),
                requestPayload,
                errorPayload(ex),
                Map.of(),
                Map.of()
        ));
    }

    private Map<String, Object> commandPayload(RegistrationCommand command) {
        Map<String, Object> payload = new HashMap<>();
        if (command == null) {
            payload.put("request", "null");
            return Map.copyOf(payload);
        }
        putIfPresent(payload, "userId", command.userId());
        putIfPresent(payload, "patientId", command.patientId());
        putIfPresent(payload, "departmentCode", command.departmentCode());
        putIfPresent(payload, "doctorId", command.doctorId());
        putIfPresent(payload, "clinicDate", command.clinicDate());
        putIfPresent(payload, "startTime", command.startTime());
        putIfPresent(payload, "externalRequestId", command.externalRequestId());
        putIfPresent(payload, "chatId", command.chatId());
        payload.put("confirmed", command.confirmed());
        return Map.copyOf(payload);
    }

    private Map<String, Object> cancelPayload(RegistrationCancelRequest request) {
        Map<String, Object> payload = new HashMap<>();
        if (request == null) {
            payload.put("request", "null");
            return Map.copyOf(payload);
        }
        putIfPresent(payload, "registrationId", request.registrationId());
        putIfPresent(payload, "userId", request.userId());
        putIfPresent(payload, "reason", request.reason());
        payload.put("confirmed", request.confirmed());
        return Map.copyOf(payload);
    }

    private Map<String, Object> reschedulePayload(RegistrationRescheduleRequest request) {
        Map<String, Object> payload = new HashMap<>();
        if (request == null) {
            payload.put("request", "null");
            return Map.copyOf(payload);
        }
        putIfPresent(payload, "registrationId", request.registrationId());
        putIfPresent(payload, "userId", request.userId());
        putIfPresent(payload, "clinicDate", request.clinicDate());
        putIfPresent(payload, "startTime", request.startTime());
        payload.put("confirmed", request.confirmed());
        return Map.copyOf(payload);
    }

    private Map<String, Object> searchPayload(RegistrationSearchRequest request) {
        Map<String, Object> payload = new HashMap<>();
        if (request == null) {
            payload.put("request", "null");
            return Map.copyOf(payload);
        }
        putIfPresent(payload, "userId", request.userId());
        putIfPresent(payload, "clinicDate", request.clinicDate());
        putIfPresent(payload, "departmentCode", request.departmentCode());
        putIfPresent(payload, "doctorId", request.doctorId());
        putIfPresent(payload, "status", request.status());
        return Map.copyOf(payload);
    }

    private Map<String, Object> resultPayload(RegistrationResult result) {
        Map<String, Object> payload = new HashMap<>();
        putIfPresent(payload, "registrationId", result.registrationId());
        putIfPresent(payload, "status", result.status());
        putIfPresent(payload, "message", result.message());
        putIfPresent(payload, "patientId", result.patientId());
        putIfPresent(payload, "departmentCode", result.departmentCode());
        putIfPresent(payload, "doctorId", result.doctorId());
        putIfPresent(payload, "clinicDate", result.clinicDate());
        putIfPresent(payload, "startTime", result.startTime());
        return Map.copyOf(payload);
    }

    private Map<String, Object> searchResponsePayload(List<RegistrationResult> results) {
        return Map.of(
                "count", results.size(),
                "records", results.stream().map(this::resultPayload).toList()
        );
    }

    private Map<String, Object> recordSnapshot(RegistrationRecord record) {
        Map<String, Object> payload = new HashMap<>();
        putIfPresent(payload, "registrationId", record.registrationId());
        putIfPresent(payload, "userId", record.userId());
        putIfPresent(payload, "patientId", record.patientId());
        putIfPresent(payload, "departmentCode", record.departmentCode());
        putIfPresent(payload, "doctorId", record.doctorId());
        putIfPresent(payload, "clinicDate", record.clinicDate());
        putIfPresent(payload, "startTime", record.startTime());
        putIfPresent(payload, "externalRequestId", record.externalRequestId());
        putIfPresent(payload, "chatId", record.chatId());
        payload.put("status", record.status().code());
        return Map.copyOf(payload);
    }

    private Map<String, Object> errorPayload(RuntimeException ex) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("errorType", ex.getClass().getSimpleName());
        payload.put("message", errorMessage(ex));
        if (ex instanceof RegistrationOperationException operationException) {
            payload.put("code", operationException.getCode());
            if (!operationException.getDetails().isEmpty()) {
                payload.put("details", operationException.getDetails());
            }
        }
        return Map.copyOf(payload);
    }

    private String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }

    private void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (!isBlank(value)) {
            payload.put(key, value.trim());
        }
    }
}
