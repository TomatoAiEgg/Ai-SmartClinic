package com.example.airegistration.registration.service.rag;

import com.example.airegistration.dto.ChatRequest;
import com.example.airegistration.registration.enums.RegistrationIntent;
import com.example.airegistration.registration.enums.RegistrationReplyScene;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class RegistrationRuleService {

    private static final String GUARD_WRITE_CONFIRMATION = "guard-write-confirmation-gate";
    private static final String GUARD_CREATE_PATIENT_SLOT = "guard-create-patient-and-slot";
    private static final String GUARD_QUERY_DETAIL_LIST = "guard-query-detail-vs-list";
    private static final String GUARD_CANCEL_PREVIEW_RELEASE = "guard-cancel-preview-and-release";
    private static final String GUARD_RESCHEDULE_SCOPE = "guard-reschedule-scope";
    private static final String GUARD_RESPONSE_FACT = "guard-response-fact-guard";

    private final Supplier<RegistrationPolicyRagService> policyRagServiceSupplier;

    @Autowired
    public RegistrationRuleService(ObjectProvider<RegistrationPolicyRagService> policyRagServiceProvider) {
        this.policyRagServiceSupplier = policyRagServiceProvider::getIfAvailable;
    }

    public RegistrationRuleService(RegistrationPolicyRagService policyRagService) {
        this.policyRagServiceSupplier = () -> policyRagService;
    }

    public Map<String, Object> buildContext(ChatRequest request,
                                            RegistrationReplyScene scene,
                                            boolean requiresConfirmation,
                                            Map<String, Object> data) {
        RegistrationWorkflowRules guardrails = deterministicRules(inferIntent(scene, data));
        Map<String, Object> policyContext = retrievePolicyContext(request, scene, requiresConfirmation, data);
        List<String> ruleIds = appendResponseGuard(guardrails.ruleIds());

        return Map.of(
                "ruleIds", ruleIds,
                "policyIds", policyIds(policyContext),
                "ruleText", buildRuleText(guardrails, policyContext),
                "policyContext", policyContext
        );
    }

    public RegistrationWorkflowRules resolveWorkflowRules(ChatRequest request,
                                                          RegistrationIntent intent,
                                                          Map<String, Object> data) {
        return deterministicRules(intent);
    }

    private RegistrationWorkflowRules deterministicRules(RegistrationIntent intent) {
        return switch (intent) {
            case CREATE -> new RegistrationWorkflowRules(
                    List.of(GUARD_WRITE_CONFIRMATION, GUARD_CREATE_PATIENT_SLOT),
                    true,
                    List.of("departmentCode"),
                    false,
                    List.of(),
                    false,
                    false
            );
            case QUERY -> new RegistrationWorkflowRules(
                    List.of(GUARD_QUERY_DETAIL_LIST),
                    false,
                    List.of(),
                    false,
                    List.of(),
                    false,
                    false
            );
            case CANCEL -> new RegistrationWorkflowRules(
                    List.of(GUARD_WRITE_CONFIRMATION, GUARD_CANCEL_PREVIEW_RELEASE),
                    true,
                    List.of(),
                    true,
                    List.of(),
                    false,
                    false
            );
            case RESCHEDULE -> new RegistrationWorkflowRules(
                    List.of(GUARD_WRITE_CONFIRMATION, GUARD_RESCHEDULE_SCOPE),
                    true,
                    List.of(),
                    true,
                    List.of("clinicDate", "startTime"),
                    false,
                    false
            );
        };
    }

    private Map<String, Object> retrievePolicyContext(ChatRequest request,
                                                      RegistrationReplyScene scene,
                                                      boolean requiresConfirmation,
                                                      Map<String, Object> data) {
        RegistrationPolicyRagService policyRagService = policyRagServiceSupplier.get();
        if (policyRagService == null) {
            return RegistrationPolicyRagService.emptyContext(
                    RegistrationPolicyRagService.queryText(request, scene, requiresConfirmation, data),
                    actionTag(scene, data)
            );
        }
        Map<String, Object> context = policyRagService.buildContext(request, scene, requiresConfirmation, data);
        if (context == null) {
            return RegistrationPolicyRagService.emptyContext(
                    RegistrationPolicyRagService.queryText(request, scene, requiresConfirmation, data),
                    actionTag(scene, data)
            );
        }
        return context;
    }

    private String buildRuleText(RegistrationWorkflowRules guardrails, Map<String, Object> policyContext) {
        return """
                Deterministic workflow guardrails:
                1. Write actions must not be executed unless the system has a preview context and the user explicitly confirms.
                2. Create registration requires a bound patient and a real slot resolved from MCP/database.
                3. Query uses registrationId when present; otherwise it searches the current user's registration list.
                4. Cancel requires an existing registrationId, current order lookup, confirmation, and slot release after success.
                5. Reschedule requires registrationId, target clinicDate/startTime, current order lookup, target slot reservation, order update, and old slot release.
                6. Current reschedule scope is same department and same doctor unless code-level policy changes.
                7. Reply may only use structured business data and retrieved policy evidence. Never invent ids, doctors, slots, statuses, or hospital policies.

                Active guardrail ids: %s

                Retrieved registration policy evidence:
                %s
                """.formatted(appendResponseGuard(guardrails.ruleIds()), referenceText(policyContext)).strip();
    }

    private RegistrationIntent inferIntent(RegistrationReplyScene scene, Map<String, Object> data) {
        String action = stringValue(data.get("action")).trim().toUpperCase(Locale.ROOT);
        return switch (action) {
            case "CREATE" -> RegistrationIntent.CREATE;
            case "QUERY" -> RegistrationIntent.QUERY;
            case "CANCEL" -> RegistrationIntent.CANCEL;
            case "RESCHEDULE" -> RegistrationIntent.RESCHEDULE;
            default -> inferIntentFromScene(scene);
        };
    }

    private RegistrationIntent inferIntentFromScene(RegistrationReplyScene scene) {
        String name = scene.name();
        if (name.startsWith("CREATE")) {
            return RegistrationIntent.CREATE;
        }
        if (name.startsWith("QUERY")) {
            return RegistrationIntent.QUERY;
        }
        if (name.startsWith("CANCEL") || name.equals("SLOT_RELEASE_FAILED")) {
            return RegistrationIntent.CANCEL;
        }
        if (name.startsWith("RESCHEDULE") || name.equals("OLD_SLOT_RELEASE_FAILED")) {
            return RegistrationIntent.RESCHEDULE;
        }
        return RegistrationIntent.QUERY;
    }

    private String actionTag(RegistrationReplyScene scene, Map<String, Object> data) {
        return inferIntent(scene, data).name();
    }

    private List<String> appendResponseGuard(List<String> ruleIds) {
        List<String> ids = new ArrayList<>(ruleIds);
        if (!ids.contains(GUARD_RESPONSE_FACT)) {
            ids.add(GUARD_RESPONSE_FACT);
        }
        return List.copyOf(ids);
    }

    private List<String> policyIds(Map<String, Object> policyContext) {
        Object policyIds = policyContext.get("policyIds");
        if (policyIds instanceof List<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    private String referenceText(Map<String, Object> policyContext) {
        Object referenceText = policyContext.get("referenceText");
        if (referenceText instanceof String text && !text.isBlank()) {
            return text;
        }
        return "No registration policy evidence was retrieved. Use deterministic workflow guardrails only.";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
