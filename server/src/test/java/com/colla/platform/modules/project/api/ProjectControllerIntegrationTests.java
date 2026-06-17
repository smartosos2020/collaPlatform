package com.colla.platform.modules.project.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ProjectControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainEventWorker domainEventWorker;

    @Test
    void projectIssueTransitionCommentMentionAndNotificationFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "project-admin-device-" + UUID.randomUUID());
        String bobUsername = "pmember" + UUID.randomUUID().toString().substring(0, 8);
        UUID bobId = createMember(adminToken, bobUsername, "Project Member");
        String bobToken = login(bobUsername, "member123456", "project-member-device-" + UUID.randomUUID());
        String projectKey = projectKey("M4");
        String expectedIssueKey = projectKey + "-1";

        String projectResponse = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "M4 Project",
                          "description": "Project bug MVP",
                          "memberIds": ["%s"]
                        }
                        """.formatted(projectKey, bobId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectKey").value(projectKey))
            .andExpect(jsonPath("$.conversationId", not(blankOrNullString())))
            .andExpect(jsonPath("$.members.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode projectJson = objectMapper.readTree(projectResponse);
        UUID projectId = UUID.fromString(projectJson.get("id").asText());
        UUID conversationId = UUID.fromString(projectJson.get("conversationId").asText());

        String issueResponse = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "bug",
                          "title": "Login button fails",
                          "description": "Submit does not respond",
                          "priority": "high",
                          "assigneeId": "%s"
                        }
                        """.formatted(bobId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.issueType").value("bug"))
            .andExpect(jsonPath("$.issue.issueKey").value(expectedIssueKey))
            .andExpect(jsonPath("$.issue.status").value("open"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID issueId = UUID.fromString(objectMapper.readTree(issueResponse).get("issue").get("id").asText());

        mockMvc.perform(get("/api/projects/" + projectId + "/issues?status=open&priority=high&assigneeId=" + bobId + "&sort=priority_desc")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].issueKey").value(expectedIssueKey))
            .andExpect(jsonPath("$[0].priority").value("high"));

        mockMvc.perform(get("/api/platform/objects/issue/" + issueId + "/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("available"))
            .andExpect(jsonPath("$.title").value(expectedIssueKey + " Login button fails"));

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.items[0].links[0].summary.title").value(expectedIssueKey + " Login button fails"));

        mockMvc.perform(post("/api/issues/" + issueId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"in_progress\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("in_progress"))
            .andExpect(jsonPath("$.activities[0].action").value("transitioned"));

        mockMvc.perform(get("/api/admin/audit-logs?action=issue.transitioned&targetType=issue&limit=5")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.targetId == '" + issueId + "')].action").value(hasItem("issue.transitioned")));

        mockMvc.perform(post("/api/issues/" + issueId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"invalid\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/issues/" + issueId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"please verify @" + bobUsername + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].content").value("please verify @" + bobUsername));

        domainEventWorker.processPendingEvents();

        mockMvc.perform(get("/api/notifications?unreadOnly=true")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void rejectsIssueAccessForNonProjectMember() throws Exception {
        String adminToken = login("admin", "admin123456", "project-owner-device-" + UUID.randomUUID());
        String outsiderUsername = "pout" + UUID.randomUUID().toString().substring(0, 8);
        createMember(adminToken, outsiderUsername, "Project Outsider");
        String outsiderToken = login(outsiderUsername, "member123456", "project-outsider-device-" + UUID.randomUUID());
        String projectKey = projectKey("SEC");

        String projectResponse = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "Private Project",
                          "memberIds": []
                        }
                        """.formatted(projectKey)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID projectId = UUID.fromString(objectMapper.readTree(projectResponse).get("id").asText());

        String issueResponse = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "task",
                          "title": "Private task",
                          "priority": "medium"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID issueId = UUID.fromString(objectMapper.readTree(issueResponse).get("issue").get("id").asText());

        mockMvc.perform(get("/api/issues/" + issueId)
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/objects/issue/" + issueId + "/summary")
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("forbidden"));
    }

    @Test
    void verifiesResolvedBugAndClosesIt() throws Exception {
        String adminToken = login("admin", "admin123456", "project-verify-device-" + UUID.randomUUID());
        String projectKey = projectKey("VRF");
        String projectResponse = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "Verification Project",
                          "memberIds": []
                        }
                        """.formatted(projectKey)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID projectId = UUID.fromString(objectMapper.readTree(projectResponse).get("id").asText());

        String bugResponse = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "bug",
                          "title": "Verification target",
                          "priority": "high"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID bugId = UUID.fromString(objectMapper.readTree(bugResponse).get("issue").get("id").asText());

        mockMvc.perform(post("/api/issues/" + bugId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"resolved\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("resolved"));

        mockMvc.perform(post("/api/issues/" + bugId + "/verifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "result": "passed",
                          "note": "local verification passed",
                          "environment": "chrome-local",
                          "reproductionSteps": "open login and submit",
                          "fixVersion": "m27"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("closed"))
            .andExpect(jsonPath("$.verifications[0].result").value("passed"))
            .andExpect(jsonPath("$.verifications[0].note").value("local verification passed"))
            .andExpect(jsonPath("$.verifications[0].environment").value("chrome-local"))
            .andExpect(jsonPath("$.verifications[0].reproductionSteps").value("open login and submit"))
            .andExpect(jsonPath("$.verifications[0].fixVersion").value("m27"));

        String taskResponse = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "task",
                          "title": "Non bug verification target",
                          "priority": "medium"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID taskId = UUID.fromString(objectMapper.readTree(taskResponse).get("issue").get("id").asText());

        mockMvc.perform(post("/api/issues/" + bugId + "/relations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"issue\",\"targetId\":\"" + taskId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[0].target.objectType").value("issue"))
            .andExpect(jsonPath("$.relations[0].target.objectId").value(taskId.toString()))
            .andExpect(jsonPath("$.relations[0].target.accessState").value("available"));

        mockMvc.perform(post("/api/issues/" + taskId + "/verifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"passed\"}"))
            .andExpect(status().isBadRequest());
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

    private String projectKey(String prefix) {
        return (prefix + UUID.randomUUID().toString().replace("-", ""))
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
    }
}
