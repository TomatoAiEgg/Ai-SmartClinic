package com.example.airegistration.schedulemcp.service;

import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.dto.SlotSummary;
import java.util.List;

public interface ScheduleCatalogUseCase {

    SlotSummary recommend(String departmentCode);

    List<SlotSummary> search(String keyword);

    SlotSummary resolve(ScheduleSlotRequest request);

    SlotSummary reserve(ScheduleSlotRequest request);

    SlotSummary release(ScheduleSlotRequest request);
}
