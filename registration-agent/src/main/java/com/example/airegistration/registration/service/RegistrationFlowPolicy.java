package com.example.airegistration.registration.service;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.dto.ScheduleSlotRequest;
import com.example.airegistration.enums.RegistrationStatus;
import com.example.airegistration.registration.enums.RegistrationIntent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class RegistrationFlowPolicy {

    private static final String[] TRIAGE_SIGNAL_KEYWORDS = {
            "symptom", "symptoms", "condition", "illness", "cough", "fever", "rash", "pain",
            "describe symptoms", "describing symptoms", "based on symptoms", "by symptoms",
            "症状", "病情", "咳嗽", "发烧", "发热", "疼", "痛",
            "描述症状", "描述病情", "说病情", "根据症状",
            "根据病情", "按症状"
    };

    private static final Pattern STANDARD_REGISTRATION_ID_PATTERN =
            Pattern.compile("REG-[A-Z0-9]{8,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIXED_REGISTRATION_ID_PATTERN =
            Pattern.compile("\\bREG\\s*[-_]?\\s*([A-Z0-9]{8,16})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTEXTUAL_SHORT_ID_PATTERN =
            Pattern.compile("\\b([A-Z0-9]{8,16})\\b", Pattern.CASE_INSENSITIVE);

    private final RegistrationSlotExtractor slotExtractor;

    public RegistrationFlowPolicy(RegistrationSlotExtractor slotExtractor) {
        this.slotExtractor = slotExtractor;
    }

    public RegistrationIntent determineIntent(ChatRequest request) {
        String action = request.metadata().get("action");
        if (!isBlank(action)) {
            return switch (action.trim().toLowerCase(Locale.ROOT)) {
                case "query" -> RegistrationIntent.QUERY;
                case "cancel" -> RegistrationIntent.CANCEL;
                case "reschedule" -> RegistrationIntent.RESCHEDULE;
                default -> RegistrationIntent.CREATE;
            };
        }

        String text = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        if (hasExplicitQueryLanguage(text)) {
            return RegistrationIntent.QUERY;
        }
        if (containsAny(text, "cancel", "取消", "退号")) {
            return RegistrationIntent.CANCEL;
        }
        if (containsAny(text, "reschedule", "change time", "改约", "改时间", "换时间")) {
            return RegistrationIntent.RESCHEDULE;
        }
        if (looksLikeQuery(text)) {
            return RegistrationIntent.QUERY;
        }
        return RegistrationIntent.CREATE;
    }

    public boolean canExecuteWrite(ChatRequest request, RegistrationIntent intent) {
        if (!"true".equalsIgnoreCase(normalizeText(request.metadata().get("confirmed")))) {
            return false;
        }
        if (!"true".equalsIgnoreCase(normalizeText(request.metadata().get("previewed")))) {
            return false;
        }
        return expectedAction(intent).equalsIgnoreCase(normalizeText(request.metadata().get("confirmationAction")));
    }

    public String extractRegistrationId(ChatRequest request) {
        String metadataId = request.metadata().get("registrationId");
        if (!isBlank(metadataId)) {
            return normalizeRegistrationId(metadataId, true);
        }

        String text = request.message() == null ? "" : request.message().toUpperCase(Locale.ROOT);
        Matcher standardMatcher = STANDARD_REGISTRATION_ID_PATTERN.matcher(text);
        if (standardMatcher.find()) {
            return normalizeRegistrationId(standardMatcher.group(), true);
        }

        Matcher prefixedMatcher = PREFIXED_REGISTRATION_ID_PATTERN.matcher(text);
        if (prefixedMatcher.find()) {
            return normalizeRegistrationId("REG-" + prefixedMatcher.group(1), true);
        }

        if (!containsRegistrationIdContext(text)) {
            return null;
        }
        Matcher contextualMatcher = CONTEXTUAL_SHORT_ID_PATTERN.matcher(text);
        while (contextualMatcher.find()) {
            String candidate = contextualMatcher.group(1);
            if (candidate.startsWith("REG")
                    || "REGISTRATION".equals(candidate)
                    || "APPOINTMENT".equals(candidate)) {
                continue;
            }
            String normalized = normalizeRegistrationId(candidate, false);
            if (!isBlank(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    public ScheduleSlotRequest extractExactSlotRequest(ChatRequest request) {
        String departmentCode = resolveDepartmentCode(request);
        String doctorId = resolveDoctorId(request);
        String clinicDate = resolveClinicDate(request);
        String startTime = resolveStartTime(request);
        if (isBlank(departmentCode) || isBlank(doctorId) || isBlank(clinicDate) || isBlank(startTime)) {
            return null;
        }
        return new ScheduleSlotRequest(departmentCode.toUpperCase(Locale.ROOT), doctorId, clinicDate, startTime);
    }

    public String resolveDepartmentCode(ChatRequest request) {
        String metadataDepartmentCode = normalizeText(request.metadata().get("departmentCode"));
        if (!isBlank(metadataDepartmentCode)) {
            return metadataDepartmentCode.toUpperCase(Locale.ROOT);
        }
        String extractedDepartmentCode = slotExtractor.extractDepartmentCode(request.message());
        return isBlank(extractedDepartmentCode) ? null : extractedDepartmentCode.toUpperCase(Locale.ROOT);
    }

    public String resolveDoctorId(ChatRequest request) {
        String metadataDoctorId = normalizeText(request.metadata().get("doctorId"));
        if (!isBlank(metadataDoctorId)) {
            return metadataDoctorId;
        }
        return slotExtractor.extractDoctorId(request.message());
    }

    public String resolveScheduleSearchKeyword(ChatRequest request) {
        String metadataScheduleKeyword = normalizeText(request.metadata().get("scheduleKeyword"));
        if (!isBlank(metadataScheduleKeyword)) {
            return metadataScheduleKeyword;
        }
        String metadataDoctorName = normalizeText(request.metadata().get("doctorName"));
        if (!isBlank(metadataDoctorName)) {
            return metadataDoctorName;
        }
        String doctorId = resolveDoctorId(request);
        if (!isBlank(doctorId)) {
            return doctorId;
        }
        return slotExtractor.extractScheduleSearchKeyword(request.message());
    }

    public String resolveClinicDate(ChatRequest request) {
        String metadataClinicDate = normalizeText(request.metadata().get("clinicDate"));
        if (!isBlank(metadataClinicDate)) {
            return metadataClinicDate;
        }
        return slotExtractor.extractClinicDate(request.message());
    }

    public String resolveStartTime(ChatRequest request) {
        String metadataStartTime = normalizeText(request.metadata().get("startTime"));
        if (!isBlank(metadataStartTime)) {
            return metadataStartTime;
        }
        return slotExtractor.extractStartTime(request.message());
    }

    public String resolveRegistrationStatus(ChatRequest request) {
        String metadataStatus = normalizeText(request.metadata().get("status"));
        if (!isBlank(metadataStatus)) {
            return metadataStatus.toUpperCase(Locale.ROOT);
        }

        String text = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        if (containsAny(text, "已取消", "取消的", "取消记录", "退号记录", "cancelled", "canceled")) {
            return RegistrationStatus.CANCELLED.code();
        }
        if (containsAny(text, "已改约", "改约的", "改约记录", "rescheduled")) {
            return RegistrationStatus.RESCHEDULED.code();
        }
        if (containsAny(text, "已预约", "预约成功", "挂号成功", "有效", "未取消", "booked")) {
            return RegistrationStatus.BOOKED.code();
        }
        return null;
    }

    public String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public boolean shouldRedirectCreateToTriage(ChatRequest request) {
        String text = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        return containsAny(text, TRIAGE_SIGNAL_KEYWORDS);
    }

    private String expectedAction(RegistrationIntent intent) {
        return intent.name().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeQuery(String text) {
        return containsAny(text,
                "query", "status", "查询", "状态", "单号", "结果", "记录", "订单",
                "查挂号", "查预约", "挂号结果", "预约结果", "挂号记录", "预约记录")
                || (containsAny(text, "查", "看一个", "看看")
                && containsAny(text, "挂号", "预约", "号", "单", "结果", "记录"));
    }

    private boolean hasExplicitQueryLanguage(String text) {
        return containsAny(text,
                "query", "status", "查询", "查看", "查一下", "查下", "查", "看看", "看一下", "看下",
                "我的挂号", "我的预约", "挂号记录", "预约记录", "挂号结果", "预约结果",
                "所有挂号", "全部挂号", "挂号列表", "预约列表");
    }

    private String normalizeRegistrationId(String value, boolean allowPrefixed) {
        if (isBlank(value)) {
            return null;
        }
        String compact = value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace('_', '-');
        if (compact.startsWith("REG-")) {
            String suffix = compact.substring("REG-".length()).replace("-", "");
            return suffix.length() >= 8 ? "REG-" + suffix : null;
        }
        if (compact.startsWith("REG")) {
            String suffix = compact.substring("REG".length()).replace("-", "");
            return allowPrefixed && suffix.length() >= 8 ? "REG-" + suffix : null;
        }
        String suffix = compact.replace("-", "");
        if (suffix.length() < 8 || suffix.length() > 16) {
            return null;
        }
        if (!suffix.matches("[A-Z0-9]+")) {
            return null;
        }
        return "REG-" + suffix;
    }

    private boolean containsRegistrationIdContext(String text) {
        return containsAny(text,
                "挂号单号", "预约单号", "挂号号", "预约号", "单号", "订单号",
                "REGISTRATION ID", "REGISTRATIONID", "APPOINTMENT ID", "APPOINTMENTID");
    }
}
