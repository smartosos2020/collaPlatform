package com.colla.platform.modules.permission.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class PermissionDecisionIntegrationTests {
    private static final UUID MEMBER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void documentAccessExplanationAndSearchUseUnifiedResourcePermissions() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String adminToken = login("admin", "admin123456", "permission-admin-" + suffix);
        UUID departmentId = createDepartment(adminToken, "acl_dept_" + suffix, "ACL Dept " + suffix);
        String memberUsername = "acldept" + suffix;
        createMember(adminToken, memberUsername, "ACL Department Member", departmentId);
        String memberToken = login(memberUsername, "member123456", "permission-member-" + suffix);
        String outsiderUsername = "aclout" + suffix;
        createMember(adminToken, outsiderUsername, "ACL Outsider", null);
        String outsiderToken = login(outsiderUsername, "member123456", "permission-outsider-" + suffix);

        UUID departmentDocumentId = createDocument(adminToken, "acl-department-" + suffix);
        grantDocumentPermission(adminToken, departmentDocumentId, "department", departmentId, "view");

        mockMvc.perform(get("/api/docs/" + departmentDocumentId)
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.permissionLevel").value("view"));

        mockMvc.perform(get("/api/platform/objects/document/" + departmentDocumentId + "/permission-explanation?action=view")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowed").value(true))
            .andExpect(jsonPath("$.currentLevel").value("view"))
            .andExpect(jsonPath("$.source", containsString("部门 ACL Dept " + suffix)));

        mockMvc.perform(post("/api/search/reindex")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/search?q=acl-department-" + suffix + "&limit=20")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].objectId").value(hasItem(departmentDocumentId.toString())));

        mockMvc.perform(get("/api/search?q=acl-department-" + suffix + "&limit=20")
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].objectId").value(not(hasItem(departmentDocumentId.toString()))));

        UUID roleDocumentId = createDocument(adminToken, "acl-role-" + suffix);
        grantDocumentPermission(adminToken, roleDocumentId, "role", MEMBER_ROLE_ID, "comment");

        mockMvc.perform(get("/api/platform/objects/document/" + roleDocumentId + "/permission-explanation?action=comment")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowed").value(true))
            .andExpect(jsonPath("$.currentLevel").value("comment"))
            .andExpect(jsonPath("$.source", containsString("角色 Member")));
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
        Map<String, Object> request = new LinkedHashMap<>();
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

    private void grantDocumentPermission(String token, UUID documentId, String subjectType, UUID subjectId, String permissionLevel) throws Exception {
        mockMvc.perform(post("/api/docs/" + documentId + "/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", subjectType,
                    "subjectId", subjectId,
                    "permissionLevel", permissionLevel
                ))))
            .andExpect(status().isOk());
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
