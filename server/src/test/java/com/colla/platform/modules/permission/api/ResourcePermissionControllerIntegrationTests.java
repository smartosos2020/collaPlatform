package com.colla.platform.modules.permission.api;

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
class ResourcePermissionControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void knowledgeContentDepartmentGrantAndInheritedChildGrantAreVisible() throws Exception {
        String adminToken = login("admin", "admin123456", "resource-doc-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID departmentId = createDepartment(adminToken, "rp_dept_" + suffix, "RP Dept " + suffix);
        createMember(adminToken, "rpdept" + suffix, "RP Department Member", departmentId);
        String memberToken = login("rpdept" + suffix, "member123456", "resource-doc-member-" + UUID.randomUUID());
        KnowledgeSpaceFixture contentSpace = createKnowledgeSpace(adminToken, "RP Content " + suffix);
        UUID parentId = createItem(adminToken, contentSpace.spaceId(), contentSpace.rootItemId(), "RP Parent " + suffix);

        mockMvc.perform(post("/api/resource-permissions/knowledge_content/" + parentId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "department",
                    "subjectId", departmentId,
                    "permissionLevel", "edit"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subjectType").value("department"))
            .andExpect(jsonPath("$.permissionLevel").value("edit"));

        mockMvc.perform(post("/api/resource-permissions/knowledge_base/" + contentSpace.spaceId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "department",
                    "subjectId", departmentId,
                    "permissionLevel", "view"
                ))))
            .andExpect(status().isOk());

        UUID childId = createItem(adminToken, contentSpace.spaceId(), parentId, "RP Child " + suffix);

        mockMvc.perform(get(itemPath(contentSpace.spaceId(), childId))
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.permissionLevel").value("edit"));

        String inheritedResponse = mockMvc.perform(get("/api/resource-permissions/knowledge_content/" + childId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].sourceType", hasItem("inherited")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID inheritedPermissionId = UUID.fromString(objectMapper.readTree(inheritedResponse).get(0).get("id").asText());

        mockMvc.perform(post("/api/resource-permissions/" + inheritedPermissionId + "/revoke")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("confirmHighRisk", false))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void baseUserGroupGrantCanBeRevokedAndHighRiskRequiresConfirmation() throws Exception {
        String adminToken = login("admin", "admin123456", "resource-base-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID memberId = createMember(adminToken, "rpug" + suffix, "RP UG Member", null);
        String memberToken = login("rpug" + suffix, "member123456", "resource-base-member-" + UUID.randomUUID());
        UUID groupId = createGroup(adminToken, "rp_group_" + suffix, "RP Group " + suffix);
        addUserGroupMember(adminToken, groupId, "user", memberId);
        UUID baseId = createBase(adminToken, "RP Base " + suffix);

        mockMvc.perform(post("/api/resource-permissions/base/" + baseId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user_group",
                    "subjectId", groupId,
                    "permissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subjectType").value("user_group"));

        mockMvc.perform(get("/api/bases/" + baseId)
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.base.permissionLevel").value("view"));

        mockMvc.perform(post("/api/resource-permissions/base/" + baseId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user_group",
                    "subjectId", groupId,
                    "permissionLevel", "manage"
                ))))
            .andExpect(status().isBadRequest());

        String highRiskResponse = mockMvc.perform(post("/api/resource-permissions/base/" + baseId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user_group",
                    "subjectId", groupId,
                    "permissionLevel", "manage",
                    "confirmHighRisk", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissionLevel").value("manage"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID permissionId = UUID.fromString(objectMapper.readTree(highRiskResponse).get("id").asText());

        mockMvc.perform(get("/api/admin/audit-logs?action=resource.permission.granted&targetType=base&targetId=" + baseId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/resource-permissions/" + permissionId + "/revoke")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("confirmHighRisk", true))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/bases/" + baseId)
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeBaseAccessRequestApprovalPropagatesDocumentTreeAndInheritanceCanBeRestored() throws Exception {
        String adminToken = login("admin", "admin123456", "resource-kb-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID viewerId = createMember(adminToken, "rpkb" + suffix, "RP KB Viewer", null);
        String viewerToken = login("rpkb" + suffix, "member123456", "resource-kb-viewer-" + UUID.randomUUID());
        UUID roleId = createRole(adminToken, "rpkb_role_" + suffix, "RP KB Role " + suffix);

        String createResponse = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "RP KB " + suffix,
                    "code", "rp-kb-" + suffix,
                    "description", "KB resource permission inheritance",
                    "visibility", "private",
                    "defaultPermissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID spaceId = UUID.fromString(created.get("space").get("id").asText());
        UUID homeItemId = UUID.fromString(created.get("space").get("homeItemId").asText());

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId)
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isForbidden());

        String requestResponse = mockMvc.perform(post("/api/resource-permissions/knowledge_base/" + spaceId + "/requests")
                .header("Authorization", "Bearer " + viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "permissionLevel", "view",
                    "reason", "Need to read KB"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("submitted"))
            .andExpect(jsonPath("$.requesterId").value(viewerId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID requestId = UUID.fromString(objectMapper.readTree(requestResponse).get("id").asText());

        mockMvc.perform(get("/api/resource-permissions/knowledge_base/" + spaceId + "/requests?status=submitted")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(requestId.toString())));

        mockMvc.perform(post("/api/resource-permissions/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "approved"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("approved"));

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId)
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.id").value(spaceId.toString()));

        mockMvc.perform(get(itemPath(spaceId, homeItemId))
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.permissionLevel").value("view"));

        mockMvc.perform(post("/api/resource-permissions/knowledge_base/" + spaceId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "role",
                    "subjectId", roleId,
                    "permissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subjectType").value("role"));

        mockMvc.perform(post("/api/resource-permissions/knowledge_content/" + homeItemId + "/inheritance/break")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("confirmHighRisk", true))))
            .andExpect(status().isOk());

        mockMvc.perform(get(itemPath(spaceId, homeItemId))
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/resource-permissions/knowledge_content/" + homeItemId + "/inheritance/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get(itemPath(spaceId, homeItemId))
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.permissionLevel").value("view"));
    }

    private KnowledgeSpaceFixture createKnowledgeSpace(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", name,
                    "code", "rp-" + UUID.randomUUID().toString().substring(0, 8),
                    "visibility", "private"
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode space = objectMapper.readTree(response).get("space");
        return new KnowledgeSpaceFixture(
            UUID.fromString(space.get("id").asText()),
            UUID.fromString(space.get("rootItemId").asText())
        );
    }

    private UUID createItem(String token, UUID spaceId, UUID parentId, String title) throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("title", title);
        request.put("content", "# " + title);
        request.put("contentType", "markdown");
        request.put("parentId", parentId.toString());
        String response = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("item").get("id").asText());
    }

    private String itemPath(UUID spaceId, UUID itemId) {
        return "/api/knowledge-bases/" + spaceId + "/items/" + itemId;
    }

    private record KnowledgeSpaceFixture(UUID spaceId, UUID rootItemId) {
    }

    private UUID createBase(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "description", "Resource permission test"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("base").get("id").asText());
    }

    private UUID createRole(String token, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/roles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code, "name", name, "scope", "workspace"))))
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

    private UUID createGroup(String token, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/admin/user-groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", code,
                    "name", name,
                    "groupType", "permission",
                    "description", "Resource permission test"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void addUserGroupMember(String token, UUID groupId, String subjectType, UUID subjectId) throws Exception {
        mockMvc.perform(post("/api/admin/user-groups/" + groupId + "/members")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subjectType", subjectType, "subjectId", subjectId))))
            .andExpect(status().isOk());
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
