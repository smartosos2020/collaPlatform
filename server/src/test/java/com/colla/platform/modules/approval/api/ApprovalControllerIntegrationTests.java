package com.colla.platform.modules.approval.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.event.application.DomainEventWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApprovalControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainEventWorker domainEventWorker;

    @Test
    void approvalTodoNotificationImCardAndActionsFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "m10-admin-" + UUID.randomUUID());
        UUID adminId = currentUserId(adminToken);
        String applicantUsername = "m10app" + UUID.randomUUID().toString().substring(0, 8);
        UUID applicantId = createMember(adminToken, applicantUsername, "M10 Applicant");
        String applicantToken = login(applicantUsername, "member123456", "m10-applicant-" + UUID.randomUUID());
        String reviewerUsername = "m10rev" + UUID.randomUUID().toString().substring(0, 8);
        UUID reviewerId = createMember(adminToken, reviewerUsername, "M10 Reviewer");
        String reviewerToken = login(reviewerUsername, "member123456", "m10-reviewer-" + UUID.randomUUID());

        UUID leaveFormId = leaveFormId(applicantToken);
        UUID approvedInstanceId = startLeave(applicantToken, leaveFormId, "M10 请假审批");
        domainEventWorker.processPendingEvents();

        mockMvc.perform(get("/api/approvals/todos")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].instanceId").value(approvedInstanceId.toString()))
            .andExpect(jsonPath("$[0].status").value("pending"));

        mockMvc.perform(get("/api/notifications?status=unread&targetType=approval")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].targetId", hasItem(approvedInstanceId.toString())));

        UUID conversationId = createConversation(applicantToken, adminId);
        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "审批链接 /approvals/%s"
                        }
                        """.formatted(UUID.randomUUID(), approvedInstanceId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.links[0].targetType").value("approval"))
            .andExpect(jsonPath("$.links[0].summary.title").value("M10 请假审批"));

        mockMvc.perform(post("/api/approvals/instances/" + approvedInstanceId + "/approve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"同意\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instance.status").value("approved"))
            .andExpect(jsonPath("$.actions[*].action", hasItem("approved")));

        UUID withdrawnInstanceId = startLeave(applicantToken, leaveFormId, "M10 撤回审批");
        mockMvc.perform(post("/api/approvals/instances/" + withdrawnInstanceId + "/withdraw")
                .header("Authorization", "Bearer " + applicantToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"计划调整\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instance.status").value("withdrawn"));

        UUID rejectedInstanceId = startLeave(applicantToken, leaveFormId, "M10 转交拒绝审批");
        mockMvc.perform(post("/api/approvals/instances/" + rejectedInstanceId + "/transfer")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":\"" + reviewerId + "\",\"comment\":\"请代审\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks[0].assigneeId").value(reviewerId.toString()))
            .andExpect(jsonPath("$.actions[*].action", hasItem("transferred")));

        mockMvc.perform(post("/api/approvals/instances/" + rejectedInstanceId + "/reject")
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"信息不足\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instance.status").value("rejected"));

        mockMvc.perform(get("/api/approvals/stats")
                .header("Authorization", "Bearer " + applicantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approved").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.rejected").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.withdrawn").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.byForm[*].key", hasItem("leave")));

        mockMvc.perform(get("/api/workspace/dashboard")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalTodos.length()", greaterThanOrEqualTo(0)));

        mockMvc.perform(get("/api/admin/audit-logs?targetType=approval")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("approval.approved")));
    }

    private UUID leaveFormId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/approvals/forms")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].formKey", hasItem("leave")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        for (JsonNode form : objectMapper.readTree(response)) {
            if ("leave".equals(form.get("formKey").asText())) {
                return UUID.fromString(form.get("id").asText());
            }
        }
        throw new IllegalStateException("leave form not found");
    }

    private UUID startLeave(String token, UUID formId, String title) throws Exception {
        String response = mockMvc.perform(post("/api/approvals/instances")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "formId": "%s",
                          "title": "%s",
                          "payload": {
                            "leaveType": "年假",
                            "startAt": "2026-06-15T09:00:00",
                            "endAt": "2026-06-15T18:00:00",
                            "reason": "M10 integration"
                          }
                        }
                        """.formatted(formId, title)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.instance.id", not(blankOrNullString())))
            .andExpect(jsonPath("$.instance.status").value("pending"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("instance").get("id").asText());
    }

    private UUID createConversation(String token, UUID memberId) throws Exception {
        String response = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "direct",
                          "title": "M10 Approval",
                          "memberIds": ["%s"]
                        }
                        """.formatted(memberId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
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

    private UUID currentUserId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
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
