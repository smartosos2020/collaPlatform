package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.project.application.ProjectSpaceMigrationService;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationBatchRecord;
import com.colla.platform.shared.auth.CurrentUser;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Executable evidence for the frozen S02 compatibility boundary (PROJECT-PLATFORM-S02-M4 T08):
 * legacy project/issue writes keep flowing only into the legacy tables, and the new project-space
 * model tables stay untouched by those writes, both before a migration batch exists and after one
 * has been applied to the workspace (no dual-write, no silent cutover of project/issue writes).
 *
 * <p>Division of labor: the reverse direction, that the migration code itself never writes back
 * into the legacy tables, is already covered by
 * {@link com.colla.platform.modules.project.application.ProjectSpaceMigrationServiceIntegrationTests},
 * which hashes the four legacy tables (projects, project_members, conversations,
 * conversation_members) across execute and rollback. This class therefore asserts only the
 * legacy-write to new-model direction and does not repeat the legacy-table hashing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectLegacyWriteBoundaryIntegrationTests {
    private static final List<String> NEW_MODEL_TABLES = List.of(
        "project_spaces",
        "project_space_members",
        "project_space_role_assignments",
        "project_legacy_space_maps",
        "project_space_migration_batches"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectSpaceMigrationService migrationService;

    @Test
    void legacyWritesBypassNewModelTablesBeforeMigration() throws Exception {
        WorkspaceAdmin admin = createWorkspaceAdmin("wb1");
        String token = login(admin.adminUsername(), "admin123456", "wb1-admin-" + UUID.randomUUID());
        UUID memberA = createMember(token, "wb1a" + suffix(), "Boundary Member A");
        UUID memberB = createMember(token, "wb1b" + suffix(), "Boundary Member B");

        Map<String, String> before = newModelHashes(admin.workspaceId());

        UUID projectId = createProject(token, "WB1-" + suffix(), "Boundary Project One", memberA);
        addProjectMember(token, projectId, memberB);
        UUID issueId = createIssue(token, projectId, "Legacy write before migration");
        transitionIssue(token, issueId, "in_progress");

        assertThat(newModelHashes(admin.workspaceId())).isEqualTo(before);
        for (String table : NEW_MODEL_TABLES) {
            assertThat(countRows(table, admin.workspaceId())).as(table).isZero();
        }

        assertThat(countRows("projects", admin.workspaceId())).isEqualTo(1);
        assertThat(countRows("project_members", admin.workspaceId())).isEqualTo(3);
        assertThat(countRows("issues", admin.workspaceId())).isEqualTo(1);
        assertThat(issueStatus(admin.workspaceId(), issueId)).isEqualTo("in_progress");
    }

    @Test
    void legacyWritesAfterMigrationDoNotPropagateToNewModel() throws Exception {
        WorkspaceAdmin admin = createWorkspaceAdmin("wb2");
        String token = login(admin.adminUsername(), "admin123456", "wb2-admin-" + UUID.randomUUID());
        UUID memberA = createMember(token, "wb2a" + suffix(), "Boundary Member A");
        UUID memberB = createMember(token, "wb2b" + suffix(), "Boundary Member B");
        UUID migratedProjectId = createProject(token, "WB2A-" + suffix(), "Boundary Migrated Project", memberA);

        MigrationBatchRecord batch = migrationService.execute(adminUser(admin));
        assertThat(batch.status()).isEqualTo("completed");
        assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(1);
        assertThat(countRows("project_space_members", admin.workspaceId())).isEqualTo(2);
        assertThat(countRows("project_space_role_assignments", admin.workspaceId())).isEqualTo(2);
        assertThat(countRows("project_legacy_space_maps", admin.workspaceId())).isEqualTo(1);
        assertThat(countRows("project_space_migration_batches", admin.workspaceId())).isEqualTo(1);
        Map<String, String> migrated = newModelHashes(admin.workspaceId());

        createProject(token, "WB2B-" + suffix(), "Boundary Post Migration Project", memberB);
        addProjectMember(token, migratedProjectId, memberB);
        UUID issueId = createIssue(token, migratedProjectId, "Legacy write after migration");
        transitionIssue(token, issueId, "in_progress");

        assertThat(newModelHashes(admin.workspaceId())).isEqualTo(migrated);
        assertThat(countRows("project_spaces", admin.workspaceId())).isEqualTo(1);
        assertThat(countRows("project_space_members", admin.workspaceId())).isEqualTo(2);
        assertThat(countRows("project_space_role_assignments", admin.workspaceId())).isEqualTo(2);
        assertThat(countRows("project_legacy_space_maps", admin.workspaceId())).isEqualTo(1);
        assertThat(countRows("project_space_migration_batches", admin.workspaceId())).isEqualTo(1);

        assertThat(countRows("projects", admin.workspaceId())).isEqualTo(2);
        assertThat(countRows("project_members", admin.workspaceId())).isEqualTo(5);
        assertThat(countRows("issues", admin.workspaceId())).isEqualTo(1);
        assertThat(issueStatus(admin.workspaceId(), issueId)).isEqualTo("in_progress");
    }

    private UUID createProject(String token, String projectKey, String name, UUID memberId) throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectKey": "%s",
                      "name": "%s",
                      "description": "legacy write boundary probe",
                      "memberIds": ["%s"]
                    }
                    """.formatted(projectKey, name, memberId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void addProjectMember(String token, UUID projectId, UUID memberId) throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/members", projectId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberIds\":[\"" + memberId + "\"]}"))
            .andExpect(status().isOk());
    }

    private UUID createIssue(String token, UUID projectId, String title) throws Exception {
        String response = mockMvc.perform(post("/api/projects/{projectId}/issues", projectId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "issueType": "task",
                      "title": "%s",
                      "priority": "medium"
                    }
                    """.formatted(title)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("issue").get("id").asText());
    }

    private void transitionIssue(String token, UUID issueId, String status) throws Exception {
        mockMvc.perform(post("/api/issues/{issueId}/transition", issueId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"))
            .andExpect(status().isOk());
    }

    private Map<String, String> newModelHashes(UUID workspaceId) {
        Map<String, String> hashes = new HashMap<>();
        for (String table : NEW_MODEL_TABLES) {
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

    private long countRows(String table, UUID workspaceId) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where workspace_id = ?",
            Long.class, workspaceId
        );
        return count == null ? 0 : count;
    }

    private String issueStatus(UUID workspaceId, UUID issueId) {
        return jdbcTemplate.queryForObject(
            "select status from issues where workspace_id = ? and id = ?",
            String.class, workspaceId, issueId
        );
    }

    private UUID createMember(String token, String username, String displayName) throws Exception {
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "password": "member123456",
                      "displayName": "%s",
                      "email": "%s@example.com",
                      "roleCode": "member"
                    }
                    """.formatted(username, displayName, username)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private CurrentUser adminUser(WorkspaceAdmin admin) {
        return new CurrentUser(
            admin.adminUserId(), admin.workspaceId(), null,
            admin.adminUsername(), "Write Boundary Admin", Set.of("admin"), Set.of()
        );
    }

    private WorkspaceAdmin createWorkspaceAdmin(String usernamePrefix) {
        UUID workspaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            workspaceId, "Write Boundary Workspace", "wb-" + suffix()
        );
        UUID adminId = UUID.randomUUID();
        String username = usernamePrefix + suffix();
        UUID seedAdminId = jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
        jdbcTemplate.update(
            """
                insert into users (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                select ?, ?, ?, password_hash, 'Write Boundary Admin', 'active', now(), now()
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
        jdbcTemplate.update(
            """
                insert into roles (id, workspace_id, code, name, scope, is_builtin, created_at, updated_at)
                values (?, ?, 'member', 'Member', 'system', true, now(), now())
                """,
            UUID.randomUUID(), workspaceId
        );
        return new WorkspaceAdmin(workspaceId, adminId, username);
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
