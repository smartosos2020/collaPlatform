package com.colla.platform.modules.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class UserGroupControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanManageUserGroupMembersExpansionAuditAndDocumentGrant() throws Exception {
        String adminToken = login("admin", "admin123456", "user-group-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID departmentId = createDepartment(adminToken, "ug_dept_" + suffix, "User Group Dept");
        UUID departmentUserId = createMember(adminToken, "ugdept" + suffix, "UG Department Member", departmentId);
        UUID directUserId = createMember(adminToken, "ugdirect" + suffix, "UG Direct Member", null);
        String departmentUserToken = login("ugdept" + suffix, "member123456", "user-group-member-" + UUID.randomUUID());

        JsonNode group = createGroup(adminToken, "reviewers_" + suffix, "Reviewers " + suffix, "permission");
        UUID groupId = UUID.fromString(group.get("id").asText());
        assertThat(group.get("memberExpansion").get("directMemberCount").asInt()).isEqualTo(0);
        assertThat(group.get("authorizationSubject").get("subjectType").asText()).isEqualTo("user_group");

        mockMvc.perform(post("/api/admin/user-groups/" + groupId + "/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subjectType", "user", "subjectId", directUserId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subjectType").value("user"))
            .andExpect(jsonPath("$.authorizationSubject.subjectType").value("user"))
            .andExpect(jsonPath("$.governance.managedObjectType").value("user"));

        mockMvc.perform(post("/api/admin/user-groups/" + groupId + "/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subjectType", "department", "subjectId", departmentId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subjectType").value("department"))
            .andExpect(jsonPath("$.authorizationSubject.subjectType").value("department"));

        mockMvc.perform(get("/api/admin/user-groups/" + groupId + "/expanded-members")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].userId", hasItem(departmentUserId.toString())))
            .andExpect(jsonPath("$[*].userId", hasItem(directUserId.toString())))
            .andExpect(jsonPath("$[*].expansionSource.sourceType", hasItem("department")))
            .andExpect(jsonPath("$[*].governance.managedObjectType", hasItem("user")));

        mockMvc.perform(get("/api/admin/audit-logs?action=usergroup.member.added&targetType=user_group&targetId=" + groupId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        UUID documentId = createDocument(adminToken, "User Group Grant " + suffix);
        mockMvc.perform(post("/api/docs/" + documentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user_group",
                    "subjectId", groupId,
                    "permissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[*].subjectType", hasItem("user_group")))
            .andExpect(jsonPath("$.permissions[*].subjectName", hasItem("Reviewers " + suffix)));

        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + departmentUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.permissionLevel").value("view"));

        mockMvc.perform(get("/api/docs")
                .header("Authorization", "Bearer " + departmentUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(documentId.toString())));

        mockMvc.perform(post("/api/admin/user-groups/" + groupId + "/disable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        UUID blockedDocumentId = createDocument(adminToken, "Disabled Group Grant " + suffix);
        mockMvc.perform(post("/api/docs/" + blockedDocumentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user_group",
                    "subjectId", groupId,
                    "permissionLevel", "view"
                ))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void memberCannotViewOrManageUserGroups() throws Exception {
        String adminToken = login("admin", "admin123456", "user-group-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        createMember(adminToken, "ugforbidden" + suffix, "UG Forbidden", null);
        String memberToken = login("ugforbidden" + suffix, "member123456", "user-group-forbidden-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/user-groups")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/user-groups")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", "forbidden_" + suffix,
                    "name", "Forbidden",
                    "groupType", "normal"
                ))))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanEnableDisabledUserGroup() throws Exception {
        String adminToken = login("admin", "admin123456", "user-group-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode group = createGroup(adminToken, "toggle_group_" + suffix, "Toggle Group " + suffix, "normal");
        UUID groupId = UUID.fromString(group.get("id").asText());

        mockMvc.perform(post("/api/admin/user-groups/" + groupId + "/disable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/user-groups/" + groupId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("disabled"));

        mockMvc.perform(post("/api/admin/user-groups/" + groupId + "/enable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/user-groups/" + groupId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("active"));
    }

    private JsonNode createGroup(String token, String code, String name, String groupType) throws Exception {
        String response = mockMvc.perform(post("/api/admin/user-groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", code,
                    "name", name,
                    "description", "Integration test group",
                    "groupType", groupType
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.memberExpansion.directMemberCount").value(0))
            .andExpect(jsonPath("$.governance.managedObjectType").value("user_group"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
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
