package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectSpaceControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerCreatesUpdatesAndTransitionsSpaceWithAuditAndPlatformObject() throws Exception {
        String adminToken = login("admin", "admin123456", "space-owner-" + UUID.randomUUID());
        String key = key("owner");
        UUID spaceId = createSpace(adminToken, key, "Owner Space", "private");

        mockMvc.perform(get("/api/project-spaces/" + spaceId)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spaceKey").value(key))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.visibility").value("private"))
            .andExpect(jsonPath("$.currentUserRole").value("owner"))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.availableActions", hasItem("settings")));

        mockMvc.perform(get("/api/platform/objects/project_space/" + spaceId + "/summary")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("available"))
            .andExpect(jsonPath("$.title").value("Owner Space"))
            .andExpect(jsonPath("$.webPath").value("/project-spaces/" + spaceId));

        mockMvc.perform(patch("/api/project-spaces/" + spaceId + "/settings")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Renamed Space","description":"updated","visibility":"discoverable"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed Space"))
            .andExpect(jsonPath("$.visibility").value("discoverable"))
            .andExpect(jsonPath("$.version").value(1));

        transitionSettings(adminToken, spaceId, "disable", "disabled");
        transitionSettings(adminToken, spaceId, "restore", "active");
        String archiveRequestId = "space-archive-" + suffix();
        transitionSettings(adminToken, spaceId, "archive", "archived", archiveRequestId);

        mockMvc.perform(get("/api/project-spaces")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", not(hasItem(spaceId.toString()))));

        mockMvc.perform(patch("/api/project-spaces/" + spaceId + "/settings")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Cannot Edit Archived","visibility":"private"}
                    """))
            .andExpect(status().isConflict());

        transitionSettings(adminToken, spaceId, "restore", "active");

        mockMvc.perform(get("/api/admin/audit-logs?action=project_space.archived&targetType=project_space&targetId=" + spaceId + "&limit=5")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].action").value("project_space.archived"))
            .andExpect(jsonPath("$[0].metadata.previousStatus").value("active"))
            .andExpect(jsonPath("$[0].metadata.currentStatus").value("archived"))
            .andExpect(jsonPath("$[0].metadata.source").value("space_settings"))
            .andExpect(jsonPath("$[0].metadata.requestId").value(archiveRequestId))
            .andExpect(jsonPath("$[0].metadata.requestPath", containsString("/settings/archive")));
    }

    @Test
    void enforcesOwnerAdminMemberGuestAndNonMemberRoleMatrix() throws Exception {
        String enterpriseAdminToken = login("admin", "admin123456", "space-role-root-" + UUID.randomUUID());
        UUID ownerId = createMember(enterpriseAdminToken, "roleowner" + suffix(), "Role Owner");
        String ownerUsername = jdbcTemplate.queryForObject("select username from users where id = ?", String.class, ownerId);
        String ownerToken = login(ownerUsername, "member123456", "space-role-owner-" + UUID.randomUUID());
        UUID spaceId = createSpace(ownerToken, key("roles"), "Role Matrix Space", "private");

        UUID adminId = createMember(enterpriseAdminToken, "roleadmin" + suffix(), "Space Admin");
        UUID memberId = createMember(enterpriseAdminToken, "rolemember" + suffix(), "Space Member");
        UUID guestId = createMember(enterpriseAdminToken, "roleguest" + suffix(), "Space Guest");
        UUID outsiderId = createMember(enterpriseAdminToken, "roleout" + suffix(), "Space Outsider");
        addSpaceMember(spaceId, adminId, "admin", ownerId);
        addSpaceMember(spaceId, memberId, "member", ownerId);
        addSpaceMember(spaceId, guestId, "guest", ownerId);

        String adminToken = login(username(adminId), "member123456", "space-role-admin-" + UUID.randomUUID());
        String memberToken = login(username(memberId), "member123456", "space-role-member-" + UUID.randomUUID());
        String guestToken = login(username(guestId), "member123456", "space-role-guest-" + UUID.randomUUID());
        String outsiderToken = login(username(outsiderId), "member123456", "space-role-outsider-" + UUID.randomUUID());

        assertSpaceRole(ownerToken, spaceId, "owner");
        assertSpaceRole(adminToken, spaceId, "admin");
        assertSpaceRole(memberToken, spaceId, "member");
        assertSpaceRole(guestToken, spaceId, "guest");

        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/settings")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/settings")
                .header("Authorization", bearer(memberToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/settings")
                .header("Authorization", bearer(guestToken)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/project-spaces/" + spaceId)
                .header("Authorization", bearer(outsiderToken)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/project-spaces/" + spaceId)
                .header("Authorization", bearer(enterpriseAdminToken)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/project-spaces/" + spaceId)
                .header("Authorization", bearer(enterpriseAdminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentAccessGranted").value(false));
    }

    @Test
    void visibilitySettingsAndEnterpriseGovernanceRemainIndependent() throws Exception {
        String adminToken = login("admin", "admin123456", "space-governor-" + UUID.randomUUID());
        String ownerUsername = "sowner" + suffix();
        createMember(adminToken, ownerUsername, "Space Owner");
        String ownerToken = login(ownerUsername, "member123456", "space-member-owner-" + UUID.randomUUID());
        String outsiderUsername = "sout" + suffix();
        createMember(adminToken, outsiderUsername, "Space Outsider");
        String outsiderToken = login(outsiderUsername, "member123456", "space-outsider-" + UUID.randomUUID());
        UUID spaceId = createSpace(ownerToken, key("private"), "Private Member Space", "private");

        mockMvc.perform(get("/api/project-spaces/" + spaceId)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/project-spaces/" + spaceId)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentAccessGranted").value(false))
            .andExpect(jsonPath("$.governancePermission").value("project.manage"));

        mockMvc.perform(get("/api/admin/project-spaces/" + spaceId + "/permission-explanation")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.governanceAllowed").value(true))
            .andExpect(jsonPath("$.contentAccessGranted").value(false))
            .andExpect(jsonPath("$.contentAccessSource").value("none"));

        mockMvc.perform(post("/api/admin/project-spaces/" + spaceId + "/disable")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("disabled"))
            .andExpect(jsonPath("$.contentAccessGranted").value(false));

        mockMvc.perform(get("/api/project-spaces/" + spaceId)
                .header("Authorization", bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("disabled"))
            .andExpect(jsonPath("$.currentUserRole").value("owner"));

        transitionSettings(ownerToken, spaceId, "restore", "active");
        mockMvc.perform(patch("/api/project-spaces/" + spaceId + "/settings")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Discoverable Space","description":"metadata visible","visibility":"discoverable"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/project-spaces")
                .header("Authorization", bearer(outsiderToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(spaceId.toString())));

        mockMvc.perform(get("/api/project-spaces/" + spaceId)
                .header("Authorization", bearer(outsiderToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.member").value(false))
            .andExpect(jsonPath("$.currentUserRole").doesNotExist());

        mockMvc.perform(get("/api/project-spaces/" + spaceId + "/settings")
                .header("Authorization", bearer(outsiderToken)))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/project-spaces")
                .header("Authorization", bearer(outsiderToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void validatesContractsAndEnforcesWorkspaceIsolationInQueriesAndForeignKeys() throws Exception {
        String adminToken = login("admin", "admin123456", "space-contract-" + UUID.randomUUID());
        String duplicateKey = key("duplicate");
        UUID localSpaceId = createSpace(adminToken, duplicateKey, "Contract Space", "workspace");

        mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"spaceKey":"%s","name":"Duplicate","visibility":"workspace"}
                    """.formatted(duplicateKey)))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"spaceKey":"INVALID KEY","name":"Invalid","visibility":"workspace"}
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"spaceKey":"invalid-visibility","name":"Invalid","visibility":"public"}
                    """))
            .andExpect(status().isBadRequest());

        UUID foreignWorkspaceId = UUID.randomUUID();
        UUID foreignUserId = UUID.randomUUID();
        UUID foreignSpaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            foreignWorkspaceId, "Foreign Workspace", "foreign-" + suffix()
        );
        jdbcTemplate.update(
            """
                insert into users
                    (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                values (?, ?, ?, 'unused-test-hash', 'Foreign User', 'active', now(), now())
                """,
            foreignUserId, foreignWorkspaceId, "foreign" + suffix()
        );
        jdbcTemplate.update(
            """
                insert into project_spaces
                    (id, workspace_id, space_key, name, status, visibility, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, 'Foreign Space', 'active', 'workspace', ?, now(), ?, now())
                """,
            foreignSpaceId, foreignWorkspaceId, key("foreign"), foreignUserId, foreignUserId
        );

        mockMvc.perform(get("/api/project-spaces")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(localSpaceId.toString())))
            .andExpect(jsonPath("$[*].id", not(hasItem(foreignSpaceId.toString()))));

        mockMvc.perform(get("/api/admin/project-spaces?includeArchived=true")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", not(hasItem(foreignSpaceId.toString()))));

        UUID defaultWorkspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from project_spaces where id = ?", UUID.class, localSpaceId
        );
        UUID adminId = jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
                insert into project_space_members
                    (id, workspace_id, space_id, user_id, status, joined_at, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            UUID.randomUUID(), defaultWorkspaceId, localSpaceId, foreignUserId, adminId, adminId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID createSpace(String token, String key, String name, String visibility) throws Exception {
        String response = mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"spaceKey":"%s","name":"%s","description":"integration test","visibility":"%s"}
                    """.formatted(key, name, visibility)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentUserRole").value("owner"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void transitionSettings(String token, UUID spaceId, String action, String expectedStatus) throws Exception {
        transitionSettings(token, spaceId, action, expectedStatus, null);
    }

    private void transitionSettings(
        String token,
        UUID spaceId,
        String action,
        String expectedStatus,
        String requestId
    ) throws Exception {
        var request = post("/api/project-spaces/" + spaceId + "/settings/" + action)
            .header("Authorization", bearer(token));
        if (requestId != null) {
            request.header("X-Colla-Request-Id", requestId);
        }
        mockMvc.perform(request)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    private void assertSpaceRole(String token, UUID spaceId, String role) throws Exception {
        mockMvc.perform(get("/api/project-spaces/" + spaceId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentUserRole").value(role));
    }

    private void addSpaceMember(UUID spaceId, UUID userId, String role, UUID actorId) {
        UUID workspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from project_spaces where id = ?", UUID.class, spaceId
        );
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_space_members
                    (id, workspace_id, space_id, user_id, status, joined_at, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId, workspaceId, spaceId, userId, actorId, actorId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments
                    (id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at)
                values (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, memberId, role, actorId
        );
    }

    private String username(UUID userId) {
        return jdbcTemplate.queryForObject("select username from users where id = ?", String.class, userId);
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

    private String key(String prefix) {
        return prefix + "-" + suffix();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
