package com.colla.platform.modules.permission.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
class RoleControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanManageRolesPermissionsAndInheritedDepartmentAssignments() throws Exception {
        String adminToken = login("admin", "admin123456", "role-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID departmentId = createDepartment(adminToken, "role_dept_" + suffix, "Role Dept " + suffix);
        createMember(adminToken, "roledept" + suffix, "Role Dept Member", departmentId);
        String memberToken = login("roledept" + suffix, "member123456", "role-member-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/permissions")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code", hasItem("role.manage")))
            .andExpect(jsonPath("$[*].code", hasItem("permission.inspect")))
            .andExpect(jsonPath("$[*].category.module", hasItem("identity")))
            .andExpect(jsonPath("$[*].risk.level", hasItem("high")));

        JsonNode role = createRole(adminToken, "org_viewer_" + suffix, "Org Viewer " + suffix);
        UUID roleId = UUID.fromString(role.get("id").asText());
        assertThat(role.get("roleClassification").get("category").asText()).isEqualTo("business_collaboration");
        assertThat(role.get("permissionMatrix").get("permissionCount").asInt()).isEqualTo(0);

        mockMvc.perform(put("/api/admin/roles/" + roleId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("permissionCodes", List.of("org.view")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[*].code", hasItem("org.view")))
            .andExpect(jsonPath("$.permissionMatrix[*].module", hasItem("identity")))
            .andExpect(jsonPath("$.governance.permissionCount").value(1));

        mockMvc.perform(post("/api/admin/role-assignments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "roleId", roleId,
                    "subjectType", "department",
                    "subjectId", departmentId
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subjectType").value("department"))
            .andExpect(jsonPath("$.subject.subjectType").value("department"))
            .andExpect(jsonPath("$.lifecycle.status").value("active"));

        mockMvc.perform(get("/api/admin/departments/tree")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].department.id", hasItem(departmentId.toString())));
    }

    @Test
    void userGroupAssignmentsGrantInheritedPermissionAndCanBeRevoked() throws Exception {
        String adminToken = login("admin", "admin123456", "role-ug-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID memberId = createMember(adminToken, "roleug" + suffix, "Role UG Member", null);
        String memberToken = login("roleug" + suffix, "member123456", "role-ug-member-" + UUID.randomUUID());
        UUID groupId = createGroup(adminToken, "role_group_" + suffix, "Role Group " + suffix);

        mockMvc.perform(post("/api/admin/user-groups/" + groupId + "/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subjectType", "user", "subjectId", memberId))))
            .andExpect(status().isOk());

        JsonNode role = createRole(adminToken, "ug_viewer_" + suffix, "UG Viewer " + suffix);
        UUID roleId = UUID.fromString(role.get("id").asText());
        mockMvc.perform(put("/api/admin/roles/" + roleId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("permissionCodes", List.of("usergroup.view")))))
            .andExpect(status().isOk());

        String assignmentResponse = mockMvc.perform(post("/api/admin/role-assignments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "roleId", roleId,
                    "subjectType", "user_group",
                    "subjectId", groupId
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID assignmentId = UUID.fromString(objectMapper.readTree(assignmentResponse).get("id").asText());

        mockMvc.perform(get("/api/admin/user-groups")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(groupId.toString())));

        mockMvc.perform(delete("/api/admin/role-assignments/" + assignmentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/user-groups")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void highRiskPermissionChangesRequireConfirmationAndWriteAudit() throws Exception {
        String adminToken = login("admin", "admin123456", "role-risk-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode role = createRole(adminToken, "risk_role_" + suffix, "Risk Role " + suffix);
        UUID roleId = UUID.fromString(role.get("id").asText());

        mockMvc.perform(put("/api/admin/roles/" + roleId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("permissionCodes", List.of("role.manage")))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/roles/" + roleId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "permissionCodes", List.of("role.manage"),
                    "confirmHighRisk", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[*].code", hasItem("role.manage")))
            .andExpect(jsonPath("$.permissionMatrix[*].highRiskCount", hasItem(1)));

        mockMvc.perform(get("/api/admin/audit-logs?action=role.permissions.updated&targetType=role&targetId=" + roleId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[*].riskTag", hasItem("high")))
            .andExpect(jsonPath("$[*].context.action", hasItem("role.permissions.updated")));
    }

    @Test
    void memberCannotViewOrManageRoles() throws Exception {
        String adminToken = login("admin", "admin123456", "role-forbid-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        createMember(adminToken, "roleforbid" + suffix, "Role Forbidden", null);
        String memberToken = login("roleforbid" + suffix, "member123456", "role-forbid-member-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/roles")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/roles")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", "forbidden_role", "name", "Forbidden"))))
            .andExpect(status().isForbidden());
    }

    private JsonNode createRole(String token, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", code,
                    "name", name,
                    "scope", "workspace",
                    "description", "Integration test role"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.roleClassification.scope").value("workspace"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createGroup(String token, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/user-groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", code,
                    "name", name,
                    "description", "Integration test user group",
                    "groupType", "permission"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
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

    private UUID createMember(String token, String username, String displayName, UUID primaryDepartmentId) throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("username", username);
        request.put("password", "member123456");
        request.put("displayName", displayName);
        request.put("email", username + "@colla.local");
        request.put("roleCode", "member");
        if (primaryDepartmentId != null) {
            request.put("primaryDepartmentId", primaryDepartmentId.toString());
        }
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
