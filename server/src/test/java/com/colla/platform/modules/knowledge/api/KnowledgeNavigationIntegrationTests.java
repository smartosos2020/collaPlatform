package com.colla.platform.modules.knowledge.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.colla.platform.modules.permission.application.PermissionDecisionService;
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
class KnowledgeNavigationIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PermissionDecisionService permissionDecisionService;

    @Test
    void resolvesCanonicalHomeAndFallsBackToRootWhenHomeIsMissing() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String token = login("admin", "admin123456", "m8-home-" + suffix);
        JsonNode created = createSpace(token, "M8 Home " + suffix);
        UUID spaceId = UUID.fromString(created.at("/space/id").asText());
        UUID rootItemId = UUID.fromString(created.at("/space/rootItemId").asText());
        UUID homeItemId = UUID.fromString(created.at("/space/homeItemId").asText());

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.navigation.homeItemId").value(homeItemId.toString()))
            .andExpect(jsonPath("$.space.navigation.webPath").value("/knowledge-bases/" + spaceId + "/items/" + homeItemId))
            .andExpect(jsonPath("$.homeItem.id").value(homeItemId.toString()));

        jdbcTemplate.update("update knowledge_base_spaces set home_item_id = null where id = ?", spaceId);

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.homeItemId").value(rootItemId.toString()))
            .andExpect(jsonPath("$.space.navigation.homeItemId").value(rootItemId.toString()))
            .andExpect(jsonPath("$.space.navigation.webPath").value("/knowledge-bases/" + spaceId + "/items/" + rootItemId))
            .andExpect(jsonPath("$.homeItem.id").value(rootItemId.toString()))
            .andExpect(jsonPath("$.homeItem.contentType").value("space"));
    }

    @Test
    void keepsContentPathStableAcrossOwnerEditorViewerAndRejectsOutsiderWithoutTitleLeak() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerToken = login("admin", "admin123456", "m8-owner-" + suffix);
        JsonNode created = createSpace(ownerToken, "M8 Private " + suffix);
        UUID spaceId = UUID.fromString(created.at("/space/id").asText());
        UUID rootItemId = UUID.fromString(created.at("/space/rootItemId").asText());
        UUID homeItemId = UUID.fromString(created.at("/space/homeItemId").asText());
        UUID ownerId = UUID.fromString(created.at("/space/ownerId").asText());
        UUID workspaceId = jdbcTemplate.queryForObject("select id from workspaces where slug = 'default'", UUID.class);
        UUID editorId = createMember(ownerToken, "m8edit" + suffix, "M8 Editor");
        UUID viewerId = createMember(ownerToken, "m8view" + suffix, "M8 Viewer");
        createMember(ownerToken, "m8out" + suffix, "M8 Outsider");
        grant(workspaceId, ownerId, spaceId, rootItemId, homeItemId, editorId, "edit");
        grant(workspaceId, ownerId, spaceId, rootItemId, homeItemId, viewerId, "view");

        String editorToken = login("m8edit" + suffix, "member123456", "m8-editor-" + suffix);
        String viewerToken = login("m8view" + suffix, "member123456", "m8-viewer-" + suffix);
        String outsiderToken = login("m8out" + suffix, "member123456", "m8-outsider-" + suffix);
        String canonicalPath = "/knowledge-bases/" + spaceId + "/items/" + homeItemId;

        for (String token : new String[] {ownerToken, editorToken, viewerToken}) {
            mockMvc.perform(get("/api/knowledge-bases/" + spaceId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.space.navigation.webPath").value(canonicalPath));
            mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/items/" + homeItemId)
                    .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.webPath").value(canonicalPath));
        }

        String denied = mockMvc.perform(get("/api/knowledge-bases/" + spaceId)
                .header("Authorization", bearer(outsiderToken)))
            .andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        assertFalse(denied.contains("M8 Private " + suffix));
        assertTrue(denied.isBlank() || denied.contains("403") || denied.contains("Forbidden"));
    }

    private JsonNode createSpace(String token, String name) throws Exception {
        return json(postJson(token, "/api/knowledge-bases", """
            {"name":"%s","code":"%s","visibility":"private","defaultPermissionLevel":"view"}
            """.formatted(name, "m8-" + UUID.randomUUID().toString().substring(0, 8)), 200));
    }

    private UUID createMember(String token, String username, String displayName) throws Exception {
        JsonNode response = json(postJson(token, "/api/admin/users", """
            {"username":"%s","password":"member123456","displayName":"%s","email":"%s@example.com","roleCode":"member"}
            """.formatted(username, displayName, username), 200));
        return UUID.fromString(response.get("id").asText());
    }

    private void grant(
        UUID workspaceId,
        UUID actorId,
        UUID spaceId,
        UUID rootItemId,
        UUID homeItemId,
        UUID userId,
        String permission
    ) {
        permissionDecisionService.grantResource(workspaceId, "knowledge_base", spaceId, "user", userId, permission, "direct", null, null, actorId);
        permissionDecisionService.grantResource(workspaceId, "knowledge_content", rootItemId, "user", userId, permission, "direct", null, null, actorId);
        permissionDecisionService.grantResource(workspaceId, "knowledge_content", homeItemId, "user", userId, permission, "direct", null, null, actorId);
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        JsonNode response = json(postJson(null, "/api/auth/login", """
            {"username":"%s","password":"%s","deviceType":"web","deviceFingerprint":"%s","deviceName":"MockMvc","appVersion":"test"}
            """.formatted(username, password, fingerprint), 200));
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
