package com.example.airegistration.dto;

import java.util.List;

public record ScheduleSearchResponse(List<SlotSummary> slots) {

    public ScheduleSearchResponse {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }
}
