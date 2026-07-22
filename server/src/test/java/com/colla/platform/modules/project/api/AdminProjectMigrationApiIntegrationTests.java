package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProjectMigrationApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void runsDryRunExecuteResumeVerifyRollbackChain() throws Exception {
        WorkspaceAdmin admin = createWorkspaceAdmin("api-chain");
        String token = login(admin.adminUsername(), "admin123456", "mig-api-chain-" + UUID.randomUUID());
        UUID owner = insertUser(admin.workspaceId(), "chown", "active", false);
        UUID orphanOwner = UUID.randomUUID();
        UUID projectA = insertProject(admin.workspaceId(), "CHA-" + suffix(), admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectA, owner, "owner", admin.adminUserId());
        UUID projectB = insertProject(admin.workspaceId(), "CHB-" + suffix(), admin.adminUserId());
        insertProjectMember(admin.workspaceId(), projectB, orphanOwner, "owner", admin.adminUserId());

        String dryRunBody = mockMvc.perform(post("/api/admin/project-migrations/spaces:dry-run")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.summary.mode").value("dry_run"))
            .andExpect(jsonPath("$.summary.counts.total").value(2))
            .andReturn().getResponse().getContentAsString();
        UUID dryRunBatchId = UUID.fromString(objectMapper.readTree(dryRunBody).get("id").asText());

        String executeBody = mockMvc.perform(post("/api/admin/project-migrations/spaces:execute")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"EXECUTE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dryRun").value(false))
            .andExpect(jsonPath("$.status").value("failed"))
            .andExpect(jsonPath("$.summary.counts.created").value(1))
            .andExpect(jsonPath("$.summary.counts.skipped").value(1))
            .andReturn().getResponse().getContentAsString();
        UUID batchId = UUID.fromString(objectMapper.readTree(executeBody).get("id").asText());

        mockMvc.perform(get("/api/admin/project-migrations/batches")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.id == '" + batchId + "')].dryRun").value(org.hamcrest.Matchers.hasItem(false)))
            .andExpect(jsonPath("$[?(@.id == '" + batchId + "')].status").value(org.hamcrest.Matchers.hasItem("failed")))
            .andExpect(jsonPath("$[?(@.id == '" + batchId + "')].counts.created").value(org.hamcrest.Matchers.hasItem(1)))
            .andExpect(jsonPath("$[?(@.id == '" + batchId + "')].startedBy").value(org.hamcrest.Matchers.hasItem(admin.adminUserId().toString())))
            .andExpect(jsonPath("$[?(@.id == '" + dryRunBatchId + "')].dryRun").value(org.hamcrest.Matchers.hasItem(true)));

        mockMvc.perform(get("/api/admin/project-migrations/batches/{batchId}", batchId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(batchId.toString()))
            .andExpect(jsonPath("$.workspaceId").value(admin.workspaceId().toString()))
            .andExpect(jsonPath("$.sourceChecksum").isNotEmpty())
            .andExpect(jsonPath("$.sourceWatermark").exists())
            .andExpect(jsonPath("$.resultChecksum").isNotEmpty())
            .andExpect(jsonPath("$.failures[?(@.projectId == '" + projectB + "')].reason")
                .value(org.hamcrest.Matchers.hasItem("NO_VALID_OWNER")))
            .andExpect(jsonPath("$.summary.counts.created").value(1));

        UUID ownerB = insertUser(admin.workspaceId(), "chowb", "active", false);
        insertProjectMember(admin.workspaceId(), projectB, ownerB, "owner", admin.adminUserId());

        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:resume", batchId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.summary.resumed").value(true))
            .andExpect(jsonPath("$.summary.counts.reused").value(1))
            .andExpect(jsonPath("$.summary.counts.created").value(1));

        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:verify", batchId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.batchId").value(batchId.toString()))
            .andExpect(jsonPath("$.allMatched").value(true))
            .andExpect(jsonPath("$.matches.length()").value(2))
            .andExpect(jsonPath("$.mismatches.length()").value(0));

        mockMvc.perform(post("/api/admin/project-migrations/workspaces:verify-convergence")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allMatched").value(true))
            .andExpect(jsonPath("$.matches.length()").value(2))
            .andExpect(jsonPath("$.mismatches.length()").value(0));

        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:rollback", batchId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"ROLLBACK\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("rolled_back"))
            .andExpect(jsonPath("$.rolledBackBy").value(admin.adminUserId().toString()))
            .andExpect(jsonPath("$.rolledBackAt").exists());

        assertThat(auditCount(admin.workspaceId(), "project_migration.dry_run", dryRunBatchId)).isEqualTo(1);
        assertThat(auditCount(admin.workspaceId(), "project_migration.executed", batchId)).isEqualTo(1);
        assertThat(auditCount(admin.workspaceId(), "project_migration.resumed", batchId)).isEqualTo(1);
        assertThat(auditCount(admin.workspaceId(), "project_migration.verified", batchId)).isEqualTo(1);
        assertThat(auditCount(admin.workspaceId(), "project_migration.convergence_verified", admin.workspaceId())).isEqualTo(1);
        assertThat(auditCount(admin.workspaceId(), "project_migration.rolled_back", batchId)).isEqualTo(1);
    }

    @Test
    void rejectsHighRiskOperationsWithoutExactConfirmation() throws Exception {
        WorkspaceAdmin admin = createWorkspaceAdmin("api-conf");
        String token = login(admin.adminUsername(), "admin123456", "mig-api-conf-" + UUID.randomUUID());
        UUID owner = insertUser(admin.workspaceId(), "cfown", "active", false);
        UUID project = insertProject(admin.workspaceId(), "CF-" + suffix(), admin.adminUserId());
        insertProjectMember(admin.workspaceId(), project, owner, "owner", admin.adminUserId());

        mockMvc.perform(post("/api/admin/project-migrations/spaces:execute")
                .header("Authorization", bearer(token)))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResolvedException())
                .isNotNull()
                .satisfies(exception -> assertThat(exception.getMessage()).contains("EXECUTE")));

        mockMvc.perform(post("/api/admin/project-migrations/spaces:execute")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/project-migrations/spaces:execute")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"execute\"}"))
            .andExpect(status().isBadRequest());

        String executeBody = mockMvc.perform(post("/api/admin/project-migrations/spaces:execute")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"EXECUTE\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        UUID batchId = UUID.fromString(objectMapper.readTree(executeBody).get("id").asText());

        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:rollback", batchId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResolvedException())
                .isNotNull()
                .satisfies(exception -> assertThat(exception.getMessage()).contains("ROLLBACK")));

        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:rollback", batchId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"rollback\"}"))
            .andExpect(status().isBadRequest());

        assertThat(countMigrationBatches(admin.workspaceId(), batchId)).isEqualTo(1);
        assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(1);
    }

    @Test
    void rejectsPlainMembersOnEveryEndpoint() throws Exception {
        String adminToken = login("admin", "admin123456", "mig-api-deny-admin-" + UUID.randomUUID());
        String memberUsername = "apimem" + suffix();
        createMember(adminToken, memberUsername, "Migration Api Member");
        String memberToken = login(memberUsername, "member123456", "mig-api-deny-member-" + UUID.randomUUID());
        UUID batchId = UUID.randomUUID();

        mockMvc.perform(get("/api/admin/project-migrations/batches")
                .header("Authorization", bearer(memberToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/project-migrations/batches/{batchId}", batchId)
                .header("Authorization", bearer(memberToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/project-migrations/spaces:dry-run")
                .header("Authorization", bearer(memberToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/project-migrations/spaces:execute")
                .header("Authorization", bearer(memberToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"EXECUTE\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:resume", batchId)
                .header("Authorization", bearer(memberToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:verify", batchId)
                .header("Authorization", bearer(memberToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/project-migrations/workspaces:verify-convergence")
                .header("Authorization", bearer(memberToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:rollback", batchId)
                .header("Authorization", bearer(memberToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"ROLLBACK\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/project-migrations/batches"))
            .andExpect(status().isForbidden());
    }

    @Test
    void hidesBatchesOfOtherWorkspaces() throws Exception {
        WorkspaceAdmin adminA = createWorkspaceAdmin("api-wsa");
        String tokenA = login(adminA.adminUsername(), "admin123456", "mig-api-wsa-" + UUID.randomUUID());
        UUID owner = insertUser(adminA.workspaceId(), "wsown", "active", false);
        UUID project = insertProject(adminA.workspaceId(), "WS-" + suffix(), adminA.adminUserId());
        insertProjectMember(adminA.workspaceId(), project, owner, "owner", adminA.adminUserId());
        String executeBody = mockMvc.perform(post("/api/admin/project-migrations/spaces:execute")
                .header("Authorization", bearer(tokenA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"EXECUTE\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        UUID batchId = UUID.fromString(objectMapper.readTree(executeBody).get("id").asText());

        WorkspaceAdmin adminB = createWorkspaceAdmin("api-wsb");
        String tokenB = login(adminB.adminUsername(), "admin123456", "mig-api-wsb-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/project-migrations/batches/{batchId}", batchId)
                .header("Authorization", bearer(tokenB)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:resume", batchId)
                .header("Authorization", bearer(tokenB)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:verify", batchId)
                .header("Authorization", bearer(tokenB)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/project-migrations/batches/{batchId}:rollback", batchId)
                .header("Authorization", bearer(tokenB))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"ROLLBACK\"}"))
            .andExpect(status().isNotFound());

        MvcResult listResult = mockMvc.perform(get("/api/admin/project-migrations/batches")
                .header("Authorization", bearer(tokenB)))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode batches = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(batches).isEmpty();
    }

    private long countMigrationBatches(UUID workspaceId, UUID batchId) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from project_space_migration_batches where workspace_id = ? and id = ?",
            Long.class, workspaceId, batchId
        );
        return count == null ? 0 : count;
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

    private WorkspaceAdmin createWorkspaceAdmin(String usernamePrefix) {
        UUID workspaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            workspaceId, "Migration Api Workspace", "mig-api-" + suffix()
        );
        UUID adminId = UUID.randomUUID();
        String username = usernamePrefix + suffix();
        UUID seedAdminId = jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
        jdbcTemplate.update(
            """
                insert into users (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                select ?, ?, ?, password_hash, 'Migration Api Admin', 'active', now(), now()
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
            userId, workspaceId, usernamePrefix + suffix(), "Migration Api User " + usernamePrefix, status, deleted
        );
        return userId;
    }

    private UUID insertProject(UUID workspaceId, String projectKey, UUID actorId) {
        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into projects (id, workspace_id, project_key, name, description, status, conversation_id,
                                      created_by, created_at, updated_by, updated_at, archived_at)
                values (?, ?, ?, ?, 'migration api test', 'active', null, ?, now(), ?, now(), null)
                """,
            projectId, workspaceId, projectKey, "Migration Api " + projectKey, actorId, actorId
        );
        return projectId;
    }

    private void insertProjectMember(UUID workspaceId, UUID projectId, UUID userId, String role, UUID actorId) {
        jdbcTemplate.update(
            """
                insert into project_members (id, workspace_id, project_id, user_id, project_role, joined_at, created_by, archived_at)
                values (?, ?, ?, ?, ?, now(), ?, null)
                """,
            UUID.randomUUID(), workspaceId, projectId, userId, role, actorId
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
