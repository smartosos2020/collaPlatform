package com.colla.platform.modules.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.event.application.DomainEventWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeviceControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainEventWorker domainEventWorker;

    @Test
    void multiClientDevicePushDeepLinkAndCatchUpFlow() throws Exception {
        Tokens admin = login("admin", "admin123456", "web", "m8-admin-" + UUID.randomUUID());
        String mobileUsername = "m8user" + UUID.randomUUID().toString().substring(0, 8);
        UUID mobileUserId = createMember(admin.accessToken(), mobileUsername, "M8 Mobile");
        Tokens webTokens = login(mobileUsername, "member123456", "web", "m8-web-" + UUID.randomUUID());
        Tokens mobileTokens = login(mobileUsername, "member123456", "android", "m8-android-" + UUID.randomUUID());

        mockMvc.perform(get("/api/devices")
                .header("Authorization", "Bearer " + mobileTokens.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$[?(@.current == true)].id").value(mobileTokens.deviceId().toString()));

        mockMvc.perform(post("/api/devices/" + mobileTokens.deviceId() + "/push-token")
                .header("Authorization", "Bearer " + mobileTokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "provider": "fake",
                          "token": "expo-push-token-for-m8"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("fake"))
            .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(post("/api/devices/" + mobileTokens.deviceId() + "/push-probe")
                .header("Authorization", "Bearer " + mobileTokens.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliverable").value(true))
            .andExpect(jsonPath("$.enabledTokenCount").value(1));

        JsonNode project = createProject(admin.accessToken(), mobileUserId);
        UUID projectId = UUID.fromString(project.get("id").asText());
        UUID conversationId = UUID.fromString(project.get("conversationId").asText());
        UUID issueId = createIssue(admin.accessToken(), projectId, mobileUserId);

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "mobile catch up /issues/%s"
                        }
                        """.formatted(UUID.randomUUID(), issueId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.links[0].summary.title", not(blankOrNullString())));

        mockMvc.perform(post("/api/issues/" + issueId + "/comments")
                .header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"mobile ping @" + mobileUsername + "\"}"))
            .andExpect(status().isOk());
        domainEventWorker.processPendingEvents();

        mockMvc.perform(post("/api/platform/links/resolve")
                .header("Authorization", "Bearer " + mobileTokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\":\"colla://issue/" + issueId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.accessState").value("available"))
            .andExpect(jsonPath("$.webPath").value("/issues/" + issueId))
            .andExpect(jsonPath("$.deepLink").value("colla://issue/" + issueId));

        String conversationsResponse = mockMvc.perform(get("/api/conversations")
                .header("Authorization", "Bearer " + mobileTokens.accessToken()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode conversations = objectMapper.readTree(conversationsResponse);
        int unreadCount = 0;
        for (JsonNode conversation : conversations) {
            if (conversationId.toString().equals(conversation.get("id").asText())) {
                unreadCount = conversation.get("unreadCount").asInt();
            }
        }
        assertThat(unreadCount).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/notifications?status=unread")
                .header("Authorization", "Bearer " + mobileTokens.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/issues/" + issueId)
                .header("Authorization", "Bearer " + mobileTokens.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.id").value(issueId.toString()));

        mockMvc.perform(delete("/api/devices/" + mobileTokens.deviceId())
                .header("Authorization", "Bearer " + mobileTokens.accessToken()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + mobileTokens.accessToken()))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + mobileTokens.refreshToken() + "\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + webTokens.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(mobileUsername));
    }

    private JsonNode createProject(String token, UUID memberId) throws Exception {
        String projectKey = ("M8" + UUID.randomUUID().toString().replace("-", ""))
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
        String response = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "M8 Project",
                          "memberIds": ["%s"]
                        }
                        """.formatted(projectKey, memberId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createIssue(String token, UUID projectId, UUID assigneeId) throws Exception {
        String response = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "bug",
                          "title": "M8 Mobile Readonly Issue",
                          "priority": "high",
                          "assigneeId": "%s"
                        }
                        """.formatted(assigneeId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("issue").get("id").asText());
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
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private Tokens login(String username, String password, String deviceType, String deviceFingerprint) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "%s",
                          "password": "%s",
                          "deviceType": "%s",
                          "deviceFingerprint": "%s",
                          "deviceName": "MockMvc %s",
                          "appVersion": "test"
                        }
                        """.formatted(username, password, deviceType, deviceFingerprint, deviceType)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deviceId", not(blankOrNullString())))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return new Tokens(
            json.get("accessToken").asText(),
            json.get("refreshToken").asText(),
            UUID.fromString(json.get("deviceId").asText())
        );
    }

    private record Tokens(String accessToken, String refreshToken, UUID deviceId) {
    }
}
