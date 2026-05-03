package com.example.airegistration.schedulemcp;

import com.example.airegistration.enums.ApiErrorCode;
import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.dto.SlotSummary;
import com.example.airegistration.schedulemcp.entity.ScheduleInventoryAuditRecord;
import com.example.airegistration.schedulemcp.entity.ScheduleSlotInventory;
import com.example.airegistration.schedulemcp.entity.ScheduleSlotKey;
import com.example.airegistration.schedulemcp.exception.ScheduleOperationException;
import com.example.airegistration.schedulemcp.repository.ScheduleInventoryAuditRepository;
import com.example.airegistration.schedulemcp.repository.ScheduleSlotRepository;
import com.example.airegistration.schedulemcp.service.ScheduleCatalogApplicationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleCatalogServiceTest {

    private final ScheduleCatalogApplicationService service =
            new ScheduleCatalogApplicationService(new FakeScheduleSlotRepository());

    @Test
    void shouldReserveAndReleaseSlot() {
        SlotSummary recommended = service.recommend("RESP");
        ScheduleSlotRequest request = new ScheduleSlotRequest(
                recommended.departmentCode(),
                recommended.doctorId(),
                recommended.clinicDate(),
                recommended.startTime()
        );

        SlotSummary reserved = service.reserve(request);
        SlotSummary released = service.release(request);

        assertThat(reserved.remainingSlots()).isEqualTo(recommended.remainingSlots() - 1);
        assertThat(released.remainingSlots()).isEqualTo(recommended.remainingSlots());
    }

    @Test
    void shouldWriteAuditForReserveAndRelease() {
        RecordingAuditRepository auditRepository = new RecordingAuditRepository();
        ScheduleCatalogApplicationService auditedService =
                new ScheduleCatalogApplicationService(new FakeScheduleSlotRepository(), auditRepository);
        SlotSummary recommended = auditedService.recommend("RESP");
        ScheduleSlotRequest request = new ScheduleSlotRequest(
                recommended.departmentCode(),
                recommended.doctorId(),
                recommended.clinicDate(),
                recommended.startTime()
        );

        SlotSummary reserved = auditedService.reserve(request, "trace-reserve");
        auditedService.release(request, "trace-release");

        assertThat(auditRepository.records)
                .extracting(ScheduleInventoryAuditRecord::operationType)
                .containsExactly("RESERVE", "RELEASE");
        assertThat(auditRepository.records.get(0).traceId()).isEqualTo("trace-reserve");
        assertThat(auditRepository.records.get(0).success()).isTrue();
        assertThat(auditRepository.records.get(0).remainingBefore()).isEqualTo(recommended.remainingSlots());
        assertThat(auditRepository.records.get(0).remainingAfter()).isEqualTo(reserved.remainingSlots());
        assertThat(auditRepository.records.get(1).traceId()).isEqualTo("trace-release");
        assertThat(auditRepository.records.get(1).remainingBefore()).isEqualTo(reserved.remainingSlots());
    }

    @Test
    void shouldWriteFailureAuditForReserve() {
        RecordingAuditRepository auditRepository = new RecordingAuditRepository();
        ScheduleCatalogApplicationService auditedService =
                new ScheduleCatalogApplicationService(new FakeScheduleSlotRepository(), auditRepository);
        SlotSummary recommended = auditedService.recommend("GYN");
        ScheduleSlotRequest request = new ScheduleSlotRequest(
                recommended.departmentCode(),
                recommended.doctorId(),
                recommended.clinicDate(),
                recommended.startTime()
        );
        for (int index = 0; index < recommended.remainingSlots(); index++) {
            auditedService.reserve(request, "trace-drain");
        }

        assertThatThrownBy(() -> auditedService.reserve(request, "trace-empty"))
                .isInstanceOf(ScheduleOperationException.class);

        ScheduleInventoryAuditRecord lastRecord = auditRepository.records.get(auditRepository.records.size() - 1);
        assertThat(lastRecord.operationType()).isEqualTo("RESERVE");
        assertThat(lastRecord.traceId()).isEqualTo("trace-empty");
        assertThat(lastRecord.success()).isFalse();
        assertThat(lastRecord.remainingBefore()).isZero();
        assertThat(lastRecord.remainingAfter()).isNull();
    }

    @Test
    void shouldRejectUnknownSlot() {
        assertThatThrownBy(() -> service.resolve(new ScheduleSlotRequest(
                "RESP",
                "doc-999",
                "2099-01-01",
                "09:00"
        )))
                .isInstanceOf(ScheduleOperationException.class)
                .satisfies(ex -> assertThat(((ScheduleOperationException) ex).getErrorCode())
                        .isEqualTo(ApiErrorCode.NOT_FOUND));
    }

    @Test
    void shouldRejectNullSlotRequest() {
        assertThatThrownBy(() -> service.resolve(null))
                .isInstanceOfSatisfying(ScheduleOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST);
                    assertThat(ex.getDetails()).containsEntry("field", "request");
                });
    }

    @Test
    void shouldRejectBlankRequiredField() {
        assertThatThrownBy(() -> service.resolve(new ScheduleSlotRequest(
                " ",
                "doc-101",
                "2026-04-09",
                "09:00"
        )))
                .isInstanceOfSatisfying(ScheduleOperationException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ApiErrorCode.INVALID_REQUEST);
                    assertThat(ex.getDetails()).containsEntry("field", "departmentCode");
                });
    }

    @Test
    void shouldSearchAllSlotsWhenKeywordIsBlank() {
        assertThat(service.search(null)).isNotEmpty();
        assertThat(service.search(" ")).isNotEmpty();
    }

    @Test
    void shouldRejectReserveWhenNoRemainingCapacity() {
        SlotSummary recommended = service.recommend("GYN");
        ScheduleSlotRequest request = new ScheduleSlotRequest(
                recommended.departmentCode(),
                recommended.doctorId(),
                recommended.clinicDate(),
                recommended.startTime()
        );

        for (int index = 0; index < recommended.remainingSlots(); index++) {
            service.reserve(request);
        }

        assertThatThrownBy(() -> service.reserve(request))
                .isInstanceOf(ScheduleOperationException.class)
                .satisfies(ex -> assertThat(((ScheduleOperationException) ex).getErrorCode())
                        .isEqualTo(ApiErrorCode.NOT_FOUND));
    }

    private static class FakeScheduleSlotRepository implements ScheduleSlotRepository {

        private final Map<ScheduleSlotKey, ScheduleSlotInventory> slots = new ConcurrentHashMap<>();

        FakeScheduleSlotRepository() {
            seedSlots().forEach(slot -> slots.put(slot.key(), slot));
        }

        @Override
        public List<ScheduleSlotInventory> findAll() {
            return List.copyOf(slots.values());
        }

        @Override
        public Optional<ScheduleSlotInventory> findByKey(ScheduleSlotKey key) {
            return Optional.ofNullable(slots.get(key));
        }

        @Override
        public SlotSummary reserve(ScheduleSlotKey key) {
            return getRequiredSlot(key).reserve();
        }

        @Override
        public SlotSummary release(ScheduleSlotKey key) {
            return getRequiredSlot(key).release();
        }

        private ScheduleSlotInventory getRequiredSlot(ScheduleSlotKey key) {
            ScheduleSlotInventory slot = slots.get(key);
            if (slot == null) {
                throw new ScheduleOperationException(
                        ApiErrorCode.NOT_FOUND,
                        "Slot does not exist.",
                        Map.of(
                                "departmentCode", key.departmentCode(),
                                "doctorId", key.doctorId(),
                                "clinicDate", key.clinicDate(),
                                "startTime", key.startTime()
                        )
                );
            }
            return slot;
        }

        private List<ScheduleSlotInventory> seedSlots() {
            LocalDate today = LocalDate.now();
            return List.of(
                    new ScheduleSlotInventory("RESP", "Respiratory Medicine", "doc-101", "Dr. Rivera", today.plusDays(1).toString(), "09:00", 6),
                    new ScheduleSlotInventory("RESP", "Respiratory Medicine", "doc-106", "Dr. Murphy", today.plusDays(1).toString(), "14:30", 4),
                    new ScheduleSlotInventory("GEN", "General Medicine", "doc-102", "Dr. Park", today.plusDays(1).toString(), "10:30", 8),
                    new ScheduleSlotInventory("DERM", "Dermatology", "doc-103", "Dr. Patel", today.plusDays(1).toString(), "14:00", 4),
                    new ScheduleSlotInventory("GI", "Gastroenterology", "doc-104", "Dr. Khan", today.plusDays(2).toString(), "09:30", 3),
                    new ScheduleSlotInventory("PED", "Pediatrics", "doc-105", "Dr. Gomez", today.plusDays(2).toString(), "15:00", 5),
                    new ScheduleSlotInventory("GYN", "Gynecology", "doc-107", "Dr. Lopez", today.plusDays(2).toString(), "13:30", 4)
            );
        }
    }

    private static class RecordingAuditRepository implements ScheduleInventoryAuditRepository {

        private final List<ScheduleInventoryAuditRecord> records = new ArrayList<>();

        @Override
        public void append(ScheduleInventoryAuditRecord record) {
            records.add(record);
        }
    }
}

