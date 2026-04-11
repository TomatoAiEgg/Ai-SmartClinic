package com.example.airegistration.schedulemcp;

import com.example.airegistration.domain.ScheduleSlotRequest;
import com.example.airegistration.domain.SlotSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleCatalogServiceTest {

    private final ScheduleCatalogService service = new ScheduleCatalogService();

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
    void shouldRejectUnknownSlot() {
        assertThatThrownBy(() -> service.resolve(new ScheduleSlotRequest(
                "RESP",
                "doc-999",
                "2099-01-01",
                "09:00"
        )))
                .isInstanceOf(ScheduleOperationException.class)
                .hasMessageContaining("does not exist");
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
                .hasMessageContaining("No remaining slot");
    }
}
