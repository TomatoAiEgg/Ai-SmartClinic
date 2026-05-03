package com.example.airegistration.registrationmcp;

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
import com.example.airegistration.registrationmcp.service.RegistrationLedgerApplicationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationLedgerServiceTest {

    private final RegistrationLedgerApplicationService service =
            new RegistrationLedgerApplicationService(new FakeRegistrationLedgerRepository());

    @Test
    void shouldCreateAndQueryRegistration() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        ));

        RegistrationResult queried = service.query(created.registrationId());

        assertThat(created.registrationId()).startsWith("REG-");
        assertThat(queried.status()).isEqualTo(RegistrationStatus.BOOKED.code());
        assertThat(queried.patientId()).isEqualTo("patient-test-001");
        assertThat(queried.departmentCode()).isEqualTo("RESP");
    }

    @Test
    void shouldRejectQueryForWrongUser() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        ));

        assertThatThrownBy(() -> service.query(created.registrationId(), "user-999"))
                .isInstanceOfSatisfying(RegistrationOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));
    }

    @Test
    void shouldSearchRegistrationsByUserAndOptionalFilters() {
        RegistrationResult first = service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        ));
        service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "DERM",
                "doc-103",
                "2099-04-09",
                "14:00",
                true
        ));
        service.create(new RegistrationCommand(
                "user-test-002",
                "patient-test-002",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        ));

        assertThat(service.search(new RegistrationSearchRequest("user-test-001", null, null, null, null)))
                .hasSize(2);
        assertThat(service.search(new RegistrationSearchRequest("user-test-001", "2099-04-08", null, null, null)))
                .extracting(RegistrationResult::registrationId)
                .containsExactly(first.registrationId());
        assertThat(service.search(new RegistrationSearchRequest("user-test-001", null, "resp", null, "booked")))
                .extracting(RegistrationResult::registrationId)
                .containsExactly(first.registrationId());
    }

    @Test
    void shouldRejectCreateWithoutConfirmation() {
        assertThatThrownBy(() -> service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                false
        )))
                .isInstanceOf(RegistrationOperationException.class)
                .satisfies(ex -> assertThat(((RegistrationOperationException) ex).getErrorCode())
                        .isEqualTo(ApiErrorCode.REQUIRES_CONFIRMATION));
    }

    @Test
    void shouldRejectCreateForPastSlot() {
        assertThatThrownBy(() -> service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2000-04-08",
                "09:00",
                true
        )))
                .isInstanceOfSatisfying(RegistrationOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));
    }

    @Test
    void shouldRejectNullCreateCommand() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOfSatisfying(RegistrationOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST);
                    assertThat(ex.getDetails()).containsEntry("field", "command");
                });
    }

    @Test
    void shouldRejectBlankRequiredCreateField() {
        assertThatThrownBy(() -> service.create(new RegistrationCommand(
                "user-test-001",
                " ",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        )))
                .isInstanceOfSatisfying(RegistrationOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST);
                    assertThat(ex.getDetails()).containsEntry("field", "patientId");
                });
    }

    @Test
    void shouldReturnSameRegistrationForSameExternalRequestId() {
        RegistrationCommand command = new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true,
                "CONF-001",
                "chat-001"
        );

        RegistrationResult first = service.create(command);
        RegistrationResult second = service.create(command);

        assertThat(second.registrationId()).isEqualTo(first.registrationId());
        assertThat(service.search(new RegistrationSearchRequest("user-test-001", null, null, null, null)))
                .hasSize(1);
    }

    @Test
    void shouldWriteAuditForCreateAndFailure() {
        RecordingAuditRepository auditRepository = new RecordingAuditRepository();
        RegistrationLedgerApplicationService auditedService = new RegistrationLedgerApplicationService(
                new FakeRegistrationLedgerRepository(),
                auditRepository
        );

        RegistrationResult created = auditedService.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true,
                "CONF-AUDIT",
                "chat-audit"
        ), "trace-create");

        assertThatThrownBy(() -> auditedService.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-09",
                "09:00",
                true,
                "CONF-AUDIT",
                "chat-audit"
        ), "trace-create-failure")).isInstanceOf(RegistrationOperationException.class);

        assertThat(auditRepository.records)
                .extracting(RegistrationAuditRecord::operationType)
                .containsExactly("CREATE", "CREATE");
        assertThat(auditRepository.records.get(0).registrationId()).isEqualTo(created.registrationId());
        assertThat(auditRepository.records.get(0).success()).isTrue();
        assertThat(auditRepository.records.get(0).traceId()).isEqualTo("trace-create");
        assertThat(auditRepository.records.get(1).success()).isFalse();
        assertThat(auditRepository.records.get(1).traceId()).isEqualTo("trace-create-failure");
    }

    @Test
    void shouldCancelRegistration() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        ));

        RegistrationResult cancelled = service.cancel(new RegistrationCancelRequest(
                created.registrationId(),
                "user-test-001",
                true,
                "user_requested"
        ));

        assertThat(cancelled.status()).isEqualTo(RegistrationStatus.CANCELLED.code());
        assertThat(cancelled.message()).contains("reason: user_requested");
    }

    @Test
    void shouldRejectNullCancelRequest() {
        assertThatThrownBy(() -> service.cancel(null))
                .isInstanceOfSatisfying(RegistrationOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST);
                    assertThat(ex.getDetails()).containsEntry("field", "request");
                });
    }

    @Test
    void shouldRescheduleRegistration() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        ));

        RegistrationResult rescheduled = service.reschedule(new RegistrationRescheduleRequest(
                created.registrationId(),
                "user-test-001",
                "2099-04-10",
                "14:30",
                true
        ));

        assertThat(rescheduled.status()).isEqualTo(RegistrationStatus.RESCHEDULED.code());
        assertThat(rescheduled.clinicDate()).isEqualTo("2099-04-10");
        assertThat(rescheduled.startTime()).isEqualTo("14:30");
    }

    @Test
    void shouldRejectNullRescheduleRequest() {
        assertThatThrownBy(() -> service.reschedule(null))
                .isInstanceOfSatisfying(RegistrationOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST);
                    assertThat(ex.getDetails()).containsEntry("field", "request");
                });
    }

    @Test
    void shouldRejectCancelForWrongUser() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        ));

        assertThatThrownBy(() -> service.cancel(new RegistrationCancelRequest(
                created.registrationId(),
                "user-999",
                true,
                "user_requested"
        )))
                .isInstanceOf(RegistrationOperationException.class)
                .satisfies(ex -> assertThat(((RegistrationOperationException) ex).getErrorCode())
                        .isEqualTo(ApiErrorCode.INVALID_REQUEST));
    }

    @Test
    void shouldRejectDuplicateCancellation() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        ));

        service.cancel(new RegistrationCancelRequest(
                created.registrationId(),
                "user-test-001",
                true,
                "user_requested"
        ));

        assertThatThrownBy(() -> service.cancel(new RegistrationCancelRequest(
                created.registrationId(),
                "user-test-001",
                true,
                "user_requested"
        )))
                .isInstanceOf(RegistrationOperationException.class)
                .satisfies(ex -> assertThat(((RegistrationOperationException) ex).getErrorCode())
                        .isEqualTo(ApiErrorCode.INVALID_REQUEST));
    }

    @Test
    void shouldRejectRescheduleToTheSameSlot() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2099-04-08",
                "09:00",
                true
        ));

        assertThatThrownBy(() -> service.reschedule(new RegistrationRescheduleRequest(
                created.registrationId(),
                "user-test-001",
                "2099-04-08",
                "09:00",
                true
        )))
                .isInstanceOf(RegistrationOperationException.class)
                .satisfies(ex -> assertThat(((RegistrationOperationException) ex).getErrorCode())
                        .isEqualTo(ApiErrorCode.INVALID_REQUEST));
    }

    @Test
    void shouldReturnExpiredStatusForPastBookedRegistration() {
        FakeRegistrationLedgerRepository repository = new FakeRegistrationLedgerRepository();
        RegistrationLedgerApplicationService localService = new RegistrationLedgerApplicationService(repository);
        repository.save(new RegistrationRecord(
                "REG-EXPIRED",
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2000-04-08",
                "09:00",
                RegistrationStatus.BOOKED
        ));

        RegistrationResult result = localService.query("REG-EXPIRED", "user-test-001");

        assertThat(result.status()).isEqualTo(RegistrationStatus.EXPIRED.code());
        assertThat(localService.search(new RegistrationSearchRequest("user-test-001", null, null, null, "EXPIRED")))
                .extracting(RegistrationResult::registrationId)
                .containsExactly("REG-EXPIRED");
    }

    @Test
    void shouldRejectCancelAndRescheduleForExpiredRegistration() {
        FakeRegistrationLedgerRepository repository = new FakeRegistrationLedgerRepository();
        RegistrationLedgerApplicationService localService = new RegistrationLedgerApplicationService(repository);
        repository.save(new RegistrationRecord(
                "REG-EXPIRED",
                "user-test-001",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2000-04-08",
                "09:00",
                RegistrationStatus.BOOKED
        ));

        assertThatThrownBy(() -> localService.cancel(new RegistrationCancelRequest(
                "REG-EXPIRED",
                "user-test-001",
                true,
                "user_requested"
        )))
                .isInstanceOfSatisfying(RegistrationOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> localService.reschedule(new RegistrationRescheduleRequest(
                "REG-EXPIRED",
                "user-test-001",
                "2099-04-08",
                "09:00",
                true
        )))
                .isInstanceOfSatisfying(RegistrationOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST));
    }

    private static class RecordingAuditRepository implements RegistrationAuditRepository {

        private final List<RegistrationAuditRecord> records = new ArrayList<>();

        @Override
        public void append(RegistrationAuditRecord record) {
            records.add(record);
        }
    }

    private static class FakeRegistrationLedgerRepository implements RegistrationLedgerRepository {

        private final Map<String, RegistrationRecord> registrations = new ConcurrentHashMap<>();

        @Override
        public void save(RegistrationRecord record) {
            registrations.put(record.registrationId(), record);
        }

        @Override
        public Optional<RegistrationRecord> findById(String registrationId) {
            return Optional.ofNullable(registrations.get(registrationId));
        }

        @Override
        public Optional<RegistrationRecord> findByExternalRequestId(String externalRequestId) {
            if (externalRequestId == null || externalRequestId.isBlank()) {
                return Optional.empty();
            }
            return registrations.values().stream()
                    .filter(record -> externalRequestId.equals(record.externalRequestId()))
                    .findFirst();
        }

        @Override
        public List<RegistrationRecord> findAll() {
            return List.copyOf(registrations.values());
        }
    }
}

