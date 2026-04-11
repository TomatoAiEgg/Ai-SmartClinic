package com.example.airegistration.registrationmcp;

import com.example.airegistration.domain.RegistrationCancelRequest;
import com.example.airegistration.domain.RegistrationCommand;
import com.example.airegistration.domain.RegistrationRescheduleRequest;
import com.example.airegistration.domain.RegistrationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationLedgerServiceTest {

    private final RegistrationLedgerService service = new RegistrationLedgerService();

    @Test
    void shouldCreateAndQueryRegistration() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-001",
                "patient-001",
                "RESP",
                "doc-101",
                "2026-04-08",
                "09:00",
                true
        ));

        RegistrationResult queried = service.query(created.registrationId());

        assertThat(created.registrationId()).startsWith("REG-");
        assertThat(queried.status()).isEqualTo("BOOKED");
        assertThat(queried.patientId()).isEqualTo("patient-001");
        assertThat(queried.departmentCode()).isEqualTo("RESP");
    }

    @Test
    void shouldRejectCreateWithoutConfirmation() {
        assertThatThrownBy(() -> service.create(new RegistrationCommand(
                "user-001",
                "patient-001",
                "RESP",
                "doc-101",
                "2026-04-08",
                "09:00",
                false
        )))
                .isInstanceOf(RegistrationOperationException.class)
                .hasMessageContaining("requires explicit confirmation");
    }

    @Test
    void shouldCancelRegistration() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-001",
                "patient-001",
                "RESP",
                "doc-101",
                "2026-04-08",
                "09:00",
                true
        ));

        RegistrationResult cancelled = service.cancel(new RegistrationCancelRequest(
                created.registrationId(),
                "user-001",
                true,
                "user_requested"
        ));

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.message()).contains("Reason: user_requested");
    }

    @Test
    void shouldRescheduleRegistration() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-001",
                "patient-001",
                "RESP",
                "doc-101",
                "2026-04-08",
                "09:00",
                true
        ));

        RegistrationResult rescheduled = service.reschedule(new RegistrationRescheduleRequest(
                created.registrationId(),
                "user-001",
                "2026-04-10",
                "14:30",
                true
        ));

        assertThat(rescheduled.status()).isEqualTo("RESCHEDULED");
        assertThat(rescheduled.clinicDate()).isEqualTo("2026-04-10");
        assertThat(rescheduled.startTime()).isEqualTo("14:30");
    }

    @Test
    void shouldRejectCancelForWrongUser() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-001",
                "patient-001",
                "RESP",
                "doc-101",
                "2026-04-08",
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
                .hasMessageContaining("does not belong");
    }

    @Test
    void shouldRejectDuplicateCancellation() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-001",
                "patient-001",
                "RESP",
                "doc-101",
                "2026-04-08",
                "09:00",
                true
        ));

        service.cancel(new RegistrationCancelRequest(
                created.registrationId(),
                "user-001",
                true,
                "user_requested"
        ));

        assertThatThrownBy(() -> service.cancel(new RegistrationCancelRequest(
                created.registrationId(),
                "user-001",
                true,
                "user_requested"
        )))
                .isInstanceOf(RegistrationOperationException.class)
                .hasMessageContaining("already been cancelled");
    }

    @Test
    void shouldRejectRescheduleToTheSameSlot() {
        RegistrationResult created = service.create(new RegistrationCommand(
                "user-001",
                "patient-001",
                "RESP",
                "doc-101",
                "2026-04-08",
                "09:00",
                true
        ));

        assertThatThrownBy(() -> service.reschedule(new RegistrationRescheduleRequest(
                created.registrationId(),
                "user-001",
                "2026-04-08",
                "09:00",
                true
        )))
                .isInstanceOf(RegistrationOperationException.class)
                .hasMessageContaining("same as the current registration");
    }
}
