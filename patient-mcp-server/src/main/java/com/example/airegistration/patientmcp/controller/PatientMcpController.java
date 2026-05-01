package com.example.airegistration.patientmcp.controller;

import com.example.airegistration.dto.PatientCreateRequest;
import com.example.airegistration.dto.PatientDefaultRequest;
import com.example.airegistration.dto.PatientListRequest;
import com.example.airegistration.dto.PatientListResponse;
import com.example.airegistration.dto.PatientSetDefaultRequest;
import com.example.airegistration.dto.PatientSummary;
import com.example.airegistration.support.TraceIdSupport;
import com.example.airegistration.patientmcp.service.PatientDirectoryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/mcp/patients")
@Tag(name = "Patient MCP", description = "患者查询相关接口")
public class PatientMcpController {

    private static final Logger log = LoggerFactory.getLogger(PatientMcpController.class);

    private final PatientDirectoryUseCase patientDirectoryUseCase;

    public PatientMcpController(PatientDirectoryUseCase patientDirectoryUseCase) {
        this.patientDirectoryUseCase = patientDirectoryUseCase;
    }

    @GetMapping("/default")
    @Operation(summary = "按 userId 查询默认就诊人", description = "供 registration-agent 在挂号前查询默认就诊人。")
    public PatientSummary getDefaultPatient(@RequestParam String userId,
                                            @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[patient-mcp] get default patient trace_id={} user_id={}", traceId, userId);
        return patientDirectoryUseCase.getDefaultPatient(userId);
    }

    @PostMapping("/default")
    @Operation(summary = "结构化查询默认就诊人", description = "使用 JSON 请求体查询默认就诊人。")
    public PatientSummary getDefaultPatient(@RequestBody PatientDefaultRequest request,
                                            @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[patient-mcp] get default patient trace_id={} user_id={}", traceId, request.userId());
        return patientDirectoryUseCase.getDefaultPatient(request.userId());
    }

    @GetMapping
    @Operation(summary = "查询用户绑定的就诊人", description = "按 userId 返回当前用户绑定的所有有效就诊人。")
    public PatientListResponse listPatients(@RequestParam String userId,
                                            @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[patient-mcp] list patients trace_id={} user_id={}", traceId, userId);
        return new PatientListResponse(patientDirectoryUseCase.listPatients(userId));
    }

    @PostMapping("/search")
    @Operation(summary = "结构化查询就诊人列表", description = "使用 JSON 请求体查询当前用户绑定的就诊人。")
    public PatientListResponse listPatients(@RequestBody PatientListRequest request,
                                            @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[patient-mcp] list patients trace_id={} user_id={}", traceId, request.userId());
        return new PatientListResponse(patientDirectoryUseCase.listPatients(request.userId()));
    }

    @PostMapping
    @Operation(summary = "创建并绑定就诊人", description = "为当前用户创建真实就诊人档案并绑定。")
    public PatientSummary createPatient(@RequestBody PatientCreateRequest request,
                                        @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[patient-mcp] create patient trace_id={} user_id={} name={}", traceId, request.userId(), request.name());
        return patientDirectoryUseCase.createPatient(request);
    }

    @PostMapping("/default/set")
    @Operation(summary = "设置默认就诊人", description = "将当前用户已绑定的就诊人设为默认。")
    public PatientSummary setDefaultPatient(@RequestBody PatientSetDefaultRequest request,
                                            @RequestHeader(value = TraceIdSupport.TRACE_HEADER, required = false) String traceId) {
        log.info("[patient-mcp] set default patient trace_id={} user_id={} patient_id={}",
                traceId,
                request.userId(),
                request.patientId());
        return patientDirectoryUseCase.setDefaultPatient(request.userId(), request.patientId());
    }
}
