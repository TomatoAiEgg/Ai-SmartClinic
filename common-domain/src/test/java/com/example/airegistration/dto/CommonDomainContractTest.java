package com.example.airegistration.dto;

import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.enums.ApiErrorCode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonDomainContractTest {

    @Test
    void chatRequestMetadataShouldBeNullSafeAndImmutable() {
        ChatRequest empty = new ChatRequest("chat-1", "user-1", "hello", null);

        assertThat(empty.metadata()).isEmpty();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("action", "create");

        ChatRequest request = new ChatRequest("chat-1", "user-1", "hello", metadata);
        metadata.put("action", "cancel");

        assertThat(request.metadata()).containsEntry("action", "create");
        assertThatThrownBy(() -> request.metadata().put("confirmed", "true"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void chatResponseDataShouldBeNullSafeAndImmutable() {
        ChatResponse empty = new ChatResponse("chat-1", AgentRoute.REGISTRATION, "ok", false, null);

        assertThat(empty.data()).isEmpty();

        Map<String, Object> data = new HashMap<>();
        data.put("registrationId", "REG-1234ABCD");

        ChatResponse response = new ChatResponse("chat-1", AgentRoute.REGISTRATION, "ok", true, data);
        data.put("registrationId", "REG-CHANGED");

        assertThat(response.data()).containsEntry("registrationId", "REG-1234ABCD");
        assertThatThrownBy(() -> response.data().put("status", "BOOKED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void apiErrorShouldFallbackToDefaultMessageAndKeepDetailsImmutable() {
        Map<String, Object> details = new HashMap<>();
        details.put("field", "registrationId");

        ApiError error = new ApiError(ApiErrorCode.INVALID_REQUEST, "", details);
        details.put("field", "clinicDate");

        assertThat(error.code()).isEqualTo(400);
        assertThat(error.message()).isEqualTo(ApiErrorCode.INVALID_REQUEST.message());
        assertThat(error.details()).containsEntry("field", "registrationId");
        assertThatThrownBy(() -> error.details().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void scheduleSearchResponseSlotsShouldBeNullSafeAndImmutable() {
        ScheduleSearchResponse empty = new ScheduleSearchResponse(null);

        assertThat(empty.slots()).isEmpty();

        SlotSummary slot = new SlotSummary("RESP", "呼吸内科", "doc-101", "王医生", "2026-04-15", "09:00", 3);
        List<SlotSummary> slots = new ArrayList<>();
        slots.add(slot);

        ScheduleSearchResponse response = new ScheduleSearchResponse(slots);
        slots.clear();

        assertThat(response.slots()).containsExactly(slot);
        assertThatThrownBy(() -> response.slots().add(slot))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registrationSearchResponseRecordsShouldBeNullSafeAndImmutable() {
        RegistrationSearchResponse empty = new RegistrationSearchResponse(null);

        assertThat(empty.records()).isEmpty();

        RegistrationResult record = new RegistrationResult(
                "REG-1234ABCD",
                "BOOKED",
                "已查询到挂号记录。",
                "patient-test-001",
                "RESP",
                "doc-101",
                "2026-04-17",
                "09:00"
        );
        List<RegistrationResult> records = new ArrayList<>();
        records.add(record);

        RegistrationSearchResponse response = new RegistrationSearchResponse(records);
        records.clear();

        assertThat(response.records()).containsExactly(record);
        assertThatThrownBy(() -> response.records().add(record))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
