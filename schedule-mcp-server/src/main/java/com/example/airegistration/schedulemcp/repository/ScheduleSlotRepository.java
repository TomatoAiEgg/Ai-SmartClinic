package com.example.airegistration.schedulemcp.repository;

import com.example.airegistration.schedulemcp.entity.ScheduleSlotInventory;
import com.example.airegistration.schedulemcp.entity.ScheduleSlotKey;
import com.example.airegistration.dto.SlotSummary;
import java.util.List;
import java.util.Optional;

public interface ScheduleSlotRepository {

    List<ScheduleSlotInventory> findAll();

    Optional<ScheduleSlotInventory> findByKey(ScheduleSlotKey key);

    SlotSummary reserve(ScheduleSlotKey key);

    SlotSummary release(ScheduleSlotKey key);
}
