package com.colla.platform.modules.workspace.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
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
class WorkspaceControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainEventWorker domainEventWorker;

    @Test
    void dashboardNavigationNotificationAndCrossModuleFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "m7-admin-device-" + UUID.randomUUID());
        String viewerUsername = "m7user" + UUID.randomUUID().toString().substring(0, 8);
        UUID viewerId = createMember(adminToken, viewerUsername, "M7 Viewer");
        String viewerToken = login(viewerUsername, "member123456", "m7-viewer-device-" + UUID.randomUUID());

        JsonNode project = createProject(adminToken, viewerId);
        UUID projectId = UUID.fromString(project.get("id").asText());
        UUID conversationId = UUID.fromString(project.get("conversationId").asText());
        UUID issueId = createIssue(adminToken, projectId, viewerId);
        domainEventWorker.processPendingEvents();

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "M7 link /issues/%s"
                        }
                        """.formatted(UUID.randomUUID(), issueId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.links[0].summary.title", not(blankOrNullString())));

        mockMvc.perform(post("/api/issues/" + issueId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"please check @" + viewerUsername + "\"}"))
            .andExpect(status().isOk());
        domainEventWorker.processPendingEvents();

        UUID documentId = createDocument(adminToken, viewerId);
        mockMvc.perform(post("/api/docs/" + documentId + "/relations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"issue\",\"targetId\":\"" + issueId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[0].webPath").value("/issues/" + issueId));

        UUID baseId = createBase(adminToken, viewerId);

        mockMvc.perform(get("/api/platform/objects/issue/" + issueId + "/navigation")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.accessState").value("available"))
            .andExpect(jsonPath("$.webPath").value("/issues/" + issueId))
            .andExpect(jsonPath("$.mobileFallbackPath").value("/issues/" + issueId));

        mockMvc.perform(post("/api/platform/objects/issue/" + issueId + "/favorite")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", not(blankOrNullString())));

        mockMvc.perform(get("/api/platform/recent")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].objectType").value("issue"));

        mockMvc.perform(get("/api/platform/favorites")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].objectId").value(issueId.toString()));

        String notificationResponse = mockMvc.perform(get("/api/notifications?source=issue&status=unread&targetType=issue")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[0].targetType").value("issue"))
            .andExpect(jsonPath("$[0].sourceType").value("issue"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID notificationId = UUID.fromString(objectMapper.readTree(notificationResponse).get(0).get("id").asText());

        mockMvc.perform(get("/api/workspace/dashboard")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.myIssues[0].id").value(issueId.toString()))
            .andExpect(jsonPath("$.unreadMessageCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.unreadNotificationCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.latestNotifications.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.recentDocuments[0].id").value(documentId.toString()))
            .andExpect(jsonPath("$.recentBases[0].id").value(baseId.toString()))
            .andExpect(jsonPath("$.recentObjects[0].objectType").value("issue"))
            .andExpect(jsonPath("$.favoriteObjects[0].objectId").value(issueId.toString()));

        mockMvc.perform(post("/api/notifications/read-batch")
                .header("Authorization", "Bearer " + viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationIds\":[\"" + notificationId + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.changed").value(1));
    }

    private JsonNode createProject(String token, UUID memberId) throws Exception {
        String key = projectKey();
        String response = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "M7 Project",
                          "memberIds": ["%s"]
                        }
                        """.formatted(key, memberId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId", not(blankOrNullString())))
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
                          "title": "M7 Cross Module Bug",
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

    private UUID createDocument(String token, UUID viewerId) throws Exception {
        String response = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M7 Design Doc\",\"content\":\"M7\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(response).get("document").get("id").asText());
        mockMvc.perform(post("/api/docs/" + documentId + "/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + viewerId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk());
        return documentId;
    }

    private UUID createBase(String token, UUID viewerId) throws Exception {
        String response = mockMvc.perform(post("/api/bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"M7 Base\",\"description\":\"dashboard\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID baseId = UUID.fromString(objectMapper.readTree(response).get("base").get("id").asText());
        mockMvc.perform(post("/api/bases/" + baseId + "/members")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + viewerId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk());
        return baseId;
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

    private String projectKey() {
        return ("M7" + UUID.randomUUID().toString().replace("-", ""))
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
    }
}
