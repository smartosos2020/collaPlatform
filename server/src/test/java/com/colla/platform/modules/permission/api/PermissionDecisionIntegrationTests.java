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
    void knowledgeContentAccessExplanationAndSearchUseUnifiedResourcePermissions() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String adminToken = login("admin", "admin123456", "permission-admin-" + suffix);
        UUID departmentId = createDepartment(adminToken, "acl_dept_" + suffix, "ACL Dept " + suffix);
        String memberUsername = "acldept" + suffix;
        createMember(adminToken, memberUsername, "ACL Department Member", departmentId);
        String memberToken = login(memberUsername, "member123456", "permission-member-" + suffix);
        String outsiderUsername = "aclout" + suffix;
        createMember(adminToken, outsiderUsername, "ACL Outsider", null);
        String outsiderToken = login(outsiderUsername, "member123456", "permission-outsider-" + suffix);

        KnowledgeFixture departmentContent = createItem(adminToken, "acl-department-" + suffix);
        grantKnowledgeContentPermission(adminToken, departmentContent, "department", departmentId, "view");

        mockMvc.perform(get(itemPath(departmentContent))
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.permissionLevel").value("view"));

        mockMvc.perform(get("/api/platform/objects/knowledge_content/" + departmentContent.itemId() + "/permission-explanation?action=view")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowed").value(true))
            .andExpect(jsonPath("$.currentLevel").value("view"))
            .andExpect(jsonPath("$.source", containsString("部门 ACL Dept " + suffix)));

        mockMvc.perform(post("/api/admin/search-governance/reindex")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/search?q=acl-department-" + suffix + "&limit=20")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].objectId").value(hasItem(departmentContent.itemId().toString())));

        mockMvc.perform(get("/api/search?q=acl-department-" + suffix + "&limit=20")
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].objectId").value(not(hasItem(departmentContent.itemId().toString()))));

        KnowledgeFixture roleContent = createItem(adminToken, "acl-role-" + suffix);
        grantKnowledgeContentPermission(adminToken, roleContent, "role", MEMBER_ROLE_ID, "comment");

        mockMvc.perform(get("/api/platform/objects/knowledge_content/" + roleContent.itemId() + "/permission-explanation?action=comment")
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

    private KnowledgeFixture createItem(String token, String title) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String spaceResponse = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", title, "code", "acl-" + suffix))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var space = objectMapper.readTree(spaceResponse).get("space");
        UUID spaceId = UUID.fromString(space.get("id").asText());
        UUID rootItemId = UUID.fromString(space.get("rootItemId").asText());
        String itemResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", rootItemId, "title", title, "contentType", "markdown", "content", "# " + title
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return new KnowledgeFixture(spaceId, UUID.fromString(objectMapper.readTree(itemResponse).get("item").get("id").asText()));
    }

    private void grantKnowledgeContentPermission(String token, KnowledgeFixture fixture, String subjectType, UUID subjectId, String permissionLevel) throws Exception {
        mockMvc.perform(post("/api/resource-permissions/knowledge_base/" + fixture.spaceId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", subjectType,
                    "subjectId", subjectId,
                    "permissionLevel", "view"
                ))))
            .andExpect(status().isOk());
        mockMvc.perform(post(itemPath(fixture) + "/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", subjectType,
                    "subjectId", subjectId,
                    "permissionLevel", permissionLevel
                ))))
            .andExpect(status().isOk());
    }

    private String itemPath(KnowledgeFixture fixture) {
        return "/api/knowledge-bases/" + fixture.spaceId() + "/items/" + fixture.itemId();
    }

    private record KnowledgeFixture(UUID spaceId, UUID itemId) {
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
