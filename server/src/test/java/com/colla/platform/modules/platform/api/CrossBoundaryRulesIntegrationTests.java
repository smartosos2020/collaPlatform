package com.colla.platform.modules.platform.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CrossBoundaryRulesIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void crossBoundarySearchAuditNotificationAndObjectCardsAreSeparated() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String adminToken = login("admin", "admin123456", "m11-admin-" + suffix);
        UUID memberId = createMember(adminToken, "m11member" + suffix, "M11 Member " + suffix);
        String memberToken = login("m11member" + suffix, "member123456", "m11-member-" + suffix);
        UUID workspaceId = currentWorkspaceId();

        mockMvc.perform(get("/api/admin/audit-logs?action=user.created&targetType=user&targetId=" + memberId + "&limit=5")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sourceUi").value("admin_console"))
            .andExpect(jsonPath("$[0].apiSurface").value("admin_governance"))
            .andExpect(jsonPath("$[0].context.sourceUi").value("admin_console"))
            .andExpect(jsonPath("$[0].metadata.requestPath").value("/api/admin/users"));

        mockMvc.perform(get("/api/admin/search-governance?q=permission&limit=10")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchScope").value("admin_governance"))
            .andExpect(jsonPath("$.items[*].governanceType").value(hasItem("permission")));

        mockMvc.perform(get("/api/admin/search-governance?q=permission&limit=10")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/search?q=permission&limit=10")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchScope").value("user_content"))
            .andExpect(jsonPath("$.items[*].objectType").value(not(hasItem("audit_log"))))
            .andExpect(jsonPath("$.items[*].objectType").value(not(hasItem("permission"))));

        insertNotification(workspaceId, memberId, "issue_assigned", "用户协作通知");
        insertNotification(workspaceId, memberId, "admin_permission_risk", "后台治理通知");

        mockMvc.perform(get("/api/notifications?limit=10")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].notificationType").value(hasItem("issue_assigned")))
            .andExpect(jsonPath("$[*].notificationType").value(not(hasItem("admin_permission_risk"))))
            .andExpect(jsonPath("$[*].notificationScope").value(hasItem("user_collaboration")));

        mockMvc.perform(get("/api/notifications/unread-count")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));

        UUID issueId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into object_links
                    (id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, created_at, updated_at)
                values (?, ?, 'issue', ?, ?, ?, 'M11 object card issue', now(), now())
                """,
            UUID.randomUUID(),
            workspaceId,
            issueId,
            "/issues/" + issueId,
            "colla://issue/" + issueId
        );

        mockMvc.perform(get("/api/platform/objects/issue/" + issueId + "/card?context=admin")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.presentationContext").value("admin"))
            .andExpect(jsonPath("$.actions[*].key").value(hasItem("inspect_permissions")))
            .andExpect(jsonPath("$.actions[*].key").value(hasItem("audit_logs")));

        mockMvc.perform(get("/api/platform/objects/issue/" + issueId + "/card?context=admin")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.presentationContext").value("user"))
            .andExpect(jsonPath("$.actions[*].key").value(not(hasItem("inspect_permissions"))))
            .andExpect(jsonPath("$.actions[*].key").value(not(hasItem("audit_logs"))));

        mockMvc.perform(get("/api/platform/objects/issue/" + issueId + "/permission-explanation?action=permission&context=admin")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actionCategory").value("object_management"))
            .andExpect(jsonPath("$.presentationContext").value("admin"))
            .andExpect(jsonPath("$.policySourceDetail").exists());
    }

    private UUID currentWorkspaceId() {
        return jdbcTemplate.queryForObject("select id from workspaces where slug = 'default'", UUID.class);
    }

    private void insertNotification(UUID workspaceId, UUID recipientId, String notificationType, String title) {
        jdbcTemplate.update(
            """
                insert into notifications
                    (id, workspace_id, recipient_id, notification_type, title, body, target_type, target_id, web_path, created_at)
                values (?, ?, ?, ?, ?, ?, 'issue', ?, '/issues/' || ?::text, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            recipientId,
            notificationType,
            title,
            title,
            UUID.randomUUID(),
            UUID.randomUUID()
        );
    }

    private UUID createMember(String token, String username, String displayName) throws Exception {
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "%s",
                          "password": "member123456",
                          "displayName": "%s",
                          "email": "%s@colla.local",
                          "roleCode": "member"
                        }
                        """.formatted(username, displayName, username)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String login(String username, String password, String deviceFingerprint) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "%s",
                          "password": "%s",
                          "deviceType": "web",
                          "deviceFingerprint": "%s",
                          "deviceName": "MockMvc",
                          "appVersion": "test"
                        }
                        """.formatted(username, password, deviceFingerprint)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
