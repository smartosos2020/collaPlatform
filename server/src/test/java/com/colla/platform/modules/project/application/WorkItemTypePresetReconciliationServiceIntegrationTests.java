package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.application.WorkItemTypePresetReconciliationService.ReconciliationResult;
import com.colla.platform.modules.project.application.WorkItemTypePresetBackfillService.BackfillReport;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.CreateWorkItemType;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "colla.project.work-item-types.preset-backfill-enabled=false")
class WorkItemTypePresetReconciliationServiceIntegrationTests {
    private static final List<String> PRESET_KEYS = List.of(
        "project", "requirement", "task", "bug", "iteration", "release"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkItemTypePresetReconciliationService reconciliationService;

    @Autowired
    private WorkItemTypePresetBackfillService backfillService;

    @Autowired
    private WorkItemTypeDefinitionService definitionService;

    @Autowired
    private ProjectSpaceService projectSpaceService;

    @Test
    void existingSpaceBackfillAndReplayConvergeWithoutDuplicateEvidence() {
        Fixture fixture = fixture("backfill", "active");

        ReconciliationResult first = reconciliationService.reconcile(
            fixture.workspaceId(), fixture.spaceId(), fixture.actorId()
        );
        ReconciliationResult replay = reconciliationService.reconcile(
            fixture.workspaceId(), fixture.spaceId(), fixture.actorId()
        );

        assertThat(first.status()).isEqualTo("installed");
        assertThat(first.installedKeys()).containsExactlyElementsOf(PRESET_KEYS);
        assertThat(replay.status()).isEqualTo("current");
        assertThat(typeKeys(fixture.spaceId())).containsExactlyElementsOf(PRESET_KEYS);
        assertThat(count("project_work_item_type_versions", fixture.spaceId())).isEqualTo(6);
        assertThat(countEvidence("audit_logs", fixture.spaceId())).isEqualTo(1);
        assertThat(countEvidence("domain_events", fixture.spaceId())).isEqualTo(1);
    }

    @Test
    void customPresetKeyConflictReturnsFailureListAndPerformsNoPartialWrites() {
        Fixture fixture = fixture("conflict", "active");
        definitionService.create(new CreateWorkItemType(
            fixture.workspaceId(), fixture.spaceId(), fixture.actorId(),
            "task", "Custom Task", "custom", "Owned by the workspace", 1, false
        ));

        ReconciliationResult result = reconciliationService.reconcile(
            fixture.workspaceId(), fixture.spaceId(), fixture.actorId()
        );

        assertThat(result.status()).isEqualTo("conflict");
        assertThat(result.conflictKeys()).containsExactly("task");
        assertThat(typeKeys(fixture.spaceId())).containsExactly("task");
        assertThat(countEvidence("audit_logs", fixture.spaceId())).isZero();
        assertThat(countEvidence("domain_events", fixture.spaceId())).isZero();
    }

    @Test
    void concurrentBackfillSerializesAtSpaceAndInstallsOneCatalog() throws Exception {
        Fixture fixture = fixture("concurrent", "active");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<ReconciliationResult> first = executor.submit(() -> reconcileAfterBarrier(fixture, ready, start));
            Future<ReconciliationResult> second = executor.submit(() -> reconcileAfterBarrier(fixture, ready, start));
            ready.await();
            start.countDown();
            assertThat(List.of(first.get().status(), second.get().status()))
                .containsExactlyInAnyOrder("installed", "current");
        }
        assertThat(count("project_work_item_types", fixture.spaceId())).isEqualTo(6);
        assertThat(count("project_work_item_type_versions", fixture.spaceId())).isEqualTo(6);
        assertThat(countEvidence("domain_events", fixture.spaceId())).isEqualTo(1);
    }

