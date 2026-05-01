package com.example.airegistration.registration.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.registration.enums.RegistrationIntent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationFlowPolicyTest {

    private final RegistrationFlowPolicy flowPolicy = new RegistrationFlowPolicy(new RegistrationSlotExtractor(
            Clock.fixed(Instant.parse("2026-04-16T00:00:00Z"), ZoneId.of("Asia/Hong_Kong"))
    ));

    @Test
    void shouldRequirePreviewContextBeforeExecutingWrite() {
        ChatRequest confirmedOnly = new ChatRequest(
                "chat-1",
                "user-1",
                "确认提交挂号",
                Map.of(
                        "action", "create",
                        "confirmed", "true"
                )
        );
        ChatRequest previewConfirmed = new ChatRequest(
                "chat-1",
                "user-1",
                "确认提交挂号",
                Map.of(
                        "action", "create",
                        "confirmed", "true",
                        "previewed", "true",
                        "confirmationAction", "create"
                )
        );

        assertThat(flowPolicy.canExecuteWrite(confirmedOnly, RegistrationIntent.CREATE)).isFalse();
        assertThat(flowPolicy.canExecuteWrite(previewConfirmed, RegistrationIntent.CREATE)).isTrue();
    }

    @Test
    void shouldExtractExactSlotFromNaturalLanguage() {
        ScheduleSlotRequest request = flowPolicy.extractExactSlotRequest(new ChatRequest(
                "chat-1",
                "user-1",
                "帮我挂呼吸内科 doc-106 2026-04-18 14:30 的号",
                Map.of()
        ));

        assertThat(request).isNotNull();
        assertThat(request.departmentCode()).isEqualTo("RESP");
        assertThat(request.doctorId()).isEqualTo("doc-106");
        assertThat(request.clinicDate()).isEqualTo("2026-04-18");
        assertThat(request.startTime()).isEqualTo("14:30");
    }

    @Test
    void shouldParseRelativeRescheduleDateAndChineseTime() {
        ChatRequest request = new ChatRequest(
                "chat-1",
                "user-1",
                "把 REG-1234ABCD 改到明天下午三点半",
                Map.of("action", "reschedule")
        );

        assertThat(flowPolicy.extractRegistrationId(request)).isEqualTo("REG-1234ABCD");
        assertThat(flowPolicy.resolveClinicDate(request)).isEqualTo("2026-04-17");
        assertThat(flowPolicy.resolveStartTime(request)).isEqualTo("15:30");
    }

    @Test
    void shouldNormalizeCommonRegistrationIdFormats() {
        assertThat(flowPolicy.extractRegistrationId(new ChatRequest(
                "chat-1",
                "user-1",
                "帮我查一下 reg1234abcd",
                Map.of()
        ))).isEqualTo("REG-1234ABCD");

        assertThat(flowPolicy.extractRegistrationId(new ChatRequest(
                "chat-1",
                "user-1",
                "取消 reg 1234abcd 这个预约",
                Map.of()
        ))).isEqualTo("REG-1234ABCD");

        assertThat(flowPolicy.extractRegistrationId(new ChatRequest(
                "chat-1",
                "user-1",
                "挂号单号 eb56caf5 查询结果",
                Map.of()
        ))).isEqualTo("REG-EB56CAF5");
    }

    @Test
    void shouldNotTreatBareCodeAsRegistrationIdWithoutContext() {
        ChatRequest request = new ChatRequest(
                "chat-1",
                "user-1",
                "帮我挂 20260419 的号",
                Map.of()
        );

        assertThat(flowPolicy.extractRegistrationId(request)).isNull();
    }

    @Test
    void shouldExtractScheduleSearchKeywordFromDoctorName() {
        assertThat(flowPolicy.resolveScheduleSearchKeyword(new ChatRequest(
                "chat-1",
                "user-1",
                "帮我挂 Dr. Murphy 的号",
                Map.of()
        ))).isEqualTo("Dr. Murphy");

        assertThat(flowPolicy.resolveScheduleSearchKeyword(new ChatRequest(
                "chat-1",
                "user-1",
                "帮我挂王医生的号",
                Map.of()
        ))).isEqualTo("王");
    }

    @Test
    void shouldTreatExplicitQueryWithCancelledKeywordAsQueryIntentAndStatusFilter() {
        ChatRequest request = new ChatRequest(
                "chat-query",
                "user-test-001",
                "查询已取消的挂号记录",
                Map.of()
        );

        assertThat(flowPolicy.determineIntent(request)).isEqualTo(RegistrationIntent.QUERY);
        assertThat(flowPolicy.resolveRegistrationStatus(request)).isEqualTo("CANCELLED");
    }

    @Test
    void shouldKeepPlainCancelRequestAsCancelIntent() {
        ChatRequest request = new ChatRequest(
                "chat-cancel",
                "user-test-001",
                "取消 REG-1234ABCD",
                Map.of()
        );

        assertThat(flowPolicy.determineIntent(request)).isEqualTo(RegistrationIntent.CANCEL);
    }

    @Test
    void shouldNotDefaultDepartmentWhenNoHintExists() {
        ChatRequest request = new ChatRequest(
                "chat-1",
                "user-1",
                "帮我挂号",
                Map.of("action", "create")
        );

        assertThat(flowPolicy.resolveDepartmentCode(request)).isNull();
    }
    @Test
    void shouldRedirectSymptomDrivenCreateRequestToTriage() {
        ChatRequest request = new ChatRequest(
                "chat-1",
                "user-1",
                "我能不能通过说病情来挂号",
                Map.of("action", "create")
        );

        assertThat(flowPolicy.shouldRedirectCreateToTriage(request)).isTrue();
    }
}
