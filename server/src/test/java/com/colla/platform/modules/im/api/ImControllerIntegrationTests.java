package com.colla.platform.modules.im.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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

        String secondMessageResponse = mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "second unread message"
                        }
                        """.formatted(UUID.randomUUID())
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID secondMessageId = UUID.fromString(objectMapper.readTree(secondMessageResponse).get("id").asText());

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages/" + messageId + "/pin")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinnedAt", not(blankOrNullString())));

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(secondMessageId.toString()))
            .andExpect(jsonPath("$.items[1].id").value(messageId.toString()))
            .andExpect(jsonPath("$.items[1].pinnedAt", not(blankOrNullString())));

        String bobToken = login(bobUsername, "member123456", "im-bob-device-" + UUID.randomUUID());
        mockMvc.perform(get("/api/conversations")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(conversationId.toString()))
            .andExpect(jsonPath("$[0].unreadCount").value(2));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/read")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":\"" + secondMessageId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(0));

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

    @Test
    void togglesMessageReactionWithoutBlocking() throws Exception {
        String adminToken = login("admin", "admin123456", "im-reaction-admin-" + UUID.randomUUID());
        String aliceUsername = "reactalice" + UUID.randomUUID().toString().substring(0, 8);
        UUID aliceId = createMember(adminToken, aliceUsername, "Reaction Alice");

        String conversationResponse = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "group",
                          "title": "Reaction IM",
                          "memberIds": ["%s"]
                        }
                        """.formatted(aliceId)
                ))
            .andExpect(status().isOk())
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
                          "content": "reaction target"
                        }
                        """.formatted(UUID.randomUUID())
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID messageId = UUID.fromString(objectMapper.readTree(messageResponse).get("id").asText());
        String aliceToken = login(aliceUsername, "member123456", "im-reaction-alice-" + UUID.randomUUID());

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages/" + messageId + "/reactions")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"👍\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reactions[0].emoji").value("👍"))
            .andExpect(jsonPath("$.reactions[0].count").value(1))
            .andExpect(jsonPath("$.reactions[0].reactedByMe").value(true));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages/" + messageId + "/reactions")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"👍\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reactions.length()").value(0));
    }

    @Test
    void managesConversationMembersAndRevokedMessages() throws Exception {
        String adminToken = login("admin", "admin123456", "im-members-admin-" + UUID.randomUUID());
        String aliceUsername = "memberalice" + UUID.randomUUID().toString().substring(0, 8);
        String bobUsername = "memberbob" + UUID.randomUUID().toString().substring(0, 8);
        UUID aliceId = createMember(adminToken, aliceUsername, "Member Alice");
        UUID bobId = createMember(adminToken, bobUsername, "Member Bob");

        String directResponse = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "direct",
                          "title": "Direct Alice",
                          "memberIds": ["%s"]
                        }
                        """.formatted(aliceId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationType").value("direct"))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(directResponse).get("id").asText());

        mockMvc.perform(post("/api/conversations/" + conversationId + "/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberIds\":[\"" + bobId + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationType").value("group"))
            .andExpect(jsonPath("$.members.length()").value(3));

        String messageResponse = mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "message to revoke"
                        }
                        """.formatted(UUID.randomUUID())
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID messageId = UUID.fromString(objectMapper.readTree(messageResponse).get("id").asText());

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages/" + messageId + "/revoke")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.senderName", not(blankOrNullString())))
            .andExpect(jsonPath("$.content").value(""))
            .andExpect(jsonPath("$.revokedAt", not(blankOrNullString())));

        mockMvc.perform(delete("/api/conversations/" + conversationId + "/members/" + aliceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationType").value("group"))
            .andExpect(jsonPath("$.members.length()").value(2));

        String bobToken = login(bobUsername, "member123456", "im-members-bob-" + UUID.randomUUID());
        mockMvc.perform(post("/api/conversations/" + conversationId + "/leave")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations/" + conversationId)
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conversations/" + conversationId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationType").value("group"))
            .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void managesConversationPreferencesAndDirectCloseIsolation() throws Exception {
        String adminToken = login("admin", "admin123456", "im-preferences-admin-" + UUID.randomUUID());
        String aliceUsername = "prefalice" + UUID.randomUUID().toString().substring(0, 8);
        String bobUsername = "prefbob" + UUID.randomUUID().toString().substring(0, 8);
        UUID aliceId = createMember(adminToken, aliceUsername, "Preference Alice");
        UUID bobId = createMember(adminToken, bobUsername, "Preference Bob");

        String groupResponse = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "group",
                          "title": "Preference Group",
                          "memberIds": ["%s"]
                        }
                        """.formatted(aliceId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.muted").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID groupId = UUID.fromString(objectMapper.readTree(groupResponse).get("id").asText());

        mockMvc.perform(post("/api/conversations/" + groupId + "/mute")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"muted\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.muted").value(true));

        mockMvc.perform(post("/api/conversations/" + groupId + "/pin")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinnedAt", not(blankOrNullString())));

        mockMvc.perform(get("/api/conversations")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(groupId.toString()))
            .andExpect(jsonPath("$[0].muted").value(true))
            .andExpect(jsonPath("$[0].pinnedAt", not(blankOrNullString())));

        String aliceToken = login(aliceUsername, "member123456", "im-preferences-alice-" + UUID.randomUUID());
        mockMvc.perform(post("/api/conversations/" + groupId + "/leave")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations/" + groupId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].userId").value(adminUserId().toString()));

        String directResponse = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "direct",
                          "title": "Direct Bob",
                          "memberIds": ["%s"]
                        }
                        """.formatted(bobId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID directId = UUID.fromString(objectMapper.readTree(directResponse).get("id").asText());

        String oldMessageResponse = mockMvc.perform(post("/api/conversations/" + directId + "/messages")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "old direct history"
                        }
                        """.formatted(UUID.randomUUID())
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID oldMessageId = UUID.fromString(objectMapper.readTree(oldMessageResponse).get("id").asText());
        String bobToken = login(bobUsername, "member123456", "im-preferences-bob-" + UUID.randomUUID());

        mockMvc.perform(post("/api/conversations/" + directId + "/close")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations/" + directId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conversations/" + directId + "/messages")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(oldMessageId.toString()));

        String newDirectResponse = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "direct",
                          "title": "Direct Bob Again",
                          "memberIds": ["%s"]
                        }
                        """.formatted(bobId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(not(directId.toString())))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID newDirectId = UUID.fromString(objectMapper.readTree(newDirectResponse).get("id").asText());

        mockMvc.perform(get("/api/conversations/" + newDirectId + "/messages")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));
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

    private UUID adminUserId() {
        return jdbcTemplate.queryForObject("select id from users where username = 'admin'", UUID.class);
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
