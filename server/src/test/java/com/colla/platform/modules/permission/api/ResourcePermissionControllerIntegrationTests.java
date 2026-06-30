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
    void documentDepartmentGrantAndInheritedChildGrantAreVisible() throws Exception {
        String adminToken = login("admin", "admin123456", "resource-doc-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID departmentId = createDepartment(adminToken, "rp_dept_" + suffix, "RP Dept " + suffix);
        createMember(adminToken, "rpdept" + suffix, "RP Department Member", departmentId);
        String memberToken = login("rpdept" + suffix, "member123456", "resource-doc-member-" + UUID.randomUUID());
        UUID parentId = createDocument(adminToken, null, "RP Parent " + suffix);

        mockMvc.perform(post("/api/resource-permissions/document/" + parentId)
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

        UUID childId = createDocument(adminToken, parentId, "RP Child " + suffix);

        mockMvc.perform(get("/api/docs/" + childId)
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.permissionLevel").value("edit"));

        String inheritedResponse = mockMvc.perform(get("/api/resource-permissions/document/" + childId)
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
        UUID homeDocumentId = UUID.fromString(created.get("space").get("homeDocumentId").asText());

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

        mockMvc.perform(get("/api/docs/" + homeDocumentId)
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.permissionLevel").value("view"));

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

        mockMvc.perform(post("/api/resource-permissions/document/" + homeDocumentId + "/inheritance/break")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("confirmHighRisk", true))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/docs/" + homeDocumentId)
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/resource-permissions/document/" + homeDocumentId + "/inheritance/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/docs/" + homeDocumentId)
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.permissionLevel").value("view"));
    }

    private UUID createDocument(String token, UUID parentId, String title) throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("title", title);
        request.put("content", "# " + title);
        if (parentId != null) {
            request.put("parentId", parentId.toString());
        }
        String response = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
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
