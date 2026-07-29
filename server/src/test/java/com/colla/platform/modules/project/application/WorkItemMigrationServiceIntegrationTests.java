package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.WorkItemMigrationRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class WorkItemMigrationServiceIntegrationTests {
    @Autowired
    private WorkItemMigrationService service;

    @Autowired
    private WorkItemMigrationRepository repository;

    @Autowired
    private WorkItemCompatibilityService compatibilityService;

    @Autowired
    private WorkItemConfigurationSnapshotCanonicalizer snapshotCanonicalizer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void plansExecutesVerifiesAndRollsBackOneAtomicProjectUnit() throws Exception {
        Fixture fixture = fixture("complete", true);
        var batch = service.plan(fixture.admin(), false, 0);
        assertThat(batch.status()).isEqualTo("planned");
        assertThat(batch.units()).hasSize(1);
        assertThat(batch.failures()).isEmpty();

        var execution = service.execute(fixture.admin(), batch.id(), "test-worker");
        assertThat(execution.batch().status()).isEqualTo("completed");
        assertThat(execution.completedUnits()).isEqualTo(1);
        assertThat(execution.failedUnits()).isZero();
        assertThat(execution.migratedObjects()).isEqualTo(6);
        assertThat(count("project_work_items", fixture.workspaceId())).isEqualTo(2);
        assertThat(count("project_legacy_work_item_maps", fixture.workspaceId())).isEqualTo(2);
        assertThat(count("project_work_item_migration_provenance", fixture.workspaceId())).isEqualTo(6);
        assertThat(count("project_work_item_comments", fixture.workspaceId())).isEqualTo(1);
        assertThat(count("project_work_item_attachments", fixture.workspaceId())).isEqualTo(1);

        var verification = service.verifyBatch(fixture.admin(), batch.id());
        assertThat(verification.matched()).isTrue();
        var convergence = service.verifyConvergence(fixture.admin());
        assertThat(convergence.matched()).isTrue();

        var rolledBack = service.rollback(fixture.admin(), batch.id(), true);
        assertThat(rolledBack.status()).isEqualTo("rolled_back");
        assertThat(count("project_work_items", fixture.workspaceId())).isZero();
        assertThat(jdbc.queryForObject("""
            select count(*) from project_legacy_work_item_maps
             where workspace_id=? and status='active'
            """, Integer.class, fixture.workspaceId())).isZero();
        assertThat(count("project_work_item_migration_provenance", fixture.workspaceId())).isEqualTo(6);
        assertThat(service.verifyBatch(fixture.admin(), batch.id()).matched()).isFalse();
    }

    @Test
    void scopedPlanIncludesOnlyRequestedProjectsAndReportsUnknownSources() throws Exception {
        Fixture fixture = fixture("scoped-plan", false);
        UUID siblingProjectId = insertSiblingProject(fixture);

        var scoped = service.plan(
            fixture.admin(), false, 0, Set.of(fixture.projectId())
        );
        assertThat(scoped.units())
            .extracting(unit -> unit.legacyProjectId())
            .containsExactly(fixture.projectId());
        assertThat(scoped.failures()).isEmpty();
        assertThat(service.execute(
            fixture.admin(), scoped.id(), "scoped-worker"
        ).batch().status()).isEqualTo("completed");
        assertThat(repository.findActiveMap(
            fixture.workspaceId(), "project", fixture.projectId()
        )).isPresent();
        assertThat(repository.findActiveMap(
            fixture.workspaceId(), "project", siblingProjectId
        )).isEmpty();

        UUID unknownProjectId = UUID.randomUUID();
        var invalid = service.plan(
            fixture.admin(), false, 0, Set.of(unknownProjectId)
        );
        assertThat(invalid.units()).isEmpty();
        assertThat(invalid.failures())
            .anySatisfy(failure -> {
                assertThat(failure.failureCode()).isEqualTo("LEGACY_PROJECT_NOT_FOUND");
                assertThat(failure.sourceId()).isEqualTo(unknownProjectId);
            });
    }

    @Test
    void staleSourceFailsOneUnitAndResumeUsesAFreshImmutablePlan() throws Exception {
        Fixture fixture = fixture("stale", false);
        var batch = service.plan(fixture.admin(), false, 0);
        jdbc.update(
            "update issues set title='changed after plan',updated_at=clock_timestamp() where id=?",
            fixture.issueId()
        );

        var failed = service.execute(fixture.admin(), batch.id(), "stale-worker");
        assertThat(failed.batch().status()).isEqualTo("failed");
        assertThat(failed.batch().failures())
            .extracting(failure -> failure.failureCode())
            .contains("LEGACY_SOURCE_CHANGED");
        assertThat(count("project_work_items", fixture.workspaceId())).isZero();
        assertThat(count("project_legacy_work_item_maps", fixture.workspaceId())).isZero();

        var retryBatch = service.plan(fixture.admin(), false, 0);
        var completed = service.execute(fixture.admin(), retryBatch.id(), "retry-worker");
        assertThat(completed.batch().status()).isEqualTo("completed");
        assertThat(service.verifyBatch(fixture.admin(), retryBatch.id()).matched()).isTrue();
    }

    @Test
    void dryRunHasNoBusinessWritesAndLeaseFencingRejectsASecondOwner() throws Exception {
        Fixture fixture = fixture("dry", false);
        var dryRun = service.plan(fixture.admin(), true, 0);
        assertThat(dryRun.status()).isEqualTo("completed");
        assertThat(count("project_work_items", fixture.workspaceId())).isZero();
        assertThat(count("project_legacy_work_item_maps", fixture.workspaceId())).isZero();

        var batch = service.plan(fixture.admin(), false, 0);
        var first = repository.acquireLease(
            fixture.workspaceId(), batch.id(), "worker-a", Instant.now().minusSeconds(120)
        );
        assertThatThrownBy(() -> repository.acquireLease(
            fixture.workspaceId(), batch.id(), "worker-b", Instant.now().minusSeconds(120)
        )).hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("MIGRATION_LEASE_UNAVAILABLE");
        repository.releaseLease(
            fixture.workspaceId(), batch.id(), first.token(), first.fenceVersion()
        );

        assertThat(service.pause(fixture.admin(), batch.id(), "operator checkpoint").status())
            .isEqualTo("paused");
        var resumed = service.execute(fixture.admin(), batch.id(), "resume-worker");
        assertThat(resumed.batch().status()).isEqualTo("completed");
        assertThat(service.verifyBatch(fixture.admin(), batch.id()).matched()).isTrue();
    }

    @Test
    void appendOnlyHistoryAndBatchIdentityCannotBeRewritten() throws Exception {
        Fixture fixture = fixture("immutable", false);
        var batch = service.plan(fixture.admin(), false, 0);
        service.execute(fixture.admin(), batch.id(), "immutable-worker");
        service.verifyBatch(fixture.admin(), batch.id());

        UUID manifestId = jdbc.queryForObject("""
            select id from project_work_item_migration_manifests
             where workspace_id=? and batch_id=? limit 1
            """, UUID.class, fixture.workspaceId(), batch.id());
        assertThatThrownBy(() -> jdbc.update(
            "update project_work_item_migration_manifests set manifest_version=2 where id=?",
            manifestId
        )).rootCause()
            .hasMessageContaining("project work item migration history is append-only");

        UUID verificationId = jdbc.queryForObject("""
            select id from project_work_item_migration_verifications
             where workspace_id=? and batch_id=? limit 1
            """, UUID.class, fixture.workspaceId(), batch.id());
        assertThatThrownBy(() -> jdbc.update(
            "delete from project_work_item_migration_verifications where id=?",
            verificationId
        )).rootCause()
            .hasMessageContaining("project work item migration history is append-only");
    }

    @Test
    void failedProjectUnitDoesNotBlockASiblingUnit() throws Exception {
        Fixture fixture = fixture("isolation", false);
        insertSiblingProject(fixture);
        var batch = service.plan(fixture.admin(), false, 0);
        assertThat(batch.units()).hasSize(2);
        jdbc.update(
            "update issues set title='stale unit',updated_at=clock_timestamp() where id=?",
            fixture.issueId()
        );

        var execution = service.execute(fixture.admin(), batch.id(), "isolation-worker");

        assertThat(execution.batch().status()).isEqualTo("failed");
        assertThat(execution.completedUnits()).isEqualTo(1);
        assertThat(execution.failedUnits()).isEqualTo(1);
        assertThat(count("project_work_items", fixture.workspaceId())).isEqualTo(1);
        assertThat(count("project_legacy_work_item_maps", fixture.workspaceId())).isEqualTo(1);
        assertThat(service.verifyBatch(fixture.admin(), batch.id()).matched()).isFalse();
    }

    @Test
    void occupiedLegacyUuidIsExplicitlyRemapped() throws Exception {
        Fixture fixture = fixture("id-conflict", false);
        jdbc.update("""
            insert into project_work_items(
              id,workspace_id,space_id,type_definition_id,type_version_id,config_hash,
              item_number,display_key,title,field_values,status,version,
              created_by,created_at,updated_by,updated_at
            )
            select ?,t.workspace_id,t.space_id,t.id,t.current_version_id,v.config_hash,
                   999999,'CONFLICT-999999','Existing canonical item','{}'::jsonb,
                   'active',0,?,now(),?,now()
              from project_work_item_types t
              join project_work_item_type_versions v
                on v.workspace_id=t.workspace_id
               and v.space_id=t.space_id
               and v.type_definition_id=t.id
               and v.id=t.current_version_id
             where t.workspace_id=? and t.space_id=? and t.type_key='project'
            """, fixture.projectId(), fixture.userId(), fixture.userId(),
            fixture.workspaceId(), fixture.spaceId());

        var batch = service.plan(fixture.admin(), false, 0);
        service.execute(fixture.admin(), batch.id(), "conflict-worker");
        UUID targetId = jdbc.queryForObject("""
            select work_item_id from project_legacy_work_item_maps
             where workspace_id=? and source_type='project' and source_id=? and status='active'
            """, UUID.class, fixture.workspaceId(), fixture.projectId());

        assertThat(targetId).isNotEqualTo(fixture.projectId());
        assertThat(service.verifyBatch(fixture.admin(), batch.id()).matched()).isTrue();
    }

    @Test
    void migrationGovernanceRejectsWorkspaceRolesAndHidesForeignBatches() throws Exception {
        Fixture fixture = fixture("governance", false);
        var batch = service.plan(fixture.admin(), false, 0);
        assertThat(service.get(fixture.admin(), batch.id()).id()).isEqualTo(batch.id());

        for (String role : Set.of("owner", "space-admin", "member", "guest", "non-member")) {
            CurrentUser denied = new CurrentUser(
                fixture.userId(), fixture.workspaceId(), UUID.randomUUID(),
                "denied-" + role, "Denied " + role, Set.of(role), Set.of()
            );
            assertThatThrownBy(() -> service.get(denied, batch.id()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
        }

        CurrentUser foreignAdmin = new CurrentUser(
            fixture.userId(), UUID.randomUUID(), UUID.randomUUID(),
            "foreign-admin", "Foreign admin", Set.of("admin"), Set.of("project.manage")
        );
        assertThatThrownBy(() -> service.get(foreignAdmin, batch.id()))
            .isInstanceOf(WorkItemRuntimeException.class)
            .extracting(error -> ((WorkItemRuntimeException) error).code())
            .isEqualTo("MIGRATION_BATCH_NOT_FOUND");
    }

    @Test
    void migrationControlQueriesUseBoundedIndexesAndRejectUnboundedThrottle() throws Exception {
        Fixture fixture = fixture("query-budget", false);
        var batch = service.plan(fixture.admin(), false, 0);

        String plan = transactionTemplate.execute(status -> {
            jdbc.execute("set local enable_seqscan=off");
            return String.join("\n", jdbc.queryForList("""
                explain
                select id from project_work_item_migration_units
                 where workspace_id=? and batch_id=? and status in ('planned','paused')
                 order by id
                 limit 1
                """, String.class, fixture.workspaceId(), batch.id()));
        });

        assertThat(plan).contains("Index Scan");
        assertThat(jdbc.queryForObject("""
            select count(*) from pg_indexes
             where schemaname='public'
               and tablename='project_work_item_migration_units'
               and indexname='idx_project_work_item_migration_units_status'
            """, Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> service.plan(fixture.admin(), false, 60_001))
            .isInstanceOf(WorkItemRuntimeException.class)
            .extracting(error -> ((WorkItemRuntimeException) error).code())
            .isEqualTo("INVALID_MIGRATION_THROTTLE");
    }

    private UUID insertSiblingProject(Fixture fixture) throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        String suffix = projectId.toString().replace("-", "").substring(0, 10);
        jdbc.update("""
            insert into project_spaces(
              id,workspace_id,space_key,name,status,visibility,version,
              created_by,created_at,updated_by,updated_at
            ) values (?,?,?,'Sibling migration space','active','private',0,?,now(),?,now())
            """, spaceId, fixture.workspaceId(), "sibling_" + suffix,
            fixture.userId(), fixture.userId());
        createPublishedType(fixture.workspaceId(), spaceId, fixture.userId(), "project");
        createPublishedType(fixture.workspaceId(), spaceId, fixture.userId(), "task");
        jdbc.update("""
            insert into projects(
              id,workspace_id,project_key,name,description,status,
              created_by,created_at,updated_by,updated_at
            ) values (?,?,?,'Sibling project','independent unit','active',?,now(),?,now())
            """, projectId, fixture.workspaceId(), "SIB-" + suffix,
            fixture.userId(), fixture.userId());
        jdbc.update("""
            insert into project_members(
              id,workspace_id,project_id,user_id,project_role,joined_at,created_by
            ) values (?,?,?,?, 'owner',now(),?)
            """, UUID.randomUUID(), fixture.workspaceId(), projectId,
            fixture.userId(), fixture.userId());
        jdbc.update("""
            insert into project_legacy_space_maps(
              id,workspace_id,legacy_project_id,space_id,mapping_version,mapping_status,
            source_checksum,mapped_by,mapped_at
            ) values (?,?,?,?,1,'active','sibling-fixture',?,now())
            """, UUID.randomUUID(), fixture.workspaceId(), projectId,
            spaceId, fixture.userId());
        return projectId;
    }

    private Fixture fixture(String label, boolean withAttachment) throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();
        String suffix = workspaceId.toString().replace("-", "").substring(0, 10);
        jdbc.update("""
            insert into workspaces(id,name,slug,status,created_at,updated_at)
            values (?,? ,?,'active',now(),now())
            """, workspaceId, "Migration " + label, "migration-" + suffix);
        jdbc.update("""
            insert into users(
              id,workspace_id,username,password_hash,display_name,status,created_at,updated_at
            ) values (?,?,?,'not-used',?,'active',now(),now())
            """, userId, workspaceId, "migration_" + suffix, "Migration " + label);
        jdbc.update("""
            insert into project_spaces(
              id,workspace_id,space_key,name,status,visibility,version,
              created_by,created_at,updated_by,updated_at
            ) values (?,?,?,?,'active','private',0,?,now(),?,now())
            """, spaceId, workspaceId, "migration_" + suffix, "Migration " + label,
            userId, userId);
        UUID memberId = UUID.randomUUID();
        jdbc.update("""
            insert into project_space_members(
              id,workspace_id,space_id,user_id,status,joined_at,
              created_by,created_at,updated_by,updated_at
            ) values (?,?,?,?,'active',now(),?,now(),?,now())
            """, memberId, workspaceId, spaceId, userId, userId, userId);
        jdbc.update("""
            insert into project_space_role_assignments(
              id,workspace_id,space_id,member_id,role_key,assigned_by,assigned_at
            ) values (?,?,?,?,'owner',?,now())
            """, UUID.randomUUID(), workspaceId, spaceId, memberId, userId);
        createPublishedType(workspaceId, spaceId, userId, "project");
        createPublishedType(workspaceId, spaceId, userId, "task");

        jdbc.update("""
            insert into projects(
              id,workspace_id,project_key,name,description,status,
              created_by,created_at,updated_by,updated_at
            ) values (?,?,? ,?,'legacy project','active',?,now(),?,now())
            """, projectId, workspaceId, "MIG-" + suffix, "Migration project", userId, userId);
        jdbc.update("""
            insert into project_members(
              id,workspace_id,project_id,user_id,project_role,joined_at,created_by
            ) values (?,?,?,?, 'owner',now(),?)
            """, UUID.randomUUID(), workspaceId, projectId, userId, userId);
        jdbc.update("""
            insert into project_legacy_space_maps(
              id,workspace_id,legacy_project_id,space_id,mapping_version,mapping_status,
              source_checksum,mapped_by,mapped_at
            ) values (?,?,?,?,1,'active','fixture',?,now())
            """, UUID.randomUUID(), workspaceId, projectId, spaceId, userId);
        jdbc.update("""
            insert into issues(
              id,workspace_id,project_id,issue_key,issue_type,title,description,
              priority,status,assignee_id,reporter_id,created_by,created_at,updated_by,updated_at
            ) values (?,?,?,?, 'task','Legacy task','legacy issue','high','open',?,?,?,now(),?,now())
            """, issueId, workspaceId, projectId, "MIG-" + suffix + "-1",
            userId, userId, userId, userId);
        UUID commentId = UUID.randomUUID();
        jdbc.update("""
            insert into issue_comments(
              id,workspace_id,issue_id,author_id,content,created_at
            ) values (?,?,?,?, 'legacy comment',now())
            """, commentId, workspaceId, issueId, userId);
        jdbc.update("""
            insert into issue_activity_logs(
              id,workspace_id,issue_id,actor_id,action,from_value,to_value,metadata,created_at
            ) values (?,?,?,?, 'priority_changed','medium','high','{}'::jsonb,now())
            """, UUID.randomUUID(), workspaceId, issueId, userId);
        if (withAttachment) {
            UUID fileId = UUID.randomUUID();
            jdbc.update("""
                insert into files(
                  id,workspace_id,object_key,original_name,content_type,size_bytes,
                  status,uploaded_by,created_at,completed_at
                ) values (?,?,?,?, 'text/plain',12,'active',?,now(),now())
                """, fileId, workspaceId, "migration/" + fileId, "evidence.txt", userId);
            jdbc.update("""
                insert into issue_attachments(
                  id,workspace_id,issue_id,file_id,created_by,created_at
                ) values (?,?,?,?,?,now())
                """, UUID.randomUUID(), workspaceId, issueId, fileId, userId);
        } else {
            // Keep every fixture's expected source count deterministic with a real attachment.
            UUID fileId = UUID.randomUUID();
            jdbc.update("""
                insert into files(
                  id,workspace_id,object_key,original_name,content_type,size_bytes,
                  status,uploaded_by,created_at,completed_at
                ) values (?,?,?,?, 'text/plain',12,'active',?,now(),now())
                """, fileId, workspaceId, "migration/" + fileId, "evidence.txt", userId);
            jdbc.update("""
                insert into issue_attachments(
                  id,workspace_id,issue_id,file_id,created_by,created_at
                ) values (?,?,?,?,?,now())
                """, UUID.randomUUID(), workspaceId, issueId, fileId, userId);
        }
        CurrentUser admin = new CurrentUser(
            userId, workspaceId, UUID.randomUUID(), "migration_" + suffix,
            "Migration " + label, Set.of("admin"), Set.of("project.manage")
        );
        return new Fixture(workspaceId, userId, spaceId, projectId, issueId, admin);
    }

    private void createPublishedType(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String typeKey
    ) throws Exception {
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        JsonNode snapshot = objectMapper.readTree("""
            {
              "snapshotSchemaVersion":1,
              "typeDefinition":{
                "id":"%s","workspaceId":"%s","spaceId":"%s","typeKey":"%s",
                "name":"%s","icon":"","description":"","sortOrder":0,
                "status":"active","system":true
              },
              "fields":[
                {"id":"%s","fieldKey":"description","name":"Description","fieldType":"text",
                 "config":{"schemaVersion":1,"required":false,"validationRules":[]},
                 "sortOrder":0,"status":"active","system":true,"options":[]},
                {"id":"%s","fieldKey":"priority","name":"Priority","fieldType":"text",
                 "config":{"schemaVersion":1,"required":false,"validationRules":[]},
                 "sortOrder":1,"status":"active","system":true,"options":[]},
                {"id":"%s","fieldKey":"due_at","name":"Due","fieldType":"date",
                 "config":{"schemaVersion":1,"required":false,"validationRules":[]},
                 "sortOrder":2,"status":"active","system":true,"options":[]},
                {"id":"%s","fieldKey":"assignee","name":"Assignee","fieldType":"user",
                 "config":{"schemaVersion":1,"required":false,"validationRules":[],
                           "typeConfig":{"maxSelections":10}},
                 "sortOrder":3,"status":"active","system":true,"options":[]}
              ],
              "layouts":[
                {"id":"%s","layoutKind":"create","status":"active","nodes":[],"policies":[]},
                {"id":"%s","layoutKind":"detail","status":"active","nodes":[],"policies":[]}
              ]
            }
            """.formatted(
                typeId, workspaceId, spaceId, typeKey, typeKey,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()
            ));
        var canonical = snapshotCanonicalizer.canonicalize(snapshot);
        transactionTemplate.executeWithoutResult(status -> {
            jdbc.update("""
                insert into project_work_item_types(
                  id,workspace_id,space_id,type_key,name,icon,description,sort_order,status,
                  is_system,current_version_id,created_by,created_at,updated_by,updated_at,
                  aggregate_version
                ) values (?,?,?,?,?,'','',0,'active',true,?,?,now(),?,now(),0)
                """, typeId, workspaceId, spaceId, typeKey, typeKey,
                versionId, actorId, actorId);
            jdbc.update("""
                insert into project_work_item_type_versions(
                  id,workspace_id,space_id,type_definition_id,version_number,config_hash,
                  status,config,created_by,created_at,published_by,published_at,
                  snapshot_schema_version
                ) values (?,?,?,?,1,?,'published',?::jsonb,?,now(),?,now(),1)
                """, versionId, workspaceId, spaceId, typeId, canonical.configHash(),
                canonical.payload().toString(), actorId, actorId);
        });
    }

    private int count(String table, UUID workspaceId) {
        return jdbc.queryForObject(
            "select count(*) from " + table + " where workspace_id=?",
            Integer.class,
            workspaceId
        );
    }

    private record Fixture(
        UUID workspaceId,
        UUID userId,
        UUID spaceId,
        UUID projectId,
        UUID issueId,
        CurrentUser admin
    ) {
    }
}
