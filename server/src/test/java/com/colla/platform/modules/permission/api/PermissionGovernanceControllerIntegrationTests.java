package com.colla.platform.modules.permission.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PermissionGovernanceControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanInspectUserResourcePermissionAndExportRisks() throws Exception {
        String adminToken = login("admin", "admin123456", "governance-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID departmentId = createDepartment(adminToken, "gov_dept_" + suffix, "Gov Dept " + suffix);
        UUID memberId = createMember(adminToken, "govuser" + suffix, "Gov User", departmentId);
        UUID documentId = createDocument(adminToken, "Gov Doc " + suffix);

        grantResource(adminToken, "document", documentId, "department", departmentId, "view", false);

        mockMvc.perform(get("/api/admin/permission-governance/inspect")
                .param("userId", memberId.toString())
                .param("resourceType", "document")
                .param("resourceId", documentId.toString())
                .param("action", "view")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowed").value(true))
            .andExpect(jsonPath("$.currentLevel").value("view"))
            .andExpect(jsonPath("$.risk.level").value("low"))
            .andExpect(jsonPath("$.impactScope.resourceType").value("document"))
            .andExpect(jsonPath("$.suggestedAction").value("monitor"));

        mockMvc.perform(get("/api/admin/permission-governance/risks/export")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("ruleCode,severity,resourceType")));
    }

    @Test
    void riskRulesDetectDisabledGroupsAndBroadHighRiskPermissions() throws Exception {
        String adminToken = login("admin", "admin123456", "governance-risk-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID groupId = createGroup(adminToken, "gov_group_" + suffix, "Gov Group " + suffix);
        UUID baseId = createBase(adminToken, "Gov Base " + suffix);

        grantResource(adminToken, "base", baseId, "user_group", groupId, "manage", true);
        mockMvc.perform(post("/api/admin/user-groups/" + groupId + "/disable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/permission-governance/risks")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].ruleCode", hasItem("disabled_user_group_active_permission")))
            .andExpect(jsonPath("$.items[*].ruleCode", hasItem("high_risk_broad_permission")))
            .andExpect(jsonPath("$.items[*].impactScope.resourceType", hasItem("base")))
            .andExpect(jsonPath("$.items[*].suggestedAction", hasItem("reduce_or_revoke_permission")))
            .andExpect(jsonPath("$.severityBuckets.high").exists());
    }

    private void grantResource(String token, String resourceType, UUID resourceId, String subjectType, UUID subjectId, String level, boolean confirm) throws Exception {
        mockMvc.perform(post("/api/resource-permissions/" + resourceType + "/" + resourceId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", subjectType,
                    "subjectId", subjectId,
                    "permissionLevel", level,
                    "confirmHighRisk", confirm
                ))))
            .andExpect(status().isOk());
    }

    private UUID createDocument(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", title, "content", "# " + title))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("document").get("id").asText());
    }

    private UUID createBase(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "description", "Governance test"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("base").get("id").asText());
    }

    private UUID createDepartment(String token, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/departments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code, "name", name, "sortOrder", 0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createGroup(String token, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/user-groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", code,
                    "name", name,
                    "groupType", "permission",
                    "description", "Governance test"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createMember(String token, String username, String displayName, UUID primaryDepartmentId) throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("username", username);
        request.put("password", "member123456");
        request.put("displayName", displayName);
        request.put("email", username + "@colla.local");
        request.put("roleCode", "member");
        request.put("primaryDepartmentId", primaryDepartmentId.toString());
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
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
