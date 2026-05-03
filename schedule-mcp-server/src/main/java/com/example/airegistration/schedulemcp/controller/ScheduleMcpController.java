package com.example.airegistration.schedulemcp.controller;

import com.example.airegistration.dto.ScheduleRecommendRequest;
import com.example.airegistration.dto.ScheduleSearchRequest;
import com.example.airegistration.dto.ScheduleSearchResponse;
import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.dto.SlotSummary;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.schedulemcp.service.ScheduleCatalogUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp/schedules")
@Tag(name = "Schedule MCP", description = "排班与号源查询接口")
public class ScheduleMcpController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleMcpController.class);

    private final ScheduleCatalogUseCase scheduleCatalogUseCase;

    public ScheduleMcpController(ScheduleCatalogUseCase scheduleCatalogUseCase) {
        this.scheduleCatalogUseCase = scheduleCatalogUseCase;
    }

    @GetMapping("/recommend")
    @Operation(summary = "按科室推荐号源", description = "返回一个默认推荐号源，用于挂号预览。")
    public SlotSummary recommend(@RequestParam String departmentCode,
                                 @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[schedule-mcp] recommend slot trace_id={} department_code={}", traceId, departmentCode);
        return scheduleCatalogUseCase.recommend(departmentCode);
    }

    @PostMapping("/recommend")
    @Operation(summary = "结构化推荐号源", description = "使用 JSON 请求体按科室编码查询推荐号源。")
    public SlotSummary recommend(@RequestBody ScheduleRecommendRequest request,
                                 @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[schedule-mcp] recommend slot trace_id={} department_code={}", traceId, request.departmentCode());
        return scheduleCatalogUseCase.recommend(request.departmentCode());
    }

    @GetMapping("/search")
    @Operation(summary = "按关键字搜索号源", description = "根据科室或医生关键字搜索号源。")
    public List<SlotSummary> search(@RequestParam String keyword,
                                    @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[schedule-mcp] search slots trace_id={} keyword={}", traceId, keyword);
        return scheduleCatalogUseCase.search(keyword);
    }

    @PostMapping("/search")
    @Operation(summary = "结构化搜索号源", description = "使用 JSON 请求体搜索排班和号源。")
    public ScheduleSearchResponse search(@RequestBody ScheduleSearchRequest request,
                                         @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[schedule-mcp] search slots trace_id={} keyword={}", traceId, request.keyword());
        return new ScheduleSearchResponse(scheduleCatalogUseCase.search(request.keyword()));
    }

    @PostMapping("/resolve")
    @Operation(summary = "精确查询号源", description = "根据科室、医生、日期和开始时间查询具体号源。")
    public SlotSummary resolve(@RequestBody ScheduleSlotRequest request,
                               @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[schedule-mcp] resolve slot trace_id={} department_code={} doctor_id={} clinic_date={} start_time={}",
                traceId,
                request.departmentCode(),
                request.doctorId(),
                request.clinicDate(),
                request.startTime());
        return scheduleCatalogUseCase.resolve(request);
    }

    @PostMapping("/reserve")
    @Operation(summary = "占用号源", description = "为写操作预占一个具体号源。")
    public SlotSummary reserve(@RequestBody ScheduleSlotRequest request,
                               @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[schedule-mcp] reserve slot trace_id={} department_code={} doctor_id={} clinic_date={} start_time={}",
                traceId,
                request.departmentCode(),
                request.doctorId(),
                request.clinicDate(),
                request.startTime());
        return scheduleCatalogUseCase.reserve(request, traceId);
    }

    @PostMapping("/release")
    @Operation(summary = "释放号源", description = "在取消或改约后回收具体号源。")
    public SlotSummary release(@RequestBody ScheduleSlotRequest request,
                               @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[schedule-mcp] release slot trace_id={} department_code={} doctor_id={} clinic_date={} start_time={}",
                traceId,
                request.departmentCode(),
                request.doctorId(),
                request.clinicDate(),
                request.startTime());
        return scheduleCatalogUseCase.release(request, traceId);
    }
}
