package com.example.airegistration.domain;

import java.util.List;

public record ScheduleSearchResponse(List<SlotSummary> slots) {
}
