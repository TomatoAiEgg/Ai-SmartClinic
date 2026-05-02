package com.example.airegistration.supervisor.client;

import com.example.airegistration.agent.AgentCapability;
import com.example.airegistration.enums.AgentRoute;
import com.example.airegistration.supervisor.config.AgentClientProperties;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@EnableConfigurationProperties(AgentClientProperties.class)
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);
    private static final String CAPABILITIES_PATH = "/api/agent/capabilities";
    private static final Duration CAPABILITY_TIMEOUT = Duration.ofSeconds(2);

    private final WebClient.Builder webClientBuilder;
    private final Map<AgentRoute, RegisteredAgent> agents = new ConcurrentHashMap<>();

    public AgentRegistry(WebClient.Builder webClientBuilder, AgentClientProperties properties) {
        this.webClientBuilder = webClientBuilder;
        registerDefaults(properties == null ? new AgentClientProperties() : properties);
    }

    public static AgentRegistry fromProperties(AgentClientProperties properties) {
        return new AgentRegistry(WebClient.builder(), properties);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadCapabilitiesOnStartup() {
        refreshCapabilities()
                .subscribe(
                        ignored -> {
                        },
                        error -> log.warn("[agent-registry] capability refresh failed reason={}", error.getMessage())
                );
    }

    public Mono<Void> refreshCapabilities() {
        List<RegisteredAgent> snapshot = List.copyOf(agents.values());
        int concurrency = Math.max(1, Math.min(3, snapshot.size()));
        return Flux.fromIterable(snapshot)
                .flatMap(this::refreshCapability, concurrency)
                .then();
    }

    public RegisteredAgent requireAgent(AgentRoute route) {
        RegisteredAgent agent = agents.get(route);
        if (agent == null) {
            throw new IllegalArgumentException("No agent registered for route " + route);
        }
        return agent;
    }

    public Optional<RegisteredAgent> findAgent(AgentRoute route) {
        return Optional.ofNullable(agents.get(route));
    }

    public Optional<AgentCapability> capabilityFor(AgentRoute route) {
        return findAgent(route).map(RegisteredAgent::capability);
    }

    public Map<AgentRoute, RegisteredAgent> snapshot() {
        return Map.copyOf(agents);
    }

    private Mono<RegisteredAgent> refreshCapability(RegisteredAgent agent) {
        if (!hasText(agent.baseUrl())) {
            log.warn("[agent-registry] skip capability load route={} agent={} reason=blank_base_url",
                    agent.route(),
                    agent.agentName());
            return Mono.just(agent);
        }
        return webClientBuilder.clone()
                .baseUrl(agent.baseUrl())
                .build()
                .get()
                .uri(CAPABILITIES_PATH)
                .retrieve()
                .bodyToMono(AgentCapability.class)
                .timeout(CAPABILITY_TIMEOUT)
                .map(capability -> updateCapability(agent, capability))
                .doOnNext(updated -> {
                    if (updated.capability() != null) {
                        log.info("[agent-registry] capability loaded route={} agent={} base_url={} patterns={}",
                                updated.route(),
                                updated.agentName(),
                                updated.baseUrl(),
                                updated.capability().patterns());
                    }
                })
                .onErrorResume(error -> {
                    log.warn("[agent-registry] capability unavailable route={} agent={} base_url={} reason={}",
                            agent.route(),
                            agent.agentName(),
                            agent.baseUrl(),
                            error.getMessage());
                    return Mono.just(agent);
                });
    }

    private RegisteredAgent updateCapability(RegisteredAgent agent, AgentCapability capability) {
        if (capability == null) {
            return agent;
        }
        if (!capability.supportedRoutes().isEmpty()
                && !capability.supportedRoutes().contains(agent.route().name())) {
            log.warn("[agent-registry] capability route mismatch expected={} agent={} supported_routes={}",
                    agent.route(),
                    capability.agentName(),
                    capability.supportedRoutes());
            return agent;
        }
        RegisteredAgent updated = agent.withCapability(capability);
        agents.put(agent.route(), updated);
        return updated;
    }

    private void registerDefaults(AgentClientProperties properties) {
        Map<AgentRoute, RegisteredAgent> defaults = new EnumMap<>(AgentRoute.class);
        defaults.put(AgentRoute.TRIAGE, new RegisteredAgent(
                AgentRoute.TRIAGE,
                "triage-agent",
                properties.getTriage().getBaseUrl(),
                "/api/triage",
                null
        ));
        defaults.put(AgentRoute.REGISTRATION, new RegisteredAgent(
                AgentRoute.REGISTRATION,
                "registration-agent",
                properties.getRegistration().getBaseUrl(),
                "/api/registration",
                null
        ));
        defaults.put(AgentRoute.GUIDE, new RegisteredAgent(
                AgentRoute.GUIDE,
                "guide-agent",
                properties.getGuide().getBaseUrl(),
                "/api/guide",
                null
        ));
        agents.putAll(defaults);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
