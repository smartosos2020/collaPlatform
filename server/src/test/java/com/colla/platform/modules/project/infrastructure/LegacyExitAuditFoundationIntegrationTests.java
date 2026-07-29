package com.colla.platform.modules.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.application.LegacyExitAuditService;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class LegacyExitAuditFoundationIntegrationTests {
    @Autowired
    private LegacyExitAuditService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void snapshotsFindingsAndExactRemovalDecisionsAreImmutableAndWorkspaceScoped() {
        Fixture fixture = fixture("complete");
        var ready = service.createSnapshot(fixture.admin());

        assertThat(ready.status()).isEqualTo("ready");
        assertThat(ready.inventoryVersion()).isEqualTo("s21-m1-v1");
        assertThat(ready.surfaces()).hasSize(10);
        assertThat(ready.findings()).singleElement()
            .extracting(value -> value.key())
            .isEqualTo("audit_complete");

        var decision = service.decide(
            fixture.admin(), ready.id(), "api.issues", "remove",
            "Remove the active issue API after the dependency inventory is verified.",
            "audit-decision-complete"
        );
        var replay = service.decide(
            fixture.admin(), ready.id(), "api.issues", "remove",
            "Remove the active issue API after the dependency inventory is verified.",
            "audit-decision-complete"
        );

        assertThat(replay.id()).isEqualTo(decision.id());
        assertThat(replay.replayed()).isTrue();
        assertThatThrownBy(() -> service.decide(
            fixture.admin(), ready.id(), "api.issues", "blocked",
            "Conflicting payload must never overwrite the immutable prior decision.",
            "audit-decision-complete"
        )).hasMessageContaining("Request id was already used");

        UUID snapshotRow = jdbc.queryForObject("""
            select id from project_legacy_audit_snapshots
             where workspace_id=? and id=?
            """, UUID.class, fixture.workspaceId(), ready.id());
        assertThatThrownBy(() -> jdbc.update(
            "update project_legacy_audit_snapshots set status='blocked' where id=?",
            snapshotRow
        )).rootCause().hasMessageContaining("project legacy audit history is append-only");

        CurrentUser foreign = new CurrentUser(
            fixture.userId(), UUID.randomUUID(), UUID.randomUUID(), "foreign", "Foreign",
            Set.of("admin"), Set.of("project.manage")
        );
        assertThatThrownBy(() -> service.get(foreign, ready.id()))
            .hasMessageContaining("Legacy audit snapshot is not available");
    }

    @Test
    void unmappedLegacySourcesAndMismatchedEvidenceBlockDeletionReadiness() {
        Fixture fixture = fixture("blocked");
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        jdbc.update("""
            insert into projects(
              id,workspace_id,project_key,name,description,status,
              created_by,created_at,updated_by,updated_at
            ) values (?,?,?,'Unmapped project','','active',?,now(),?,now())
            """, projectId, fixture.workspaceId(), "AUD-" + suffix(), fixture.userId(), fixture.userId());
        jdbc.update("""
            insert into issues(
              id,workspace_id,project_id,issue_key,issue_type,title,description,
              priority,status,reporter_id,created_by,created_at,updated_by,updated_at
            ) values (?,?,?,?, 'task','Unmapped issue','','medium','open',?,?,now(),?,now())
            """, issueId, fixture.workspaceId(), projectId, "AUD-" + suffix() + "-1",
            fixture.userId(), fixture.userId(), fixture.userId());

        var blocked = service.createSnapshot(fixture.admin());

        assertThat(blocked.status()).isEqualTo("blocked");
        assertThat(blocked.totals()).containsEntry("unmappedProjects", 1L)
            .containsEntry("unmappedIssues", 1L);
        assertThat(blocked.findings())
            .filteredOn(value -> "blocking".equals(value.severity()))
            .extracting(value -> value.key())
            .containsExactlyInAnyOrder("unmappedProjects", "unmappedIssues");
    }

    private Fixture fixture(String label) {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String value = suffix();
        jdbc.update("""
            insert into workspaces(id,name,slug,status,created_at,updated_at)
            values (?,?,?,'active',now(),now())
            """, workspaceId, "Legacy audit " + label, "legacy-audit-" + value);
        jdbc.update("""
            insert into users(
              id,workspace_id,username,password_hash,display_name,status,created_at,updated_at
            ) values (?,?,?,'not-used',?,'active',now(),now())
            """, userId, workspaceId, "legacy_audit_" + value, "Legacy audit " + label);
        CurrentUser admin = new CurrentUser(
            userId, workspaceId, UUID.randomUUID(), "legacy_audit_" + value,
            "Legacy audit " + label, Set.of("admin"), Set.of("project.manage")
        );
        return new Fixture(workspaceId, userId, admin);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record Fixture(UUID workspaceId, UUID userId, CurrentUser admin) {
    }
}
