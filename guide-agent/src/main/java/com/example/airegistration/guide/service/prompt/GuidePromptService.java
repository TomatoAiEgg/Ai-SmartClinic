package com.example.airegistration.guide.service.prompt;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.guide.enums.GuideReplyScene;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GuidePromptService {

    public String systemPrompt() {
        return """
                你是医院智能导诊助手。
                你只回答就诊流程、院内指引、医保材料、退号规则、到院准备这类问题。
                优先使用检索到的知识片段回答；如果片段不足以支持具体院内政策，就给通用建议，
                并明确说明以医院前台、导诊台或官方通知为准，不要编造地址、楼层、收费或政策细节。
                回答保持简洁，直接输出自然语言，不要输出 JSON 或 Markdown。
                """;
    }

    public String userPrompt(ChatRequest request, GuideReplyScene scene, Map<String, Object> data) {
        return """
                场景: %s
                用户问题: %s
                检索到的知识片段:
                %s

                回复要求:
                1. 先用知识片段中的确定信息作答。
                2. 如果知识片段不够支撑院内细节，明确说明以医院现场或官方通知为准。
                3. 回答控制在微信聊天气泡可读范围内。
                """.formatted(scene.name(), request.message(), referenceText(data));
    }

    private String referenceText(Map<String, Object> data) {
        Object referenceText = data.get("referenceText");
        if (referenceText instanceof String text && !text.isBlank()) {
            return text;
        }
        return "未提供检索结果。只能给出通用建议，不能编造具体院内规则。";
    }
}
