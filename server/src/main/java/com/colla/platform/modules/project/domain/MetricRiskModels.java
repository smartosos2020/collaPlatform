package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MetricRiskModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_POLICIES = 50;
    public static final int MAX_SIGNALS = 200;
    public static final int MAX_EVIDENCE = 20;
    public static final int MAX_CHAIN_DEPTH = 8;
    public static final int MAX_FAN_OUT = 50;

    private MetricRiskModels() {
    }

    public record RiskPolicyVersion(
        UUID id,
        UUID policyId,
        int versionNumber,
        String definitionHash,
        List<String> signalTypes,
        String severity,
        int cooldownHours,
        Instant publishedAt,
        UUID publishedBy
    ) {
    }

    public record RiskPolicy(
        UUID id,
        String policyKey,
        String name,
        String description,
        String status,
        List<String> draftSignalTypes,
        String draftSeverity,
        int draftCooldownHours,
        long version,
        RiskPolicyVersion publishedVersion,
        Instant updatedAt
    ) {
    }

    public record EvidenceReference(
        String sourceType,
        String sourceIdentity,
        long sourceVersion,
        Instant observedAt,
        String explanation,
        boolean available
    ) {
    }

    public record RiskSignal(
        UUID id,
        UUID policyId,
        int policyVersion,
        String signalType,
        String severity,
        String state,
        String dedupeKey,
        String evidenceFingerprint,
        List<EvidenceReference> evidence,
        long version,
        UUID acknowledgedBy,
        Instant acknowledgedAt,
        UUID closedBy,
        Instant closedAt,
        String resolutionReason,
        Instant observedAt,
        Instant updatedAt
    ) {
    }

    public record RiskFoundation(
        int schemaVersion,
        List<RiskPolicy> policies,
        List<RiskSignal> signals,
        List<String> signalTypes,
        List<String> severities,
        List<String> states,
        boolean truncated,
        Map<String, Integer> budgets,
        String diagnostic
    ) {
    }

    public record SaveRiskPolicyCommand(
        int schemaVersion,
        String requestId,
        UUID policyId,
        long expectedVersion,
        String policyKey,
        String name,
        String description,
        List<String> signalTypes,
        String severity,
        int cooldownHours
    ) {
    }

    public record RiskPolicyLifecycleCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String action
    ) {
    }

    public record EvaluateRisksCommand(
        int schemaVersion,
        String requestId,
        Instant anchor
    ) {
    }

    public record RiskSignalActionCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String action,
        String reason
    ) {
    }

    public record SignalCandidate(
        UUID policyId,
        int policyVersion,
        String signalType,
        String severity,
        String dedupeKey,
        String evidenceFingerprint,
        List<EvidenceReference> evidence,
        int cooldownHours,
        Instant observedAt
    ) {
    }
}
