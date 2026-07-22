package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProjectMigrationControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reportsLegacyProjectProfileWithAllFindingCategories() throws Exception {
        WorkspaceAdmin admin = createWorkspaceAdmin("prof");
        String token = login(admin.adminUsername(), "admin123456", "migration-profile-" + UUID.randomUUID());
        UUID workspaceId = admin.workspaceId();
        UUID actor = admin.adminUserId();

        UUID healthyOwner = insertUser(workspaceId, "hown", "active", false);
        UUID healthyMember = insertUser(workspaceId, "hmem", "active", false);
        UUID healthyViewer = insertUser(workspaceId, "hview", "active", false);
        UUID archivedHealthyMember = insertUser(workspaceId, "harch", "active", false);
        UUID healthyConversation = insertConversation(workspaceId, "project", false, actor);
        insertConversationMember(workspaceId, healthyConversation, healthyOwner);
        insertConversationMember(workspaceId, healthyConversation, healthyMember);
        insertConversationMember(workspaceId, healthyConversation, healthyViewer);
        String healthyKey = "H-" + suffix();
        UUID healthyProject = insertProject(workspaceId, healthyKey, healthyConversation, false, actor);
        insertProjectMember(workspaceId, healthyProject, healthyOwner, "owner", false, actor);
        insertProjectMember(workspaceId, healthyProject, healthyMember, "member", false, actor);
        insertProjectMember(workspaceId, healthyProject, healthyViewer, "viewer", false, actor);
        insertProjectMember(workspaceId, healthyProject, archivedHealthyMember, "member", true, actor);

        UUID orphanOwner = insertUser(workspaceId, "oown", "active", false);
        UUID missingUserId = UUID.randomUUID();
        UUID deletedUserId = insertUser(workspaceId, "odel", "active", true);
        UUID disabledUserId = insertUser(workspaceId, "odis", "disabled", false);
        UUID foreignWorkspaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            foreignWorkspaceId, "Foreign Profile Workspace", "mig-foreign-" + suffix()
        );
        UUID crossWorkspaceUserId = insertUser(foreignWorkspaceId, "oxws", "active", false);
        UUID orphanConversation = insertConversation(workspaceId, "project", false, actor);
        insertConversationMember(workspaceId, orphanConversation, orphanOwner);
        insertConversationMember(workspaceId, orphanConversation, missingUserId);
        insertConversationMember(workspaceId, orphanConversation, deletedUserId);
        insertConversationMember(workspaceId, orphanConversation, disabledUserId);
        insertConversationMember(workspaceId, orphanConversation, crossWorkspaceUserId);
        String orphanKey = "O-" + suffix();
        UUID orphanProject = insertProject(workspaceId, orphanKey, orphanConversation, false, actor);
        insertProjectMember(workspaceId, orphanProject, orphanOwner, "owner", false, actor);
        insertProjectMember(workspaceId, orphanProject, missingUserId, "member", false, actor);
        insertProjectMember(workspaceId, orphanProject, deletedUserId, "member", false, actor);
        insertProjectMember(workspaceId, orphanProject, disabledUserId, "member", false, actor);
        insertProjectMember(workspaceId, orphanProject, crossWorkspaceUserId, "member", false, actor);

        UUID illegalOwner = insertUser(workspaceId, "iown", "active", false);
        UUID bossUser = insertUser(workspaceId, "iboss", "active", false);
        UUID illegalViewer = insertUser(workspaceId, "iview", "active", false);
        UUID illegalConversation = insertConversation(workspaceId, "project", false, actor);
        insertConversationMember(workspaceId, illegalConversation, illegalOwner);
        insertConversationMember(workspaceId, illegalConversation, bossUser);
        insertConversationMember(workspaceId, illegalConversation, illegalViewer);
        UUID illegalProject = insertProject(workspaceId, "I-" + suffix(), illegalConversation, false, actor);
        insertProjectMember(workspaceId, illegalProject, illegalOwner, "owner", false, actor);
        insertProjectMember(workspaceId, illegalProject, bossUser, "boss", false, actor);
        insertProjectMember(workspaceId, illegalProject, illegalViewer, "viewer", false, actor);

        UUID dupOwnerA = insertUser(workspaceId, "dowa", "active", false);
        UUID dupOwnerB = insertUser(workspaceId, "dowb", "active", false);
        UUID dupConversation = insertConversation(workspaceId, "project", false, actor);
        insertConversationMember(workspaceId, dupConversation, dupOwnerA);
        insertConversationMember(workspaceId, dupConversation, dupOwnerB);
        UUID dupProject = insertProject(workspaceId, "D-" + suffix(), dupConversation, false, actor);
        insertProjectMember(workspaceId, dupProject, dupOwnerA, "owner", false, actor);
        insertProjectMember(workspaceId, dupProject, dupOwnerB, "owner", false, actor);

        UUID sharedOwner = insertUser(workspaceId, "sown", "active", false);
        UUID sharedConversation = insertConversation(workspaceId, "project", false, actor);
        insertConversationMember(workspaceId, sharedConversation, sharedOwner);
        UUID sharedProjectA = insertProject(workspaceId, "S1-" + suffix(), sharedConversation, false, actor);
        UUID sharedProjectB = insertProject(workspaceId, "S2-" + suffix(), sharedConversation, false, actor);
        insertProjectMember(workspaceId, sharedProjectA, sharedOwner, "owner", false, actor);
        insertProjectMember(workspaceId, sharedProjectB, sharedOwner, "owner", false, actor);

        UUID noOwnerMember = insertUser(workspaceId, "nmem", "active", false);
        UUID noOwnerConversation = insertConversation(workspaceId, "project", false, actor);
        insertConversationMember(workspaceId, noOwnerConversation, noOwnerMember);
        String noOwnerKey = "N-" + suffix();
        UUID noOwnerProject = insertProject(workspaceId, noOwnerKey, noOwnerConversation, false, actor);
        insertProjectMember(workspaceId, noOwnerProject, noOwnerMember, "member", false, actor);

        UUID driftOwner = insertUser(workspaceId, "rown", "active", false);
        UUID driftProjectOnlyUser = insertUser(workspaceId, "rout", "active", false);
        UUID driftGroupOnlyUser = insertUser(workspaceId, "rin", "active", false);
        UUID driftConversation = insertConversation(workspaceId, "project", false, actor);
        insertConversationMember(workspaceId, driftConversation, driftOwner);
        insertConversationMember(workspaceId, driftConversation, driftGroupOnlyUser);
        UUID driftProject = insertProject(workspaceId, "R-" + suffix(), driftConversation, false, actor);
        insertProjectMember(workspaceId, driftProject, driftOwner, "owner", false, actor);
        insertProjectMember(workspaceId, driftProject, driftProjectOnlyUser, "member", false, actor);

        UUID noConversationOwner = insertUser(workspaceId, "mone", "active", false);
        UUID noConversationProject = insertProject(workspaceId, "M1-" + suffix(), null, false, actor);
        insertProjectMember(workspaceId, noConversationProject, noConversationOwner, "owner", false, actor);

        UUID danglingConversationOwner = insertUser(workspaceId, "mtwo", "active", false);
        UUID danglingConversationProject = insertProject(workspaceId, "M2-" + suffix(), UUID.randomUUID(), false, actor);
        insertProjectMember(workspaceId, danglingConversationProject, danglingConversationOwner, "owner", false, actor);

        UUID archivedConversationOwner = insertUser(workspaceId, "mthree", "active", false);
        UUID archivedConversation = insertConversation(workspaceId, "project", true, actor);
        UUID archivedConversationProject = insertProject(workspaceId, "M3-" + suffix(), archivedConversation, false, actor);
        insertProjectMember(workspaceId, archivedConversationProject, archivedConversationOwner, "owner", false, actor);

        UUID groupConversationOwner = insertUser(workspaceId, "mfour", "active", false);
        UUID groupConversation = insertConversation(workspaceId, "group", false, actor);
        UUID groupConversationProject = insertProject(workspaceId, "M4-" + suffix(), groupConversation, false, actor);
        insertProjectMember(workspaceId, groupConversationProject, groupConversationOwner, "owner", false, actor);

        UUID archivedProjectMember = insertUser(workspaceId, "amem", "active", false);
        UUID archivedProject = insertProject(workspaceId, "A-" + suffix(), null, true, actor);
        insertProjectMember(workspaceId, archivedProject, archivedProjectMember, "member", false, actor);

        mockMvc.perform(get("/api/admin/project-migrations/profile")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
            .andExpect(jsonPath("$.generatedAt").exists())
            .andExpect(jsonPath("$.totals.activeProjects").value(12))
            .andExpect(jsonPath("$.totals.archivedProjects").value(1))
            .andExpect(jsonPath("$.totals.activeMembers").value(23))
            .andExpect(jsonPath("$.totals.archivedMembers").value(1))
            .andExpect(jsonPath("$.roleDistribution.ownerCount").value(12))
            .andExpect(jsonPath("$.roleDistribution.memberCount").value(8))
            .andExpect(jsonPath("$.roleDistribution.viewerCount").value(2))
            .andExpect(jsonPath("$.roleDistribution.otherCount").value(1))
            .andExpect(jsonPath("$.orphanMembers.totalCount").value(4))
            .andExpect(jsonPath("$.orphanMembers.truncated").value(false))
            .andExpect(jsonPath("$.orphanMembers.items.length()").value(4))
            .andExpect(jsonPath("$.orphanMembers.items[*].projectId", everyItem(is(orphanProject.toString()))))
            .andExpect(jsonPath("$.orphanMembers.items[?(@.userId == '" + missingUserId + "')].reason", hasItem("USER_MISSING")))
            .andExpect(jsonPath("$.orphanMembers.items[?(@.userId == '" + deletedUserId + "')].reason", hasItem("USER_DELETED")))
            .andExpect(jsonPath("$.orphanMembers.items[?(@.userId == '" + disabledUserId + "')].reason", hasItem("USER_DISABLED")))
            .andExpect(jsonPath("$.orphanMembers.items[?(@.userId == '" + crossWorkspaceUserId + "')].reason", hasItem("WORKSPACE_MISMATCH")))
            .andExpect(jsonPath("$.orphanMembers.items[0].projectKey").value(orphanKey))
            .andExpect(jsonPath("$.illegalRoles.totalCount").value(1))
            .andExpect(jsonPath("$.illegalRoles.items[0].projectId").value(illegalProject.toString()))
            .andExpect(jsonPath("$.illegalRoles.items[0].userId").value(bossUser.toString()))
            .andExpect(jsonPath("$.illegalRoles.items[0].projectRole").value("boss"))
            .andExpect(jsonPath("$.duplicateOwners.totalCount").value(1))
            .andExpect(jsonPath("$.duplicateOwners.items[0].projectId").value(dupProject.toString()))
            .andExpect(jsonPath("$.duplicateOwners.items[0].userIds", hasItems(dupOwnerA.toString(), dupOwnerB.toString())))
            .andExpect(jsonPath("$.sharedConversations.totalCount").value(1))
            .andExpect(jsonPath("$.sharedConversations.items[0].conversationId").value(sharedConversation.toString()))
            .andExpect(jsonPath("$.sharedConversations.items[0].projectIds", hasItems(sharedProjectA.toString(), sharedProjectB.toString())))
            .andExpect(jsonPath("$.sharedConversations.items[0].projectKeys.length()").value(2))
            .andExpect(jsonPath("$.projectsWithoutOwner.totalCount").value(1))
            .andExpect(jsonPath("$.projectsWithoutOwner.items[0].projectId").value(noOwnerProject.toString()))
            .andExpect(jsonPath("$.projectsWithoutOwner.items[0].projectKey").value(noOwnerKey))
            .andExpect(jsonPath("$.imDrifts.totalCount").value(2))
            .andExpect(jsonPath("$.imDrifts.items[*].projectId", everyItem(is(driftProject.toString()))))
            .andExpect(jsonPath("$.imDrifts.items[?(@.userId == '" + driftProjectOnlyUser + "')].direction", hasItem("PROJECT_MEMBER_NOT_IN_GROUP")))
            .andExpect(jsonPath("$.imDrifts.items[?(@.userId == '" + driftGroupOnlyUser + "')].direction", hasItem("GROUP_MEMBER_NOT_IN_PROJECT")))
            .andExpect(jsonPath("$.imDrifts.items[*].conversationId", everyItem(is(driftConversation.toString()))))
            .andExpect(jsonPath("$.missingConversations.totalCount").value(4))
            .andExpect(jsonPath("$.missingConversations.items[?(@.projectId == '" + noConversationProject + "')].reason", hasItem("NO_CONVERSATION_ID")))
            .andExpect(jsonPath("$.missingConversations.items[?(@.projectId == '" + danglingConversationProject + "')].reason", hasItem("CONVERSATION_NOT_FOUND")))
            .andExpect(jsonPath("$.missingConversations.items[?(@.projectId == '" + archivedConversationProject + "')].reason", hasItem("CONVERSATION_ARCHIVED")))
            .andExpect(jsonPath("$.missingConversations.items[?(@.projectId == '" + groupConversationProject + "')].reason", hasItem("CONVERSATION_TYPE_MISMATCH")))
            .andExpect(jsonPath("$.orphanMembers.items[*].projectId", not(hasItem(healthyProject.toString()))))
            .andExpect(jsonPath("$.illegalRoles.items[*].projectId", not(hasItem(healthyProject.toString()))))
            .andExpect(jsonPath("$.duplicateOwners.items[*].projectId", not(hasItem(healthyProject.toString()))))
            .andExpect(jsonPath("$.sharedConversations.items[*].projectIds[*]", not(hasItem(healthyProject.toString()))))
            .andExpect(jsonPath("$.projectsWithoutOwner.items[*].projectId", not(hasItem(healthyProject.toString()))))
            .andExpect(jsonPath("$.imDrifts.items[*].projectId", not(hasItem(healthyProject.toString()))))
            .andExpect(jsonPath("$.missingConversations.items[*].projectId", not(hasItem(healthyProject.toString()))))
            .andExpect(jsonPath("$.projectsWithoutOwner.items[*].projectId", not(hasItem(archivedProject.toString()))))
            .andExpect(jsonPath("$.missingConversations.items[*].projectId", not(hasItem(archivedProject.toString()))));

        String metadataJson = jdbcTemplate.queryForObject(
            """
                select metadata::text from audit_logs
                 where workspace_id = ? and action = 'project_migration.profiled'
                   and target_type = 'project_migration' and target_id = ?
                 order by created_at desc limit 1
                """,
            String.class, workspaceId, workspaceId
        );
        JsonNode metadata = objectMapper.readTree(metadataJson);
        assertThat(metadata.get("activeProjects").asLong()).isEqualTo(12);
        assertThat(metadata.get("archivedProjects").asLong()).isEqualTo(1);
        assertThat(metadata.get("activeMembers").asLong()).isEqualTo(23);
        assertThat(metadata.get("archivedMembers").asLong()).isEqualTo(1);
        assertThat(metadata.get("orphanMembers").asLong()).isEqualTo(4);
        assertThat(metadata.get("illegalRoles").asLong()).isEqualTo(1);
        assertThat(metadata.get("duplicateOwners").asLong()).isEqualTo(1);
        assertThat(metadata.get("sharedConversations").asLong()).isEqualTo(1);
        assertThat(metadata.get("projectsWithoutOwner").asLong()).isEqualTo(1);
        assertThat(metadata.get("imDrifts").asLong()).isEqualTo(2);
        assertThat(metadata.get("missingConversations").asLong()).isEqualTo(4);
        UUID auditActorId = jdbcTemplate.queryForObject(
            """
                select actor_id from audit_logs
                 where workspace_id = ? and action = 'project_migration.profiled' and target_id = ?
                 order by created_at desc limit 1
                """,
            UUID.class, workspaceId, workspaceId
        );
        assertThat(auditActorId).isEqualTo(actor);
    }

    @Test
    void requiresAuthenticationAndProjectManagePermission() throws Exception {
        mockMvc.perform(get("/api/admin/project-migrations/profile"))
            .andExpect(status().isForbidden());

        String adminToken = login("admin", "admin123456", "migration-perm-admin-" + UUID.randomUUID());
        String memberUsername = "nomgr" + suffix();
        createMember(adminToken, memberUsername, "No Governance Member");
        String memberToken = login(memberUsername, "member123456", "migration-perm-member-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/project-migrations/profile")
                .header("Authorization", bearer(memberToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void returnsZeroedProfileForWorkspaceWithoutLegacyProjects() throws Exception {
        WorkspaceAdmin admin = createWorkspaceAdmin("zero");
        String token = login(admin.adminUsername(), "admin123456", "migration-zero-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/project-migrations/profile")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workspaceId").value(admin.workspaceId().toString()))
            .andExpect(jsonPath("$.totals.activeProjects").value(0))
            .andExpect(jsonPath("$.totals.archivedProjects").value(0))
            .andExpect(jsonPath("$.totals.activeMembers").value(0))
            .andExpect(jsonPath("$.totals.archivedMembers").value(0))
            .andExpect(jsonPath("$.roleDistribution.ownerCount").value(0))
            .andExpect(jsonPath("$.roleDistribution.memberCount").value(0))
            .andExpect(jsonPath("$.roleDistribution.viewerCount").value(0))
            .andExpect(jsonPath("$.roleDistribution.otherCount").value(0))
            .andExpect(jsonPath("$.orphanMembers.totalCount").value(0))
            .andExpect(jsonPath("$.orphanMembers.items.length()").value(0))
            .andExpect(jsonPath("$.illegalRoles.totalCount").value(0))
            .andExpect(jsonPath("$.illegalRoles.items.length()").value(0))
            .andExpect(jsonPath("$.duplicateOwners.totalCount").value(0))
            .andExpect(jsonPath("$.duplicateOwners.items.length()").value(0))
            .andExpect(jsonPath("$.sharedConversations.totalCount").value(0))
            .andExpect(jsonPath("$.sharedConversations.items.length()").value(0))
            .andExpect(jsonPath("$.projectsWithoutOwner.totalCount").value(0))
            .andExpect(jsonPath("$.projectsWithoutOwner.items.length()").value(0))
            .andExpect(jsonPath("$.imDrifts.totalCount").value(0))
            .andExpect(jsonPath("$.imDrifts.items.length()").value(0))
            .andExpect(jsonPath("$.missingConversations.totalCount").value(0))
            .andExpect(jsonPath("$.missingConversations.items.length()").value(0));
    }

    private WorkspaceAdmin createWorkspaceAdmin(String usernamePrefix) {
        UUID workspaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            workspaceId, "Migration Profile Workspace", "mig-prof-" + suffix()
        );
        UUID adminId = UUID.randomUUID();
        String username = usernamePrefix + suffix();
        UUID seedAdminId = jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
        jdbcTemplate.update(
            """
                insert into users (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                select ?, ?, ?, password_hash, 'Migration Profile Admin', 'active', now(), now()
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
            userId, workspaceId, usernamePrefix + suffix(), "Profile User " + usernamePrefix, status, deleted
        );
        return userId;
    }

    private UUID insertProject(UUID workspaceId, String projectKey, UUID conversationId, boolean archived, UUID actorId) {
        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into projects (id, workspace_id, project_key, name, description, status, conversation_id,
                                      created_by, created_at, updated_by, updated_at, archived_at)
                values (?, ?, ?, ?, 'migration profile test', 'active', ?, ?, now(), ?, now(), case when ? then now() else null end)
                """,
            projectId, workspaceId, projectKey, "Profile " + projectKey, conversationId, actorId, actorId, archived
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
                values (?, ?, ?, 'Profile Conversation', ?, ?, now(), now(), case when ? then now() else null end)
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

    private UUID createMember(String token, String username, String displayName) throws Exception {
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"%s",
                      "password":"member123456",
                      "displayName":"%s",
                      "email":"%s@example.com",
                      "roleCode":"member"
                    }
                    """.formatted(username, displayName, username)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String login(String username, String password, String deviceFingerprint) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"%s",
                      "password":"%s",
                      "deviceType":"web",
                      "deviceFingerprint":"%s",
                      "deviceName":"MockMvc",
                      "appVersion":"test"
                    }
                    """.formatted(username, password, deviceFingerprint)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record WorkspaceAdmin(UUID workspaceId, UUID adminUserId, String adminUsername) {
    }
}
