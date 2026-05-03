package com.example.airegistration.schedulemcp.service;

import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.dto.SlotSummary;
import java.util.List;

public interface ScheduleCatalogUseCase {

    SlotSummary recommend(String departmentCode);

    List<SlotSummary> search(String keyword);

    SlotSummary resolve(ScheduleSlotRequest request);

    default SlotSummary reserve(ScheduleSlotRequest request) {
        return reserve(request, null);
    }

    SlotSummary reserve(ScheduleSlotRequest request, String traceId);

    default SlotSummary release(ScheduleSlotRequest request) {
        return release(request, null);
    }

    SlotSummary release(ScheduleSlotRequest request, String traceId);
}
