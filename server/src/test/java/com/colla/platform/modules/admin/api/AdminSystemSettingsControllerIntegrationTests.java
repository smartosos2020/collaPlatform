package com.colla.platform.modules.admin.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class AdminSystemSettingsControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void administratorCanReadSystemSettingsAndExportMinimalAuditFields() throws Exception {
        String token = login("admin", "admin123456", "m7-admin-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/system-settings")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workspace.name").isNotEmpty())
            .andExpect(jsonPath("$.securityPolicy.passwordMinLength").value(8))
            .andExpect(jsonPath("$.runtime.activeSessionCount").isNumber());

        mockMvc.perform(get("/api/admin/audit-logs/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(content().string(containsString("id,created_at,actor,action,target_type,target_id")))
            .andExpect(content().string(not(containsString("user-agent"))));
    }

    @Test
    void memberCannotReadSystemSettingsOrExportAuditLogs() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String adminToken = login("admin", "admin123456", "m7-admin-create-" + UUID.randomUUID());
        String username = "m7member" + suffix;
        createMember(adminToken, username, "M7 Member");
        String memberToken = login(username, "member123456", "m7-member-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/system-settings")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit-logs/export")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void batchGovernancePreviewsChecksAndExecutesUserDisable() throws Exception {
        String adminToken = login("admin", "admin123456", "m7-batch-admin-" + UUID.randomUUID());
        UUID first = createMember(adminToken, "m7batcha" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), "Batch A");
        UUID second = createMember(adminToken, "m7batchb" + UUID.randomUUID().toString().replace("-", "").substring(0, 8), "Batch B");
        String command = "{\"resourceType\":\"users\",\"action\":\"disable\",\"targetIds\":[\"" + first + "\",\"" + second + "\"]}";

        mockMvc.perform(post("/api/admin/batch-governance/preview")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executed").value(false))
            .andExpect(jsonPath("$.readyCount").value(2))
            .andExpect(jsonPath("$.items[0].status").value("ready"));

        mockMvc.perform(post("/api/admin/batch-governance/execute?confirm=true")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executed").value(true))
            .andExpect(jsonPath("$.items[*].status", hasItem("ready")));

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + first + "')].status", hasItem("disabled")))
            .andExpect(jsonPath("$[?(@.id == '" + second + "')].status", hasItem("disabled")));
    }

    @Test
    void auditExportNeutralizesSpreadsheetFormulaPrefixes() throws Exception {
        String adminToken = login("admin", "admin123456", "m7-csv-admin-" + UUID.randomUUID());
        UUID workspaceId = jdbcTemplate.queryForObject("select workspace_id from users where username = 'admin'", UUID.class);
        UUID actorId = jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
        jdbcTemplate.update(
            "insert into audit_logs (id, workspace_id, actor_id, action, target_type, target_id, metadata, created_at) values (?, ?, ?, ?, ?, ?, '{}'::jsonb, now())",
            UUID.randomUUID(), workspaceId, actorId, "=HYPERLINK(\"http://evil\")", "user", actorId
        );

        mockMvc.perform(get("/api/admin/audit-logs/export")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"'=HYPERLINK(\"\"http://evil\"\")\"")));
    }

    @Test
    void offboardingTransfersOpenConversationDisablesAccountAndRecordsAudit() throws Exception {
        String adminToken = login("admin", "admin123456", "m7-offboard-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String leavingUsername = "m7leaving" + suffix;
        String handoverUsername = "m7handover" + suffix;
        UUID leavingUserId = createMember(adminToken, leavingUsername, "Leaving Member");
        UUID handoverUserId = createMember(adminToken, handoverUsername, "Handover Member");
        UUID workspaceId = jdbcTemplate.queryForObject("select workspace_id from users where username = 'admin'", UUID.class);
        UUID conversationId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into conversations (id, workspace_id, conversation_type, title, owner_id, created_by, created_at, updated_at)
                values (?, ?, 'group', 'M7 handover', ?, ?, now(), now())
                """,
            conversationId, workspaceId, leavingUserId, leavingUserId
        );

        mockMvc.perform(post("/api/admin/users/" + leavingUserId + "/offboard")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"handoverToUserId\":\"" + handoverUserId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationCount").value(1));

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + leavingUserId + "')].status", hasItem("disabled")));
        mockMvc.perform(get("/api/admin/audit-logs?action=user.offboarded&targetType=user&targetId=" + leavingUserId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].metadata.conversationCount").value(1));
        UUID ownerId = jdbcTemplate.queryForObject("select owner_id from conversations where id = ?", UUID.class, conversationId);
        org.junit.jupiter.api.Assertions.assertEquals(handoverUserId, ownerId);
    }

    private UUID createMember(String adminToken, String username, String displayName) throws Exception {
        String body = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"member123456\",\"displayName\":\"" + displayName + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"deviceType\":\"web\",\"deviceFingerprint\":\"" + fingerprint + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("accessToken").asText();
    }
}
