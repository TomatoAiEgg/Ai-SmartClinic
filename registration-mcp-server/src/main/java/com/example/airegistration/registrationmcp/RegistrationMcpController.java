package com.example.airegistration.registrationmcp;

import com.example.airegistration.domain.RegistrationCancelRequest;
import com.example.airegistration.domain.RegistrationCommand;
import com.example.airegistration.domain.RegistrationQueryRequest;
import com.example.airegistration.domain.RegistrationResult;
import com.example.airegistration.domain.RegistrationRescheduleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp/registrations")
@Tag(name = "Registration MCP", description = "挂号创建、查询、取消、改约接口")
public class RegistrationMcpController {

    private final RegistrationLedgerService registrationLedgerService;

    public RegistrationMcpController(RegistrationLedgerService registrationLedgerService) {
        this.registrationLedgerService = registrationLedgerService;
    }

    @PostMapping
    @Operation(summary = "创建挂号", description = "在用户明确确认后创建挂号。")
    public RegistrationResult create(@RequestBody RegistrationCommand command) {
        return registrationLedgerService.create(command);
    }

    @GetMapping("/{registrationId}")
    @Operation(summary = "按挂号单号查询", description = "使用路径参数查询挂号结果。")
    public RegistrationResult query(@PathVariable String registrationId) {
        return registrationLedgerService.query(registrationId);
    }

    @PostMapping("/query")
    @Operation(summary = "结构化查询挂号", description = "使用 JSON 请求体查询挂号结果。")
    public RegistrationResult query(@RequestBody RegistrationQueryRequest request) {
        return registrationLedgerService.query(request.registrationId());
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消挂号", description = "在 confirmed=true 的情况下取消挂号。")
    public RegistrationResult cancel(@RequestBody RegistrationCancelRequest request) {
        return registrationLedgerService.cancel(request);
    }

    @PostMapping("/reschedule")
    @Operation(summary = "改约", description = "在 confirmed=true 的情况下修改就诊日期和时间。")
    public RegistrationResult reschedule(@RequestBody RegistrationRescheduleRequest request) {
        return registrationLedgerService.reschedule(request);
    }
}
