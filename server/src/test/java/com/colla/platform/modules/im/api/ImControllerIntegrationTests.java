package com.colla.platform.modules.im.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
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
class ImControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void conversationMessageMentionLinkAndUnreadFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "im-admin-device-" + UUID.randomUUID());
        String bobUsername = "bob" + UUID.randomUUID().toString().substring(0, 8);
        UUID bobId = createMember(adminToken, bobUsername, "Bob IM");
        UUID workspaceId = currentWorkspaceId();
        UUID issueId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into object_links
                    (id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, created_at, updated_at)
                values (?, ?, 'issue', ?, ?, ?, 'IM unread bug', now(), now())
                """,
            UUID.randomUUID(),
            workspaceId,
            issueId,
            "/issues/" + issueId,
            "colla://issue/" + issueId
        );

        String conversationResponse = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "group",
                          "title": "IM MVP",
                          "memberIds": ["%s"]
                        }
                        """.formatted(bobId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("IM MVP"))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(conversationResponse).get("id").asText());

        String messageResponse = mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "hi @%s see /issues/%s"
                        }
                        """.formatted(UUID.randomUUID(), bobUsername, issueId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("hi @" + bobUsername + " see /issues/" + issueId))
            .andExpect(jsonPath("$.mentions[0].username").value(bobUsername))
            .andExpect(jsonPath("$.links[0].summary.title").value("IM unread bug"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID messageId = UUID.fromString(objectMapper.readTree(messageResponse).get("id").asText());

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.items[0].id").value(messageId.toString()));

        String bobToken = login(bobUsername, "member123456", "im-bob-device-" + UUID.randomUUID());
        mockMvc.perform(get("/api/conversations")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(conversationId.toString()))
            .andExpect(jsonPath("$[0].unreadCount").value(1));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/read")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":\"" + messageId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void rejectsConversationAccessForNonMember() throws Exception {
        String adminToken = login("admin", "admin123456", "im-owner-device-" + UUID.randomUUID());
        UUID aliceId = createMember(adminToken, "alice" + UUID.randomUUID().toString().substring(0, 8), "Alice IM");
        String outsiderUsername = "outsider" + UUID.randomUUID().toString().substring(0, 8);
        createMember(adminToken, outsiderUsername, "Outsider IM");

        String conversationResponse = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "group",
                          "title": "Private IM",
                          "memberIds": ["%s"]
                        }
                        """.formatted(aliceId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(conversationResponse).get("id").asText());
        String outsiderToken = login(outsiderUsername, "member123456", "im-outsider-device-" + UUID.randomUUID());

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isForbidden());
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
                          "email": "%s@example.com",
                          "roleCode": "member"
                        }
                        """.formatted(username, displayName, username)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", not(blankOrNullString())))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID currentWorkspaceId() {
        return jdbcTemplate.queryForObject("select id from workspaces where slug = 'default'", UUID.class);
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
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }
}
