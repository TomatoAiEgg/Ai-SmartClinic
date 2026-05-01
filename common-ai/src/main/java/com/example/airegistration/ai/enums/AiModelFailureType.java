package com.example.airegistration.ai.enums;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

public enum AiModelFailureType {

    RATE_LIMITED(Set.of(429), Set.of("rate limit", "too many requests", "throttl")),
    QUOTA_EXHAUSTED(Set.of(), Set.of("quota", "exceeded", "insufficient", "billing", "balance")),
    TIMEOUT(Set.of(504), Set.of("timeout", "timed out")),
    SERVICE_UNAVAILABLE(Set.of(503), Set.of("service unavailable", "try again")),
    SERVER_ERROR(Set.of(502), Set.of("server error", "bad gateway")),
    CONNECTION_ERROR(Set.of(), Set.of("connection reset", "connection refused")),
    UNKNOWN(Set.of(), Set.of());

    private final Set<Integer> httpStatusCodes;
    private final Set<String> keywords;

    AiModelFailureType(Set<Integer> httpStatusCodes, Set<String> keywords) {
        this.httpStatusCodes = httpStatusCodes;
        this.keywords = keywords;
    }

    public static EnumSet<AiModelFailureType> classify(Throwable throwable) {
        String summary = exceptionSummary(throwable).toLowerCase(Locale.ROOT);
        EnumSet<AiModelFailureType> failureTypes = EnumSet.noneOf(AiModelFailureType.class);
        Arrays.stream(values())
                .filter(type -> type != UNKNOWN)
                .filter(type -> type.matches(summary))
                .forEach(failureTypes::add);
        return failureTypes.isEmpty() ? EnumSet.of(UNKNOWN) : failureTypes;
    }

    private boolean matches(String summary) {
        return httpStatusCodes.stream()
                .map(String::valueOf)
                .anyMatch(summary::contains)
                || keywords.stream().anyMatch(summary::contains);
    }

    private static String exceptionSummary(Throwable throwable) {
        StringJoiner summary = new StringJoiner(" | ");
        Throwable current = throwable;
        while (current != null) {
            summary.add(current.getClass().getSimpleName() + ": " + current.getMessage());
            current = current.getCause();
        }
        return summary.toString();
    }
}
