package com.colla.platform.modules.base.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
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
class BaseControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void baseFieldRecordViewAndShareCardFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "base-admin-device-" + UUID.randomUUID());
        String viewerUsername = "bview" + UUID.randomUUID().toString().substring(0, 8);
        UUID viewerId = createMember(adminToken, viewerUsername, "Base Viewer");
        String viewerToken = login(viewerUsername, "member123456", "base-viewer-device-" + UUID.randomUUID());
        String outsiderUsername = "bout" + UUID.randomUUID().toString().substring(0, 8);
        createMember(adminToken, outsiderUsername, "Base Outsider");
        String outsiderToken = login(outsiderUsername, "member123456", "base-outsider-device-" + UUID.randomUUID());

        JsonNode base = createBase(adminToken, "需求跟踪", "M6 多维表格");
        UUID baseId = UUID.fromString(base.get("base").get("id").asText());

        mockMvc.perform(post("/api/bases/" + baseId + "/members")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + viewerId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()", greaterThanOrEqualTo(2)));

        JsonNode table = createTable(adminToken, baseId, "需求单");
        UUID tableId = UUID.fromString(table.get("table").get("id").asText());

        UUID titleFieldId = createField(adminToken, baseId, tableId, "标题", "text", Map.of(), true);
        UUID priorityFieldId = createField(adminToken, baseId, tableId, "优先级", "number", Map.of(), false);
        UUID ownerFieldId = createField(adminToken, baseId, tableId, "负责人", "member", Map.of(), false);
        UUID dueFieldId = createField(adminToken, baseId, tableId, "截止日期", "date", Map.of(), false);
        UUID statusFieldId = createField(adminToken, baseId, tableId, "状态", "status", Map.of("options", List.of("未开始", "进行中", "完成")), false);
        UUID tagsFieldId = createField(adminToken, baseId, tableId, "标签", "multi_select", Map.of("options", List.of("产品", "后端", "前端")), false);
        UUID urlFieldId = createField(adminToken, baseId, tableId, "官网", "url", Map.of(), false);
        UUID objectLinkFieldId = createField(adminToken, baseId, tableId, "关联文档", "object_link", Map.of("targetTypes", List.of("document")), false);
        UUID fileId = createFile(adminToken, baseId);
        UUID attachmentFieldId = createField(adminToken, baseId, tableId, "附件", "attachment", Map.of(), false);
        UUID docId = createDocument(adminToken);

        mockMvc.perform(post("/api/bases/" + baseId + "/tables/" + tableId + "/records")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("values", Map.of(
                    titleFieldId.toString(), "Bad Number",
                    priorityFieldId.toString(), "NaN"
                )))))
            .andExpect(status().isBadRequest());

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(titleFieldId.toString(), "Login Case");
        values.put(priorityFieldId.toString(), 2);
        values.put(ownerFieldId.toString(), viewerId.toString());
        values.put(dueFieldId.toString(), "2026-06-30");
        values.put(statusFieldId.toString(), "进行中");
        values.put(tagsFieldId.toString(), List.of("产品", "后端"));
        values.put(urlFieldId.toString(), "https://example.com/login");
        values.put(objectLinkFieldId.toString(), Map.of("objectType", "document", "objectId", docId.toString()));
        values.put(attachmentFieldId.toString(), fileId.toString());
        JsonNode record = createRecord(adminToken, baseId, tableId, values);
        UUID recordId = UUID.fromString(record.get("id").asText());

        mockMvc.perform(get("/api/base-records/" + recordId + "/detail")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[0].targetType").value("document"))
            .andExpect(jsonPath("$.activities[0].action").value("base.record.created"));

        mockMvc.perform(post("/api/base-records/" + recordId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"请补充回归结果\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].content").value("请补充回归结果"))
            .andExpect(jsonPath("$.activities[0].action").value("base.record.commented"));

        mockMvc.perform(post("/api/base-records/" + recordId + "/relations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"base\",\"targetId\":\"" + baseId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations.length()", greaterThanOrEqualTo(2)));

        mockMvc.perform(post("/api/bases/" + baseId + "/tables/" + tableId + "/records/query")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "filters", List.of(Map.of("fieldId", titleFieldId.toString(), "operator", "contains", "value", "Login")),
                    "sorts", List.of(Map.of("fieldId", priorityFieldId.toString(), "direction", "desc")),
                    "limit", 20,
                    "offset", 0
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value(recordId.toString()))
            .andExpect(jsonPath("$.items[0].primaryText").value("Login Case"));

        mockMvc.perform(post("/api/bases/" + baseId + "/tables/" + tableId + "/views")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "进行中需求",
                    "filters", List.of(Map.of("fieldId", statusFieldId.toString(), "operator", "eq", "value", "进行中")),
                    "sorts", List.of(Map.of("fieldId", dueFieldId.toString(), "direction", "asc")),
                    "visibleFieldIds", List.of(titleFieldId.toString(), statusFieldId.toString())
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("进行中需求"))
            .andExpect(jsonPath("$.visibleFieldIds.length()").value(2));

        mockMvc.perform(get("/api/bases/" + baseId + "/tables/" + tableId + "/export.csv")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("官网")))
            .andExpect(content().string(containsString("https://example.com/login")));

        mockMvc.perform(post("/api/bases/" + baseId + "/tables/" + tableId + "/import.csv")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "csv",
                    "标题,优先级,状态,官网\nImported Case,5,未开始,https://example.com/import\n"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(1))
            .andExpect(jsonPath("$.errors.length()").value(0));

        mockMvc.perform(get("/api/platform/objects/base/" + baseId + "/summary")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("available"))
            .andExpect(jsonPath("$.title").value("需求跟踪"));

        mockMvc.perform(get("/api/platform/objects/base_table/" + tableId + "/summary")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("available"))
            .andExpect(jsonPath("$.title").value("需求单"));

        mockMvc.perform(get("/api/platform/objects/base_record/" + recordId + "/summary")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("available"))
            .andExpect(jsonPath("$.title").value("Login Case"))
            .andExpect(jsonPath("$.webPath").value("/bases/" + baseId + "/tables/" + tableId + "/records/" + recordId));

        mockMvc.perform(post("/api/docs/" + docId + "/relations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"base_record\",\"targetId\":\"" + recordId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[0].targetType").value("base_record"))
            .andExpect(jsonPath("$.relations[0].title").value("Login Case"));

        UUID conversationId = createConversation(adminToken, viewerId);
        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "请看 /bases/%s/tables/%s/records/%s"
                        }
                        """.formatted(UUID.randomUUID(), baseId, tableId, recordId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.links[0].summary.title").value("Login Case"));

        mockMvc.perform(get("/api/base-records/" + recordId)
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isForbidden());
    }

    private JsonNode createBase(String token, String name, String description) throws Exception {
        String response = mockMvc.perform(post("/api/bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "description", description))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.base.name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode createTable(String token, UUID baseId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/bases/" + baseId + "/tables")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.table.name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createField(String token, UUID baseId, UUID tableId, String name, String fieldType, Map<String, Object> config, boolean required) throws Exception {
        String response = mockMvc.perform(post("/api/bases/" + baseId + "/tables/" + tableId + "/fields")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", name,
                    "fieldType", fieldType,
                    "config", config,
                    "required", required
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode fields = objectMapper.readTree(response).get("fields");
        return UUID.fromString(fields.get(fields.size() - 1).get("id").asText());
    }

    private JsonNode createRecord(String token, UUID baseId, UUID tableId, Map<String, Object> values) throws Exception {
        String response = mockMvc.perform(post("/api/bases/" + baseId + "/tables/" + tableId + "/records")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("values", values))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.primaryText").value("Login Case"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createFile(String token, UUID baseId) throws Exception {
        String response = mockMvc.perform(post("/api/files/upload-url")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "fileName": "base-evidence.txt",
                          "contentType": "text/plain",
                          "sizeBytes": 32,
                          "targetType": "base",
                          "targetId": "%s"
                        }
                        """.formatted(baseId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadUrl", not(blankOrNullString())))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("uploadId").asText());
    }

    private UUID createDocument(String token) throws Exception {
        String response = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Base Relation Note\",\"content\":\"M6\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("document").get("id").asText());
    }

    private UUID createConversation(String token, UUID memberId) throws Exception {
        String response = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "group",
                          "title": "Base Share",
                          "memberIds": ["%s"]
                        }
                        """.formatted(memberId)
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
