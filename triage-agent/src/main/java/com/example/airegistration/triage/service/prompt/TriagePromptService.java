package com.example.airegistration.triage.service.prompt;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.triage.enums.TriageReplyScene;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TriagePromptService {

    public String departmentSystemPrompt() {
        return """
                You are a hospital triage department classifier.
                Your job is to choose the most suitable registration department from the allowed list.
                This is registration guidance, not a medical diagnosis.
                Prefer the RAG evidence when it is relevant to the user message.
                If RAG evidence conflicts with the rule suggestion, explain the chosen department in the reason.
                If neither rule nor RAG is reliable, choose GEN with a low confidence or ask for more information.

                Allowed department codes:
                - RESP: 呼吸内科, cough, fever, throat, phlegm, respiratory symptoms
                - GI: 消化内科, stomach pain, abdomen pain, diarrhea, vomiting
                - DERM: 皮肤科, rash, itch, skin symptoms
                - PED: 儿科, child, baby, pediatric cases
                - GYN: 妇科, pregnancy, menstrual, gynecology
                - OPH: 眼科, eye discomfort, eye pain, red eye, vision problems
                - NEURO: 神经内科, headache, dizziness, migraine, neurologic discomfort
                - GEN: 全科医学科, unclear symptoms that do not match a specialty
                - ER: 急诊, emergency or red-flag symptoms

                Output exactly one JSON object. Do not output Markdown or extra text.
                JSON schema:
                {"departmentCode":"OPH","departmentName":"眼科","emergency":false,"confidence":0.9,"reason":"眼部不适更适合先看眼科","evidenceIds":["triage-oph-001"]}
                """;
    }

    public String departmentUserPrompt(ChatRequest request,
                                       Map<String, Object> ruleSuggestion,
                                       Map<String, Object> ragContext) {
        return """
                User message: %s
                Metadata: %s
                Rule suggestion: %s
                RAG evidence: %s
                """.formatted(request.message(), request.metadata(), ruleSuggestion, ragContext);
    }

    public String systemPrompt() {
        return """
                You are the reply assistant for a hospital triage agent.
                The triage agent has already produced structured department guidance.
                Respond in Simplified Chinese for patients.
                Clearly state that this is not a diagnosis and is only registration guidance.
                If emergency is true, prioritize advising offline emergency care or hospital staff help.
                Use only the provided structured data. Do not invent departments or reasons.
                Keep the reply concise.
                Do not output JSON or Markdown.
                """;
    }

    public String userPrompt(ChatRequest request, TriageReplyScene scene, Map<String, Object> data) {
        return """
                Scene: %s
                Original user message: %s
                Structured triage result: %s
                """.formatted(scene.name(), request.message(), data);
    }
}
