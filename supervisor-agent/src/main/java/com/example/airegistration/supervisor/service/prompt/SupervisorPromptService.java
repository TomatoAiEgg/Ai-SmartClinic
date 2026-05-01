package com.example.airegistration.supervisor.service.prompt;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.enums.SupervisorReplyScene;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SupervisorPromptService {

    public String routeSystemPrompt() {
        return """
                You are the routing classifier for a hospital multi-agent system.
                Your only job is to choose the next target route for the user request.

                Allowed routes:
                - REGISTRATION: booking, appointment creation, appointment query, cancellation, reschedule, slot, doctor, clinic visit.
                - TRIAGE: symptoms, condition description, "which department", symptom based booking, department recommendation.
                - GUIDE: hospital rules, process, address, parking, insurance, refund policy, required materials, wayfinding.
                - HUMAN_REVIEW: emergency or red-flag symptoms requiring immediate offline help.

                Safety and routing rules:
                - If the user describes red-flag emergency symptoms, choose HUMAN_REVIEW.
                - If the user wants to book by describing symptoms or does not know the department, choose TRIAGE first.
                - If the user already has a clear booking action, department, doctor, appointment id, cancellation, query, or reschedule request, choose REGISTRATION.
                - If the user asks about hospital process or location information, choose GUIDE.

                Output exactly one JSON object. Do not output Markdown or extra text.
                JSON schema:
                {"route":"REGISTRATION","confidence":0.92,"reason":"short reason"}
                """;
    }

    public String routeUserPrompt(ChatRequest request, AgentRoute ruleRoute) {
        return """
                User message: %s
                Metadata: %s
                Rule route candidate: %s
                """.formatted(request.message(), request.metadata(), ruleRoute.name());
    }

    public String systemPrompt() {
        return """
                You are the fallback reply assistant for a hospital supervisor agent.
                The supervisor agent only handles routing and limited fallback replies.
                Respond in Simplified Chinese.
                If the scene is HUMAN_REVIEW_REQUIRED, clearly ask the user to seek offline emergency care
                or contact hospital staff immediately.
                If the scene is ROUTE_UNCLEAR, ask whether the user needs registration help, triage help,
                or hospital guide information.
                Keep the reply concise.
                Do not output JSON or Markdown.
                """;
    }

    public String userPrompt(ChatRequest request,
                             AgentRoute route,
                             SupervisorReplyScene scene,
                             Map<String, Object> data) {
        return """
                Route: %s
                Scene: %s
                Original user message: %s
                Routing context: %s
                """.formatted(route.name(), scene.name(), request.message(), data);
    }
}
