package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.project.application.ProjectSpaceMigrationService;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationBatchRecord;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Set;
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
class ProjectSpaceLegacyResolveIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectSpaceMigrationService migrationService;

    @Test
    void resolvesMappedSpaceForSpaceMember() throws Exception {
        WorkspaceFixture fixture = createWorkspace("lrmem");
        TestUser owner = insertLoginableUser(fixture.workspaceId(), "lrmown");
        TestUser member = insertLoginableUser(fixture.workspaceId(), "lrmmem");
        UUID projectId = insertProject(fixture.workspaceId(), "LRM-" + suffix(), fixture.adminUserId());
        insertProjectMember(fixture.workspaceId(), projectId, owner.userId(), "owner", fixture.adminUserId());
        insertProjectMember(fixture.workspaceId(), projectId, member.userId(), "member", fixture.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(fixture));
        assertThat(batch.status()).isEqualTo("completed");

        String memberToken = login(member.username(), "admin123456", "lr-member-" + UUID.randomUUID());
        mockMvc.perform(get("/api/project-spaces/legacy-resolve/{projectId}", projectId)
                .header("Authorization", bearer(memberToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("mapped"))
            .andExpect(jsonPath("$.spaceId").value(deterministicSpaceId(projectId).toString()));
    }

    @Test
    void hidesSpaceIdFromNonMemberOfPrivateSpace() throws Exception {
        WorkspaceFixture fixture = createWorkspace("lrprv");
        TestUser owner = insertLoginableUser(fixture.workspaceId(), "lrpown");
        TestUser outsider = insertLoginableUser(fixture.workspaceId(), "lrpout");
        UUID projectId = insertProject(fixture.workspaceId(), "LRP-" + suffix(), fixture.adminUserId());
        insertProjectMember(fixture.workspaceId(), projectId, owner.userId(), "owner", fixture.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(fixture));
        assertThat(batch.status()).isEqualTo("completed");

        String outsiderToken = login(outsider.username(), "admin123456", "lr-outsider-" + UUID.randomUUID());
        String body = mockMvc.perform(get("/api/project-spaces/legacy-resolve/{projectId}", projectId)
                .header("Authorization", bearer(outsiderToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("unavailable"))
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).hasNonNull("spaceId")).isFalse();
    }

    @Test
    void resolvesMappedSpaceForNonMemberOfDiscoverableSpace() throws Exception {
        WorkspaceFixture fixture = createWorkspace("lrdsc");
        TestUser owner = insertLoginableUser(fixture.workspaceId(), "lrdown");
        TestUser outsider = insertLoginableUser(fixture.workspaceId(), "lrdout");
        UUID projectId = insertProject(fixture.workspaceId(), "LRD-" + suffix(), fixture.adminUserId());
        insertProjectMember(fixture.workspaceId(), projectId, owner.userId(), "owner", fixture.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(fixture));
        assertThat(batch.status()).isEqualTo("completed");
        jdbcTemplate.update(
            "update project_spaces set visibility = 'discoverable' where workspace_id = ? and id = ?",
            fixture.workspaceId(), deterministicSpaceId(projectId)
        );

        String outsiderToken = login(outsider.username(), "admin123456", "lr-discover-" + UUID.randomUUID());
        mockMvc.perform(get("/api/project-spaces/legacy-resolve/{projectId}", projectId)
                .header("Authorization", bearer(outsiderToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("mapped"))
            .andExpect(jsonPath("$.spaceId").value(deterministicSpaceId(projectId).toString()));
    }

    @Test
    void reportsUnmigratedForLegacyProjectWithoutMap() throws Exception {
        WorkspaceFixture fixture = createWorkspace("lrnon");
        TestUser user = insertLoginableUser(fixture.workspaceId(), "lruusr");
        UUID projectId = insertProject(fixture.workspaceId(), "LRU-" + suffix(), fixture.adminUserId());

        String token = login(user.username(), "admin123456", "lr-unmigrated-" + UUID.randomUUID());
        String body = mockMvc.perform(get("/api/project-spaces/legacy-resolve/{projectId}", projectId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("unmigrated"))
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).hasNonNull("spaceId")).isFalse();
    }

    @Test
    void reportsFailedForRolledBackMap() throws Exception {
        WorkspaceFixture fixture = createWorkspace("lrfai");
        TestUser owner = insertLoginableUser(fixture.workspaceId(), "lrfown");
        UUID projectId = insertProject(fixture.workspaceId(), "LRF-" + suffix(), fixture.adminUserId());
        insertProjectMember(fixture.workspaceId(), projectId, owner.userId(), "owner", fixture.adminUserId());
        MigrationBatchRecord batch = migrationService.execute(adminUser(fixture));
        migrationService.rollback(adminUser(fixture), batch.id());

        String token = login(owner.username(), "admin123456", "lr-failed-" + UUID.randomUUID());
        String body = mockMvc.perform(get("/api/project-spaces/legacy-resolve/{projectId}", projectId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("failed"))
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).hasNonNull("spaceId")).isFalse();
    }

    @Test
    void reportsUnmigratedForUnknownProjectId() throws Exception {
        WorkspaceFixture fixture = createWorkspace("lrunk");
        TestUser user = insertLoginableUser(fixture.workspaceId(), "lrkusr");

        String token = login(user.username(), "admin123456", "lr-unknown-" + UUID.randomUUID());
        mockMvc.perform(get("/api/project-spaces/legacy-resolve/{projectId}", UUID.randomUUID())
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("unmigrated"));
    }

    @Test
    void rejectsAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/project-spaces/legacy-resolve/{projectId}", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    private CurrentUser adminUser(WorkspaceFixture fixture) {
        return new CurrentUser(
            fixture.adminUserId(), fixture.workspaceId(), null,
            fixture.adminUsername(), "Legacy Resolve Admin", Set.of("admin"), Set.of()
        );
    }

    private UUID deterministicSpaceId(UUID projectId) {
        return UUID.nameUUIDFromBytes(
            ("colla:project-legacy-space:" + projectId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private WorkspaceFixture createWorkspace(String usernamePrefix) {
        UUID workspaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            workspaceId, "Legacy Resolve Workspace", "lr-" + suffix()
        );
        UUID adminId = UUID.randomUUID();
        String username = usernamePrefix + suffix();
        UUID seedAdminId = jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
        jdbcTemplate.update(
            """
                insert into users (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                select ?, ?, ?, password_hash, 'Legacy Resolve Admin', 'active', now(), now()
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
        return new WorkspaceFixture(workspaceId, adminId, username);
    }

    private TestUser insertLoginableUser(UUID workspaceId, String usernamePrefix) {
        UUID userId = UUID.randomUUID();
        String username = usernamePrefix + suffix();
        UUID seedAdminId = jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
        jdbcTemplate.update(
            """
                insert into users (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                select ?, ?, ?, password_hash, 'Legacy Resolve User', 'active', now(), now()
                  from users where id = ?
                """,
            userId, workspaceId, username, seedAdminId
        );
        return new TestUser(userId, username);
    }

    private UUID insertProject(UUID workspaceId, String projectKey, UUID actorId) {
        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into projects (id, workspace_id, project_key, name, description, status, conversation_id,
                                      created_by, created_at, updated_by, updated_at, archived_at)
                values (?, ?, ?, ?, 'legacy resolve test', 'active', null, ?, now(), ?, now(), null)
                """,
            projectId, workspaceId, projectKey, "Legacy Resolve " + projectKey, actorId, actorId
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

    private record WorkspaceFixture(UUID workspaceId, UUID adminUserId, String adminUsername) {
    }

    private record TestUser(UUID userId, String username) {
    }
}
