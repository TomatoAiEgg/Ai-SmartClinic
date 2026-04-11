package com.example.airegistration.schedulemcp;

import com.example.airegistration.domain.ScheduleRecommendRequest;
import com.example.airegistration.domain.ScheduleSearchRequest;
import com.example.airegistration.domain.ScheduleSearchResponse;
import com.example.airegistration.domain.ScheduleSlotRequest;
import com.example.airegistration.domain.SlotSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp/schedules")
@Tag(name = "Schedule MCP", description = "排班与号源查询接口")
public class ScheduleMcpController {

    private final ScheduleCatalogService scheduleCatalogService;

    public ScheduleMcpController(ScheduleCatalogService scheduleCatalogService) {
        this.scheduleCatalogService = scheduleCatalogService;
    }

    @GetMapping("/recommend")
    @Operation(summary = "按科室推荐号源", description = "返回一个默认推荐号源，用于挂号预览。")
    public SlotSummary recommend(@RequestParam String departmentCode) {
        return scheduleCatalogService.recommend(departmentCode);
    }

    @PostMapping("/recommend")
    @Operation(summary = "结构化推荐号源", description = "使用 JSON 请求体按科室编码查询推荐号源。")
    public SlotSummary recommend(@RequestBody ScheduleRecommendRequest request) {
        return scheduleCatalogService.recommend(request.departmentCode());
    }

    @GetMapping("/search")
    @Operation(summary = "按关键字搜索号源", description = "根据科室或医生关键字搜索号源。")
    public List<SlotSummary> search(@RequestParam String keyword) {
        return scheduleCatalogService.search(keyword);
    }

    @PostMapping("/search")
    @Operation(summary = "结构化搜索号源", description = "使用 JSON 请求体搜索排班和号源。")
    public ScheduleSearchResponse search(@RequestBody ScheduleSearchRequest request) {
        return new ScheduleSearchResponse(scheduleCatalogService.search(request.keyword()));
    }

    @PostMapping("/resolve")
    @Operation(summary = "精确查询号源", description = "根据科室、医生、日期和开始时间查询具体号源。")
    public SlotSummary resolve(@RequestBody ScheduleSlotRequest request) {
        return scheduleCatalogService.resolve(request);
    }

    @PostMapping("/reserve")
    @Operation(summary = "占用号源", description = "为写操作预占一个具体号源。")
    public SlotSummary reserve(@RequestBody ScheduleSlotRequest request) {
        return scheduleCatalogService.reserve(request);
    }

    @PostMapping("/release")
    @Operation(summary = "释放号源", description = "在取消或改约后回收具体号源。")
    public SlotSummary release(@RequestBody ScheduleSlotRequest request) {
        return scheduleCatalogService.release(request);
    }
}
