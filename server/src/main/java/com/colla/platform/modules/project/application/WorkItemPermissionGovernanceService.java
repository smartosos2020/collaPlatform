package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.PermissionDecision;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.PermissionExplanation;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.PermissionRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Safe explanation and deterministic governance validation. Persistence remains in the V101
 * receipt/binding/evidence authorities; this service never exposes their private rows.
 */
@Service
public final class WorkItemPermissionGovernanceService {
    private static final Duration MAX_TEMPORARY_GRANT = Duration.ofDays(365);
    private final Clock clock;

    public WorkItemPermissionGovernanceService() {
        this(Clock.systemUTC());
    }

    WorkItemPermissionGovernanceService(Clock clock) {
        this.clock = clock;
    }

    public PermissionExplanation explainForUser(
        PermissionDecision decision,
        boolean requestAvailable
    ) {
        return new PermissionExplanation(
            decision.allowed(),
            decision.action(),
            decision.reasonCode(),
            decision.disclosureScope(),
            decision.safePolicySources(),
            requestAvailable && !decision.allowed(),
            decision.evaluatedAt()
        );
    }

    public GovernanceTrace explainForGovernance(
        PermissionDecision decision,
        boolean governanceAllowed,
        boolean contentVisible
    ) {
        if (!governanceAllowed || !contentVisible) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Permission trace is not available");
        }
        return new GovernanceTrace(
            decision.decisionId(),
            decision.workItemId(),
            decision.action(),
            decision.allowed(),
            decision.reasonCode(),
            decision.policyVersionId(),
            decision.policyConfigHash(),
            decision.subjectVersion(),
            decision.safePolicySources(),
            decision.evaluatedAt()
        );
    }

    public GrantValidation validateRoleMutation(RoleMutation mutation) {
        String requestId = mutation.requestId() == null ? "" : mutation.requestId().trim();
        String reason = mutation.reason() == null ? "" : mutation.reason().trim();
        if (requestId.isEmpty() || requestId.length() > 120 || reason.length() < 3
            || reason.length() > 500) {
            throw failure("INVALID_PERMISSION_MUTATION", "Request id and reason are required");
        }
        if (mutation.remove()
            && "owner".equals(mutation.roleKey())
            && mutation.activeOwnerIds().contains(mutation.subjectId())
            && mutation.activeOwnerIds().size() <= 1) {
            throw failure("LAST_PERMISSION_OWNER", "The last content owner cannot be removed");
        }
        if (mutation.expiresAt() != null) {
            Instant now = clock.instant();
            if (!mutation.expiresAt().isAfter(now)
                || mutation.expiresAt().isAfter(now.plus(MAX_TEMPORARY_GRANT))) {
                throw failure(
                    "INVALID_PERMISSION_EXPIRY",
                    "Temporary permission expiry must be within 365 days"
                );
            }
        }
        boolean dangerous = mutation.remove() && "owner".equals(mutation.roleKey());
        if (dangerous && !"TRANSFER_PERMISSION_OWNER".equals(mutation.confirmation())) {
            throw failure("DANGEROUS_CONFIRMATION_REQUIRED", "Owner transfer confirmation is required");
        }
        return new GrantValidation(dangerous, mutation.expiresAt(), "validated");
    }

    /**
     * Validates the project adapter contract before a request is handed to the common permission
     * workflow. It deliberately does not approve or grant; those remain separate reviewed
     * transitions with their own receipt.
     */
    public PermissionRequest validatePermissionRequest(
        PermissionRequest request,
        boolean targetVisible,
        boolean duplicateSubmitted
    ) {
        if (!targetVisible) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Permission target is not available");
        }
        if (request == null
            || request.requestId() == null
            || request.requestId().isBlank()
            || request.requestId().length() > 120
            || request.workItemId() == null
            || request.action() == null
            || request.action().isBlank()
            || request.reason() == null
            || request.reason().trim().length() < 3
            || request.reason().length() > 500) {
            throw failure("INVALID_PERMISSION_REQUEST", "Permission request is incomplete");
        }
        if (duplicateSubmitted) {
            throw failure("DUPLICATE_PERMISSION_REQUEST", "An equivalent request is already submitted");
        }
        if (request.requestedUntil() != null) {
            Instant now = clock.instant();
            if (!request.requestedUntil().isAfter(now)
                || request.requestedUntil().isAfter(now.plus(MAX_TEMPORARY_GRANT))) {
                throw failure(
                    "INVALID_PERMISSION_EXPIRY",
                    "Requested permission expiry must be within 365 days"
                );
            }
        }
        return new PermissionRequest(
            request.requestId().trim(),
            request.workItemId(),
            request.action().trim(),
            request.reason().trim(),
            request.requestedUntil()
        );
    }

    public PolicyPreview previewPolicyChange(
        long expectedVersion,
        long currentVersion,
        int visibleCandidateCount,
        int hiddenCandidateCount,
        int grantCount,
        int revokeCount
    ) {
        if (expectedVersion != currentVersion) {
            throw failure("PERMISSION_POLICY_VERSION_CONFLICT", "Permission policy changed; refresh required");
        }
        if (visibleCandidateCount < 0 || hiddenCandidateCount < 0
            || grantCount < 0 || revokeCount < 0
            || visibleCandidateCount > 200 || grantCount + revokeCount > 200) {
            throw failure("PERMISSION_PREVIEW_LIMIT_EXCEEDED", "Permission preview exceeds its hard limit");
        }
        // Hidden candidates are intentionally collapsed to a boolean; their count must never
        // become part of a user-facing response.
        return new PolicyPreview(
            currentVersion,
            visibleCandidateCount,
            grantCount,
            revokeCount,
            hiddenCandidateCount > 0,
            grantCount > 0 && revokeCount > 0
        );
    }

    public GovernanceMetrics metrics(
        List<PermissionDecision> decisions,
        List<ConsistencyFinding> findings
    ) {
        long allowed = decisions.stream().filter(PermissionDecision::allowed).count();
        long denied = decisions.size() - allowed;
        long repairable = findings.stream().filter(ConsistencyFinding::repairableProjection).count();
        return new GovernanceMetrics(
            decisions.size(),
            allowed,
            denied,
            findings.size(),
            repairable,
            denied > 0 || !findings.isEmpty()
        );
    }

    public List<ConsistencyFinding> scan(
        List<BindingSnapshot> bindings,
        Set<UUID> activeSubjects,
        Set<String> configuredRoleKeys
    ) {
        List<ConsistencyFinding> findings = new ArrayList<>();
        Instant now = clock.instant();
        for (BindingSnapshot binding : bindings) {
            if (!activeSubjects.contains(binding.subjectId())) {
                findings.add(new ConsistencyFinding(binding.bindingId(), "subject_unavailable", false));
            }
            if (!configuredRoleKeys.contains(binding.roleKey())) {
                findings.add(new ConsistencyFinding(binding.bindingId(), "role_definition_missing", false));
            }
            if (binding.expiresAt() != null && !binding.expiresAt().isAfter(now)) {
                findings.add(new ConsistencyFinding(binding.bindingId(), "grant_expired", true));
            }
        }
        return List.copyOf(findings);
    }

    public LegacyDisposition classifyLegacy(String disposition, boolean wouldExpandAccess) {
        if (wouldExpandAccess || disposition == null || disposition.isBlank()) {
            return new LegacyDisposition("failed", "legacy_mapping_would_expand_access");
        }
        return switch (disposition) {
            case "map", "preserve_in_snapshot" -> new LegacyDisposition("ready", disposition);
            case "review_required" -> new LegacyDisposition("failed", "manual_review_required");
            default -> new LegacyDisposition("failed", "unknown_legacy_disposition");
        };
    }

    public record GovernanceTrace(
        UUID decisionId,
        UUID workItemId,
        String action,
        boolean allowed,
        String reasonCode,
        UUID policyVersionId,
        String policyConfigHash,
        long subjectVersion,
        List<String> safePolicySources,
        Instant evaluatedAt
    ) {
        public GovernanceTrace {
            safePolicySources = List.copyOf(safePolicySources);
        }
    }

    public record RoleMutation(
        UUID subjectId,
        String roleKey,
        boolean remove,
        Set<UUID> activeOwnerIds,
        Instant expiresAt,
        String requestId,
        String reason,
        String confirmation
    ) {
        public RoleMutation {
            activeOwnerIds = Set.copyOf(activeOwnerIds == null ? Set.of() : activeOwnerIds);
        }
    }

    public record GrantValidation(boolean dangerous, Instant expiresAt, String status) {}
    public record PolicyPreview(
        long policyVersion,
        int visibleCandidateCount,
        int grantCount,
        int revokeCount,
        boolean hiddenCandidatesPresent,
        boolean conflictingEffects
    ) {}
    public record GovernanceMetrics(
        long decisionCount,
        long allowCount,
        long denyCount,
        long findingCount,
        long repairableProjectionCount,
        boolean highRiskAlert
    ) {}
    public record BindingSnapshot(UUID bindingId, UUID subjectId, String roleKey, Instant expiresAt) {}
    public record ConsistencyFinding(UUID bindingId, String code, boolean repairableProjection) {}
    public record LegacyDisposition(String status, String code) {}
}
