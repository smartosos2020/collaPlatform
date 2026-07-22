package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationBatchRecord;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationVerificationReport;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.VerifiedSpaceMatch;
import com.colla.platform.modules.project.infrastructure.ProjectLegacyMappingRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class ProjectSpaceMigrationServiceIntegrationTests {
    private static final List<String> LEGACY_TABLES = List.of(
        "projects", "project_members", "conversations", "conversation_members"
    );

    @Autowired
    private ProjectSpaceMigrationService migrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ProjectLegacyMappingRepository legacyMappingRepository;

    @Test
    void dryRunWritesOnlyTheBatchRow() {
        WorkspaceAdmin admin = createWorkspaceAdmin("dr");
        UUID owner = insertUser(admin.workspaceId(), "drown", "active", false);
        UUID member = insertUser(admin.workspaceId(), "drmem", "active", false);
        UUID viewer = insertUser(admin.workspaceId(), "drvue", "active", false);
        UUID boss = insertUser(admin.workspaceId(), "drbos", "active", false);
        UUID orphan = UUID.randomUUID();
        String projectKey = "DR-" + suffix();
        UUID projectId = insertProject(admin.workspaceId(), projectKey, false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, member, "member", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, viewer, "viewer", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, boss, "boss", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, orphan, "member", false, admin.adminUserId());

        MigrationBatchRecord batch = migrationService.dryRun(adminUser(admin));

        assertThat(countRows("project_spaces", admin.workspaceId())).isZero();
        assertThat(countRows("project_space_members", admin.workspaceId())).isZero();
        assertThat(countRows("project_space_role_assignments", admin.workspaceId())).isZero();
        assertThat(countRows("project_legacy_space_maps", admin.workspaceId())).isZero();

        assertThat(batch.dryRun()).isTrue();
        assertThat(batch.status()).isEqualTo("completed");
        assertThat(batch.sourceChecksum()).isNotBlank();
        assertThat(batch.sourceWatermark()).isNotNull();
        assertThat(batch.finishedAt()).isNotNull();
        assertThat(batch.resultChecksum()).isNull();
        assertThat(batch.summary().get("mode")).isEqualTo("dry_run");

        Map<String, Object> counts = summaryCounts(batch);
        assertThat(countValue(counts, "total")).isEqualTo(1);
        assertThat(countValue(counts, "memberFailures")).isEqualTo(2);

        List<Map<String, Object>> projects = summaryProjects(batch);
        assertThat(projects).hasSize(1);
        Map<String, Object> projectDetail = projects.get(0);
        assertThat(projectDetail.get("projectId")).isEqualTo(projectId.toString());
        assertThat(projectDetail.get("decision")).isEqualTo("CREATE_NEW");
        assertThat(projectDetail.get("resolvedSpaceKey")).isEqualTo(projectKey.toLowerCase());
        assertThat(projectDetail.get("deterministicSpaceId")).isEqualTo(deterministicSpaceId(projectId).toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> memberMappings = (List<Map<String, Object>>) projectDetail.get("memberMappings");
        assertThat(memberMappings).hasSize(3);
        assertThat(memberMappings)
            .anySatisfy(mapping -> {
                assertThat(mapping.get("userId")).isEqualTo(viewer.toString());
                assertThat(mapping.get("targetRole")).isEqualTo("guest");
            });
        assertThat(batch.failures())
            .anySatisfy(failure -> {
                assertThat(failure.get("scope")).isEqualTo("member");
                assertThat(failure.get("userId")).isEqualTo(boss.toString());
                assertThat(failure.get("reason")).isEqualTo("UNKNOWN_ROLE");
            })
            .anySatisfy(failure -> {
                assertThat(failure.get("scope")).isEqualTo("member");
                assertThat(failure.get("userId")).isEqualTo(orphan.toString());
                assertThat(failure.get("reason")).isEqualTo("ORPHAN_USER");
            });
        assertThat(auditCount(admin.workspaceId(), "project_migration.dry_run", batch.id())).isEqualTo(1);
    }

    @Test
    void executeCreatesSpacesMembersAndMaps() {
        WorkspaceAdmin admin = createWorkspaceAdmin("ex");
        UUID ownerA = insertUser(admin.workspaceId(), "exowa", "active", false);
        UUID ownerB = insertUser(admin.workspaceId(), "exowb", "active", false);
        UUID member = insertUser(admin.workspaceId(), "exmem", "active", false);
        UUID viewer = insertUser(admin.workspaceId(), "exvue", "active", false);
        String projectKey = "EX-" + suffix();
        UUID projectId = insertProject(admin.workspaceId(), projectKey, false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, ownerA, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, ownerB, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, member, "member", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, viewer, "viewer", false, admin.adminUserId());

        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));

        assertThat(batch.status()).isEqualTo("completed");
        assertThat(batch.dryRun()).isFalse();
        assertThat(batch.resultChecksum()).isNotBlank();
        assertThat(batch.finishedAt()).isNotNull();
        Map<String, Object> counts = summaryCounts(batch);
        assertThat(countValue(counts, "created")).isEqualTo(1);
        assertThat(countValue(counts, "reused")).isZero();
        assertThat(countValue(counts, "failed")).isZero();
        assertThat(countValue(counts, "skipped")).isZero();

        UUID expectedSpaceId = deterministicSpaceId(projectId);
        Map<String, Object> space = jdbcTemplate.queryForMap(
            """
                select id, space_key, name, status, visibility, created_by, updated_by
                  from project_spaces where workspace_id = ? and id = ?
                """,
            admin.workspaceId(), expectedSpaceId
        );
        assertThat(space.get("space_key")).isEqualTo(projectKey.toLowerCase());
        assertThat(space.get("name")).isEqualTo("Migration " + projectKey);
        assertThat(space.get("status")).isEqualTo("active");
        assertThat(space.get("visibility")).isEqualTo("private");
        assertThat(space.get("created_by")).isEqualTo(admin.adminUserId());
        assertThat(space.get("updated_by")).isEqualTo(admin.adminUserId());

        assertThat(spaceMembers(admin.workspaceId(), expectedSpaceId))
            .containsExactlyInAnyOrderEntriesOf(Map.of(
                ownerA, "owner",
                ownerB, "owner",
                member, "member",
                viewer, "guest"
            ));

        Map<String, Object> map = jdbcTemplate.queryForMap(
            """
                select space_id, mapping_version, mapping_status, source_checksum, batch_id, mapped_by
                  from project_legacy_space_maps where workspace_id = ? and legacy_project_id = ?
                """,
            admin.workspaceId(), projectId
        );
        assertThat(map.get("space_id")).isEqualTo(expectedSpaceId);
        assertThat(map.get("mapping_version")).isEqualTo(1);
        assertThat(map.get("mapping_status")).isEqualTo("active");
        assertThat(map.get("source_checksum")).isNotNull();
        assertThat(map.get("batch_id")).isEqualTo(batch.id());
        assertThat(map.get("mapped_by")).isEqualTo(admin.adminUserId());

        Long objectLinks = jdbcTemplate.queryForObject(
            """
                select count(*) from object_links
                 where workspace_id = ? and object_type = 'project_space' and object_id = ? and deleted_at is null
                """,
            Long.class, admin.workspaceId(), expectedSpaceId
        );
        assertThat(objectLinks).isEqualTo(1);
        assertThat(auditCount(admin.workspaceId(), "project_migration.executed", batch.id())).isEqualTo(1);
    }

    @Test
    void executeIsIdempotentAcrossBatches() {
        WorkspaceAdmin admin = createWorkspaceAdmin("ii");
        UUID owner = insertUser(admin.workspaceId(), "iiown", "active", false);
        UUID member = insertUser(admin.workspaceId(), "iimem", "active", false);
        UUID viewer = insertUser(admin.workspaceId(), "iivue", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "II-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, member, "member", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, viewer, "viewer", false, admin.adminUserId());

        MigrationBatchRecord first = migrationService.execute(adminUser(admin));
        MigrationBatchRecord second = migrationService.execute(adminUser(admin));

        assertThat(first.status()).isEqualTo("completed");
        assertThat(second.status()).isEqualTo("completed");
        Map<String, Object> secondCounts = summaryCounts(second);
        assertThat(countValue(secondCounts, "created")).isZero();
        assertThat(countValue(secondCounts, "reused")).isEqualTo(1);
        assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(1);
        assertThat(countRows("project_space_members", admin.workspaceId())).isEqualTo(3);
        assertThat(countRows("project_space_role_assignments", admin.workspaceId())).isEqualTo(3);
        assertThat(countRows("project_legacy_space_maps", admin.workspaceId())).isEqualTo(1);
        UUID mapBatchId = jdbcTemplate.queryForObject(
            "select batch_id from project_legacy_space_maps where workspace_id = ? and legacy_project_id = ?",
            UUID.class, admin.workspaceId(), projectId
        );
        assertThat(mapBatchId).isEqualTo(first.id());
        assertThat(first.resultChecksum()).isEqualTo(second.resultChecksum());
    }

    @Test
    void legacyTablesRemainFrozenAcrossExecuteAndRollback() {
        WorkspaceAdmin admin = createWorkspaceAdmin("fz");
        UUID owner = insertUser(admin.workspaceId(), "fzown", "active", false);
        UUID member = insertUser(admin.workspaceId(), "fzmem", "active", false);
        UUID conversationId = insertConversation(admin.workspaceId(), "project", false, admin.adminUserId());
        insertConversationMember(admin.workspaceId(), conversationId, owner);
        insertConversationMember(admin.workspaceId(), conversationId, member);
        UUID projectId = insertProjectWithConversation(
            admin.workspaceId(), "FZ-" + suffix(), conversationId, false, admin.adminUserId()
        );
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, member, "member", false, admin.adminUserId());

        Map<String, String> beforeExecute = legacyHashes(admin.workspaceId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));
        assertThat(legacyHashes(admin.workspaceId())).isEqualTo(beforeExecute);

        migrationService.rollback(adminUser(admin), batch.id());
        assertThat(legacyHashes(admin.workspaceId())).isEqualTo(beforeExecute);
    }

    @Test
    void recordsFailuresWithoutPromotionAndSkipsOwnerlessProjects() {
        WorkspaceAdmin admin = createWorkspaceAdmin("fj");
        UUID owner = insertUser(admin.workspaceId(), "fjown", "active", false);
        UUID boss = insertUser(admin.workspaceId(), "fjbos", "active", false);
        UUID orphanMember = UUID.randomUUID();
        UUID orphanOwner = UUID.randomUUID();
        UUID healthyProject = insertProject(admin.workspaceId(), "FJ1-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), healthyProject, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), healthyProject, boss, "boss", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), healthyProject, orphanMember, "member", false, admin.adminUserId());
        UUID ownerlessProject = insertProject(admin.workspaceId(), "FJ2-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), ownerlessProject, orphanOwner, "owner", false, admin.adminUserId());

        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));

        assertThat(batch.status()).isEqualTo("failed");
        assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(1);
        assertThat(countRows("project_legacy_space_maps", admin.workspaceId())).isEqualTo(1);

        UUID healthySpaceId = deterministicSpaceId(healthyProject);
        assertThat(spaceMembers(admin.workspaceId(), healthySpaceId))
            .containsExactlyInAnyOrderEntriesOf(Map.of(owner, "owner"));

        assertThat(batch.failures())
            .anySatisfy(failure -> {
                assertThat(failure.get("scope")).isEqualTo("member");
                assertThat(failure.get("projectId")).isEqualTo(healthyProject.toString());
                assertThat(failure.get("userId")).isEqualTo(boss.toString());
                assertThat(failure.get("reason")).isEqualTo("UNKNOWN_ROLE");
            })
            .anySatisfy(failure -> {
                assertThat(failure.get("scope")).isEqualTo("member");
                assertThat(failure.get("projectId")).isEqualTo(healthyProject.toString());
                assertThat(failure.get("userId")).isEqualTo(orphanMember.toString());
                assertThat(failure.get("reason")).isEqualTo("ORPHAN_USER");
            })
            .anySatisfy(failure -> {
                assertThat(failure.get("scope")).isEqualTo("project");
                assertThat(failure.get("projectId")).isEqualTo(ownerlessProject.toString());
                assertThat(failure.get("reason")).isEqualTo("NO_VALID_OWNER");
            });
        Map<String, Object> counts = summaryCounts(batch);
        assertThat(countValue(counts, "created")).isEqualTo(1);
        assertThat(countValue(counts, "skipped")).isEqualTo(1);
    }

    @Test
    void resumeMigratesOnlyMissingProjectsAndCompletes() {
        WorkspaceAdmin admin = createWorkspaceAdmin("rs");
        UUID ownerA = insertUser(admin.workspaceId(), "rsowa", "active", false);
        UUID orphanOwner = UUID.randomUUID();
        UUID projectA = insertProject(admin.workspaceId(), "RS1-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectA, ownerA, "owner", false, admin.adminUserId());
        UUID projectB = insertProject(admin.workspaceId(), "RS2-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectB, orphanOwner, "owner", false, admin.adminUserId());

        MigrationBatchRecord failed = migrationService.execute(adminUser(admin));
        assertThat(failed.status()).isEqualTo("failed");
        assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(1);

        UUID ownerB = insertUser(admin.workspaceId(), "rsowb", "active", false);
        insertProjectMember(admin.workspaceId(), projectB, ownerB, "owner", false, admin.adminUserId());

        MigrationBatchRecord resumed = migrationService.resume(adminUser(admin), failed.id());

        assertThat(resumed.id()).isEqualTo(failed.id());
        assertThat(resumed.status()).isEqualTo("completed");
        Map<String, Object> counts = summaryCounts(resumed);
        assertThat(countValue(counts, "created")).isEqualTo(1);
        assertThat(countValue(counts, "reused")).isEqualTo(1);
        assertThat(resumed.summary().get("attempt")).isEqualTo(2);
        assertThat(resumed.summary().get("resumed")).isEqualTo(true);

        UUID spaceA = deterministicSpaceId(projectA);
        UUID spaceB = deterministicSpaceId(projectB);
        assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(2);
        assertThat(countRows("project_legacy_space_maps", admin.workspaceId())).isEqualTo(2);
        assertThat(spaceMembers(admin.workspaceId(), spaceA))
            .containsExactlyInAnyOrderEntriesOf(Map.of(ownerA, "owner"));
        assertThat(spaceMembers(admin.workspaceId(), spaceB))
            .containsExactlyInAnyOrderEntriesOf(Map.of(ownerB, "owner"));
        assertThat(auditCount(admin.workspaceId(), "project_migration.resumed", failed.id())).isEqualTo(1);
    }

    @Test
    void concurrentExecuteSerializesWithinWorkspace() throws Exception {
        WorkspaceAdmin admin = createWorkspaceAdmin("cc");
        UUID owner = insertUser(admin.workspaceId(), "ccown", "active", false);
        UUID projectA = insertProject(admin.workspaceId(), "CC1-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectA, owner, "owner", false, admin.adminUserId());
        UUID projectB = insertProject(admin.workspaceId(), "CC2-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectB, owner, "owner", false, admin.adminUserId());
        CurrentUser caller = adminUser(admin);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MigrationBatchRecord> first = executor.submit(() -> {
                await(start);
                return migrationService.execute(caller);
            });
            Future<MigrationBatchRecord> second = executor.submit(() -> {
                await(start);
                return migrationService.execute(caller);
            });
            start.countDown();
            MigrationBatchRecord batchA = first.get(120, TimeUnit.SECONDS);
            MigrationBatchRecord batchB = second.get(120, TimeUnit.SECONDS);

            assertThat(batchA.id()).isNotEqualTo(batchB.id());
            assertThat(batchA.status()).isEqualTo("completed");
            assertThat(batchB.status()).isEqualTo("completed");
            long createdTotal = countValue(summaryCounts(batchA), "created")
                + countValue(summaryCounts(batchB), "created");
            long reusedTotal = countValue(summaryCounts(batchA), "reused")
                + countValue(summaryCounts(batchB), "reused");
            assertThat(createdTotal).isEqualTo(2);
            assertThat(reusedTotal).isEqualTo(2);
            assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(2);
            assertThat(countRows("project_legacy_space_maps", admin.workspaceId())).isEqualTo(2);
            Long distinctSpaces = jdbcTemplate.queryForObject(
                "select count(distinct space_id) from project_legacy_space_maps where workspace_id = ?",
                Long.class, admin.workspaceId()
            );
            assertThat(distinctSpaces).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void verifyReportsMatchesAndMemberDrift() {
        WorkspaceAdmin admin = createWorkspaceAdmin("vf");
        UUID owner = insertUser(admin.workspaceId(), "vfown", "active", false);
        UUID member = insertUser(admin.workspaceId(), "vfmem", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "VF-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, member, "member", false, admin.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));
        UUID spaceId = deterministicSpaceId(projectId);

        MigrationVerificationReport clean = migrationService.verify(adminUser(admin), batch.id());

        assertThat(clean.allMatched()).isTrue();
        assertThat(clean.matches()).containsExactly(new VerifiedSpaceMatch(projectId, spaceId));
        assertThat(clean.mismatches()).isEmpty();

        jdbcTemplate.update(
            """
                delete from project_space_role_assignments
                 where workspace_id = ? and space_id = ?
                   and member_id in (select id from project_space_members where workspace_id = ? and space_id = ? and user_id = ?)
                """,
            admin.workspaceId(), spaceId, admin.workspaceId(), spaceId, member
        );
        jdbcTemplate.update(
            "delete from project_space_members where workspace_id = ? and space_id = ? and user_id = ?",
            admin.workspaceId(), spaceId, member
        );

        MigrationVerificationReport drifted = migrationService.verify(adminUser(admin), batch.id());

        assertThat(drifted.allMatched()).isFalse();
        assertThat(drifted.matches()).isEmpty();
        assertThat(drifted.mismatches()).hasSize(1);
        var mismatch = drifted.mismatches().get(0);
        assertThat(mismatch.projectId()).isEqualTo(projectId);
        assertThat(mismatch.spaceId()).isEqualTo(spaceId);
        assertThat(mismatch.differenceType()).isEqualTo("MEMBER_SET_MISMATCH");
        assertThat(mismatch.expected()).contains(member + ":member");
        assertThat(mismatch.actual()).doesNotContain(member.toString());

        String summaryJson = jdbcTemplate.queryForObject(
            "select summary::text from project_space_migration_batches where workspace_id = ? and id = ?",
            String.class, admin.workspaceId(), batch.id()
        );
        JsonNode summary = readJson(summaryJson);
        assertThat(summary.has("verification")).isTrue();
        assertThat(summary.get("verification").get("mismatched").asInt()).isEqualTo(1);
        assertThat(summary.get("verification").get("allMatched").asBoolean()).isFalse();
        assertThat(auditCount(admin.workspaceId(), "project_migration.verified", batch.id())).isEqualTo(2);
    }

    @Test
    void rollbackRemovesMigratedDataAndSupportsRemigration() {
        WorkspaceAdmin admin = createWorkspaceAdmin("rb");
        UUID owner = insertUser(admin.workspaceId(), "rbown", "active", false);
        UUID member = insertUser(admin.workspaceId(), "rbmem", "active", false);
        UUID projectA = insertProject(admin.workspaceId(), "RB1-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectA, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectA, member, "member", false, admin.adminUserId());
        UUID projectB = insertProject(admin.workspaceId(), "RB2-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectB, owner, "owner", false, admin.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));
        assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(2);
        Map<String, String> legacyBefore = legacyHashes(admin.workspaceId());

        MigrationBatchRecord rolledBack = migrationService.rollback(adminUser(admin), batch.id());

        assertThat(rolledBack.status()).isEqualTo("rolled_back");
        assertThat(rolledBack.rolledBackBy()).isEqualTo(admin.adminUserId());
        assertThat(rolledBack.rolledBackAt()).isNotNull();
        assertThat(countRows("project_spaces", admin.workspaceId())).isZero();
        assertThat(countRows("project_space_members", admin.workspaceId())).isZero();
        assertThat(countRows("project_space_role_assignments", admin.workspaceId())).isZero();
        List<Map<String, Object>> maps = jdbcTemplate.queryForList(
            """
                select mapping_status, space_id, rolled_back_by, rolled_back_at
                  from project_legacy_space_maps where workspace_id = ? order by legacy_project_id
                """,
            admin.workspaceId()
        );
        assertThat(maps).hasSize(2).allSatisfy(map -> {
            assertThat(map.get("mapping_status")).isEqualTo("rolled_back");
            assertThat(map.get("space_id")).isNull();
            assertThat(map.get("rolled_back_by")).isEqualTo(admin.adminUserId());
            assertThat(map.get("rolled_back_at")).isNotNull();
        });
        Long liveLinks = jdbcTemplate.queryForObject(
            """
                select count(*) from object_links
                 where workspace_id = ? and object_type = 'project_space' and deleted_at is null
                """,
            Long.class, admin.workspaceId()
        );
        assertThat(liveLinks).isZero();
        assertThat(legacyHashes(admin.workspaceId())).isEqualTo(legacyBefore);
        assertThat(auditCount(admin.workspaceId(), "project_migration.rolled_back", batch.id())).isEqualTo(1);

        MigrationBatchRecord remigrated = migrationService.execute(adminUser(admin));
        assertThat(remigrated.status()).isEqualTo("completed");
        assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(2);
        assertThat(spaceMembers(admin.workspaceId(), deterministicSpaceId(projectA)))
            .containsExactlyInAnyOrderEntriesOf(Map.of(owner, "owner", member, "member"));
        Integer activeMaps = jdbcTemplate.queryForObject(
            "select count(*) from project_legacy_space_maps where workspace_id = ? and mapping_status = 'active'",
            Integer.class, admin.workspaceId()
        );
        assertThat(activeMaps).isEqualTo(2);
        Integer mappingVersion = jdbcTemplate.queryForObject(
            "select mapping_version from project_legacy_space_maps where workspace_id = ? and legacy_project_id = ?",
            Integer.class, admin.workspaceId(), projectA
        );
        assertThat(mappingVersion).isEqualTo(2);
    }

    @Test
    void rollbackRejectsDryRunBatches() {
        WorkspaceAdmin admin = createWorkspaceAdmin("rd");
        UUID owner = insertUser(admin.workspaceId(), "rdown", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "RD-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        MigrationBatchRecord dryRun = migrationService.dryRun(adminUser(admin));

        assertThatThrownBy(() -> migrationService.rollback(adminUser(admin), dryRun.id()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
    }

    @Test
    void requiresManageProjectsPermissionForAllMethods() {
        WorkspaceAdmin admin = createWorkspaceAdmin("pm");
        CurrentUser member = new CurrentUser(
            UUID.randomUUID(), admin.workspaceId(), null, "plain", "Plain Member", Set.of("member"), Set.of()
        );
        UUID batchId = UUID.randomUUID();

        assertForbidden(() -> migrationService.dryRun(member));
        assertForbidden(() -> migrationService.execute(member));
        assertForbidden(() -> migrationService.resume(member, batchId));
        assertForbidden(() -> migrationService.verify(member, batchId));
        assertForbidden(() -> migrationService.rollback(member, batchId));
    }

    @Test
    void verifyRejectsDryRunBatch() {
        WorkspaceAdmin admin = createWorkspaceAdmin("vd");
        UUID owner = insertUser(admin.workspaceId(), "vdown", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "VD-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        MigrationBatchRecord dryRun = migrationService.dryRun(adminUser(admin));

        assertThatThrownBy(() -> migrationService.verify(adminUser(admin), dryRun.id()))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
    }

    @Test
    void unitFailureIsRecordedInManifestAndVerifyStaysHonest() {
        WorkspaceAdmin admin = createWorkspaceAdmin("uf");
        UUID ownerA = insertUser(admin.workspaceId(), "ufowa", "active", false);
        UUID ownerB = insertUser(admin.workspaceId(), "ufowb", "active", false);
        UUID projectA = insertProject(admin.workspaceId(), "UFA-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectA, ownerA, "owner", false, admin.adminUserId());
        UUID projectB = insertProject(admin.workspaceId(), "UFB-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectB, ownerB, "owner", false, admin.adminUserId());
        // Inject a mid-transaction fault: occupy project B's deterministic space id so its unit fails.
        jdbcTemplate.update(
            """
                insert into project_spaces (id, workspace_id, space_key, name, status, visibility,
                                            created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, 'injected conflict', 'active', 'private', ?, now(), ?, now())
                """,
            deterministicSpaceId(projectB), admin.workspaceId(), "uf-block-" + suffix(),
            admin.adminUserId(), admin.adminUserId()
        );

        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));

        assertThat(batch.status()).isEqualTo("failed");
        Map<String, Object> counts = summaryCounts(batch);
        assertThat(countValue(counts, "created")).isEqualTo(1);
        assertThat(countValue(counts, "failed")).isEqualTo(1);
        assertThat(batch.failures()).anySatisfy(failure -> {
            assertThat(failure.get("scope")).isEqualTo("project");
            assertThat(failure.get("projectId")).isEqualTo(projectB.toString());
            assertThat(failure.get("reason")).isEqualTo("UNIT_FAILED");
        });

        // Batch verification is manifest-anchored: the recorded outcome (A created, B failed) is
        // intact, so it succeeds honestly. The unmigrated project B is only visible in the
        // workspace convergence view as MAP_MISSING.
        MigrationVerificationReport batchReport = migrationService.verify(adminUser(admin), batch.id());
        assertThat(batchReport.allMatched()).isTrue();
        assertThat(batchReport.matches()).containsExactly(new VerifiedSpaceMatch(projectA, deterministicSpaceId(projectA)));
        assertThat(batchReport.mismatches()).isEmpty();

        String summaryBeforeConvergence = jdbcTemplate.queryForObject(
            "select summary::text from project_space_migration_batches where workspace_id = ? and id = ?",
            String.class, admin.workspaceId(), batch.id()
        );
        MigrationVerificationReport convergence = migrationService.verifyWorkspaceConvergence(adminUser(admin));
        assertThat(convergence.allMatched()).isFalse();
        assertThat(convergence.mismatches()).anySatisfy(mismatch -> {
            assertThat(mismatch.projectId()).isEqualTo(projectB);
            assertThat(mismatch.differenceType()).isEqualTo("MAP_MISSING");
        });
        assertThat(auditCount(admin.workspaceId(), "project_migration.convergence_verified", admin.workspaceId())).isEqualTo(1);
        String summaryAfterConvergence = jdbcTemplate.queryForObject(
            "select summary::text from project_space_migration_batches where workspace_id = ? and id = ?",
            String.class, admin.workspaceId(), batch.id()
        );
        // Convergence never writes into any batch summary.
        assertThat(summaryAfterConvergence).isEqualTo(summaryBeforeConvergence);

        jdbcTemplate.update(
            "delete from project_spaces where workspace_id = ? and id = ?",
            admin.workspaceId(), deterministicSpaceId(projectB)
        );
        MigrationBatchRecord resumed = migrationService.resume(adminUser(admin), batch.id());
        assertThat(resumed.status()).isEqualTo("completed");

        MigrationVerificationReport clean = migrationService.verify(adminUser(admin), batch.id());
        assertThat(clean.allMatched()).isTrue();
        assertThat(clean.matches()).containsExactlyInAnyOrder(
            new VerifiedSpaceMatch(projectA, deterministicSpaceId(projectA)),
            new VerifiedSpaceMatch(projectB, deterministicSpaceId(projectB))
        );
        assertThat(migrationService.verifyWorkspaceConvergence(adminUser(admin)).allMatched()).isTrue();

        assertThat(manifestProjects(resumed)).allSatisfy(item -> {
            assertThat(item.get("outcome")).isEqualTo("CREATED");
            assertThat(item.get("ownedByBatch")).isEqualTo(true);
        });
        migrationService.rollback(adminUser(admin), batch.id());
        MigrationBatchRecord successor = migrationService.execute(adminUser(admin));
        MigrationVerificationReport superseded = migrationService.verify(adminUser(admin), batch.id());
        assertThat(superseded.allMatched()).isFalse();
        assertThat(superseded.mismatches()).hasSize(2).allSatisfy(mismatch ->
            assertThat(mismatch.differenceType()).isEqualTo("MAP_SUPERSEDED")
        );
        migrationService.rollback(adminUser(admin), successor.id());
    }

    @Test
    void rerunVerifyReportsRealMatchesAcrossBatchOwnership() {
        WorkspaceAdmin admin = createWorkspaceAdmin("rv");
        UUID owner = insertUser(admin.workspaceId(), "rvown", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "RV-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());

        migrationService.execute(adminUser(admin));
        MigrationBatchRecord rerun = migrationService.execute(adminUser(admin));
        assertThat(countValue(summaryCounts(rerun), "reused")).isEqualTo(1);

        MigrationVerificationReport report = migrationService.verify(adminUser(admin), rerun.id());
        assertThat(report.allMatched()).isTrue();
        assertThat(report.matches()).containsExactly(new VerifiedSpaceMatch(projectId, deterministicSpaceId(projectId)));
        assertThat(report.mismatches()).isEmpty();
    }

    @Test
    void verifyFlagsMemberDriftWhenProjectLosesValidOwner() {
        WorkspaceAdmin admin = createWorkspaceAdmin("ux");
        UUID owner = insertUser(admin.workspaceId(), "uxown", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "UX-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));
        assertThat(migrationService.verify(adminUser(admin), batch.id()).allMatched()).isTrue();

        jdbcTemplate.update("update users set status = 'disabled', updated_at = now() where id = ?", owner);
        MigrationVerificationReport drifted = migrationService.verify(adminUser(admin), batch.id());

        assertThat(drifted.allMatched()).isFalse();
        assertThat(drifted.mismatches()).anySatisfy(mismatch -> {
            assertThat(mismatch.projectId()).isEqualTo(projectId);
            assertThat(mismatch.differenceType()).isEqualTo("MEMBER_SET_MISMATCH");
        });
        MigrationVerificationReport convergence = migrationService.verifyWorkspaceConvergence(adminUser(admin));
        assertThat(convergence.allMatched()).isFalse();
        assertThat(convergence.mismatches()).anySatisfy(mismatch -> {
            assertThat(mismatch.projectId()).isEqualTo(projectId);
            assertThat(mismatch.differenceType()).isEqualTo("MAP_UNEXPECTED");
        });
    }

    @Test
    void rolledBackBatchVerificationFailsAfterLaterBatchRemigrates() {
        WorkspaceAdmin admin = createWorkspaceAdmin("sb");
        UUID owner = insertUser(admin.workspaceId(), "sbown", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "SB-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());

        MigrationBatchRecord batchA = migrationService.execute(adminUser(admin));
        migrationService.rollback(adminUser(admin), batchA.id());
        MigrationVerificationReport rolledBackReport = migrationService.verify(adminUser(admin), batchA.id());
        assertThat(rolledBackReport.allMatched()).isTrue();

        MigrationBatchRecord batchB = migrationService.execute(adminUser(admin));
        MigrationVerificationReport superseded = migrationService.verify(adminUser(admin), batchA.id());

        assertThat(superseded.allMatched()).isFalse();
        assertThat(superseded.mismatches()).anySatisfy(mismatch -> {
            assertThat(mismatch.projectId()).isEqualTo(projectId);
            assertThat(mismatch.differenceType()).isEqualTo("MAP_SUPERSEDED");
        });
        MigrationVerificationReport current = migrationService.verify(adminUser(admin), batchB.id());
        assertThat(current.allMatched()).isTrue();
        assertThat(current.matches()).containsExactly(new VerifiedSpaceMatch(projectId, deterministicSpaceId(projectId)));

        migrationService.rollback(adminUser(admin), batchB.id());
    }

    @Test
    void crossWorkspaceBatchLinkIsRejectedByDatabase() {
        WorkspaceAdmin adminA = createWorkspaceAdmin("xa");
        UUID ownerA = insertUser(adminA.workspaceId(), "xaown", "active", false);
        UUID projectA = insertProject(adminA.workspaceId(), "XA-" + suffix(), false, adminA.adminUserId());
        insertProjectMember(adminA.workspaceId(), projectA, ownerA, "owner", false, adminA.adminUserId());
        MigrationBatchRecord batchA = migrationService.execute(adminUser(adminA));

        WorkspaceAdmin adminB = createWorkspaceAdmin("xb");
        UUID ownerB = insertUser(adminB.workspaceId(), "xbown", "active", false);
        UUID projectB = insertProject(adminB.workspaceId(), "XB-" + suffix(), false, adminB.adminUserId());
        insertProjectMember(adminB.workspaceId(), projectB, ownerB, "owner", false, adminB.adminUserId());
        migrationService.execute(adminUser(adminB));

        UUID batchAId = batchA.id();
        UUID workspaceBId = adminB.workspaceId();
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update project_legacy_space_maps set batch_id = ? where workspace_id = ?",
            batchAId, workspaceBId
        )).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void migrationServiceRejectsWriteCommittedBetweenPlanAndFingerprint() {
        WorkspaceAdmin admin = createWorkspaceAdmin("rr");
        UUID owner = insertUser(admin.workspaceId(), "rrown", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "RR-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        org.springframework.transaction.support.TransactionTemplate requiresNew =
            new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // findOccupiedSpaceKeys is the final query used to build the plan. Commit a legacy write
        // after that query returns and before the service starts its fingerprint queries.
        doAnswer(invocation -> {
            Object occupiedKeys = invocation.callRealMethod();
            requiresNew.executeWithoutResult(inner -> jdbcTemplate.update(
                "update project_members set project_role = 'member' where workspace_id = ? and project_id = ? and user_id = ?",
                admin.workspaceId(), projectId, owner
            ));
            return occupiedKeys;
        }).when(legacyMappingRepository).findOccupiedSpaceKeys(admin.workspaceId());

        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));

        // The fingerprint query remains on the plan's old REPEATABLE_READ snapshot. The unit query
        // sees the committed role change and rejects the stale plan instead of migrating it.
        assertThat(batch.status()).isEqualTo("failed");
        assertThat(batch.failures()).anySatisfy(failure -> {
            assertThat(failure.get("projectId")).isEqualTo(projectId.toString());
            assertThat(failure.get("reason")).isEqualTo("UNIT_FAILED");
            assertThat(failure.get("detail")).asString().contains("Legacy source changed");
        });
        assertThat(countRows("project_spaces", admin.workspaceId())).isZero();
        assertThat(countRows("project_legacy_space_maps", admin.workspaceId())).isZero();
        String committed = jdbcTemplate.queryForObject(
            "select project_role from project_members where workspace_id = ? and project_id = ? and user_id = ?",
            String.class, admin.workspaceId(), projectId, owner
        );
        assertThat(committed).isEqualTo("member");
    }

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void rollbackPreservesOriginalFailures() {
        WorkspaceAdmin admin = createWorkspaceAdmin("rp");
        UUID owner = insertUser(admin.workspaceId(), "rpown", "active", false);
        UUID boss = insertUser(admin.workspaceId(), "rpbos", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "RP-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, boss, "boss", false, admin.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));
        assertThat(batch.failures()).anySatisfy(failure ->
            assertThat(failure.get("reason")).isEqualTo("UNKNOWN_ROLE")
        );

        MigrationBatchRecord rolledBack = migrationService.rollback(adminUser(admin), batch.id());

        assertThat(rolledBack.status()).isEqualTo("rolled_back");
        assertThat(rolledBack.failures()).anySatisfy(failure -> {
            assertThat(failure.get("scope")).isEqualTo("member");
            assertThat(failure.get("userId")).isEqualTo(boss.toString());
            assertThat(failure.get("reason")).isEqualTo("UNKNOWN_ROLE");
        });
    }

    @Test
    void rollbackUnitFailureIsRecordedAndOriginalFailuresAccumulate() {
        WorkspaceAdmin admin = createWorkspaceAdmin("rf");
        UUID owner = insertUser(admin.workspaceId(), "rfown", "active", false);
        UUID boss = insertUser(admin.workspaceId(), "rfbos", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "RF-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, boss, "boss", false, admin.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));
        UUID spaceId = deterministicSpaceId(projectId);
        // Inject a rollback fault: a pending invitation blocks the space deletion.
        jdbcTemplate.update(
            """
                insert into project_space_invitations (id, workspace_id, space_id, invitee_user_id, role_key,
                                                       token_hash, status, expires_at, invited_by, invited_at)
                values (?, ?, ?, ?, 'member', ?, 'pending', now() + interval '3 days', ?, now())
                """,
            UUID.randomUUID(), admin.workspaceId(), spaceId, owner, "rf-token-" + suffix(), admin.adminUserId()
        );

        MigrationBatchRecord failedRollback = migrationService.rollback(adminUser(admin), batch.id());

        assertThat(failedRollback.status()).isEqualTo("failed");
        assertThat(failedRollback.failures())
            .anySatisfy(failure -> assertThat(failure.get("reason")).isEqualTo("ROLLBACK_FAILED"))
            .anySatisfy(failure -> assertThat(failure.get("reason")).isEqualTo("UNKNOWN_ROLE"));

        jdbcTemplate.update(
            "delete from project_space_invitations where workspace_id = ? and space_id = ?",
            admin.workspaceId(), spaceId
        );
        MigrationBatchRecord rolledBack = migrationService.rollback(adminUser(admin), batch.id());

        assertThat(rolledBack.status()).isEqualTo("rolled_back");
        assertThat(rolledBack.failures())
            .anySatisfy(failure -> assertThat(failure.get("reason")).isEqualTo("ROLLBACK_FAILED"))
            .anySatisfy(failure -> assertThat(failure.get("reason")).isEqualTo("UNKNOWN_ROLE"));
    }

    @Test
    void stuckRunningBatchCanBeResumed() {
        WorkspaceAdmin admin = createWorkspaceAdmin("sr");
        UUID owner = insertUser(admin.workspaceId(), "srown", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "SR-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));
        jdbcTemplate.update(
            "update project_space_migration_batches set status = 'running', finished_at = null where workspace_id = ? and id = ?",
            admin.workspaceId(), batch.id()
        );

        MigrationBatchRecord resumed = migrationService.resume(adminUser(admin), batch.id());

        assertThat(resumed.status()).isEqualTo("completed");
        assertThat(resumed.summary().get("attempt")).isEqualTo(2);
        assertThat(countValue(summaryCounts(resumed), "reused")).isEqualTo(1);
    }

    @Test
    void sourceFingerprintReflectsUserLifecycleChanges() {
        WorkspaceAdmin admin = createWorkspaceAdmin("fp");
        UUID owner = insertUser(admin.workspaceId(), "fpown", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "FP-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        MigrationBatchRecord before = migrationService.dryRun(adminUser(admin));

        jdbcTemplate.update("update users set status = 'disabled', updated_at = now() where id = ?", owner);
        MigrationBatchRecord after = migrationService.dryRun(adminUser(admin));

        assertThat(after.sourceChecksum()).isNotEqualTo(before.sourceChecksum());
        assertThat(after.sourceWatermark()).isAfter(before.sourceWatermark());
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Map<String, Object> summaryCounts(MigrationBatchRecord batch) {
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) batch.summary().get("counts");
        return counts;
    }

    private List<Map<String, Object>> summaryProjects(MigrationBatchRecord batch) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) batch.summary().get("projects");
        return projects;
    }

    private List<Map<String, Object>> manifestProjects(MigrationBatchRecord batch) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) batch.summary().get("manifestProjects");
        return projects;
    }

    private long countValue(Map<String, Object> counts, String key) {
        return ((Number) counts.get(key)).longValue();
    }

    private Map<UUID, String> spaceMembers(UUID workspaceId, UUID spaceId) {
        Map<UUID, String> members = new HashMap<>();
        jdbcTemplate.query(
            """
                select m.user_id, r.role_key
                  from project_space_members m
                  join project_space_role_assignments r on r.workspace_id = m.workspace_id
                       and r.space_id = m.space_id and r.member_id = m.id and r.revoked_at is null
                 where m.workspace_id = ? and m.space_id = ? and m.status = 'active'
                """,
            (org.springframework.jdbc.core.ResultSetExtractor<Void>) resultSet -> {
                while (resultSet.next()) {
                    members.put(
                        resultSet.getObject("user_id", UUID.class),
                        resultSet.getString("role_key")
                    );
                }
                return null;
            },
            workspaceId, spaceId
        );
        return members;
    }

    private long countRows(String table, UUID workspaceId) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where workspace_id = ?",
            Long.class, workspaceId
        );
        return count == null ? 0 : count;
    }

    private long auditCount(UUID workspaceId, String action, UUID targetId) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from audit_logs where workspace_id = ? and action = ? and target_id = ?",
            Long.class, workspaceId, action, targetId
        );
        return count == null ? 0 : count;
    }

    private Map<String, String> legacyHashes(UUID workspaceId) {
        Map<String, String> hashes = new HashMap<>();
        for (String table : LEGACY_TABLES) {
            hashes.put(table, tableHash(table, workspaceId));
        }
        return hashes;
    }

    private String tableHash(String table, UUID workspaceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "select * from " + table + " where workspace_id = ? order by id",
            workspaceId
        );
        StringBuilder content = new StringBuilder();
        for (Map<String, Object> row : rows) {
            row.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> content.append(entry.getKey()).append('=').append(entry.getValue()).append('|'));
            content.append('\n');
        }
        return sha256(content.toString());
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse json in test", exception);
        }
    }

    private UUID deterministicSpaceId(UUID projectId) {
        return UUID.nameUUIDFromBytes(
            ("colla:project-legacy-space:" + projectId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private CurrentUser adminUser(WorkspaceAdmin admin) {
        return new CurrentUser(
            admin.adminUserId(), admin.workspaceId(), null,
            admin.adminUsername(), "Migration Batch Admin", Set.of("admin"), Set.of()
        );
    }

    private WorkspaceAdmin createWorkspaceAdmin(String usernamePrefix) {
        UUID workspaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            workspaceId, "Migration Batch Workspace", "mig-batch-" + suffix()
        );
        UUID adminId = UUID.randomUUID();
        String username = usernamePrefix + suffix();
        UUID seedAdminId = jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
        jdbcTemplate.update(
            """
                insert into users (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                select ?, ?, ?, password_hash, 'Migration Batch Admin', 'active', now(), now()
                  from users where id = ?
                """,
            adminId, workspaceId, username, seedAdminId
        );
        UUID roleId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into roles (id, workspace_id, code, name, scope, is_builtin, created_at, updated_at)
                values (?, ?, 'admin', 'Administrator', 'system', true, now(), now())
                """,
            roleId, workspaceId
        );
        jdbcTemplate.update(
            "insert into user_roles (id, workspace_id, user_id, role_id, created_by, created_at) values (?, ?, ?, ?, ?, now())",
            UUID.randomUUID(), workspaceId, adminId, roleId, adminId
        );
        return new WorkspaceAdmin(workspaceId, adminId, username);
    }

    private UUID insertUser(UUID workspaceId, String usernamePrefix, String status, boolean deleted) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into users (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at, deleted_at)
                values (?, ?, ?, 'unused-test-hash', ?, ?, now(), now(), case when ? then now() else null end)
                """,
            userId, workspaceId, usernamePrefix + suffix(), "Migration User " + usernamePrefix, status, deleted
        );
        return userId;
    }

    private UUID insertProject(UUID workspaceId, String projectKey, boolean archived, UUID actorId) {
        return insertProjectWithConversation(workspaceId, projectKey, null, archived, actorId);
    }

    private UUID insertProjectWithConversation(UUID workspaceId, String projectKey, UUID conversationId, boolean archived, UUID actorId) {
        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into projects (id, workspace_id, project_key, name, description, status, conversation_id,
                                      created_by, created_at, updated_by, updated_at, archived_at)
                values (?, ?, ?, ?, 'migration batch test', 'active', ?, ?, now(), ?, now(), case when ? then now() else null end)
                """,
            projectId, workspaceId, projectKey, "Migration " + projectKey, conversationId, actorId, actorId, archived
        );
        return projectId;
    }

    private void insertProjectMember(UUID workspaceId, UUID projectId, UUID userId, String role, boolean archived, UUID actorId) {
        jdbcTemplate.update(
            """
                insert into project_members (id, workspace_id, project_id, user_id, project_role, joined_at, created_by, archived_at)
                values (?, ?, ?, ?, ?, now(), ?, case when ? then now() else null end)
                """,
            UUID.randomUUID(), workspaceId, projectId, userId, role, actorId, archived
        );
    }

    private UUID insertConversation(UUID workspaceId, String type, boolean archived, UUID actorId) {
        UUID conversationId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into conversations (id, workspace_id, conversation_type, title, owner_id, created_by, created_at, updated_at, archived_at)
                values (?, ?, ?, 'Migration Conversation', ?, ?, now(), now(), case when ? then now() else null end)
                """,
            conversationId, workspaceId, type, actorId, actorId, archived
        );
        return conversationId;
    }

    private void insertConversationMember(UUID workspaceId, UUID conversationId, UUID userId) {
        jdbcTemplate.update(
            """
                insert into conversation_members (id, workspace_id, conversation_id, user_id, member_role, joined_at)
                values (?, ?, ?, ?, 'member', now())
                """,
            UUID.randomUUID(), workspaceId, conversationId, userId
        );
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record WorkspaceAdmin(UUID workspaceId, UUID adminUserId, String adminUsername) {
    }
}
