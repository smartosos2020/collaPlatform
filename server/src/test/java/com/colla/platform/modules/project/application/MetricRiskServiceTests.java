package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.MetricRiskModels.EvaluateRisksCommand;
import com.colla.platform.modules.project.domain.MetricRiskModels.EvidenceReference;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicy;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicyVersion;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskSignal;
import com.colla.platform.modules.project.domain.MetricRiskModels.SaveRiskPolicyCommand;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.MetricRiskRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetricRiskServiceTests {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void rejectsPersonalPerformanceLikePolicyTypes() {
        Fixture fixture = fixture();
        SaveRiskPolicyCommand command = new SaveRiskPolicyCommand(
            1, "risk-save-001", null, 0, "people.performance",
            "个人绩效", "", List.of("utilization"), "critical", 24
        );

        assertThatThrownBy(() -> fixture.service.save(
            fixture.user, fixture.spaceId, command
        )).isInstanceOf(WorkItemRuntimeException.class)
            .hasMessageContaining("invalid");
        verify(fixture.repository, never()).savePolicy(
            any(), any(), any(), any(), any(), any(), any(),
            anyList(), any(), anyInt(), any(Long.class), any(), any()
        );
    }

    @Test
    void evaluatesOnlyPublishedPoliciesAgainstAuthorizedPublicEvidence() {
        Fixture fixture = fixture();
        RiskPolicy active = policy(fixture.user.id(), true);
        RiskPolicy draft = policy(fixture.user.id(), false);
        when(fixture.repository.listPolicies(
            fixture.user.workspaceId(), fixture.spaceId, 50
        )).thenReturn(List.of(active, draft));
        when(fixture.evidence.resolve(
            eq(fixture.user), eq(fixture.spaceId), any()
        )).thenReturn(Map.of(
            "overdue", List.of(new EvidenceReference(
                "ProjectPlanService", UUID.randomUUID().toString(), 3,
                Instant.parse("2026-07-29T00:00:00Z"),
                "one visible milestone overdue", true
            )),
            "resource", List.of()
        ));
        when(fixture.repository.upsertSignals(
            eq(fixture.user.workspaceId()), eq(fixture.spaceId),
            eq(fixture.user.id()), anyList(), eq("risk-eval-001"), any()
        )).thenAnswer(invocation -> {
            var candidate = ((List<com.colla.platform.modules.project.domain.MetricRiskModels.SignalCandidate>)
                invocation.getArgument(3)).getFirst();
            return List.of(new RiskSignal(
                UUID.randomUUID(), candidate.policyId(), candidate.policyVersion(),
                candidate.signalType(), candidate.severity(), "open",
                candidate.dedupeKey(), candidate.evidenceFingerprint(),
                candidate.evidence(), 1, null, null, null, null, "",
                candidate.observedAt(), candidate.observedAt()
            ));
        });

        List<RiskSignal> result = fixture.service.evaluate(
            fixture.user,
            fixture.spaceId,
            new EvaluateRisksCommand(
                1, "risk-eval-001", Instant.parse("2026-07-29T00:00:00Z")
            )
        );

        assertThat(result).singleElement()
            .satisfies(signal -> {
                assertThat(signal.signalType()).isEqualTo("overdue");
                assertThat(signal.evidence()).singleElement()
                    .extracting(EvidenceReference::sourceType)
                    .isEqualTo("ProjectPlanService");
            });
    }

    @Test
    void truncatedDirectoryNeverClaimsCompleteCounts() {
        Fixture fixture = fixture();
        when(fixture.repository.listPolicies(any(), any(), anyInt()))
            .thenReturn(java.util.stream.IntStream.range(0, 51)
                .mapToObj(index -> policy(fixture.user.id(), false)).toList());
        when(fixture.repository.listSignals(any(), any(), anyInt()))
            .thenReturn(List.of());

        var foundation = fixture.service.foundation(fixture.user, fixture.spaceId);

        assertThat(foundation.truncated()).isTrue();
        assertThat(foundation.policies()).hasSize(50);
        assertThat(foundation.diagnostic()).contains("not complete");
    }

    private static RiskPolicy policy(UUID actorId, boolean published) {
        UUID id = UUID.randomUUID();
        RiskPolicyVersion version = published
            ? new RiskPolicyVersion(
                UUID.randomUUID(), id, 1, "a".repeat(64),
                List.of("overdue", "resource"), "warning", 24,
                Instant.parse("2026-07-29T00:00:00Z"), actorId
            ) : null;
        return new RiskPolicy(
            id, "delivery.risk." + id.toString().substring(0, 8),
            "交付风险", "", published ? "active" : "draft",
            List.of("overdue", "resource"), "warning", 24,
            published ? 2 : 1, version, Instant.parse("2026-07-29T00:00:00Z")
        );
    }

    private static Fixture fixture() {
        MetricRiskRepository repository = mock(MetricRiskRepository.class);
        MetricRiskEvidenceResolver evidence = mock(MetricRiskEvidenceResolver.class);
        WorkItemRelationAccessDecisionService access =
            mock(WorkItemRelationAccessDecisionService.class);
        MetricRiskService service = new MetricRiskService(
            repository, evidence, access, mock(AuditLog.class),
            mock(TransactionalOutbox.class), JSON
        );
        return new Fixture(
            service, repository, evidence, user(), UUID.randomUUID()
        );
    }

    private static CurrentUser user() {
        return new CurrentUser(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "owner", "Owner", Set.of(), Set.of()
        );
    }

    private record Fixture(
        MetricRiskService service,
        MetricRiskRepository repository,
        MetricRiskEvidenceResolver evidence,
        CurrentUser user,
        UUID spaceId
    ) {
    }
}
