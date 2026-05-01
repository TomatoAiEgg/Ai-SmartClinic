package com.example.airegistration.patientmcp;

import com.example.airegistration.dto.PatientCreateRequest;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.patientmcp.exception.PatientOperationException;
import com.example.airegistration.patientmcp.repository.PatientDirectoryRepository;
import com.example.airegistration.patientmcp.service.PatientDirectoryApplicationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientDirectoryApplicationServiceTest {

    private final PatientDirectoryApplicationService service = new PatientDirectoryApplicationService(
            new FakePatientDirectoryRepository()
    );

    @Test
    void shouldReturnKnownDefaultPatient() {
        PatientSummary patient = service.getDefaultPatient("user-test-001");

        assertThat(patient.patientId()).isEqualTo("patient-test-001");
        assertThat(patient.userId()).isEqualTo("user-test-001");
    }

    @Test
    void shouldTrimUserIdBeforeLookup() {
        PatientSummary patient = service.getDefaultPatient(" user-test-002 ");

        assertThat(patient.patientId()).isEqualTo("patient-test-002");
        assertThat(patient.userId()).isEqualTo("user-test-002");
    }

    @Test
    void shouldRejectUnknownUserWithoutDefaultPatient() {
        assertThatThrownBy(() -> service.getDefaultPatient("user-new"))
                .isInstanceOfSatisfying(PatientOperationException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.NOT_FOUND));
    }

    @Test
    void shouldRejectBlankUserId() {
        assertThatThrownBy(() -> service.getDefaultPatient(" "))
                .isInstanceOfSatisfying(PatientOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST);
                    assertThat(ex.getDetails()).containsEntry("field", "userId");
                });
    }

    @Test
    void shouldCreatePatientAndUseFirstBindingAsDefault() {
        PatientSummary patient = service.createPatient(new PatientCreateRequest(
                "user-new",
                "New Patient",
                "ID_CARD",
                "110101199001011234",
                "13800001111",
                "SELF",
                false
        ));

        assertThat(patient.patientId()).startsWith("patient-test-");
        assertThat(patient.userId()).isEqualTo("user-new");
        assertThat(patient.defaultPatient()).isTrue();
        assertThat(service.getDefaultPatient("user-new").patientId()).isEqualTo(patient.patientId());
    }

    @Test
    void shouldSetDefaultPatient() {
        PatientSummary second = service.createPatient(new PatientCreateRequest(
                "user-test-001",
                "Second Patient",
                "ID_CARD",
                "110101199001019999",
                "13800009999",
                "OTHER",
                false
        ));

        PatientSummary updated = service.setDefaultPatient("user-test-001", second.patientId());

        assertThat(updated.defaultPatient()).isTrue();
        assertThat(service.getDefaultPatient("user-test-001").patientId()).isEqualTo(second.patientId());
        assertThat(service.listPatients("user-test-001"))
                .filteredOn(PatientSummary::defaultPatient)
                .extracting(PatientSummary::patientId)
                .containsExactly(second.patientId());
    }

    private static class FakePatientDirectoryRepository implements PatientDirectoryRepository {

        private final Map<String, List<PatientSummary>> patients = new HashMap<>();
        private final AtomicInteger sequence = new AtomicInteger(100);

        FakePatientDirectoryRepository() {
            patients.put("user-test-001", List.of(new PatientSummary("patient-test-001", "user-test-001", "Alex Chen", "ID_CARD", "110***********1234", "138****0001", "SELF", true)));
            patients.put("user-test-002", List.of(new PatientSummary("patient-test-002", "user-test-002", "Jamie Lin", "ID_CARD", "310***********5678", "138****0002", "SELF", true)));
        }

        @Override
        public PatientSummary findDefaultByUserId(String userId) {
            return patients.getOrDefault(userId, List.of()).stream()
                    .filter(PatientSummary::defaultPatient)
                    .findFirst()
                    .orElseThrow(() -> new PatientOperationException(
                            ApiErrorCode.NOT_FOUND,
                            "No active patient binding found for user.",
                            Map.of("userId", userId)
                    ));
        }

        @Override
        public List<PatientSummary> findByUserId(String userId) {
            return patients.getOrDefault(userId, List.of());
        }

        @Override
        public PatientSummary createForUser(PatientCreateRequest request) {
            List<PatientSummary> existing = patients.getOrDefault(request.userId(), List.of());
            boolean defaultPatient = request.defaultPatient() || existing.isEmpty();
            PatientSummary created = new PatientSummary(
                    "patient-test-" + sequence.incrementAndGet(),
                    request.userId(),
                    request.name(),
                    request.idType(),
                    "110***********1234",
                    "138****1111",
                    request.relationCode(),
                    defaultPatient
            );
            List<PatientSummary> updated = defaultPatient
                    ? existing.stream().map(patient -> withDefault(patient, false)).toList()
                    : existing;
            updated = new java.util.ArrayList<>(updated);
            updated.add(created);
            patients.put(request.userId(), List.copyOf(updated));
            return created;
        }

        @Override
        public PatientSummary setDefault(String userId, String patientId) {
            List<PatientSummary> existing = patients.getOrDefault(userId, List.of());
            if (existing.stream().noneMatch(patient -> patient.patientId().equals(patientId))) {
                throw new PatientOperationException(
                        ApiErrorCode.NOT_FOUND,
                        "Patient binding does not exist.",
                        Map.of("userId", userId, "patientId", patientId)
                );
            }
            List<PatientSummary> updated = existing.stream()
                    .map(patient -> withDefault(patient, patient.patientId().equals(patientId)))
                    .toList();
            patients.put(userId, updated);
            return updated.stream()
                    .filter(PatientSummary::defaultPatient)
                    .findFirst()
                    .orElseThrow();
        }

        private PatientSummary withDefault(PatientSummary patient, boolean defaultPatient) {
            return new PatientSummary(
                    patient.patientId(),
                    patient.userId(),
                    patient.name(),
                    patient.idType(),
                    patient.maskedIdNumber(),
                    patient.maskedPhone(),
                    patient.relationCode(),
                    defaultPatient
            );
        }
    }
}
