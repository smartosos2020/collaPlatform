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
import org.springframework.test.web.servlet.MvcResult;

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
            .andExpect(jsonPath("$.issue.workflowReason").value("started"))
            .andExpect(jsonPath("$.activities[0].action").value("workflow.start_progress"))
            .andExpect(jsonPath("$.availableActions[?(@.key == 'mark_fixed')].label").value(hasItem("提交修复")));

        mockMvc.perform(get("/api/admin/audit-logs?action=issue.workflow.start_progress&targetType=issue&limit=5")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.targetId == '" + issueId + "')].action").value(hasItem("issue.workflow.start_progress")));

        mockMvc.perform(get("/api/admin/audit-logs?action=issue.workflow.start_progress&targetType=issue&targetId=" + issueId + "&limit=5")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].targetId").value(issueId.toString()));

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

        assertMentionNotificationEventuallyAvailable(bobToken, issueId);
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

        mockMvc.perform(get("/api/platform/objects/issue/" + issueId + "/permission-explanation?action=view")
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowed").value(false))
            .andExpect(jsonPath("$.accessState").value("forbidden"))
            .andExpect(jsonPath("$.source").value("project_members"));
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
            .andExpect(jsonPath("$.issue.workflowReason").value("verified"))
            .andExpect(jsonPath("$.issue.resolution").value("verified"))
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

    @Test
    void recordsFailedVerificationBeforeTheBugIsFixedAndVerified() throws Exception {
        String adminToken = login("admin", "admin123456", "project-reverify-device-" + UUID.randomUUID());
        String projectKey = projectKey("REV");
        String projectResponse = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "Reverification Project",
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
                .content("{\"issueType\":\"bug\",\"title\":\"Reverification target\",\"priority\":\"high\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID bugId = UUID.fromString(objectMapper.readTree(bugResponse).get("issue").get("id").asText());

        mockMvc.perform(post("/api/issues/" + bugId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"resolved\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/issues/" + bugId + "/verifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"failed\",\"note\":\"Regression still reproduces\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("in_progress"))
            .andExpect(jsonPath("$.issue.workflowReason").value("verification_failed"))
            .andExpect(jsonPath("$.verifications[0].result").value("failed"));

        mockMvc.perform(post("/api/issues/" + bugId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"resolved\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/issues/" + bugId + "/verifications")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"passed\",\"note\":\"Regression fixed\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("closed"))
            .andExpect(jsonPath("$.verifications[0].result").value("passed"))
            .andExpect(jsonPath("$.verifications[1].result").value("failed"));
    }

    @Test
    void issueWorkflowActionsCoverRequirementAndBugBranches() throws Exception {
        String adminToken = login("admin", "admin123456", "project-workflow-device-" + UUID.randomUUID());
        String projectKey = projectKey("WF");
        String projectResponse = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "Workflow Project",
                          "memberIds": []
                        }
                        """.formatted(projectKey)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID projectId = UUID.fromString(objectMapper.readTree(projectResponse).get("id").asText());

        String requirementResponse = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "requirement",
                          "title": "Checkout redesign",
                          "priority": "high"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableActions[?(@.key == 'request_info')].requiresReason").value(hasItem(true)))
            .andExpect(jsonPath("$.availableActions[?(@.key == 'delay')].requiresDueAt").value(hasItem(true)))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID requirementId = UUID.fromString(objectMapper.readTree(requirementResponse).get("issue").get("id").asText());

        mockMvc.perform(post("/api/issues/" + requirementId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "action": "request_info",
                          "reason": "business goal is incomplete"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("open"))
            .andExpect(jsonPath("$.issue.workflowReason").value("info_required"))
            .andExpect(jsonPath("$.issue.workflowNote").value("business goal is incomplete"));

        mockMvc.perform(post("/api/issues/" + requirementId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "action": "delay",
                          "reason": "waiting for design review",
                          "dueAt": "2026-07-03"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("in_progress"))
            .andExpect(jsonPath("$.issue.workflowReason").value("delayed"))
            .andExpect(jsonPath("$.issue.resolution").value("delayed"))
            .andExpect(jsonPath("$.issue.dueAt").value("2026-07-03"));

        mockMvc.perform(post("/api/issues/" + requirementId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "action": "cancel",
                          "reason": "campaign was canceled"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("closed"))
            .andExpect(jsonPath("$.issue.resolution").value("canceled"));

        String sourceBugResponse = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "bug",
                          "title": "Primary login bug",
                          "priority": "urgent"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID sourceBugId = UUID.fromString(objectMapper.readTree(sourceBugResponse).get("issue").get("id").asText());

        String duplicateBugResponse = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "bug",
                          "title": "Duplicate login bug",
                          "priority": "medium"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID duplicateBugId = UUID.fromString(objectMapper.readTree(duplicateBugResponse).get("issue").get("id").asText());

        mockMvc.perform(post("/api/issues/" + duplicateBugId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "action": "mark_duplicate",
                          "reason": "same reproduction path",
                          "targetIssueId": "%s"
                        }
                        """.formatted(sourceBugId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("closed"))
            .andExpect(jsonPath("$.issue.workflowReason").value("duplicate"))
            .andExpect(jsonPath("$.issue.resolution").value("duplicate"))
            .andExpect(jsonPath("$.relations[0].target.objectId").value(sourceBugId.toString()));

        String cannotReproduceBugResponse = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "bug",
                          "title": "Cannot reproduce target",
                          "priority": "low"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID cannotReproduceBugId = UUID.fromString(objectMapper.readTree(cannotReproduceBugResponse).get("issue").get("id").asText());

        mockMvc.perform(post("/api/issues/" + cannotReproduceBugId + "/transition")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "action": "cannot_reproduce",
                          "reason": "not reproducible on Chrome 126"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.status").value("closed"))
            .andExpect(jsonPath("$.issue.workflowReason").value("cannot_reproduce"))
            .andExpect(jsonPath("$.issue.resolution").value("cannot_reproduce"));
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

    private void assertMentionNotificationEventuallyAvailable(String token, UUID issueId) throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            domainEventWorker.processPendingEvents();
            MvcResult result = mockMvc.perform(get("/api/notifications?unreadOnly=true")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode notifications = objectMapper.readTree(result.getResponse().getContentAsString());
            for (JsonNode notification : notifications) {
                if (issueId.toString().equals(notification.path("targetId").asText())
                    && "issue_comment_mention".equals(notification.path("notificationType").asText())) {
                    return;
                }
            }
        }

        mockMvc.perform(get("/api/notifications?unreadOnly=true")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.targetId == '" + issueId + "')].notificationType")
                .value(hasItem("issue_comment_mention")));
    }

    private String projectKey(String prefix) {
        return (prefix + UUID.randomUUID().toString().replace("-", ""))
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
    }
}
