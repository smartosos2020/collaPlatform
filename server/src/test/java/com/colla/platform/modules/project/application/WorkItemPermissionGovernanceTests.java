package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.PermissionDecision;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.PermissionRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemPermissionGovernanceTests {
    private final Instant now = Instant.parse("2026-07-27T00:00:00Z");
    private final WorkItemPermissionGovernanceService service =
        new WorkItemPermissionGovernanceService(Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void governanceTraceRequiresBothGovernanceAndContentVisibility() {
        PermissionDecision decision = decision(false);
        assertThatThrownBy(() -> service.explainForGovernance(decision, true, false))
            .hasMessageContaining("not available");
        assertThat(service.explainForGovernance(decision, true, true).safePolicySources())
            .containsExactly("deny_hidden");
        assertThat(service.explainForUser(decision, true).requestAvailable()).isTrue();
    }

    @Test
    void lastOwnerExpiryAndDangerousConfirmationFailClosed() {
        UUID owner = UUID.randomUUID();
        var removeLast = new WorkItemPermissionGovernanceService.RoleMutation(
            owner, "owner", true, Set.of(owner), null, "request-1", "transfer", "TRANSFER_PERMISSION_OWNER"
        );
        assertThatThrownBy(() -> service.validateRoleMutation(removeLast))
            .hasMessageContaining("last content owner");
        var tooLong = new WorkItemPermissionGovernanceService.RoleMutation(
            UUID.randomUUID(), "member", false, Set.of(owner), now.plusSeconds(366L * 86400),
            "request-2", "temporary access", ""
        );
        assertThatThrownBy(() -> service.validateRoleMutation(tooLong))
            .hasMessageContaining("within 365 days");
    }

    @Test
    void scanAndLegacyClassificationAreDeterministicAndNeverExpandAccess() {
        UUID binding = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        var findings = service.scan(
            List.of(new WorkItemPermissionGovernanceService.BindingSnapshot(
                binding, missing, "removed_role", now.minusSeconds(1)
            )),
            Set.of(),
            Set.of("owner")
        );
        assertThat(findings).extracting(WorkItemPermissionGovernanceService.ConsistencyFinding::code)
            .containsExactly("subject_unavailable", "role_definition_missing", "grant_expired");
        assertThat(service.classifyLegacy("map", true).status()).isEqualTo("failed");
        assertThat(service.classifyLegacy("review_required", false).code())
            .isEqualTo("manual_review_required");
    }

    @Test
    void requestAndPreviewContractsAreBoundedAndPreserveMinimalDisclosure() {
        UUID workItemId = UUID.randomUUID();
        PermissionRequest validated = service.validatePermissionRequest(
            new PermissionRequest(
                " request-3 ", workItemId, "view", " business need ", now.plusSeconds(86400)
            ),
            true,
            false
        );
        assertThat(validated.requestId()).isEqualTo("request-3");
        assertThatThrownBy(() -> service.validatePermissionRequest(validated, true, true))
            .hasMessageContaining("already submitted");
        var preview = service.previewPolicyChange(7, 7, 10, 4, 2, 1);
        assertThat(preview.visibleCandidateCount()).isEqualTo(10);
        assertThat(preview.hiddenCandidatesPresent()).isTrue();
        assertThatThrownBy(() -> service.previewPolicyChange(6, 7, 10, 0, 0, 0))
            .hasMessageContaining("refresh required");
    }

    private PermissionDecision decision(boolean allowed) {
        return new PermissionDecision(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "view", allowed, "explicit_deny", "minimal", UUID.randomUUID(), "d".repeat(64),
            4, List.of("deny_hidden"), now
        );
    }
}
