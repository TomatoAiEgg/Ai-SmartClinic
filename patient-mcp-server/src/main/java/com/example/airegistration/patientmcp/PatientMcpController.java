package com.example.airegistration.patientmcp;

import com.example.airegistration.domain.PatientDefaultRequest;
import com.example.airegistration.domain.PatientSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp/patients")
@Tag(name = "Patient MCP", description = "患者查询相关接口")
public class PatientMcpController {

    private final PatientDirectoryService patientDirectoryService;

    public PatientMcpController(PatientDirectoryService patientDirectoryService) {
        this.patientDirectoryService = patientDirectoryService;
    }

    @GetMapping("/default")
    @Operation(summary = "按 userId 查询默认就诊人", description = "供 registration-agent 在挂号前查询默认就诊人。")
    public PatientSummary getDefaultPatient(@RequestParam String userId) {
        return patientDirectoryService.getDefaultPatient(userId);
    }

    @PostMapping("/default")
    @Operation(summary = "结构化查询默认就诊人", description = "使用 JSON 请求体查询默认就诊人。")
    public PatientSummary getDefaultPatient(@RequestBody PatientDefaultRequest request) {
        return patientDirectoryService.getDefaultPatient(request.userId());
    }
}