    @Test
    void newSpaceCreationCommitsOwnerAndCompletePresetCatalogTogether() {
        Fixture fixture = fixtureWithoutSpace("new-space");
        CurrentUser actor = new CurrentUser(
            fixture.actorId(), fixture.workspaceId(), null, "preset-actor", "Preset Actor",
            Set.of("admin"), Set.of()
        );

        UUID spaceId = projectSpaceService.create(
            actor, "preset-new-" + suffix(), "Preset New Space", "", "private"
        ).id();

        assertThat(typeKeys(spaceId)).containsExactlyElementsOf(PRESET_KEYS);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from project_space_members where space_id=? and status='active'",
            Integer.class,
            spaceId
        )).isEqualTo(1);
    }

    @Test
    void inactiveSpaceIsExplicitlySkipped() {
        Fixture fixture = fixture("disabled", "disabled");
        ReconciliationResult result = reconciliationService.reconcile(
            fixture.workspaceId(), fixture.spaceId(), fixture.actorId()
        );
        assertThat(result.status()).isEqualTo("skipped");
        assertThat(count("project_work_item_types", fixture.spaceId())).isZero();
    }

    @Test
    void startupCoordinatorIsolatesConflictsAndContinuesOtherSpaces() {
        Fixture conflict = fixture("startup-conflict", "active");
        Fixture healthy = fixture("startup-healthy", "active");
        definitionService.create(new CreateWorkItemType(
            conflict.workspaceId(), conflict.spaceId(), conflict.actorId(),
            "task", "Custom Task", "custom", "Requires governance", 1, false
        ));

        BackfillReport report = backfillService.reconcileExistingSpaces();

        assertThat(report.failures()).anySatisfy(failure -> {
            assertThat(failure.spaceId()).isEqualTo(conflict.spaceId());
            assertThat(failure.code()).isEqualTo("PRESET_KEY_CONFLICT");
            assertThat(failure.conflictKeys()).containsExactly("task");
        });
        assertThat(typeKeys(conflict.spaceId())).containsExactly("task");
        assertThat(typeKeys(healthy.spaceId())).containsExactlyElementsOf(PRESET_KEYS);
    }

    @Test
    void databaseRejectsSystemPresetOverrideRetirementAndPhysicalDeletion() {
        Fixture fixture = fixture("database-guard", "active");
        reconciliationService.reconcile(fixture.workspaceId(), fixture.spaceId(), fixture.actorId());
        UUID projectTypeId = jdbcTemplate.queryForObject(
            "select id from project_work_item_types where space_id=? and type_key='project'",
            UUID.class,
            fixture.spaceId()
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            "update project_work_item_types set name='Overridden' where id=?", projectTypeId
        )).hasMessageContaining("system work item type definition is protected");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update project_work_item_types set status='retired' where id=?", projectTypeId
        )).hasMessageContaining("system work item type definition is protected");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "delete from project_work_item_types where id=?", projectTypeId
        )).hasMessageContaining("work item type definitions cannot be physically deleted");
    }

    private ReconciliationResult reconcileAfterBarrier(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return reconciliationService.reconcile(fixture.workspaceId(), fixture.spaceId(), fixture.actorId());
    }

    private List<String> typeKeys(UUID spaceId) {
        return jdbcTemplate.queryForList(
            "select type_key from project_work_item_types where space_id=? order by sort_order, type_key",
            String.class,
            spaceId
        );
    }

    private int count(String table, UUID spaceId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where space_id=?",
            Integer.class,
            spaceId
        );
    }

    private int countEvidence(String table, UUID spaceId) {
        String targetColumn = "audit_logs".equals(table) ? "target_id" : "aggregate_id";
        String actionColumn = "audit_logs".equals(table) ? "action" : "event_type";
        return jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where " + targetColumn + "=? and " + actionColumn + "='work_item_type.presets_reconciled'",
            Integer.class,
            spaceId
        );
    }

    private Fixture fixture(String label, String status) {
        Fixture fixture = fixtureWithoutSpace(label);
        jdbcTemplate.update(
            """
                insert into project_spaces
                    (id, workspace_id, space_key, name, description, status, visibility,
                     created_by, created_at, updated_by, updated_at, disabled_at)
                values (?, ?, ?, ?, '', ?, 'private', ?, now(), ?, now(),
                        case when ? = 'disabled' then now() else null end)
                """,
            fixture.spaceId(), fixture.workspaceId(), "preset-" + suffix(), "Preset " + label,
            status, fixture.actorId(), fixture.actorId(), status
        );
        return fixture;
    }

    private Fixture fixtureWithoutSpace(String label) {
        UUID workspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        String suffix = suffix();
        jdbcTemplate.update(
            "insert into workspaces (id,name,slug,status,created_at,updated_at) values (?, ?, ?, 'active', now(), now())",
            workspaceId, "Preset " + label, "preset-" + label + "-" + suffix
        );
        jdbcTemplate.update(
            """
                insert into users
                    (id,workspace_id,username,password_hash,display_name,status,created_at,updated_at)
                values (?, ?, ?, 'not-used', 'Preset Actor', 'active', now(), now())
                """,
            actorId, workspaceId, "preset" + suffix
        );
        return new Fixture(workspaceId, actorId, spaceId);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record Fixture(UUID workspaceId, UUID actorId, UUID spaceId) {
    }
}
