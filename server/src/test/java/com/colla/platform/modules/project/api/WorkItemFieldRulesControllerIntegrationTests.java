package com.colla.platform.modules.project.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WorkItemFieldRulesControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerConfiguresStableOptionsDefaultsRulesAndReplaysWithoutDuplicateEffects() throws Exception {
        TestUser root = root("wif2-full-root");
        TestUser owner = member(root.token(), "wif2owner");
        UUID spaceId = createSpace(owner.token(), "wif2-full");
        UUID typeId = createType(owner.token(), spaceId, "delivery", "Delivery");
        JsonNode priority = createField(owner.token(), spaceId, typeId, "priority", "Priority", "single_select");
        String requestId = "wif2-config-" + suffix();
        String body = configuration(0, "high", """
            [{"ruleKey":"allowed","kind":"allowed_values","schemaVersion":1,
              "config":{"values":["low","high"]}}]
            """, """
            [
              {"optionKey":"high","name":"High","color":"#EF4444","sortOrder":10,"status":"active"},
              {"optionKey":"low","name":"Low","color":"#22C55E","sortOrder":20,"status":"active"}
            ]
            """);

        JsonNode configured = configure(owner.token(), spaceId, typeId, priority, requestId, body, 200);
        assertEquals(1, configured.path("aggregateVersion").asInt());
        assertEquals("high", configured.path("config").path("defaultValue").asText());
        assertEquals(2, configured.path("options").size());
        assertTrue(configured.path("availableActions").toString().contains("configure"));

        JsonNode replay = configure(owner.token(), spaceId, typeId, priority, requestId, body, 200);
        assertEquals(configured.path("configHash").asText(), replay.path("configHash").asText());
        assertEquals(2, countOptions(UUID.fromString(priority.path("id").asText())));
        assertEquals(1, countAudit(UUID.fromString(priority.path("id").asText()), "work_item_field.configured"));
        assertEquals(1, countEvent(UUID.fromString(priority.path("id").asText()), "work_item_field.configured"));
        String auditMetadata = jdbcTemplate.queryForObject(
            "select metadata::text from audit_logs where target_id=? and action='work_item_field.configured'",
            String.class,
            UUID.fromString(priority.path("id").asText())
        );
        assertFalse(auditMetadata.contains("defaultValue"));
        assertTrue(auditMetadata.contains("optionDiff"));

        String next = configuration(1, "low", "[]", """
            [
              {"optionKey":"high","name":"Urgent","color":"#DC2626","sortOrder":20,"status":"disabled"},
              {"optionKey":"low","name":"Low","color":"#22C55E","sortOrder":10,"status":"active"}
            ]
            """);
        configured = configure(owner.token(), spaceId, typeId, priority, "wif2-update-" + suffix(), next, 200);
        assertEquals(2, configured.path("aggregateVersion").asInt());
        assertEquals("high", configured.path("options").get(1).path("optionKey").asText());
        assertEquals("disabled", configured.path("options").get(1).path("status").asText());

        configure(owner.token(), spaceId, typeId, priority, "wif2-remove-" + suffix(),
            configuration(2, "low", "[]", """
                [{"optionKey":"low","name":"Low","color":"#22C55E","sortOrder":10,"status":"active"}]
                """), 400);
        configure(owner.token(), spaceId, typeId, priority, "wif2-disabled-default-" + suffix(),
            configuration(2, "high", "[]", """
                [
                  {"optionKey":"low","name":"Low","color":"#22C55E","sortOrder":10,"status":"active"},
                  {"optionKey":"high","name":"Urgent","color":"#DC2626","sortOrder":20,"status":"disabled"}
                ]
                """), 400);
        assertEquals(2L, fieldVersion(priority));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
            "delete from project_work_item_field_options where field_definition_id=?",
            UUID.fromString(priority.path("id").asText())
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
            "update project_work_item_field_options set option_key='replacement' where field_definition_id=? and option_key='low'",
            UUID.fromString(priority.path("id").asText())
        ));
    }

    @Test
    void roleBoundariesAndCrossFieldOptionReferencesUseStableErrors() throws Exception {
        TestUser root = root("wif2-rbac-root");
        TestUser owner = member(root.token(), "wif2owner");
        TestUser admin = member(root.token(), "wif2admin");
        TestUser ordinary = member(root.token(), "wif2member");
        TestUser guest = member(root.token(), "wif2guest");
        TestUser outsider = member(root.token(), "wif2outside");
        UUID spaceId = createSpace(owner.token(), "wif2-rbac");
        addSpaceMember(spaceId, admin.id(), "admin", owner.id());
        addSpaceMember(spaceId, ordinary.id(), "member", owner.id());
        addSpaceMember(spaceId, guest.id(), "guest", owner.id());
        UUID typeId = createType(owner.token(), spaceId, "delivery", "Delivery");
        JsonNode field = createField(owner.token(), spaceId, typeId, "priority", "Priority", "single_select");
        String valid = configuration(0, "high", "[]", """
            [{"optionKey":"high","name":"High","color":"#EF4444","sortOrder":10,"status":"active"}]
            """);

        configure(admin.token(), spaceId, typeId, field, "wif2-admin-" + suffix(), valid, 200);
        for (TestUser denied : List.of(ordinary, guest)) {
            configure(denied.token(), spaceId, typeId, field, "wif2-denied-" + suffix(),
                configuration(1, "high", "[]", """
                    [{"optionKey":"high","name":"High","color":"#EF4444","sortOrder":10,"status":"active"}]
                    """), 403);
        }
        for (TestUser hidden : List.of(outsider, root)) {
            configure(hidden.token(), spaceId, typeId, field, "wif2-hidden-" + suffix(),
                configuration(1, "high", "[]", """
                    [{"optionKey":"high","name":"High","color":"#EF4444","sortOrder":10,"status":"active"}]
                    """), 404);
        }

        JsonNode second = createField(owner.token(), spaceId, typeId, "severity", "Severity", "single_select");
        mockMvc.perform(put(configurationPath(spaceId, typeId, second))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif2-cross-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(configuration(0, "high", "[]", "[]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("invalid_default_value"));

        mockMvc.perform(put(configurationPath(spaceId, typeId, second))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif2-version-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"schemaVersion":2,"required":false,"defaultValue":null,"validationRules":[],
                     "options":[],"aggregateVersion":0}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("invalid_field_configuration"));
    }

    @Test
    void optimisticConcurrencyAndInvalidRuleFailuresLeaveNoPartialConfiguration() throws Exception {
        TestUser root = root("wif2-race-root");
        TestUser owner = member(root.token(), "wif2owner");
        UUID spaceId = createSpace(owner.token(), "wif2-race");
        UUID typeId = createType(owner.token(), spaceId, "delivery", "Delivery");
        JsonNode field = createField(owner.token(), spaceId, typeId, "priority", "Priority", "single_select");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> first = executor.submit(() -> concurrentConfigure(
                owner.token(), spaceId, typeId, field, "alpha", "wif2-race-a-" + suffix(), ready, start
            ));
            Future<MvcResult> second = executor.submit(() -> concurrentConfigure(
                owner.token(), spaceId, typeId, field, "beta", "wif2-race-b-" + suffix(), ready, start
            ));
            ready.await();
            start.countDown();
            List<Integer> statuses = List.of(
                first.get().getResponse().getStatus(), second.get().getResponse().getStatus()
            );
            assertEquals(1, statuses.stream().filter(status -> status == 200).count());
            assertEquals(1, statuses.stream().filter(status -> status == 409).count());
        }
        assertEquals(1L, fieldVersion(field));
        assertEquals(1, countOptions(UUID.fromString(field.path("id").asText())));
        assertEquals(1, countAudit(UUID.fromString(field.path("id").asText()), "work_item_field.configured"));
        String winnerKey = jdbcTemplate.queryForObject(
            "select option_key from project_work_item_field_options where field_definition_id=?",
            String.class,
            UUID.fromString(field.path("id").asText())
        );

        mockMvc.perform(put(configurationPath(spaceId, typeId, field))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif2-invalid-rule-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"schemaVersion":1,"required":false,"defaultValue":null,
                     "validationRules":[{"ruleKey":"range","kind":"number_range","schemaVersion":1,
                       "config":{"min":10,"max":1}}],
                     "options":[{"optionKey":"%s","name":"Winner","color":"#111111","sortOrder":10,"status":"active"}],
                     "aggregateVersion":1}
                    """.formatted(winnerKey)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("invalid_validation_rule"));
        assertEquals(1L, fieldVersion(field));

        JsonNode openApi = json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertTrue(openApi.path("paths").has(
            "/api/project-spaces/{spaceId}/configuration/types/{typeId}/fields/{fieldId}/configuration"
        ));
        assertFalse(openApi.path("paths").has("/api/work-items"));
    }

    private MvcResult concurrentConfigure(
        String token,
        UUID spaceId,
        UUID typeId,
        JsonNode field,
        String option,
        String requestId,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(put(configurationPath(spaceId, typeId, field))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(configuration(0, option, "[]", """
                    [{"optionKey":"%s","name":"%s","color":"#111111","sortOrder":10,"status":"active"}]
                    """.formatted(option, option))))
            .andReturn();
    }

    private JsonNode configure(
        String token,
        UUID spaceId,
        UUID typeId,
        JsonNode field,
        String requestId,
        String body,
        int expectedStatus
    ) throws Exception {
        MvcResult result = mockMvc.perform(put(configurationPath(spaceId, typeId, field))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().is(expectedStatus))
            .andReturn();
        return result.getResponse().getContentAsString().isBlank()
            ? objectMapper.createObjectNode()
            : json(result);
    }

    private String configuration(long version, String defaultValue, String rules, String options) {
        String defaultJson = defaultValue == null ? "null" : "\"" + defaultValue + "\"";
        return """
            {"schemaVersion":1,"required":true,"defaultValue":%s,
             "validationRules":%s,"options":%s,"aggregateVersion":%d}
            """.formatted(defaultJson, rules, options, version);
    }

    private JsonNode createField(String token, UUID spaceId, UUID typeId, String key, String name, String type) throws Exception {
        return json(mockMvc.perform(post(fieldsPath(spaceId, typeId))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wif2-field-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldKey":"%s","name":"%s","fieldType":"%s","config":{},"sortOrder":10}
                    """.formatted(key, name, type)))
            .andExpect(status().isOk())
            .andReturn());
    }

    private UUID createType(String token, UUID spaceId, String key, String name) throws Exception {
        JsonNode response = json(mockMvc.perform(post(basePath(spaceId) + "/types")
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wif2-type-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeKey\":\"" + key + "\",\"name\":\"" + name + "\",\"sortOrder\":10}"))
            .andExpect(status().isOk())
            .andReturn());
        return UUID.fromString(response.path("id").asText());
    }

    private UUID createSpace(String token, String prefix) throws Exception {
        String key = prefix + "-" + suffix();
        JsonNode response = json(mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spaceKey\":\"" + key + "\",\"name\":\"" + prefix + "\",\"visibility\":\"private\"}"))
            .andExpect(status().isOk())
            .andReturn());
        return UUID.fromString(response.path("id").asText());
    }

    private TestUser root(String fingerprint) throws Exception {
        UUID id = jdbcTemplate.queryForObject("select id from users where username='admin'", UUID.class);
        UUID workspaceId = jdbcTemplate.queryForObject("select workspace_id from users where id=?", UUID.class, id);
        return new TestUser(id, workspaceId, login("admin", "admin123456", fingerprint + "-" + suffix()));
    }

    private TestUser member(String rootToken, String prefix) throws Exception {
        String username = prefix + suffix();
        JsonNode response = json(mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(rootToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"member123456","displayName":"%s","email":"%s@example.com","roleCode":"member"}
                    """.formatted(username, prefix, username)))
            .andExpect(status().isOk())
            .andReturn());
        UUID id = UUID.fromString(response.path("id").asText());
        UUID workspaceId = jdbcTemplate.queryForObject("select workspace_id from users where id=?", UUID.class, id);
        return new TestUser(id, workspaceId, login(username, "member123456", prefix + "-" + suffix()));
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        return json(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","deviceType":"web","deviceFingerprint":"%s","deviceName":"MockMvc","appVersion":"test"}
                    """.formatted(username, password, fingerprint)))
            .andExpect(status().isOk())
            .andReturn()).path("accessToken").asText();
    }

    private void addSpaceMember(UUID spaceId, UUID userId, String role, UUID actorId) {
        UUID workspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from project_spaces where id=?", UUID.class, spaceId
        );
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_space_members
                    (id, workspace_id, space_id, user_id, status, joined_at, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId, workspaceId, spaceId, userId, actorId, actorId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments
                    (id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at)
                values (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), workspaceId, spaceId, memberId, role, actorId
        );
    }

    private long fieldVersion(JsonNode field) {
        return jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_field_definitions where id=?",
            Long.class,
            UUID.fromString(field.path("id").asText())
        );
    }

    private int countOptions(UUID fieldId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from project_work_item_field_options where field_definition_id=?",
            Integer.class,
            fieldId
        );
    }

    private int countAudit(UUID fieldId, String action) {
        return jdbcTemplate.queryForObject(
            "select count(*) from audit_logs where target_id=? and action=?",
            Integer.class,
            fieldId,
            action
        );
    }

    private int countEvent(UUID fieldId, String eventType) {
        return jdbcTemplate.queryForObject(
            "select count(*) from domain_events where aggregate_id=? and event_type=?",
            Integer.class,
            fieldId,
            eventType
        );
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String configurationPath(UUID spaceId, UUID typeId, JsonNode field) {
        return fieldsPath(spaceId, typeId) + "/" + field.path("id").asText() + "/configuration";
    }

    private String fieldsPath(UUID spaceId, UUID typeId) {
        return basePath(spaceId) + "/types/" + typeId + "/fields";
    }

    private String basePath(UUID spaceId) {
        return "/api/project-spaces/" + spaceId + "/configuration";
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
