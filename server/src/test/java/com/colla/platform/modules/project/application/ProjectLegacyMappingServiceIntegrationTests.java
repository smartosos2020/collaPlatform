package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.LegacySpaceMappingPlan;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.MemberFailureReason;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.ProjectFailureReason;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.ProjectMappingPlan;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.SpaceMappingDecision;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class ProjectLegacyMappingServiceIntegrationTests {
    @Autowired
    private ProjectLegacyMappingService projectLegacyMappingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void plansCreateNewWithNormalizedKeyAndMemberMappings() {
        WorkspaceAdmin admin = createWorkspaceAdmin("cn");
        UUID owner = insertUser(admin.workspaceId(), "cown", "active", false);
        UUID member = insertUser(admin.workspaceId(), "cmem", "active", false);
        UUID viewer = insertUser(admin.workspaceId(), "cvue", "active", false);
        String projectKey = "ABC Def_123 " + suffix();
        UUID projectId = insertProject(admin.workspaceId(), projectKey, false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, member, "member", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, viewer, "viewer", false, admin.adminUserId());
        UUID archivedProjectId = insertProject(admin.workspaceId(), "ARCH-" + suffix(), true, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), archivedProjectId, member, "member", false, admin.adminUserId());

        LegacySpaceMappingPlan plan = projectLegacyMappingService.planWorkspaceMigration(adminUser(admin));

        assertThat(plan.workspaceId()).isEqualTo(admin.workspaceId());
        assertThat(plan.generatedAt()).isNotNull();
        assertThat(plan.totalProjects()).isEqualTo(1);
        assertThat(plan.migratableProjects()).isEqualTo(1);
        assertThat(plan.reuseCount()).isZero();
        assertThat(plan.keyConflictCount()).isZero();
        assertThat(plan.memberFailureCount()).isZero();
        assertThat(plan.projectFailureCount()).isZero();
        assertThat(plan.plans()).noneMatch(item -> item.projectId().equals(archivedProjectId));

        ProjectMappingPlan projectPlan = onlyPlan(plan, projectId);
        assertThat(projectPlan.projectKey()).isEqualTo(projectKey);
        assertThat(projectPlan.decision()).isEqualTo(SpaceMappingDecision.CREATE_NEW);
        assertThat(projectPlan.resolvedSpaceKey()).isEqualTo(normalize(projectKey));
        assertThat(projectPlan.resolvedSpaceKey()).matches("[a-z0-9][a-z0-9-]*");
        assertThat(projectPlan.resolvedSpaceId()).isNull();
        assertThat(projectPlan.deterministicSpaceId()).isEqualTo(deterministicSpaceId(projectId));
        assertThat(projectPlan.projectFailure()).isNull();
        assertThat(projectPlan.memberMappings()).hasSize(3);
        assertThat(projectPlan.memberMappings())
            .anySatisfy(mapping -> {
                assertThat(mapping.userId()).isEqualTo(owner);
                assertThat(mapping.legacyRole()).isEqualTo("owner");
                assertThat(mapping.targetRole()).isEqualTo("owner");
                assertThat(mapping.explanation()).contains("owner");
            })
            .anySatisfy(mapping -> {
                assertThat(mapping.userId()).isEqualTo(member);
                assertThat(mapping.legacyRole()).isEqualTo("member");
                assertThat(mapping.targetRole()).isEqualTo("member");
            })
            .anySatisfy(mapping -> {
                assertThat(mapping.userId()).isEqualTo(viewer);
                assertThat(mapping.legacyRole()).isEqualTo("viewer");
                assertThat(mapping.targetRole()).isEqualTo("guest");
            });
        assertThat(projectPlan.memberFailures()).isEmpty();
    }

    @Test
    void suffixesKeyConflictsDeterministically() {
        WorkspaceAdmin admin = createWorkspaceAdmin("kc");
        UUID owner = insertUser(admin.workspaceId(), "kown", "active", false);
        String activeKey = "taken-" + suffix();
        String archivedKey = "gone-" + suffix();
        insertSpace(admin.workspaceId(), activeKey, "active", admin.adminUserId());
        insertSpace(admin.workspaceId(), archivedKey, "archived", admin.adminUserId());
        UUID activeConflictProject = insertProject(admin.workspaceId(), activeKey.toUpperCase(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), activeConflictProject, owner, "owner", false, admin.adminUserId());
        UUID archivedConflictProject = insertProject(admin.workspaceId(), archivedKey, false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), archivedConflictProject, owner, "owner", false, admin.adminUserId());

        LegacySpaceMappingPlan plan = projectLegacyMappingService.planWorkspaceMigration(adminUser(admin));

        assertThat(plan.keyConflictCount()).isEqualTo(2);
        assertThat(plan.migratableProjects()).isEqualTo(2);
        ProjectMappingPlan activePlan = onlyPlan(plan, activeConflictProject);
        assertThat(activePlan.decision()).isEqualTo(SpaceMappingDecision.KEY_CONFLICT_SUFFIXED);
        assertThat(activePlan.resolvedSpaceKey()).isEqualTo(activeKey + "-" + shortHex(activeConflictProject));
        assertThat(activePlan.resolvedSpaceKey()).hasSizeLessThanOrEqualTo(64);
        assertThat(activePlan.resolvedSpaceId()).isNull();
        ProjectMappingPlan archivedPlan = onlyPlan(plan, archivedConflictProject);
        assertThat(archivedPlan.decision()).isEqualTo(SpaceMappingDecision.KEY_CONFLICT_SUFFIXED);
        assertThat(archivedPlan.resolvedSpaceKey()).isEqualTo(archivedKey + "-" + shortHex(archivedConflictProject));
    }

    @Test
    void reusesActiveMappingsAndIgnoresInactiveOnes() {
        WorkspaceAdmin admin = createWorkspaceAdmin("ru");
        UUID owner = insertUser(admin.workspaceId(), "rown", "active", false);
        UUID mappedSpaceId = insertSpace(admin.workspaceId(), "mapped-" + suffix(), "active", admin.adminUserId());
        UUID rolledBackSpaceId = insertSpace(admin.workspaceId(), "rolled-" + suffix(), "active", admin.adminUserId());
        UUID reusedProject = insertProject(admin.workspaceId(), "REU-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), reusedProject, owner, "owner", false, admin.adminUserId());
        insertLegacyMap(admin.workspaceId(), reusedProject, mappedSpaceId, "active", admin.adminUserId());
        UUID rolledBackProject = insertProject(admin.workspaceId(), "RBK-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), rolledBackProject, owner, "owner", false, admin.adminUserId());
        insertLegacyMap(admin.workspaceId(), rolledBackProject, rolledBackSpaceId, "rolled_back", admin.adminUserId());

        LegacySpaceMappingPlan plan = projectLegacyMappingService.planWorkspaceMigration(adminUser(admin));

        assertThat(plan.reuseCount()).isEqualTo(1);
        ProjectMappingPlan reusedPlan = onlyPlan(plan, reusedProject);
        assertThat(reusedPlan.decision()).isEqualTo(SpaceMappingDecision.REUSE_EXISTING_MAP);
        assertThat(reusedPlan.resolvedSpaceId()).isEqualTo(mappedSpaceId);
        assertThat(reusedPlan.resolvedSpaceKey()).isNull();
        assertThat(reusedPlan.deterministicSpaceId()).isEqualTo(deterministicSpaceId(reusedProject));
        ProjectMappingPlan rolledBackPlan = onlyPlan(plan, rolledBackProject);
        assertThat(rolledBackPlan.decision()).isEqualTo(SpaceMappingDecision.CREATE_NEW);
        assertThat(rolledBackPlan.resolvedSpaceId()).isNull();
    }

    @Test
    void mapsEveryOwnerAndViewerToSpaceRoles() {
        WorkspaceAdmin admin = createWorkspaceAdmin("do");
        UUID ownerA = insertUser(admin.workspaceId(), "dowa", "active", false);
        UUID ownerB = insertUser(admin.workspaceId(), "dowb", "active", false);
        UUID viewer = insertUser(admin.workspaceId(), "dvue", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "DUAL-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, ownerA, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, ownerB, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, viewer, "viewer", false, admin.adminUserId());

        LegacySpaceMappingPlan plan = projectLegacyMappingService.planWorkspaceMigration(adminUser(admin));

        ProjectMappingPlan projectPlan = onlyPlan(plan, projectId);
        assertThat(projectPlan.memberMappings()).hasSize(3);
        assertThat(projectPlan.memberMappings())
            .filteredOn(mapping -> mapping.targetRole().equals("owner"))
            .extracting(mapping -> mapping.userId())
            .containsExactlyInAnyOrder(ownerA, ownerB);
        assertThat(projectPlan.memberMappings())
            .filteredOn(mapping -> mapping.targetRole().equals("guest"))
            .extracting(mapping -> mapping.userId())
            .containsExactly(viewer);
        assertThat(projectPlan.memberFailures()).isEmpty();
    }

    @Test
    void flagsUnknownRolesAsFailuresWithoutMapping() {
        WorkspaceAdmin admin = createWorkspaceAdmin("ur");
        UUID owner = insertUser(admin.workspaceId(), "uown", "active", false);
        UUID boss = insertUser(admin.workspaceId(), "ubos", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "UNK-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, boss, "boss", false, admin.adminUserId());

        LegacySpaceMappingPlan plan = projectLegacyMappingService.planWorkspaceMigration(adminUser(admin));

        assertThat(plan.memberFailureCount()).isEqualTo(1);
        ProjectMappingPlan projectPlan = onlyPlan(plan, projectId);
        assertThat(projectPlan.memberMappings()).hasSize(1);
        assertThat(projectPlan.memberMappings().get(0).userId()).isEqualTo(owner);
        assertThat(projectPlan.memberFailures()).hasSize(1);
        assertThat(projectPlan.memberFailures().get(0).userId()).isEqualTo(boss);
        assertThat(projectPlan.memberFailures().get(0).legacyRole()).isEqualTo("boss");
        assertThat(projectPlan.memberFailures().get(0).reason()).isEqualTo(MemberFailureReason.UNKNOWN_ROLE);
    }

    @Test
    void flagsOrphanUsersAsFailuresWithoutMapping() {
        WorkspaceAdmin admin = createWorkspaceAdmin("ou");
        UUID owner = insertUser(admin.workspaceId(), "oown", "active", false);
        UUID missingUserId = UUID.randomUUID();
        UUID deletedUserId = insertUser(admin.workspaceId(), "odel", "active", true);
        UUID disabledUserId = insertUser(admin.workspaceId(), "odis", "disabled", false);
        UUID foreignWorkspaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            foreignWorkspaceId, "Foreign Mapping Workspace", "mig-map-foreign-" + suffix()
        );
        UUID crossWorkspaceUserId = insertUser(foreignWorkspaceId, "oxws", "active", false);
        UUID projectId = insertProject(admin.workspaceId(), "ORP-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, missingUserId, "member", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, deletedUserId, "member", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, disabledUserId, "member", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectId, crossWorkspaceUserId, "member", false, admin.adminUserId());

        LegacySpaceMappingPlan plan = projectLegacyMappingService.planWorkspaceMigration(adminUser(admin));

        assertThat(plan.memberFailureCount()).isEqualTo(4);
        ProjectMappingPlan projectPlan = onlyPlan(plan, projectId);
        assertThat(projectPlan.memberMappings()).hasSize(1);
        assertThat(projectPlan.memberMappings().get(0).userId()).isEqualTo(owner);
        assertThat(projectPlan.memberFailures()).hasSize(4);
        assertThat(projectPlan.memberFailures())
            .allSatisfy(failure -> assertThat(failure.reason()).isEqualTo(MemberFailureReason.ORPHAN_USER));
        assertThat(projectPlan.memberFailures())
            .extracting(failure -> failure.userId())
            .containsExactlyInAnyOrder(missingUserId, deletedUserId, disabledUserId, crossWorkspaceUserId);
    }

    @Test
    void flagsProjectsWhoseWorkspaceIsMissing() {
        UUID missingWorkspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID projectId = insertProject(missingWorkspaceId, "MWK-" + suffix(), false, actorId);
        CurrentUser caller = new CurrentUser(
            actorId, missingWorkspaceId, null, "ghost", "Ghost Admin", Set.of("admin"), Set.of()
        );

        LegacySpaceMappingPlan plan = projectLegacyMappingService.planWorkspaceMigration(caller);

        assertThat(plan.workspaceId()).isEqualTo(missingWorkspaceId);
        assertThat(plan.totalProjects()).isEqualTo(1);
        assertThat(plan.migratableProjects()).isZero();
        assertThat(plan.projectFailureCount()).isEqualTo(1);
        ProjectMappingPlan projectPlan = onlyPlan(plan, projectId);
        assertThat(projectPlan.projectFailure()).isEqualTo(ProjectFailureReason.WORKSPACE_MISSING);
    }

    @Test
    void producesIdenticalPlansAcrossRuns() {
        WorkspaceAdmin admin = createWorkspaceAdmin("dt");
        UUID owner = insertUser(admin.workspaceId(), "town", "active", false);
        UUID viewer = insertUser(admin.workspaceId(), "tvue", "active", false);
        UUID boss = insertUser(admin.workspaceId(), "tbos", "active", false);
        String conflictKey = "dup-" + suffix();
        insertSpace(admin.workspaceId(), conflictKey, "active", admin.adminUserId());
        UUID healthyProject = insertProject(admin.workspaceId(), "HEA-" + suffix(), false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), healthyProject, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), healthyProject, viewer, "viewer", false, admin.adminUserId());
        UUID conflictProject = insertProject(admin.workspaceId(), conflictKey, false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), conflictProject, owner, "owner", false, admin.adminUserId());
        insertProjectMember(admin.workspaceId(), conflictProject, boss, "boss", false, admin.adminUserId());
        CurrentUser caller = adminUser(admin);

        LegacySpaceMappingPlan first = projectLegacyMappingService.planWorkspaceMigration(caller);
        LegacySpaceMappingPlan second = projectLegacyMappingService.planWorkspaceMigration(caller);

        assertThat(second.plans()).isEqualTo(first.plans());
        assertThat(second.totalProjects()).isEqualTo(first.totalProjects());
        assertThat(second.migratableProjects()).isEqualTo(first.migratableProjects());
        assertThat(second.reuseCount()).isEqualTo(first.reuseCount());
        assertThat(second.keyConflictCount()).isEqualTo(first.keyConflictCount());
        assertThat(second.memberFailureCount()).isEqualTo(first.memberFailureCount());
        assertThat(second.projectFailureCount()).isEqualTo(first.projectFailureCount());
    }

    @Test
    void forbidsCallersWithoutManageProjectsPermission() {
        WorkspaceAdmin admin = createWorkspaceAdmin("pm");
        CurrentUser member = new CurrentUser(
            UUID.randomUUID(), admin.workspaceId(), null, "plain", "Plain Member", Set.of("member"), Set.of()
        );

        assertThatThrownBy(() -> projectLegacyMappingService.planWorkspaceMigration(member))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    private ProjectMappingPlan onlyPlan(LegacySpaceMappingPlan plan, UUID projectId) {
        return plan.plans()
            .stream()
            .filter(item -> item.projectId().equals(projectId))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No mapping plan for project " + projectId));
    }

    private UUID deterministicSpaceId(UUID projectId) {
        return UUID.nameUUIDFromBytes(
            ("colla:project-legacy-space:" + projectId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalize(String projectKey) {
        return projectKey.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
    }

    private String shortHex(UUID projectId) {
        return projectId.toString().replace("-", "").substring(0, 8);
    }

    private CurrentUser adminUser(WorkspaceAdmin admin) {
        return new CurrentUser(
            admin.adminUserId(), admin.workspaceId(), null,
            admin.adminUsername(), "Mapping Plan Admin", Set.of("admin"), Set.of()
        );
    }

    private WorkspaceAdmin createWorkspaceAdmin(String usernamePrefix) {
        UUID workspaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            workspaceId, "Migration Mapping Workspace", "mig-map-" + suffix()
        );
        UUID adminId = UUID.randomUUID();
        String username = usernamePrefix + suffix();
        UUID seedAdminId = jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
        jdbcTemplate.update(
            """
                insert into users (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                select ?, ?, ?, password_hash, 'Migration Mapping Admin', 'active', now(), now()
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
            userId, workspaceId, usernamePrefix + suffix(), "Mapping User " + usernamePrefix, status, deleted
        );
        return userId;
    }

    private UUID insertProject(UUID workspaceId, String projectKey, boolean archived, UUID actorId) {
        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into projects (id, workspace_id, project_key, name, description, status, conversation_id,
                                      created_by, created_at, updated_by, updated_at, archived_at)
                values (?, ?, ?, ?, 'legacy mapping test', 'active', null, ?, now(), ?, now(), case when ? then now() else null end)
                """,
            projectId, workspaceId, projectKey, "Mapping " + projectKey, actorId, actorId, archived
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

    private UUID insertSpace(UUID workspaceId, String spaceKey, String status, UUID actorId) {
        UUID spaceId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_spaces
                    (id, workspace_id, space_key, name, status, visibility, created_by, created_at, updated_by, updated_at, archived_at)
                values (?, ?, ?, ?, ?, 'private', ?, now(), ?, now(), case when ? = 'archived' then now() else null end)
                """,
            spaceId, workspaceId, spaceKey, "Space " + spaceKey, status, actorId, actorId, status
        );
        return spaceId;
    }

    private void insertLegacyMap(UUID workspaceId, UUID projectId, UUID spaceId, String mappingStatus, UUID actorId) {
        jdbcTemplate.update(
            """
                insert into project_legacy_space_maps
                    (id, workspace_id, legacy_project_id, space_id, mapping_version, mapping_status, mapped_by, mapped_at)
                values (?, ?, ?, ?, 1, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, projectId, spaceId, mappingStatus, actorId
        );
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record WorkspaceAdmin(UUID workspaceId, UUID adminUserId, String adminUsername) {
    }
}
