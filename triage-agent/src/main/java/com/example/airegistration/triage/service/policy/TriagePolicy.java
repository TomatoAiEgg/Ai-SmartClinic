package com.example.airegistration.triage.service.policy;

import com.example.airegistration.dto.DepartmentSuggestion;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TriagePolicy {

    private static final String[] EMERGENCY_KEYWORDS = {
            "chest pain", "shortness of breath", "unconscious", "convulsion", "massive bleeding",
            "胸痛", "呼吸困难", "意识不清", "抽搐", "大出血"
    };

    private static final String[] RESPIRATORY_KEYWORDS = {
            "cough", "fever", "throat", "phlegm",
            "咳嗽", "发热", "发烧", "咽痛", "痰"
    };

    private static final String[] GASTRO_KEYWORDS = {
            "stomach", "abdomen", "diarrhea", "vomit",
            "胃", "腹", "腹泻", "呕吐", "肚子"
    };

    private static final String[] DERMATOLOGY_KEYWORDS = {
            "rash", "itch", "skin",
            "皮疹", "瘙痒", "皮肤"
    };

    private static final String[] PEDIATRIC_KEYWORDS = {
            "child", "baby", "kid",
            "儿童", "小孩", "宝宝"
    };

    private static final String[] GYNECOLOGY_KEYWORDS = {
            "pregnant", "menstrual", "gyne",
            "怀孕", "月经", "妇科"
    };

    private static final String[] OPHTHALMOLOGY_KEYWORDS = {
            "eye", "eyes", "vision", "blurred vision", "red eye",
            "眼", "眼睛", "眼部", "视力", "视物", "视物模糊", "眼红", "眼痛"
    };

    private static final String[] NEUROLOGY_KEYWORDS = {
            "headache", "dizzy", "dizziness", "migraine", "head pain",
            "头痛", "头晕", "眩晕", "偏头痛", "头不舒服", "头部不适"
    };

    public DepartmentSuggestion suggestDepartment(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(text, EMERGENCY_KEYWORDS)) {
            return new DepartmentSuggestion(
                    "ER",
                    "急诊",
                    true,
                    "命中高风险症状规则。"
            );
        }
        if (containsAny(text, RESPIRATORY_KEYWORDS)) {
            return new DepartmentSuggestion(
                    "RESP",
                    "呼吸内科",
                    false,
                    "出现呼吸道或发热相关描述。"
            );
        }
        if (containsAny(text, GASTRO_KEYWORDS)) {
            return new DepartmentSuggestion(
                    "GI",
                    "消化内科",
                    false,
                    "出现消化系统相关症状。"
            );
        }
        if (containsAny(text, DERMATOLOGY_KEYWORDS)) {
            return new DepartmentSuggestion(
                    "DERM",
                    "皮肤科",
                    false,
                    "出现皮肤相关症状。"
            );
        }
        if (containsAny(text, PEDIATRIC_KEYWORDS)) {
            return new DepartmentSuggestion(
                    "PED",
                    "儿科",
                    false,
                    "描述中包含儿童就诊场景。"
            );
        }
        if (containsAny(text, GYNECOLOGY_KEYWORDS)) {
            return new DepartmentSuggestion(
                    "GYN",
                    "妇科",
                    false,
                    "描述中包含女性健康相关场景。"
            );
        }
        if (containsAny(text, OPHTHALMOLOGY_KEYWORDS)) {
            return new DepartmentSuggestion(
                    "OPH",
                    "眼科",
                    false,
                    "描述中包含眼部或视力相关不适。"
            );
        }
        if (containsAny(text, NEUROLOGY_KEYWORDS)) {
            return new DepartmentSuggestion(
                    "NEURO",
                    "神经内科",
                    false,
                    "描述中包含头痛、头晕或神经系统相关不适。"
            );
        }
        return new DepartmentSuggestion(
                "GEN",
                "全科医学科",
                false,
                "没有命中专科规则，先由全科承接。"
        );
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
