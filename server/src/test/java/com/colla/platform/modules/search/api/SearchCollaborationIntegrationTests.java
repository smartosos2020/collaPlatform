package com.colla.platform.modules.search.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
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
class SearchCollaborationIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchStatsDiffBaseViewsAndMessageEnhancementsFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "m9-admin-" + UUID.randomUUID());
        String memberUsername = "m9member" + UUID.randomUUID().toString().substring(0, 8);
        UUID memberId = createMember(adminToken, memberUsername, "M9 Member");
        String memberToken = login(memberUsername, "member123456", "m9-member-" + UUID.randomUUID());
        String outsiderUsername = "m9out" + UUID.randomUUID().toString().substring(0, 8);
        createMember(adminToken, outsiderUsername, "M9 Outsider");
        String outsiderToken = login(outsiderUsername, "member123456", "m9-outsider-" + UUID.randomUUID());

        JsonNode project = createProject(adminToken, memberId);
        UUID projectId = UUID.fromString(project.get("id").asText());
        UUID conversationId = UUID.fromString(project.get("conversationId").asText());
        UUID issueId = createIssue(adminToken, projectId, memberId);

        mockMvc.perform(get("/api/projects/" + projectId + "/stats")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.byStatus[?(@.key == 'open')].count").value(hasItem(1)))
            .andExpect(jsonPath("$.byAssignee[?(@.key == '" + memberId + "')].count").value(hasItem(1)));

        UUID docId = createAndUpdateDocument(adminToken, memberId);
        mockMvc.perform(get("/api/docs/" + docId + "/versions/diff?fromVersionNo=1&toVersionNo=2")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[?(@.type == 'added')].content").value(hasItem("aurora-new-line")));

        JsonNode baseRecord = createBaseTableRecord(adminToken, memberId);
        UUID baseId = UUID.fromString(baseRecord.get("baseId").asText());
        UUID tableId = UUID.fromString(baseRecord.get("tableId").asText());
        UUID statusFieldId = UUID.fromString(baseRecord.get("statusFieldId").asText());
        UUID dateFieldId = UUID.fromString(baseRecord.get("dateFieldId").asText());

        mockMvc.perform(get("/api/bases/" + baseId + "/tables/" + tableId + "/views/kanban?groupFieldId=" + statusFieldId)
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.columns[?(@.key == '进行中')].records.length()").value(hasItem(1)));

        mockMvc.perform(get("/api/bases/" + baseId + "/tables/" + tableId + "/views/calendar?dateFieldId=" + dateFieldId)
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets[?(@.date == '2026-06-30')].records.length()").value(hasItem(1)));

        UUID messageId = sendMessage(adminToken, conversationId, "aurora-message-first");
        mockMvc.perform(patch("/api/conversations/" + conversationId + "/messages/" + messageId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"aurora-message-edited\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.editedAt", not(blankOrNullString())));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages/" + messageId + "/reactions")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"👍\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reactions[0].emoji").value("👍"))
            .andExpect(jsonPath("$.reactions[0].reactedByMe").value(true));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages/" + messageId + "/pin")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pinned\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinnedAt", not(blankOrNullString())));

        mockMvc.perform(post("/api/search/reindex")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/search/reindex")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/search?q=aurora&limit=20")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()", greaterThanOrEqualTo(4)))
            .andExpect(jsonPath("$.items[*].objectType").value(hasItem("issue")))
            .andExpect(jsonPath("$.items[*].objectType").value(hasItem("document")))
            .andExpect(jsonPath("$.items[*].objectType").value(hasItem("base")))
            .andExpect(jsonPath("$.items[*].objectType").value(hasItem("base_table")))
            .andExpect(jsonPath("$.items[*].objectType").value(hasItem("base_record")))
            .andExpect(jsonPath("$.items[*].objectType").value(hasItem("message")))
            .andExpect(jsonPath("$.items[*].accessState").value(hasItem("available")))
            .andExpect(jsonPath("$.items[*].permissionExplanation").value(hasItem("当前用户具备查看该对象的权限。")));

        mockMvc.perform(get("/api/search?q=进行中&limit=20")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].objectType").value(hasItem("base_record")));

        mockMvc.perform(get("/api/search?q=aurora&limit=20")
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages/" + messageId + "/revoke")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revokedAt", not(blankOrNullString())))
            .andExpect(jsonPath("$.content").value(""));
    }

    private JsonNode createProject(String token, UUID memberId) throws Exception {
        String projectKey = ("M9" + UUID.randomUUID().toString().replace("-", ""))
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
        String response = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "M9 Project",
                          "memberIds": ["%s"]
                        }
                        """.formatted(projectKey, memberId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createIssue(String token, UUID projectId, UUID assigneeId) throws Exception {
        String response = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "bug",
                          "title": "aurora issue search",
                          "description": "aurora project stats target",
                          "priority": "high",
                          "assigneeId": "%s",
                          "dueAt": "2026-06-01"
                        }
                        """.formatted(assigneeId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("issue").get("id").asText());
    }

    private UUID createAndUpdateDocument(String token, UUID memberId) throws Exception {
        String response = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"aurora document\",\"content\":\"first-line\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID docId = UUID.fromString(objectMapper.readTree(response).get("document").get("id").asText());
        mockMvc.perform(post("/api/docs/" + docId + "/permissions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + memberId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/docs/" + docId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseVersionNo\":1,\"title\":\"aurora document\",\"content\":\"first-line\\naurora-new-line\"}"))
            .andExpect(status().isOk());
        return docId;
    }

    private JsonNode createBaseTableRecord(String token, UUID memberId) throws Exception {
        JsonNode base = objectMapper.readTree(mockMvc.perform(post("/api/bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"aurora base\",\"description\":\"m9\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        UUID baseId = UUID.fromString(base.get("base").get("id").asText());
        mockMvc.perform(post("/api/bases/" + baseId + "/members")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + memberId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk());
        JsonNode table = objectMapper.readTree(mockMvc.perform(post("/api/bases/" + baseId + "/tables")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"aurora table\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        UUID tableId = UUID.fromString(table.get("table").get("id").asText());
        UUID titleFieldId = createField(token, baseId, tableId, "标题", "text", Map.of(), true);
        UUID statusFieldId = createField(token, baseId, tableId, "状态", "single_select", Map.of("options", List.of("进行中", "完成")), false);
        UUID dateFieldId = createField(token, baseId, tableId, "日期", "date", Map.of(), false);
        mockMvc.perform(post("/api/bases/" + baseId + "/tables/" + tableId + "/records")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "values",
                    Map.of(
                        titleFieldId.toString(), "aurora base record",
                        statusFieldId.toString(), "进行中",
                        dateFieldId.toString(), "2026-06-30"
                    )
                ))))
            .andExpect(status().isOk());
        return objectMapper.valueToTree(Map.of(
            "baseId", baseId.toString(),
            "tableId", tableId.toString(),
            "statusFieldId", statusFieldId.toString(),
            "dateFieldId", dateFieldId.toString()
        ));
    }

    private UUID createField(String token, UUID baseId, UUID tableId, String name, String fieldType, Map<String, Object> config, boolean required) throws Exception {
        String response = mockMvc.perform(post("/api/bases/" + baseId + "/tables/" + tableId + "/fields")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "fieldType", fieldType, "config", config, "required", required))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode fields = objectMapper.readTree(response).get("fields");
        return UUID.fromString(fields.get(fields.size() - 1).get("id").asText());
    }

    private UUID sendMessage(String token, UUID conversationId, String content) throws Exception {
        String response = mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "%s"
                        }
                        """.formatted(UUID.randomUUID(), content)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createMember(String token, String username, String displayName) throws Exception {
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "%s",
                          "password": "member123456",
                          "displayName": "%s",
                          "email": "%s@example.com",
                          "roleCode": "member"
                        }
                        """.formatted(username, displayName, username)
                ))
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
