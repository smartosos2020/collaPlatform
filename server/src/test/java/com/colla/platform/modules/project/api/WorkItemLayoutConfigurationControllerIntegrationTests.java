package com.colla.platform.modules.project.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WorkItemLayoutConfigurationControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerSavesReplaysAndReadsLayoutWithStableDiagnosticsAndSideEffects() throws Exception {
        TestUser root = root("wil-root");
        TestUser owner = member(root.token(), "wilowner");
        UUID spaceId = createSpace(owner.token(), "wil-full");
        UUID typeId = UUID.fromString(createType(owner.token(), spaceId, "delivery").get("id").asText());
        JsonNode field = createField(owner.token(), spaceId, typeId, "title");
        UUID sectionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        String requestId = "wil-save-" + suffix();
        String body = layoutBody(sectionId, nodeId, policyId, field, 0, "editable");

        JsonNode first = save(owner.token(), spaceId, typeId, "create", requestId, body);
        assertEquals(0, first.get("aggregateVersion").asLong());
        assertEquals("create", first.get("layoutKind").asText());
        JsonNode replay = save(owner.token(), spaceId, typeId, "create", requestId, body);
        assertEquals(first.get("id").asText(), replay.get("id").asText());
        assertEquals(1, count("project_work_item_layouts", spaceId));
        assertEquals(1, count("project_work_item_layout_commands", spaceId));
        assertEquals(1, count("project_work_item_field_access_policies", spaceId));

        mockMvc.perform(get(layoutPath(spaceId, typeId, "create"))
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableActions", contains("save")))
            .andExpect(jsonPath("$.nodes.length()").value(2))
            .andExpect(jsonPath("$.policies[0].fieldKey").value("title"))
            .andExpect(jsonPath("$.diagnostics").isEmpty());

        mockMvc.perform(put(layoutPath(spaceId, typeId, "create"))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(layoutBody(sectionId, nodeId, policyId, field, 0, "readonly")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("layout_idempotency_conflict"));

        save(
            owner.token(),
            spaceId,
            typeId,
            "create",
            "wil-update-" + suffix(),
            layoutBody(sectionId, nodeId, policyId, field, 0, "readonly")
        );
        mockMvc.perform(put(layoutPath(spaceId, typeId, "create"))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wil-stale-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(layoutBody(sectionId, nodeId, policyId, field, 0, "editable")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("version_conflict"));

        jdbcTemplate.update(
            "update project_work_item_field_definitions set status='disabled' where id=?",
            UUID.fromString(field.get("id").asText())
        );
        mockMvc.perform(get(layoutPath(spaceId, typeId, "create"))
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.diagnostics[*].code", hasItem("FIELD_REFERENCE_DISABLED")))
            .andExpect(jsonPath("$.nodes.length()").value(2));

        UUID layoutId = UUID.fromString(first.get("id").asText());
        assertEquals(2, jdbcTemplate.queryForObject(
            "select count(*) from audit_logs where target_id=? and action='work_item_layout.saved'",
            Integer.class,
            layoutId
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
            "select count(*) from domain_events where aggregate_id=? and event_type='work_item_layout.saved'",
            Integer.class,
            layoutId
        ));
        String auditMetadata = jdbcTemplate.queryForObject(
            """
                select metadata::text from audit_logs
                 where target_id=?
                   and action='work_item_layout.saved'
                   and metadata->>'requestId'=?
                """,
            String.class,
            layoutId,
            requestId
        );
        assertFalse(auditMetadata.contains("editable"));
        assertTrue(auditMetadata.contains(first.get("configHash").asText()));
    }

    @Test
    void projectRolesAndTenantFieldScopeProtectLayoutConfiguration() throws Exception {
        TestUser root = root("wil-rbac-root");
        TestUser owner = member(root.token(), "wilowner");
        TestUser admin = member(root.token(), "wiladmin");
        TestUser ordinary = member(root.token(), "wilmember");
        TestUser outsider = member(root.token(), "wiloutside");
        UUID spaceId = createSpace(owner.token(), "wil-rbac");
        addSpaceMember(spaceId, admin.id(), "admin", owner.id());
        addSpaceMember(spaceId, ordinary.id(), "member", owner.id());
        UUID typeId = UUID.fromString(createType(owner.token(), spaceId, "delivery").get("id").asText());
        JsonNode field = createField(owner.token(), spaceId, typeId, "title");
        String body = layoutBody(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), field, 0, "editable"
        );

        save(admin.token(), spaceId, typeId, "detail", "wil-admin-" + suffix(), body);
        mockMvc.perform(get(layoutPath(spaceId, typeId, "detail"))
                .header("Authorization", bearer(ordinary.token())))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(layoutPath(spaceId, typeId, "detail"))
                .header("Authorization", bearer(outsider.token())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"));
        mockMvc.perform(get(layoutPath(spaceId, typeId, "detail"))
                .header("Authorization", bearer(root.token())))
            .andExpect(status().isNotFound());

        UUID foreignSpace = createSpace(owner.token(), "wil-foreign");
        UUID foreignType = UUID.fromString(
            createType(owner.token(), foreignSpace, "foreign_type").get("id").asText()
        );
        JsonNode foreignField = createField(owner.token(), foreignSpace, foreignType, "foreign_title");
        mockMvc.perform(put(layoutPath(spaceId, typeId, "create"))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wil-cross-scope-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(layoutBody(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    foreignField,
                    0,
                    "editable"
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("invalid_layout_field_reference"));
    }

    @Test
    void createAndDetailLayoutsStayIsolatedWhileAtomicCommandsAndConditionsRemainVersioned() throws Exception {
        TestUser root = root("wil-command-root");
        TestUser owner = member(root.token(), "wilcommand");
        UUID spaceId = createSpace(owner.token(), "wil-command");
        UUID typeId = UUID.fromString(createType(owner.token(), spaceId, "campaign").get("id").asText());
        JsonNode title = createField(owner.token(), spaceId, typeId, "title");
        JsonNode trigger = createField(owner.token(), spaceId, typeId, "trigger");
        UUID createSection = UUID.randomUUID();
        UUID titleNode = UUID.randomUUID();
        UUID triggerNode = UUID.randomUUID();
        String createBody = layoutWithCondition(
            createSection, titleNode, triggerNode, title, trigger, 0, "contains"
        );
        JsonNode create = save(
            owner.token(), spaceId, typeId, "create", "wil-command-create-" + suffix(), createBody
        );
        JsonNode detail = save(
            owner.token(), spaceId, typeId, "detail", "wil-command-detail-" + suffix(),
            layoutBody(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), title, 0, "readonly"
            )
        );
        assertFalse(create.get("id").asText().equals(detail.get("id").asText()));
        assertFalse(create.get("configHash").asText().equals(detail.get("configHash").asText()));

        UUID summaryId = UUID.randomUUID();
        String command = """
            {
              "operation":"add",
              "parentId":"%s",
              "targetSortOrder":2,
              "node":{
                "id":"%s","parentId":"%s","nodeKey":"summary","nodeType":"summary",
                "sortOrder":2,"config":{"title":"摘要"},"visibilityCondition":{"schemaVersion":1}
              },
              "aggregateVersion":0
            }
            """.formatted(createSection, summaryId, createSection);
        String requestId = "wil-node-add-" + suffix();
        JsonNode changed = json(mockMvc.perform(post(layoutCommandPath(spaceId, typeId, "create"))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aggregateVersion").value(1))
            .andExpect(jsonPath("$.nodes[*].nodeKey", hasItem("summary")))
            .andReturn());
        JsonNode replay = json(mockMvc.perform(post(layoutCommandPath(spaceId, typeId, "create"))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(command))
            .andExpect(status().isOk())
            .andReturn());
        assertEquals(changed.get("configHash").asText(), replay.get("configHash").asText());

        mockMvc.perform(post(layoutCommandPath(spaceId, typeId, "create"))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wil-node-stale-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(command.replace("\"aggregateVersion\":0", "\"aggregateVersion\":0")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("version_conflict"));

        mockMvc.perform(put(layoutPath(spaceId, typeId, "create"))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wil-invalid-condition-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(layoutWithCondition(
                    createSection, titleNode, triggerNode, title, trigger, 1, "gt"
                )))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("invalid_layout_condition_operator"));

        UUID conditionTypeId = UUID.fromString(
            createType(owner.token(), spaceId, "condition_safety").get("id").asText()
        );
        JsonNode conditionTitle = createField(owner.token(), spaceId, conditionTypeId, "condition_title");
        JsonNode conditionTrigger = createField(owner.token(), spaceId, conditionTypeId, "condition_trigger");
        mockMvc.perform(put(layoutPath(spaceId, conditionTypeId, "create"))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wil-hidden-condition-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(layoutWithHiddenCondition(conditionTitle, conditionTrigger)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("layout_condition_hidden_dependency"));
        mockMvc.perform(put(layoutPath(spaceId, conditionTypeId, "create"))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wil-cycle-condition-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(layoutWithConditionCycle(conditionTitle, conditionTrigger)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("layout_condition_cycle"));
    }

    @Test
    void openApiPublishesLayoutsWithoutIntroducingWorkItemInstances() throws Exception {
        JsonNode openApi = json(mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn());
        assertTrue(openApi.path("paths").has(
            "/api/project-spaces/{spaceId}/configuration/types/{typeId}/layouts/{layoutKind}"
        ));
        assertTrue(openApi.path("paths").has(
            "/api/project-spaces/{spaceId}/configuration/types/{typeId}/layouts/{layoutKind}/nodes:command"
        ));
        assertFalse(openApi.path("paths").has("/api/work-items"));
        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema='public' and table_name='project_work_items'",
            Integer.class
        ));
    }

    private JsonNode save(
        String token,
        UUID spaceId,
        UUID typeId,
        String kind,
        String requestId,
        String body
    ) throws Exception {
        return json(mockMvc.perform(put(layoutPath(spaceId, typeId, kind))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn());
    }

    private String layoutBody(
        UUID sectionId,
        UUID fieldNodeId,
        UUID policyId,
        JsonNode field,
        long version,
        String mode
    ) {
        return """
            {
              "nodes":[
                {"id":"%s","nodeKey":"main","nodeType":"section","sortOrder":0,
                 "config":{},"visibilityCondition":{"schemaVersion":1}},
                {"id":"%s","parentId":"%s","nodeKey":"title_field","nodeType":"field",
                 "fieldId":"%s","fieldKey":"%s","sortOrder":0,
                 "config":{},"visibilityCondition":{"schemaVersion":1}}
              ],
              "policies":[
                {"id":"%s","fieldId":"%s","fieldKey":"%s","policyKey":"title_access",
                 "policy":{"schemaVersion":1,"mode":"%s"}}
              ],
              "aggregateVersion":%d
            }
            """.formatted(
            sectionId,
            fieldNodeId,
            sectionId,
            field.get("id").asText(),
            field.get("fieldKey").asText(),
            policyId,
            field.get("id").asText(),
            field.get("fieldKey").asText(),
            mode,
            version
        );
    }

    private String layoutWithCondition(
        UUID sectionId,
        UUID titleNodeId,
        UUID triggerNodeId,
        JsonNode title,
        JsonNode trigger,
        long version,
        String operator
    ) {
        return """
            {
              "nodes":[
                {"id":"%s","nodeKey":"main","nodeType":"section","sortOrder":0,
                 "config":{"title":"Main"},"visibilityCondition":{"schemaVersion":1}},
                {"id":"%s","parentId":"%s","nodeKey":"title_field","nodeType":"field",
                 "fieldId":"%s","fieldKey":"%s","sortOrder":0,"config":{},
                 "visibilityCondition":{"schemaVersion":1,"expression":{
                   "kind":"predicate","source":"field","fieldId":"%s","fieldKey":"%s",
                   "operator":"%s","value":"ready"
                 }}},
                {"id":"%s","parentId":"%s","nodeKey":"trigger_field","nodeType":"field",
                 "fieldId":"%s","fieldKey":"%s","sortOrder":1,
                 "config":{},"visibilityCondition":{"schemaVersion":1}}
              ],
              "policies":[],
              "aggregateVersion":%d
            }
            """.formatted(
            sectionId,
            titleNodeId, sectionId, title.get("id").asText(), title.get("fieldKey").asText(),
            trigger.get("id").asText(), trigger.get("fieldKey").asText(), operator,
            triggerNodeId, sectionId, trigger.get("id").asText(), trigger.get("fieldKey").asText(),
            version
        );
    }

    private String layoutWithHiddenCondition(JsonNode title, JsonNode trigger) {
        UUID sectionId = UUID.randomUUID();
        return """
            {
              "nodes":[
                {"id":"%s","nodeKey":"main","nodeType":"section","sortOrder":0,
                 "config":{},"visibilityCondition":{"schemaVersion":1}},
                {"id":"%s","parentId":"%s","nodeKey":"condition_title","nodeType":"field",
                 "fieldId":"%s","fieldKey":"%s","sortOrder":0,"config":{},
                 "visibilityCondition":{"schemaVersion":1,"expression":{
                   "kind":"predicate","source":"field","fieldId":"%s","fieldKey":"%s",
                   "operator":"eq","value":"ready"
                 }}}
              ],
              "policies":[],
              "aggregateVersion":0
            }
            """.formatted(
            sectionId, UUID.randomUUID(), sectionId,
            title.get("id").asText(), title.get("fieldKey").asText(),
            trigger.get("id").asText(), trigger.get("fieldKey").asText()
        );
    }

    private String layoutWithConditionCycle(JsonNode title, JsonNode trigger) {
        UUID sectionId = UUID.randomUUID();
        return """
            {
              "nodes":[
                {"id":"%s","nodeKey":"main","nodeType":"section","sortOrder":0,
                 "config":{},"visibilityCondition":{"schemaVersion":1}},
                {"id":"%s","parentId":"%s","nodeKey":"condition_title","nodeType":"field",
                 "fieldId":"%s","fieldKey":"%s","sortOrder":0,"config":{},
                 "visibilityCondition":{"schemaVersion":1,"expression":{
                   "kind":"predicate","source":"field","fieldId":"%s","fieldKey":"%s",
                   "operator":"eq","value":"ready"
                 }}},
                {"id":"%s","parentId":"%s","nodeKey":"condition_trigger","nodeType":"field",
                 "fieldId":"%s","fieldKey":"%s","sortOrder":1,"config":{},
                 "visibilityCondition":{"schemaVersion":1,"expression":{
                   "kind":"predicate","source":"field","fieldId":"%s","fieldKey":"%s",
                   "operator":"eq","value":"ready"
                 }}}
              ],
              "policies":[],
              "aggregateVersion":0
            }
            """.formatted(
            sectionId,
            UUID.randomUUID(), sectionId,
            title.get("id").asText(), title.get("fieldKey").asText(),
            trigger.get("id").asText(), trigger.get("fieldKey").asText(),
            UUID.randomUUID(), sectionId,
            trigger.get("id").asText(), trigger.get("fieldKey").asText(),
            title.get("id").asText(), title.get("fieldKey").asText()
        );
    }

    private JsonNode createField(String token, UUID spaceId, UUID typeId, String key) throws Exception {
        return json(mockMvc.perform(post(
                "/api/project-spaces/" + spaceId + "/configuration/types/" + typeId + "/fields"
            )
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wil-field-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldKey":"%s","name":"%s","fieldType":"text","config":{},"sortOrder":10}
                    """.formatted(key, key)))
            .andExpect(status().isOk())
            .andReturn());
    }

    private JsonNode createType(String token, UUID spaceId, String key) throws Exception {
        return json(mockMvc.perform(post("/api/project-spaces/" + spaceId + "/configuration/types")
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wil-type-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeKey\":\"" + key + "\",\"name\":\"" + key + "\",\"sortOrder\":10}"))
            .andExpect(status().isOk())
            .andReturn());
    }

    private UUID createSpace(String token, String prefix) throws Exception {
        String key = prefix + "-" + suffix();
        return UUID.fromString(json(mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spaceKey\":\"" + key + "\",\"name\":\"" + prefix + "\",\"visibility\":\"private\"}"))
            .andExpect(status().isOk())
            .andReturn()).get("id").asText());
    }

    private TestUser root(String fingerprint) throws Exception {
        UUID id = jdbcTemplate.queryForObject("select id from users where username='admin'", UUID.class);
        UUID workspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from users where id=?",
            UUID.class,
            id
        );
        return new TestUser(id, workspaceId, login("admin", "admin123456", fingerprint + "-" + suffix()));
    }

    private TestUser member(String rootToken, String prefix) throws Exception {
        String username = prefix + suffix();
        JsonNode response = json(mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(rootToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"member123456","displayName":"%s",
                     "email":"%s@example.com","roleCode":"member"}
                    """.formatted(username, prefix, username)))
            .andExpect(status().isOk())
            .andReturn());
        UUID id = UUID.fromString(response.get("id").asText());
        UUID workspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from users where id=?",
            UUID.class,
            id
        );
        return new TestUser(id, workspaceId, login(username, "member123456", prefix + "-" + suffix()));
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        return json(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","deviceType":"web",
                     "deviceFingerprint":"%s","deviceName":"MockMvc","appVersion":"test"}
                    """.formatted(username, password, fingerprint)))
            .andExpect(status().isOk())
            .andReturn()).get("accessToken").asText();
    }

    private void addSpaceMember(UUID spaceId, UUID userId, String role, UUID actorId) {
        UUID workspaceId = jdbcTemplate.queryForObject(
            "select workspace_id from project_spaces where id=?",
            UUID.class,
            spaceId
        );
        UUID memberId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_space_members
                    (id, workspace_id, space_id, user_id, status, joined_at,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId,
            workspaceId,
            spaceId,
            userId,
            actorId,
            actorId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments
                    (id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at)
                values (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            spaceId,
            memberId,
            role,
            actorId
        );
    }

    private int count(String table, UUID spaceId) {
        if (!java.util.Set.of(
            "project_work_item_layouts",
            "project_work_item_layout_commands",
            "project_work_item_field_access_policies"
        ).contains(table)) {
            throw new IllegalArgumentException("Unsupported test table");
        }
        return jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where space_id=?",
            Integer.class,
            spaceId
        );
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String layoutPath(UUID spaceId, UUID typeId, String kind) {
        return "/api/project-spaces/" + spaceId + "/configuration/types/" + typeId + "/layouts/" + kind;
    }

    private String layoutCommandPath(UUID spaceId, UUID typeId, String kind) {
        return layoutPath(spaceId, typeId, kind) + "/nodes:command";
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
