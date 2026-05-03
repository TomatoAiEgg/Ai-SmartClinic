package com.example.airegistration.registrationmcp.controller;

import com.example.airegistration.dto.RegistrationCancelRequest;
import com.example.airegistration.dto.RegistrationCommand;
import com.example.airegistration.dto.RegistrationQueryRequest;
import com.example.airegistration.dto.RegistrationRescheduleRequest;
import com.example.airegistration.dto.RegistrationResult;
import com.example.airegistration.dto.RegistrationSearchRequest;
import com.example.airegistration.dto.RegistrationSearchResponse;
import com.example.airegistration.registrationmcp.dto.RegistrationAuditLogView;
import com.example.airegistration.registrationmcp.service.RegistrationAuditQueryService;
import com.example.airegistration.registrationmcp.service.RegistrationLedgerUseCase;
import com.example.airegistration.support.TraceIdSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp/registrations")
@Tag(name = "Registration MCP", description = "挂号创建、查询、取消、改约接口")
public class RegistrationMcpController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationMcpController.class);

    private final RegistrationLedgerUseCase registrationLedgerUseCase;
    private final RegistrationAuditQueryService auditQueryService;

    public RegistrationMcpController(RegistrationLedgerUseCase registrationLedgerUseCase,
                                     RegistrationAuditQueryService auditQueryService) {
        this.registrationLedgerUseCase = registrationLedgerUseCase;
        this.auditQueryService = auditQueryService;
    }

    @PostMapping
    @Operation(summary = "创建挂号", description = "在用户明确确认后创建挂号。")
    public RegistrationResult create(@RequestBody RegistrationCommand command,
                                     @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[registration-mcp] create trace_id={} user_id={} patient_id={} department_code={} doctor_id={} clinic_date={} start_time={} confirmed={}",
                traceId,
                command.userId(),
                command.patientId(),
                command.departmentCode(),
                command.doctorId(),
                command.clinicDate(),
                command.startTime(),
                command.confirmed());
        return registrationLedgerUseCase.create(command);
    }

    @GetMapping("/{registrationId}")
    @Operation(summary = "按挂号单号查询", description = "使用路径参数查询挂号结果。")
    public RegistrationResult query(@PathVariable String registrationId,
                                    @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[registration-mcp] query by path trace_id={} registration_id={}", traceId, registrationId);
        return registrationLedgerUseCase.query(registrationId);
    }

    @PostMapping("/query")
    @Operation(summary = "结构化查询挂号", description = "使用 JSON 请求体查询挂号结果。")
    public RegistrationResult query(@RequestBody RegistrationQueryRequest request,
                                    @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[registration-mcp] query trace_id={} registration_id={} user_id={}",
                traceId,
                request.registrationId(),
                request.userId());
        return registrationLedgerUseCase.query(request.registrationId(), request.userId());
    }

    @PostMapping("/search")
    @Operation(summary = "搜索挂号记录", description = "按当前用户和可选条件查询挂号记录列表。")
    public RegistrationSearchResponse search(@RequestBody RegistrationSearchRequest request,
                                             @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[registration-mcp] search trace_id={} user_id={} clinic_date={} department_code={} doctor_id={} status={}",
                traceId,
                request.userId(),
                request.clinicDate(),
                request.departmentCode(),
                request.doctorId(),
                request.status());
        return new RegistrationSearchResponse(registrationLedgerUseCase.search(request));
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消挂号", description = "在 confirmed=true 的情况下取消挂号。")
    public RegistrationResult cancel(@RequestBody RegistrationCancelRequest request,
                                     @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[registration-mcp] cancel trace_id={} registration_id={} user_id={} confirmed={}",
                traceId,
                request.registrationId(),
                request.userId(),
                request.confirmed());
        return registrationLedgerUseCase.cancel(request);
    }

    @PostMapping("/reschedule")
    @Operation(summary = "改约", description = "在 confirmed=true 的情况下修改就诊日期和时间。")
    public RegistrationResult reschedule(@RequestBody RegistrationRescheduleRequest request,
                                         @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[registration-mcp] reschedule trace_id={} registration_id={} user_id={} clinic_date={} start_time={} confirmed={}",
                traceId,
                request.registrationId(),
                request.userId(),
                request.clinicDate(),
                request.startTime(),
                request.confirmed());
        return registrationLedgerUseCase.reschedule(request);
    }

    @GetMapping("/audits")
    @Operation(summary = "查询挂号审计", description = "按 registrationId、operatorUserId、chatId、traceId 和操作结果查询挂号审计记录。")
    public List<RegistrationAuditLogView> audits(
            @RequestParam(required = false) String registrationId,
            @RequestParam(required = false) String operatorUserId,
            @RequestParam(required = false) String chatId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Integer limit) {
        return auditQueryService.listAuditLogs(
                registrationId,
                operatorUserId,
                chatId,
                traceId,
                operationType,
                success,
                limit
        );
    }
}
