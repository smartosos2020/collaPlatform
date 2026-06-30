package com.colla.platform.modules.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class OrganizationControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanManageDepartmentTreeMembersManagersAndAuditTrail() throws Exception {
        String adminToken = login("admin", "admin123456", "org-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode root = createDepartment(adminToken, null, "hq_" + suffix, "Headquarters", 0);
        UUID rootId = UUID.fromString(root.get("id").asText());
        JsonNode child = createDepartment(adminToken, rootId, "product_" + suffix, "Product", 1);
        UUID childId = UUID.fromString(child.get("id").asText());
        JsonNode member = createMember(adminToken, childId, "orgmember" + suffix);
        UUID memberId = UUID.fromString(member.get("id").asText());

        assertThat(member.get("departments")).hasSize(1);
        assertThat(member.get("departments").get(0).get("departmentId").asText()).isEqualTo(childId.toString());
        assertThat(member.get("departments").get(0).get("relationType").asText()).isEqualTo("primary");

        mockMvc.perform(post("/api/admin/departments/" + childId + "/managers")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + memberId + "\",\"managerType\":\"primary\"}"))
            .andExpect(status().isOk());

        String treeResponse = mockMvc.perform(get("/api/admin/departments/tree")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode productNode = findDepartmentNode(objectMapper.readTree(treeResponse), childId);
        assertThat(productNode).isNotNull();
        assertThat(productNode.get("department").get("name").asText()).isEqualTo("Product");
        assertThat(productNode.get("managers")).hasSize(1);
        assertThat(productNode.get("managers").get(0).get("userId").asText()).isEqualTo(memberId.toString());

        mockMvc.perform(get("/api/admin/departments/" + childId + "/members")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].username", hasItem("orgmember" + suffix)))
            .andExpect(jsonPath("$[*].relationType", hasItem("primary")));

        mockMvc.perform(get("/api/admin/users?departmentId=" + childId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].username", hasItem("orgmember" + suffix)))
            .andExpect(jsonPath("$[0].departments.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/admin/audit-logs?action=department.member.added&targetType=department&targetId=" + childId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/admin/departments/" + rootId + "/move")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":\"" + childId + "\",\"sortOrder\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void memberCannotViewOrManageOrganization() throws Exception {
        String adminToken = login("admin", "admin123456", "org-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        createMember(adminToken, null, "orgforbidden" + suffix);
        String memberToken = login("orgforbidden" + suffix, "member123456", "org-member-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/departments/tree")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/departments")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "code": "forbidden_%s",
                          "name": "Forbidden",
                          "sortOrder": 0
                        }
                        """.formatted(suffix)
                ))
            .andExpect(status().isForbidden());
    }

    @Test
    void departmentDeleteRequiresEmptyLeafDepartment() throws Exception {
        String adminToken = login("admin", "admin123456", "org-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode root = createDepartment(adminToken, null, "empty_root_" + suffix, "Empty Root", 0);
        UUID rootId = UUID.fromString(root.get("id").asText());
        JsonNode child = createDepartment(adminToken, rootId, "empty_child_" + suffix, "Empty Child", 0);
        UUID childId = UUID.fromString(child.get("id").asText());

        mockMvc.perform(delete("/api/admin/departments/" + rootId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/admin/departments/" + childId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanEnableDisabledDepartment() throws Exception {
        String adminToken = login("admin", "admin123456", "org-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JsonNode department = createDepartment(adminToken, null, "toggle_dept_" + suffix, "Toggle Department", 0);
        UUID departmentId = UUID.fromString(department.get("id").asText());

        mockMvc.perform(post("/api/admin/departments/" + departmentId + "/disable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        JsonNode disabledNode = departmentNode(adminToken, departmentId);
        assertThat(disabledNode.get("department").get("status").asText()).isEqualTo("disabled");

        mockMvc.perform(post("/api/admin/departments/" + departmentId + "/enable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        JsonNode enabledNode = departmentNode(adminToken, departmentId);
        assertThat(enabledNode.get("department").get("status").asText()).isEqualTo("active");
    }

    private JsonNode createDepartment(String token, UUID parentId, String code, String name, int sortOrder) throws Exception {
        String parentProperty = parentId == null ? "" : "\"parentId\":\"" + parentId + "\",";
        String response = mockMvc.perform(post("/api/admin/departments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          %s
                          "code": "%s",
                          "name": "%s",
                          "sortOrder": %d
                        }
                        """.formatted(parentProperty, code, name, sortOrder)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode departmentNode(String token, UUID departmentId) throws Exception {
        String treeResponse = mockMvc.perform(get("/api/admin/departments/tree")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return findDepartmentNode(objectMapper.readTree(treeResponse), departmentId);
    }

    private JsonNode createMember(String token, UUID primaryDepartmentId, String username) throws Exception {
        String departmentProperty = primaryDepartmentId == null ? "" : "\"primaryDepartmentId\":\"" + primaryDepartmentId + "\",";
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          %s
                          "username": "%s",
                          "password": "member123456",
                          "displayName": "Org Member",
                          "email": "%s@colla.local",
                          "roleCode": "member"
                        }
                        """.formatted(departmentProperty, username, username)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(username))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode findDepartmentNode(JsonNode nodes, UUID departmentId) {
        for (JsonNode node : nodes) {
            if (departmentId.toString().equals(node.get("department").get("id").asText())) {
                return node;
            }
            JsonNode match = findDepartmentNode(node.get("children"), departmentId);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private String login(String username, String password, String deviceFingerprint) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(username, password, deviceFingerprint)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String loginBody(String username, String password, String deviceFingerprint) {
        return """
            {
              "username": "%s",
              "password": "%s",
              "deviceType": "web",
              "deviceFingerprint": "%s",
              "deviceName": "MockMvc",
              "appVersion": "test"
            }
            """.formatted(username, password, deviceFingerprint);
    }
}
