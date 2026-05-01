package com.example.airegistration.registration.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RegistrationSlotExtractor {

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b");
    private static final Pattern CN_DATE_PATTERN = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})\\b");
    private static final Pattern CLOCK_TIME_PATTERN = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern CN_TIME_PATTERN = Pattern.compile(
            "(上午|中午|下午|晚上)?\\s*([零〇一二两三四五六七八九十\\d]{1,3})\\s*[点时]\\s*(半|[零〇一二两三四五六七八九十\\d]{1,2})?\\s*分?");
    private static final Pattern DOCTOR_ID_PATTERN = Pattern.compile("(?i)\\bdoc[-_]?\\d+\\b");
    private static final Pattern EN_DOCTOR_NAME_PATTERN = Pattern.compile("(?i)\\bdr\\.?\\s+([a-z][a-z-]{1,30})\\b");
    private static final Pattern CN_DOCTOR_NAME_PATTERN = Pattern.compile("([\\p{IsHan}]{1,6})\\s*(医生|大夫|主任|医师)");
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Map<String, String[]> DEPARTMENT_KEYWORDS = createDepartmentKeywords();

    private final Clock clock;

    public RegistrationSlotExtractor() {
        this(Clock.systemDefaultZone());
    }

    RegistrationSlotExtractor(Clock clock) {
        this.clock = clock;
    }

    public String extractDepartmentCode(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }

        String text = message.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> entry : DEPARTMENT_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public String extractDoctorId(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }

        Matcher matcher = DOCTOR_ID_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    public String extractScheduleSearchKeyword(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }

        Matcher doctorIdMatcher = DOCTOR_ID_PATTERN.matcher(message);
        if (doctorIdMatcher.find()) {
            return doctorIdMatcher.group().replace('_', '-').toLowerCase(Locale.ROOT);
        }

        Matcher englishDoctorMatcher = EN_DOCTOR_NAME_PATTERN.matcher(message);
        if (englishDoctorMatcher.find()) {
            return englishDoctorMatcher.group().trim();
        }

        Matcher chineseDoctorMatcher = CN_DOCTOR_NAME_PATTERN.matcher(message);
        if (chineseDoctorMatcher.find()) {
            String keyword = cleanChineseDoctorKeyword(chineseDoctorMatcher.group(1));
            return StringUtils.hasText(keyword) ? keyword : null;
        }

        return null;
    }

    private String cleanChineseDoctorKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim()
                .replaceFirst("^(帮我|给我|我想|想|请|预约|挂|找|看|查|取消|改约|改到|换到)+", "")
                .trim();
    }

    public String extractClinicDate(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }

        String text = message.trim();
        LocalDate today = LocalDate.now(clock);

        if (text.contains("今天") || text.contains("今日")) {
            return today.format(ISO_DATE_FORMATTER);
        }
        if (text.contains("明天") || text.contains("明日")) {
            return today.plusDays(1).format(ISO_DATE_FORMATTER);
        }
        if (text.contains("后天")) {
            return today.plusDays(2).format(ISO_DATE_FORMATTER);
        }

        Matcher isoDateMatcher = ISO_DATE_PATTERN.matcher(text);
        if (isoDateMatcher.find()) {
            return LocalDate.of(
                    Integer.parseInt(isoDateMatcher.group(1)),
                    Integer.parseInt(isoDateMatcher.group(2)),
                    Integer.parseInt(isoDateMatcher.group(3))
            ).format(ISO_DATE_FORMATTER);
        }

        Matcher cnDateMatcher = CN_DATE_PATTERN.matcher(text);
        if (cnDateMatcher.find()) {
            return inferYearDate(today,
                    Integer.parseInt(cnDateMatcher.group(1)),
                    Integer.parseInt(cnDateMatcher.group(2)));
        }

        Matcher slashDateMatcher = SLASH_DATE_PATTERN.matcher(text);
        if (slashDateMatcher.find()) {
            return inferYearDate(today,
                    Integer.parseInt(slashDateMatcher.group(1)),
                    Integer.parseInt(slashDateMatcher.group(2)));
        }

        return null;
    }

    public String extractStartTime(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }

        Matcher clockMatcher = CLOCK_TIME_PATTERN.matcher(message);
        if (clockMatcher.find()) {
            return "%02d:%02d".formatted(
                    Integer.parseInt(clockMatcher.group(1)),
                    Integer.parseInt(clockMatcher.group(2))
            );
        }

        Matcher cnTimeMatcher = CN_TIME_PATTERN.matcher(message);
        if (!cnTimeMatcher.find()) {
            return null;
        }

        int hour = parseNumber(cnTimeMatcher.group(2));
        if (hour < 0 || hour > 23) {
            return null;
        }

        String minuteToken = cnTimeMatcher.group(3);
        int minute = "半".equals(minuteToken) ? 30 : parseOptionalNumber(minuteToken);
        if (minute < 0 || minute > 59) {
            return null;
        }

        String period = cnTimeMatcher.group(1);
        if ("下午".equals(period) || "晚上".equals(period)) {
            if (hour < 12) {
                hour += 12;
            }
        } else if ("中午".equals(period) && hour < 11) {
            hour += 12;
        }

        return "%02d:%02d".formatted(hour, minute);
    }

    private String inferYearDate(LocalDate today, int month, int day) {
        LocalDate candidate = LocalDate.of(today.getYear(), month, day);
        if (candidate.isBefore(today)) {
            candidate = candidate.plusYears(1);
        }
        return candidate.format(ISO_DATE_FORMATTER);
    }

    private int parseOptionalNumber(String token) {
        if (!StringUtils.hasText(token)) {
            return 0;
        }
        return parseNumber(token);
    }

    private int parseNumber(String token) {
        if (!StringUtils.hasText(token)) {
            return -1;
        }

        String value = token.trim();
        if (value.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(value);
        }

        String normalized = value.replace('两', '二').replace('〇', '零');
        int tenIndex = normalized.indexOf('十');
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : parseDigit(normalized.substring(0, tenIndex));
            int ones = tenIndex == normalized.length() - 1 ? 0 : parseDigit(normalized.substring(tenIndex + 1));
            if (tens < 0 || ones < 0) {
                return -1;
            }
            return tens * 10 + ones;
        }

        return parseDigit(normalized);
    }

    private int parseDigit(String token) {
        return switch (token) {
            case "零" -> 0;
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> -1;
        };
    }

    private static Map<String, String[]> createDepartmentKeywords() {
        Map<String, String[]> keywords = new LinkedHashMap<>();
        keywords.put("RESP", new String[]{"resp", "respiratory", "cough", "fever", "呼吸", "咳嗽", "发热", "发烧", "呼吸内科"});
        keywords.put("DERM", new String[]{"derm", "dermatology", "skin", "rash", "皮肤", "皮疹", "皮肤科"});
        keywords.put("GI", new String[]{"gi", "gastro", "stomach", "abdomen", "digestive", "消化", "胃", "肚子", "腹", "消化内科"});
        keywords.put("PED", new String[]{"ped", "pediatrics", "child", "baby", "儿科", "儿童", "小孩", "宝宝"});
        keywords.put("GYN", new String[]{"gyn", "gynecology", "pregnant", "menstrual", "妇科", "怀孕", "月经"});
        keywords.put("GEN", new String[]{"gen", "general", "general practice", "全科", "普通内科", "综合门诊"});
        return Map.copyOf(keywords);
    }
}
