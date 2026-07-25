package com.colla.platform.modules.project.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WorkItemLayoutIdempotentReceiptIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void replayReturnsOriginalResponseAfterASecondRequestUpdatesTheLayout() throws Exception {
        TestUser admin = admin();
        TestUser owner = member(admin.token(), "wilreceipt");
        UUID spaceId = createSpace(owner.token());
        UUID typeId = createType(owner.token(), spaceId);
        UUID sectionId = UUID.randomUUID();
        String requestA = "wil-receipt-a-" + suffix();
        String requestB = "wil-receipt-b-" + suffix();
        String bodyA = layoutBody(sectionId, "Original response", 0);
        String bodyB = layoutBody(sectionId, "Later response", 0);

        JsonNode responseA = save(owner.token(), spaceId, typeId, requestA, bodyA);
        JsonNode responseB = save(owner.token(), spaceId, typeId, requestB, bodyB);
        JsonNode replayA = save(owner.token(), spaceId, typeId, requestA, bodyA);
        JsonNode current = json(mockMvc.perform(get(layoutPath(spaceId, typeId))
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andReturn());

        assertEquals(0, responseA.path("aggregateVersion").asLong());
        assertEquals(1, responseB.path("aggregateVersion").asLong());
        assertNotEquals(responseA.path("configHash").asText(), responseB.path("configHash").asText());
        assertEquals(responseA, replayA);
        assertEquals(responseB.path("configHash").asText(), current.path("configHash").asText());
        assertEquals(responseB.path("aggregateVersion").asLong(), current.path("aggregateVersion").asLong());

        JsonNode storedPayload = objectMapper.readTree(jdbcTemplate.queryForObject(
            """
                select response_payload::text
                  from project_work_item_layout_commands
                 where workspace_id=? and request_id=?
                """,
            String.class,
            owner.workspaceId(),
            requestA
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            """
                select response_schema_version
                  from project_work_item_layout_commands
                 where workspace_id=? and request_id=?
                """,
            Integer.class,
            owner.workspaceId(),
            requestA
        ));
        assertEquals(responseA.path("id").asText(), storedPayload.path("definition").path("id").asText());
        assertEquals(
            responseA.path("aggregateVersion").asLong(),
            storedPayload.path("definition").path("aggregateVersion").asLong()
        );
        assertEquals(
            responseA.path("configHash").asText(),
            storedPayload.path("definition").path("configHash").asText()
        );

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
            """
                update project_work_item_layout_commands
                   set response_config_hash=?
                 where workspace_id=? and request_id=?
                """,
            "0".repeat(64),
            owner.workspaceId(),
            requestA
        ));

        String legacyRequest = "wil-receipt-legacy-" + suffix();
        jdbcTemplate.update(
            """
                insert into project_work_item_layout_commands (
                    id, workspace_id, space_id, type_definition_id, request_id, operation,
                    request_hash, status, response_layout_id, created_by, created_at, completed_at
                )
                select ?, workspace_id, space_id, type_definition_id, ?, operation,
                       request_hash, 'completed', response_layout_id, created_by, now(), now()
                  from project_work_item_layout_commands
                 where workspace_id=? and request_id=?
                """,
            UUID.randomUUID(),
            legacyRequest,
            owner.workspaceId(),
            requestA
        );

        mockMvc.perform(put(layoutPath(spaceId, typeId))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", legacyRequest)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyA))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("layout_idempotency_legacy_receipt"));
    }

    private JsonNode save(
        String token,
        UUID spaceId,
        UUID typeId,
        String requestId,
        String body
    ) throws Exception {
        return json(mockMvc.perform(put(layoutPath(spaceId, typeId))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn());
    }

    private String layoutBody(UUID sectionId, String title, long aggregateVersion) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", sectionId.toString());
        node.putNull("parentId");
        node.put("nodeKey", "main");
        node.put("nodeType", "section");
        node.putNull("fieldId");
        node.putNull("fieldKey");
        node.put("sortOrder", 0);
        node.set("config", objectMapper.createObjectNode().put("title", title));
        node.set(
            "visibilityCondition",
            objectMapper.createObjectNode().put("schemaVersion", 1)
        );
        ObjectNode body = objectMapper.createObjectNode();
        body.set("nodes", objectMapper.createArrayNode().add(node));
        body.set("policies", objectMapper.createArrayNode());
        body.put("aggregateVersion", aggregateVersion);
        return objectMapper.writeValueAsString(body);
    }

    private UUID createType(String token, UUID spaceId) throws Exception {
        String key = "receipt_" + suffix();
        return UUID.fromString(json(mockMvc.perform(post(
                "/api/project-spaces/" + spaceId + "/configuration/types"
            )
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wil-receipt-type-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeKey\":\"" + key + "\",\"name\":\"Receipt\",\"sortOrder\":10}"))
            .andExpect(status().isOk())
            .andReturn()).path("id").asText());
    }

    private UUID createSpace(String token) throws Exception {
        String key = "wil-receipt-" + suffix();
        return UUID.fromString(json(mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spaceKey\":\"" + key + "\",\"name\":\"Receipt\",\"visibility\":\"private\"}"))
            .andExpect(status().isOk())
            .andReturn()).path("id").asText());
    }

    private TestUser admin() throws Exception {
        UUID id = jdbcTemplate.queryForObject(
            "select id from users where username='admin'",
            UUID.class
        );
        UUID workspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from users where id=?",
            UUID.class,
            id
        );
        return new TestUser(id, workspaceId, login("admin", "admin123456", "admin-" + suffix()));
    }

    private TestUser member(String adminToken, String prefix) throws Exception {
        String username = prefix + suffix();
        JsonNode response = json(mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"member123456","displayName":"Receipt Owner",
                     "email":"%s@example.com","roleCode":"member"}
                    """.formatted(username, username)))
            .andExpect(status().isOk())
            .andReturn());
        UUID id = UUID.fromString(response.path("id").asText());
        UUID workspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from users where id=?",
            UUID.class,
            id
        );
        return new TestUser(
            id,
            workspaceId,
            login(username, "member123456", "member-" + suffix())
        );
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        return json(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","deviceType":"web",
                     "deviceFingerprint":"%s","deviceName":"MockMvc","appVersion":"test"}
                    """.formatted(username, password, fingerprint)))
            .andExpect(status().isOk())
            .andReturn()).path("accessToken").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String layoutPath(UUID spaceId, UUID typeId) {
        return "/api/project-spaces/" + spaceId + "/configuration/types/" + typeId + "/layouts/create";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record TestUser(UUID id, UUID workspaceId, String token) {
    }
}
