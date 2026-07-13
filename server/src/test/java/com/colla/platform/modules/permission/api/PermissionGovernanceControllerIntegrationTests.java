package com.colla.platform.modules.permission.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
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
    void permissionMatrixCoversCoreModulesAndAuditExpectations() throws Exception {
        String adminToken = login("admin", "admin123456", "governance-matrix-admin-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/permission-governance/matrix")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(15)))
            .andExpect(jsonPath("$[*].module", hasItem("im")))
            .andExpect(jsonPath("$[*].module", hasItem("project")))
            .andExpect(jsonPath("$[*].module", hasItem("knowledge")))
            .andExpect(jsonPath("$[*].module", hasItem("base")))
            .andExpect(jsonPath("$[*].module", hasItem("approval")))
            .andExpect(jsonPath("$[?(@.action == 'send')].requiredLevel", hasItem("comment")))
            .andExpect(jsonPath("$[?(@.action == 'manage_members')].requiredLevel", hasItem("manage")))
            .andExpect(jsonPath("$[?(@.action == 'edit_record')].requiredLevel", hasItem("edit")))
            .andExpect(jsonPath("$[?(@.action == 'act')].requiredLevel", hasItem("edit")))
            .andExpect(jsonPath("$[0].allowedExpectation").isNotEmpty())
            .andExpect(jsonPath("$[0].deniedExpectation").isNotEmpty())
            .andExpect(jsonPath("$[0].auditExpectation").isNotEmpty());
    }

    @Test
    void adminCanInspectUserResourcePermissionAndExportRisks() throws Exception {
        String adminToken = login("admin", "admin123456", "governance-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID departmentId = createDepartment(adminToken, "gov_dept_" + suffix, "Gov Dept " + suffix);
        UUID memberId = createMember(adminToken, "govuser" + suffix, "Gov User", departmentId);
        UUID itemId = createItem(adminToken, "Gov Knowledge " + suffix);

        grantResource(adminToken, "knowledge_content", itemId, "department", departmentId, "view", false);

        mockMvc.perform(get("/api/admin/permission-governance/inspect")
                .param("userId", memberId.toString())
                .param("resourceType", "knowledge_content")
                .param("resourceId", itemId.toString())
                .param("action", "view")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowed").value(true))
            .andExpect(jsonPath("$.currentLevel").value("view"))
            .andExpect(jsonPath("$.risk.level").value("low"))
            .andExpect(jsonPath("$.impactScope.resourceType").value("knowledge_content"))
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

    @Test
    void expiredRiskSupportsPreviewConfirmedRemediationAndAudit() throws Exception {
        String adminToken = login("admin", "admin123456", "governance-remediation-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID departmentId = createDepartment(adminToken, "expired_dept_" + suffix, "Expired Dept " + suffix);
        UUID baseId = createBase(adminToken, "Expired Grant Base " + suffix);
        UUID permissionId = grantResource(
            adminToken, "base", baseId, "department", departmentId, "view", false, Instant.now().minusSeconds(60)
        );

        mockMvc.perform(get("/api/admin/permission-governance/risks")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.id == '" + permissionId + "')].ruleCode", hasItem("expired_active_permission")));

        mockMvc.perform(post("/api/admin/permission-governance/risks/" + permissionId + "/remediation")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executable").value(true))
            .andExpect(jsonPath("$.applied").value(false))
            .andExpect(jsonPath("$.action").value("revoke_permission"));

        mockMvc.perform(post("/api/admin/permission-governance/risks/" + permissionId + "/remediation?confirm=true")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applied").value(true));

        mockMvc.perform(get("/api/admin/audit-logs?action=permission.risk.remediated&targetType=base&targetId=" + baseId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].action").value("permission.risk.remediated"));

        mockMvc.perform(post("/api/admin/permission-governance/risks/" + permissionId + "/remediation?confirm=true")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void governanceSearchAndReindexStayInsideAdminBoundary() throws Exception {
        String adminToken = login("admin", "admin123456", "governance-search-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID departmentId = createDepartment(adminToken, "search_dept_" + suffix, "Search Dept " + suffix);
        String username = "searchmember" + suffix;
        createMember(adminToken, username, "Search Member", departmentId);
        String memberToken = login(username, "member123456", "governance-search-member-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/search-governance?q=权限风险")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchScope").value("admin_governance"))
            .andExpect(jsonPath("$.items[*].governanceType", hasItem("permission_risk")))
            .andExpect(jsonPath("$.items[*].adminPath", hasItem("/admin/permission-governance?severity=high")));

        mockMvc.perform(post("/api/admin/search-governance/reindex")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/search-governance/reindex")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/search/reindex")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().is4xxClientError());
    }

    private void grantResource(String token, String resourceType, UUID resourceId, String subjectType, UUID subjectId, String level, boolean confirm) throws Exception {
        grantResource(token, resourceType, resourceId, subjectType, subjectId, level, confirm, null);
    }

    private UUID grantResource(String token, String resourceType, UUID resourceId, String subjectType, UUID subjectId, String level, boolean confirm, Instant expiresAt) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("subjectType", subjectType);
        request.put("subjectId", subjectId);
        request.put("permissionLevel", level);
        request.put("confirmHighRisk", confirm);
        request.put("expiresAt", expiresAt);
        String response =
        mockMvc.perform(post("/api/resource-permissions/" + resourceType + "/" + resourceId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createItem(String token, String title) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String spaceResponse = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", title, "code", "gov-" + suffix))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var space = objectMapper.readTree(spaceResponse).get("space");
        UUID spaceId = UUID.fromString(space.get("id").asText());
        String response = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", UUID.fromString(space.get("rootItemId").asText()),
                    "title", title,
                    "contentType", "markdown",
                    "content", "# " + title
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("item").get("id").asText());
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
