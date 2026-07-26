package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.application.WorkItemConfigurationPublicationService.FailurePoint;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.WorkItemConfigurationException;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class WorkItemConfigurationPublicationServiceIntegrationTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WorkItemConfigurationPublicationService service;

    @Autowired
    private WorkItemConfigurationSnapshotCanonicalizer canonicalizer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void everyInjectedBoundaryRollsBackVersionPointerDraftAuditOutboxAndReceipt() throws Exception {
        Fixture fixture = fixture("fault");

        for (FailurePoint point : FailurePoint.values()) {
            assertThrows(
                IllegalStateException.class,
                () -> service.publish(
                    fixture.user(),
                    fixture.spaceId(),
                    fixture.typeId(),
                    0,
                    true,
                    "fault-" + point + "-" + UUID.randomUUID(),
                    point
                )
            );
            assertEquals(1, countVersions(fixture));
            assertEquals(fixture.versionId(), currentVersion(fixture));
            assertEquals("valid", draftStatus(fixture));
            assertEquals(0, countReceipts(fixture));
            assertEquals(0, countAudit(fixture));
            assertEquals(0, countOutbox(fixture));
        }

        var published = service.publish(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            0,
            true,
            "fault-success-" + UUID.randomUUID()
        );
        assertEquals(2, published.version().versionNumber());
        assertEquals(2, countVersions(fixture));
        assertEquals(published.version().id(), currentVersion(fixture));
        assertEquals("abandoned", draftStatus(fixture));
        assertEquals(1, countReceipts(fixture));
        assertEquals(1, countAudit(fixture));
        assertEquals(1, countOutbox(fixture));
    }

    @Test
    void concurrentPublishersAllocateOneNextVersionWithoutLostPointerUpdates() throws Exception {
        Fixture fixture = fixture("concurrent");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> first = executor.submit(() -> publishAfterBarrier(fixture, ready, start, "first"));
            Future<Object> second = executor.submit(() -> publishAfterBarrier(fixture, ready, start, "second"));
            ready.await();
            start.countDown();
            Object left = first.get();
            Object right = second.get();

            assertTrue(left instanceof WorkItemConfigurationPublicationService.PublicationResult
                || right instanceof WorkItemConfigurationPublicationService.PublicationResult);
            assertTrue(left instanceof WorkItemConfigurationException || right instanceof WorkItemConfigurationException);
            assertEquals(2, countVersions(fixture));
            assertEquals(2, jdbcTemplate.queryForObject(
                """
                    select version_number
                      from project_work_item_type_versions
                     where id=(select current_version_id from project_work_item_types where id=?)
                    """,
                Integer.class,
                fixture.typeId()
            ));
        }
    }

    @Test
    void systemPresetCanPublishACompleteConfigurationWithoutLosingIdentityProtection() throws Exception {
        Fixture fixture = fixture("system-preset", true);

        var published = service.publish(
            fixture.user(),
            fixture.spaceId(),
            fixture.typeId(),
            0,
            true,
            "system-preset-" + UUID.randomUUID()
        );

        assertEquals(2, published.version().versionNumber());
        assertEquals(published.version().id(), currentVersion(fixture));
        assertTrue(jdbcTemplate.queryForObject(
            "select is_system from project_work_item_types where id=?",
            Boolean.class,
            fixture.typeId()
        ));
    }

    private Object publishAfterBarrier(
        Fixture fixture,
        CountDownLatch ready,
        CountDownLatch start,
        String label
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return service.publish(
                fixture.user(),
                fixture.spaceId(),
                fixture.typeId(),
                0,
                true,
                "concurrent-" + label + "-" + UUID.randomUUID()
            );
        } catch (WorkItemConfigurationException exception) {
            return exception;
        }
    }

    private Fixture fixture(String label) throws Exception {
        return fixture(label, false);
    }

    private Fixture fixture(String label, boolean system) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var snapshot = objectMapper.readTree("""
            {
              "snapshotSchemaVersion":1,
              "typeDefinition":{
                "typeKey":"task",
                "workspaceId":"%s",
                "spaceId":"%s"
              },
              "fields":[],
              "layouts":[
                {"layoutKind":"create","nodes":[],"policies":[]},
                {"layoutKind":"detail","nodes":[],"policies":[]}
              ]
            }
            """.formatted(WORKSPACE_ID, spaceId));
        var canonical = canonicalizer.canonicalize(snapshot);

        jdbcTemplate.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, ?, 'not-used', ?, 'active', now(), now())
                """,
            userId,
            WORKSPACE_ID,
            "s06_" + label + "_" + suffix,
            "S06 " + label
        );
        jdbcTemplate.update(
            """
                insert into project_spaces (
                    id, workspace_id, space_key, name, status, visibility, version,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', 'private', 0, ?, now(), ?, now())
                """,
            spaceId,
            WORKSPACE_ID,
            "s06_" + label + "_" + suffix,
            "S06 " + label,
            userId,
            userId
        );
        jdbcTemplate.update(
            """
                insert into project_space_members (
                    id, workspace_id, space_id, user_id, status, joined_at,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId,
            WORKSPACE_ID,
            spaceId,
            userId,
            userId,
            userId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments (
                    id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at
                ) values (?, ?, ?, ?, 'owner', ?, now())
                """,
            roleId,
            WORKSPACE_ID,
            spaceId,
            memberId,
            userId
        );
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                """
                    insert into project_work_item_types (
                        id, workspace_id, space_id, type_key, name, icon, description,
                        sort_order, status, is_system, current_version_id, created_by,
                        created_at, updated_by, updated_at, aggregate_version
                ) values (?, ?, ?, 'task', 'Task', '', '', 0, 'active', ?, ?, ?, now(), ?, now(), 0)
                """,
                typeId,
                WORKSPACE_ID,
                spaceId,
                system,
                versionId,
                userId,
                userId
            );
            jdbcTemplate.update(
                """
                    insert into project_work_item_type_versions (
                        id, workspace_id, space_id, type_definition_id, version_number,
                        config_hash, status, config, created_by, created_at, published_by,
                        published_at, snapshot_schema_version
                    ) values (?, ?, ?, ?, 1, ?, 'published', '{}'::jsonb, ?, now(), ?, now(), 0)
                    """,
                versionId,
                WORKSPACE_ID,
                spaceId,
                typeId,
                "0".repeat(64),
                userId,
                userId
            );
        });
        jdbcTemplate.update(
            """
                insert into project_work_item_configuration_drafts (
                    id, workspace_id, space_id, type_definition_id, status,
                    snapshot_schema_version, config_hash, snapshot, diagnostics,
                    aggregate_version, source_version_id, lineage_kind,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'valid', 1, ?, ?::jsonb, '[]'::jsonb, 0, null, 'live_edit', ?, now(), ?, now())
                """,
            draftId,
            WORKSPACE_ID,
            spaceId,
            typeId,
            canonical.configHash(),
            canonical.payload().toString(),
            userId,
            userId
        );
        CurrentUser user = new CurrentUser(
            userId,
            WORKSPACE_ID,
            UUID.randomUUID(),
            "s06_" + label + "_" + suffix,
            "S06 " + label,
            Set.of("member"),
            Set.of()
        );
        return new Fixture(user, spaceId, typeId, versionId, draftId);
    }

    private int countVersions(Fixture fixture) {
        return jdbcTemplate.queryForObject(
            "select count(*) from project_work_item_type_versions where type_definition_id=?",
            Integer.class,
            fixture.typeId()
        );
    }

    private UUID currentVersion(Fixture fixture) {
        return jdbcTemplate.queryForObject(
            "select current_version_id from project_work_item_types where id=?",
            UUID.class,
            fixture.typeId()
        );
    }

    private String draftStatus(Fixture fixture) {
        return jdbcTemplate.queryForObject(
            "select status from project_work_item_configuration_drafts where id=?",
            String.class,
            fixture.draftId()
        );
    }

    private int countReceipts(Fixture fixture) {
        return jdbcTemplate.queryForObject(
            "select count(*) from project_work_item_configuration_publication_commands where type_definition_id=?",
            Integer.class,
            fixture.typeId()
        );
    }

    private int countAudit(Fixture fixture) {
        return jdbcTemplate.queryForObject(
            "select count(*) from audit_logs where target_type='work_item_configuration_version' and action='work_item_configuration.published' and target_id in (select id from project_work_item_type_versions where type_definition_id=?)",
            Integer.class,
            fixture.typeId()
        );
    }

    private int countOutbox(Fixture fixture) {
        return jdbcTemplate.queryForObject(
            "select count(*) from domain_events where aggregate_type='work_item_configuration_version' and aggregate_id in (select id from project_work_item_type_versions where type_definition_id=?)",
            Integer.class,
            fixture.typeId()
        );
    }

    private record Fixture(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID versionId,
        UUID draftId
    ) {
    }
}
