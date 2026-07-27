package com.colla.platform.modules.project.contract;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public, minimum-disclosure S11 permission contracts.
 *
 * <p>Consumers receive decisions and stable identities only. Policy bodies, hidden fields,
 * private role assignments and table details are not public contract data.</p>
 */
public final class WorkItemPermissionContracts {
    private WorkItemPermissionContracts() {
    }

    public record SubjectContext(
        UUID workspaceId,
        UUID userId,
        long subjectVersion,
        Set<String> enterpriseRoleKeys,
        Set<String> spaceRoleKeys,
        Set<String> workItemRoleKeys,
        Set<String> participantRoleKeys
    ) {
        public SubjectContext {
            enterpriseRoleKeys = Set.copyOf(enterpriseRoleKeys == null ? Set.of() : enterpriseRoleKeys);
            spaceRoleKeys = Set.copyOf(spaceRoleKeys == null ? Set.of() : spaceRoleKeys);
            workItemRoleKeys = Set.copyOf(workItemRoleKeys == null ? Set.of() : workItemRoleKeys);
            participantRoleKeys = Set.copyOf(participantRoleKeys == null ? Set.of() : participantRoleKeys);
        }
    }

    public record PermissionDecision(
        UUID decisionId,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        String action,
        boolean allowed,
        String reasonCode,
        String disclosureScope,
        UUID policyVersionId,
        String policyConfigHash,
        long subjectVersion,
        List<String> safePolicySources,
        Instant evaluatedAt
    ) {
        public PermissionDecision {
            safePolicySources = List.copyOf(safePolicySources == null ? List.of() : safePolicySources);
        }
    }

    public record PermissionExplanation(
        boolean allowed,
        String action,
        String reasonCode,
        String disclosureScope,
        List<String> safePolicySources,
        boolean requestAvailable,
        Instant evaluatedAt
    ) {
        public PermissionExplanation {
            safePolicySources = List.copyOf(safePolicySources == null ? List.of() : safePolicySources);
        }
    }

    public record PermissionRequest(
        String requestId,
        UUID workItemId,
        String action,
        String reason,
        Instant requestedUntil
    ) {
    }
}
