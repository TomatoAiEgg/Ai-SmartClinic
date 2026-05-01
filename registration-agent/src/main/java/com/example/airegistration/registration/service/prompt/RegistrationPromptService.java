package com.example.airegistration.registration.service.prompt;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationReplyScene;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RegistrationPromptService {

    public String systemPrompt() {
        return """
                You are the reply assistant for a hospital registration agent.
                Respond in Simplified Chinese for patients.
                Use only the structured business data and rule context already produced by registration-agent.
                Do not change the business action. Do not invent registration ids, departments, doctors, dates, times, statuses, or hospital rules.
                If doctorName or departmentName is missing, you may use doctorId or departmentCode, or omit the display name.
                If requiresConfirmation is true, clearly state that execution happens only after user confirmation.
                If business data contains requiredAction=triage or suggestedRoute=TRIAGE, do not reject the request.
                In that case, ask the user to describe symptoms first so the system can recommend a department and then continue registration.
                Keep the reply concise. Do not output JSON or Markdown.
                """;
    }

    public String userPrompt(ChatRequest request,
                             RegistrationReplyScene scene,
                             boolean requiresConfirmation,
                             Map<String, Object> data,
                             Map<String, Object> ruleContext) {
        return """
                Scene: %s
                Requires confirmation: %s
                Original user message: %s
                Structured registration data: %s
                Workflow guardrails and retrieved policy evidence:
                %s

                Reply requirements:
                1. Explain only the current result, preview, or next step.
                2. Do not add actions the system has not already decided.
                3. If confirmation is required, explicitly say execution happens only after confirmation.
                4. If data is missing, answer from existing data only.
                5. If requiredAction=triage, frame the next step as symptom-first department recommendation, not as a hard rejection.
                6. If no policy evidence is retrieved, do not invent hospital-specific policy details.
                """.formatted(
                scene.name(),
                requiresConfirmation,
                request.message(),
                data,
                ruleText(ruleContext)
        );
    }

    private String ruleText(Map<String, Object> ruleContext) {
        Object ruleText = ruleContext.get("ruleText");
        if (ruleText instanceof String text && !text.isBlank()) {
            return text;
        }
        return "No extra business rules matched. Use structured business data only and do not invent facts.";
    }
}
