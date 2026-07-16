package com.colla.platform.modules.knowledge.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeObjectEntryIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void filtersObjectChoicesAndCoversAllSelectableObjectTypes() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminToken = login("admin", "admin123456", "m7-admin-" + suffix);
        String memberName = "m7member" + suffix;
        createMember(adminToken, memberName);
        String memberToken = login(memberName, "member123456", "m7-member-" + suffix);
        UUID spaceId = createSpace(adminToken, "M7 Choice " + suffix);
        UUID knowledgeId = createKnowledgeItem(adminToken, spaceId, "M7 Knowledge " + suffix, "markdown", null, null);

        JsonNode base = json(postJson(adminToken, "/api/bases", """
            {"name":"M7 Base %s","description":"choice"}
            """.formatted(suffix), 200));
        UUID baseId = UUID.fromString(base.at("/base/id").asText());

        JsonNode project = json(postJson(adminToken, "/api/projects", """
            {"projectKey":"M7%s","name":"M7 Project %s","description":"choice","memberIds":[]}
            """.formatted(suffix.substring(0, 4), suffix), 200));
        UUID projectId = UUID.fromString(project.get("id").asText());

        JsonNode upload = json(postJson(adminToken, "/api/files/upload-url", """
            {"fileName":"m7-%s.txt","contentType":"text/plain","sizeBytes":12}
            """.formatted(suffix), 200));
        UUID fileId = UUID.fromString(upload.get("uploadId").asText());
        postJson(adminToken, "/api/files/complete", """
            {"fileId":"%s"}
            """.formatted(fileId), 200);

        String choices = mockMvc.perform(get("/api/platform/object-choices")
                .header("Authorization", bearer(adminToken))
                .param("types", "base", "project", "file", "knowledge_content")
                .param("source", "all")
                .param("limit", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[?(@.objectId=='" + baseId + "')]").exists())
            .andExpect(jsonPath("$.items[?(@.objectId=='" + projectId + "')]").exists())
            .andExpect(jsonPath("$.items[?(@.objectId=='" + fileId + "')]").exists())
            .andExpect(jsonPath("$.items[?(@.objectId=='" + knowledgeId + "')]").exists())
            .andReturn().getResponse().getContentAsString();
        assertTrue(choices.contains("M7 Base " + suffix));

        String memberChoices = mockMvc.perform(get("/api/platform/object-choices")
                .header("Authorization", bearer(memberToken))
                .param("types", "base", "project", "file", "knowledge_content")
                .param("query", suffix)
                .param("limit", "50"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(memberChoices.contains("M7 Base " + suffix));
        assertFalse(memberChoices.contains("M7 Project " + suffix));
        assertFalse(memberChoices.contains("M7 Knowledge " + suffix));
    }

    @Test
    void createsBaseEntryAtomicallyAndRejectsForgedOrInvalidTargets() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String token = login("admin", "admin123456", "m7-atomic-" + suffix);
        UUID spaceId = createSpace(token, "M7 Atomic " + suffix);

        JsonNode entry = json(postJson(token, "/api/knowledge-bases/" + spaceId + "/items/base-entry", """
            {"newBaseName":"M7 Atomic Base %s","newBaseDescription":"created with entry","displayMode":"inline","targetTitleStrategy":"follow_target"}
            """.formatted(suffix), 200));
        UUID entryId = UUID.fromString(entry.at("/item/id").asText());
        UUID baseId = UUID.fromString(entry.at("/item/targetObjectId").asText());
        assertTrue(entry.at("/item/targetRoute").asText().equals("/bases/" + baseId));
        assertTrue(entry.at("/item/targetSummary/accessState").asText().equals("available"));

        mockMvc.perform(patch("/api/bases/" + baseId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"M7 Renamed Base %s","description":"renamed target"}
                    """.formatted(suffix)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/items/" + entryId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.title").value("M7 Renamed Base " + suffix))
            .andExpect(jsonPath("$.item.targetRoute").value("/bases/" + baseId));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Forged","contentType":"object_ref","targetObjectType":"base","targetObjectId":"%s","targetRoute":"/admin/users"}
                    """.formatted(baseId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.targetRoute").value("/bases/" + baseId));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Unknown","contentType":"object_ref","targetObjectType":"base","targetObjectId":"%s"}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"Invalid","contentType":"object_ref","targetObjectType":"unknown","targetObjectId":"%s"}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest());

        String rollbackName = "M7 Rollback " + suffix;
        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/base-entry")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parentId":"%s","newBaseName":"%s"}
                    """.formatted(UUID.randomUUID(), rollbackName)))
            .andExpect(status().isNotFound());
        Integer count = jdbcTemplate.queryForObject("select count(*) from bases where name = ?", Integer.class, rollbackName);
        assertTrue(count != null && count == 0);

        mockMvc.perform(get("/api/knowledge-object-references/base/" + baseId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.itemId=='" + entryId + "')].webPath").exists());
    }

    @Test
    void followsTargetLifecycleAndHidesUnavailableTargetDetails() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String token = login("admin", "admin123456", "m7-life-" + suffix);
        UUID spaceId = createSpace(token, "M7 Lifecycle " + suffix);
        UUID targetId = createKnowledgeItem(token, spaceId, "Lifecycle Target " + suffix, "markdown", null, null);
        JsonNode reference = json(postJson(token, "/api/knowledge-bases/" + spaceId + "/items", """
            {"title":"Manual title","contentType":"object_ref","targetObjectType":"knowledge_content","targetObjectId":"%s","targetTitleStrategy":"alias","entryAlias":"Stable alias","displayMode":"preview"}
            """.formatted(targetId), 200));
        UUID referenceId = UUID.fromString(reference.at("/item/id").asText());
        assertTrue(reference.at("/item/title").asText().equals("Stable alias"));

        mockMvc.perform(patch("/api/knowledge-bases/" + spaceId + "/items/" + referenceId + "/object-entry")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"displayMode":"link","targetTitleStrategy":"follow_target"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.title").value("Lifecycle Target " + suffix))
            .andExpect(jsonPath("$.item.displayMode").value("link"));

        postJson(token, "/api/knowledge-bases/" + spaceId + "/items/" + targetId + "/archive", "{}", 200);
        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/items/" + referenceId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.targetSummary.accessState").value("deleted"))
            .andExpect(jsonPath("$.item.targetSummary.title").doesNotExist())
            .andExpect(jsonPath("$.item.title").value("已删除对象入口"))
            .andExpect(jsonPath("$.item.targetRoute").doesNotExist());

        postJson(token, "/api/knowledge-bases/" + spaceId + "/items/" + targetId + "/restore", "{}", 200);
        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/items/" + referenceId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.targetSummary.accessState").value("available"))
            .andExpect(jsonPath("$.item.title").value("Lifecycle Target " + suffix));

        JsonNode pending = json(postJson(token, "/api/files/upload-url", """
            {"fileName":"pending-%s.txt","contentType":"text/plain","sizeBytes":10}
            """.formatted(suffix), 200));
        UUID pendingFileId = UUID.fromString(pending.get("uploadId").asText());
        UUID workspaceId = jdbcTemplate.queryForObject("select id from workspaces where slug = 'default'", UUID.class);
        jdbcTemplate.update("update knowledge_base_items set target_object_type = 'file', target_object_id = ?, target_route = ? where workspace_id = ? and id = ?",
            pendingFileId, "/files/" + pendingFileId, workspaceId, referenceId);
        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/items/" + referenceId)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.targetSummary.accessState").value("disabled"))
            .andExpect(jsonPath("$.item.title").value("已停用对象入口"));

        postJson(token, "/api/knowledge-bases/" + spaceId + "/items", """
            {"title":"External docs","contentType":"external_link","targetRoute":"https://example.com/docs","displayMode":"link"}
            """, 200);
    }

    private UUID createSpace(String token, String name) throws Exception {
        JsonNode response = json(postJson(token, "/api/knowledge-bases", """
            {"name":"%s","code":"%s","visibility":"private","defaultPermissionLevel":"view"}
            """.formatted(name, "m7-" + UUID.randomUUID().toString().substring(0, 8)), 200));
        return UUID.fromString(response.at("/space/id").asText());
    }

    private UUID createKnowledgeItem(String token, UUID spaceId, String title, String type, String targetType, UUID targetId) throws Exception {
        String target = targetType == null ? "" : ",\"targetObjectType\":\"" + targetType + "\",\"targetObjectId\":\"" + targetId + "\"";
        JsonNode response = json(postJson(token, "/api/knowledge-bases/" + spaceId + "/items", """
            {"title":"%s","contentType":"%s","content":"# %s"%s}
            """.formatted(title, type, title, target), 200));
        return UUID.fromString(response.at("/item/id").asText());
    }

    private void createMember(String token, String username) throws Exception {
        postJson(token, "/api/admin/users", """
            {"username":"%s","password":"member123456","displayName":"M7 Member","email":"%s@example.com","roleCode":"member"}
            """.formatted(username, username), 200);
    }

    private String login(String username, String password, String deviceFingerprint) throws Exception {
        JsonNode response = json(postJson(null, "/api/auth/login", """
            {"username":"%s","password":"%s","deviceType":"web","deviceFingerprint":"%s","deviceName":"MockMvc","appVersion":"test"}
            """.formatted(username, password, deviceFingerprint), 200));
        return response.get("accessToken").asText();
    }

    private String postJson(String token, String path, String body, int expectedStatus) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) request.header("Authorization", bearer(token));
        return mockMvc.perform(request)
            .andExpect(status().is(expectedStatus))
            .andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String source) throws Exception {
        return objectMapper.readTree(source);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
