package com.colla.platform.modules.notification.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.event.application.DomainEventWorker;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
class NotificationPermissionIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DomainEventRepository eventRepository;
    @Autowired DomainEventWorker eventWorker;

    @Test
    void preferencesProtectRequiredNotificationsAndKeepGovernanceUnreadSeparate() throws Exception {
        String adminToken = login("admin", "admin123456");
        UUID adminId = currentUserId(adminToken);

        mockMvc.perform(put("/api/notifications/preferences/approval")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.sourceType == 'approval')].enabled", hasItem(false)));
        mockMvc.perform(put("/api/notifications/preferences/resource")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(status().isBadRequest());

        appendNotification(adminId, "approval_approved", "optional-" + UUID.randomUUID());
        appendNotification(adminId, "approval_task_created", "required-" + UUID.randomUUID());
        appendNotification(adminId, "approval_task_created", "required-dedupe");
        appendNotification(adminId, "approval_task_created", "required-dedupe");
        eventWorker.processPendingEvents();
        jdbcTemplate.update(
            "insert into notifications (id, workspace_id, recipient_id, notification_type, title, dedupe_key, created_at) values (?, ?, ?, 'governance_risk', 'Governance', ?, now())",
            UUID.randomUUID(), workspaceId(adminToken), adminId, "governance-" + UUID.randomUUID()
        );

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].notificationType", hasItem("approval_task_created")))
            .andExpect(jsonPath("$[*].notificationType", not(hasItem("approval_approved"))))
            .andExpect(jsonPath("$[*].notificationType", not(hasItem("governance_risk"))));

        Integer dedupeCount = jdbcTemplate.queryForObject(
            "select count(*) from notifications where workspace_id = ? and recipient_id = ? and dedupe_key = 'required-dedupe'",
            Integer.class, workspaceId(adminToken), adminId
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, dedupeCount);
    }

    @Test
    void groupPermissionChangeNotifiesExpandedMembersOnceAcrossOverlappingSources() throws Exception {
        String adminToken = login("admin", "admin123456");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID departmentId = createDepartment(adminToken, "notify_dept_" + suffix, "Notify Dept " + suffix);
        UUID memberId = createMember(adminToken, "notifymember" + suffix, departmentId);
        UUID groupId = createGroup(adminToken, "notify_group_" + suffix, "Notify Group " + suffix);
        addGroupMember(adminToken, groupId, "user", memberId);
        addGroupMember(adminToken, groupId, "department", departmentId);
        UUID baseId = createBase(adminToken, "Notify Base " + suffix);

        mockMvc.perform(post("/api/resource-permissions/base/" + baseId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user_group",
                    "subjectId", groupId,
                    "permissionLevel", "view",
                    "confirmHighRisk", false
                ))))
            .andExpect(status().isOk());
        Integer pendingEvents = jdbcTemplate.queryForObject(
            """
                select count(*) from domain_events
                where event_type = 'notification.created' and aggregate_type = 'base' and aggregate_id = ?
                  and payload->>'recipientId' = ?
                """,
            Integer.class, baseId, memberId.toString()
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, pendingEvents);
        eventWorker.processPendingEvents();

        String eventOutcome = jdbcTemplate.queryForObject(
            """
                select status || coalesce(':' || last_error, '') from domain_events
                where event_type = 'notification.created' and aggregate_type = 'base' and aggregate_id = ?
                  and payload->>'recipientId' = ?
                """,
            String.class, baseId, memberId.toString()
        );
        org.junit.jupiter.api.Assertions.assertEquals("processed", eventOutcome);

        Integer notificationCount = jdbcTemplate.queryForObject(
            """
                select count(*) from notifications
                where workspace_id = ? and recipient_id = ? and notification_type = 'resource_permission_granted'
                  and target_type = 'base' and target_id = ?
                """,
            Integer.class, workspaceId(adminToken), memberId, baseId
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, notificationCount);
    }

    private UUID createDepartment(String token, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/departments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code, "name", name, "sortOrder", 0))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createMember(String token, String username, UUID departmentId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username, "password", "member123456", "displayName", "Notify Member",
                    "email", username + "@colla.local", "roleCode", "member", "primaryDepartmentId", departmentId.toString()
                ))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createGroup(String token, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/user-groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", code, "name", name, "groupType", "permission", "description", "Notification test"
                ))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void addGroupMember(String token, UUID groupId, String subjectType, UUID subjectId) throws Exception {
        mockMvc.perform(post("/api/admin/user-groups/" + groupId + "/members")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subjectType", subjectType, "subjectId", subjectId))))
            .andExpect(status().isOk());
    }

    private UUID createBase(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "description", "Notification test"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("base").get("id").asText());
    }

    private void appendNotification(UUID recipientId, String type, String dedupeKey) {
        UUID targetId = UUID.randomUUID();
        eventRepository.append(
            workspaceIdDirect(), "notification.created", "approval", targetId, recipientId,
            Map.of("recipientId", recipientId.toString(), "notificationType", type, "title", type,
                "targetType", "approval", "targetId", targetId.toString(), "dedupeKey", dedupeKey),
            "test-event:" + UUID.randomUUID()
        );
    }

    private UUID workspaceIdDirect() {
        return jdbcTemplate.queryForObject("select workspace_id from users where username = 'admin'", UUID.class);
    }

    private UUID workspaceId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("workspaceId").asText());
    }

    private UUID currentUserId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
            {"username":"%s","password":"%s","deviceType":"web","deviceFingerprint":"m5-%s","deviceName":"MockMvc","appVersion":"test"}
            """.formatted(username, password, UUID.randomUUID())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
