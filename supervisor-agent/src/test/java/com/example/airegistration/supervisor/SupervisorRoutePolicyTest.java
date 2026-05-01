package com.example.airegistration.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.service.policy.SupervisorRoutePolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupervisorRoutePolicyTest {

    private final SupervisorRoutePolicy policy = new SupervisorRoutePolicy();

    @Test
    void shouldRouteExplicitRegistrationActionToRegistrationAgent() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "confirm the action",
                Map.of("action", "create")
        ));

        assertThat(route).isEqualTo(AgentRoute.REGISTRATION);
    }

    @Test
    void shouldRouteNaturalLanguageBookingToRegistrationAgent() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "I want to book a respiratory appointment",
                Map.of()
        ));

        assertThat(route).isEqualTo(AgentRoute.REGISTRATION);
    }

    @Test
    void shouldRouteBookingWithDateAndDepartmentToRegistrationAgent() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "book a respiratory clinic slot tomorrow afternoon",
                Map.of()
        ));

        assertThat(route).isEqualTo(AgentRoute.REGISTRATION);
    }

    @Test
    void shouldRouteRedFlagSymptomsToHumanReview() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "chest pain and shortness of breath",
                Map.of()
        ));

        assertThat(route).isEqualTo(AgentRoute.HUMAN_REVIEW);
    }

    @Test
    void shouldRouteSymptomsToTriageAgent() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "my child has fever and cough",
                Map.of()
        ));

        assertThat(route).isEqualTo(AgentRoute.TRIAGE);
    }

    @Test
    void shouldRouteDepartmentQuestionWithSymptomsToTriageAgent() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "I have had fever and cough for three days, which department should I visit",
                Map.of()
        ));

        assertThat(route).isEqualTo(AgentRoute.TRIAGE);
    }

    @Test
    void shouldRouteSymptomBasedRegistrationRequestToTriageAgent() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "I have fever and cough and want to book an appointment",
                Map.of()
        ));

        assertThat(route).isEqualTo(AgentRoute.TRIAGE);
    }

    @Test
    void shouldRouteDescribeSymptomsRegistrationQuestionToTriageAgent() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "我能不能通过说病情来挂号",
                Map.of()
        ));

        assertThat(route).isEqualTo(AgentRoute.TRIAGE);
    }

    @Test
    void shouldFallbackToGuideAgent() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "where is the hospital parking lot",
                Map.of()
        ));

        assertThat(route).isEqualTo(AgentRoute.GUIDE);
    }

    @Test
    void shouldRouteRefundRuleQuestionToGuideAgent() {
        AgentRoute route = policy.determineRoute(new ChatRequest(
                "chat-1",
                "user-1",
                "what is the refund policy after canceling an appointment",
                Map.of()
        ));

        assertThat(route).isEqualTo(AgentRoute.GUIDE);
    }
}
