package com.colla.platform.modules.project.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.project.application.WorkItemFieldDefinitionService;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.CreateFieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldOptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
class WorkItemFieldConfigurationControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkItemFieldDefinitionService definitionService;

    @Autowired
    private WorkItemFieldOptionRepository optionRepository;

    @Test
    void ownerManagesTheFieldAggregateAndServerProjectedActions() throws Exception {
        TestUser root = root("wif-full-root");
        TestUser owner = member(root.token(), "wifowner");
        UUID spaceId = createSpace(owner.token(), "wif-full");
        JsonNode type = createType(owner.token(), spaceId, "delivery", "Delivery");
        UUID typeId = UUID.fromString(type.get("id").asText());

        mockMvc.perform(get(basePath(spaceId) + "/field-types")
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(11))
            .andExpect(jsonPath("$.items[*].key", contains(
                "text", "number", "boolean", "single_select", "multi_select", "user",
                "date", "datetime", "url", "attachment", "work_item_reference"
            )))
            .andExpect(jsonPath("$.items[0].configSchema.type").value("object"))
            .andExpect(jsonPath("$.items[0].configSchema.additionalProperties").value(false));

        JsonNode title = createField(owner.token(), spaceId, typeId, "title", "Title", "text", 20, "wif-title-" + suffix());
        JsonNode points = createField(owner.token(), spaceId, typeId, "points", "Points", "number", 10, "wif-points-" + suffix());

        mockMvc.perform(get(fieldsPath(spaceId, typeId)).header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableActions", contains("create", "reorder")))
            .andExpect(jsonPath("$.items[0].fieldKey").value("points"))
            .andExpect(jsonPath("$.items[1].fieldKey").value("title"))
            .andExpect(jsonPath("$.items[1].availableActions", hasItem("edit")));

        title = json(mockMvc.perform(patch(fieldPath(spaceId, typeId, title))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif-update-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Summary","description":"Canonical title","config":{},"aggregateVersion":0}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fieldKey").value("title"))
            .andExpect(jsonPath("$.fieldType").value("text"))
            .andExpect(jsonPath("$.name").value("Summary"))
            .andExpect(jsonPath("$.aggregateVersion").value(1))
            .andReturn());

        title = transition(owner.token(), spaceId, typeId, title, "disable", 1, "disabled");
        title = transition(owner.token(), spaceId, typeId, title, "restore", 2, "active");

        mockMvc.perform(put(fieldsPath(spaceId, typeId) + ":reorder")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif-reorder-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[
                      {"fieldId":"%s","sortOrder":10,"aggregateVersion":3},
                      {"fieldId":"%s","sortOrder":20,"aggregateVersion":0}
                    ]}
                    """.formatted(title.get("id").asText(), points.get("id").asText())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].fieldKey").value("title"));

        points = transition(owner.token(), spaceId, typeId, points, "retire", 1, "retired");
        mockMvc.perform(get(fieldsPath(spaceId, typeId) + "?status=retired")
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].fieldKey").value("points"))
            .andExpect(jsonPath("$.items[0].availableActions").isEmpty());

        FieldDefinition system = definitionService.create(new CreateFieldDefinition(
            owner.workspaceId(), spaceId, typeId, owner.id(), "system_number", "System number", "",
            "number", objectMapper.createObjectNode(), 30, true
        ));
        mockMvc.perform(get(fieldsPath(spaceId, typeId) + "/" + system.id())
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.system").value(true))
            .andExpect(jsonPath("$.availableActions", not(hasItem("edit"))))
            .andExpect(jsonPath("$.availableActions", not(hasItem("retire"))));
        mockMvc.perform(post(fieldsPath(spaceId, typeId) + "/" + system.id() + ":retire")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif-system-retire-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aggregateVersion\":0}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("system_field_protected"));

        List<String> events = jdbcTemplate.queryForList(
            "select event_type from domain_events where payload->>'spaceId'=? and aggregate_type='work_item_field'",
            String.class,
            spaceId.toString()
        );
        assertTrue(events.containsAll(List.of(
            "work_item_field.created", "work_item_field.updated", "work_item_field.disabled",
            "work_item_field.restored", "work_item_field.reordered", "work_item_field.retired"
        )));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from audit_logs where target_id=? and action='work_item_field.updated'",
            Integer.class,
            UUID.fromString(title.get("id").asText())
        ));
        assertEquals("retired", points.get("status").asText());
    }

    @Test
    void spaceRolesAndTenantBoundariesProtectConfiguration() throws Exception {
        TestUser root = root("wif-rbac-root");
        TestUser owner = member(root.token(), "wifowner");
        TestUser admin = member(root.token(), "wifadmin");
        TestUser ordinary = member(root.token(), "wifmember");
        TestUser guest = member(root.token(), "wifguest");
        TestUser outsider = member(root.token(), "wifoutside");
        UUID spaceId = createSpace(owner.token(), "wif-rbac");
        addSpaceMember(spaceId, admin.id(), "admin", owner.id());
        addSpaceMember(spaceId, ordinary.id(), "member", owner.id());
        addSpaceMember(spaceId, guest.id(), "guest", owner.id());
        UUID typeId = UUID.fromString(createType(owner.token(), spaceId, "delivery", "Delivery").get("id").asText());
        JsonNode field = createField(owner.token(), spaceId, typeId, "title", "Title", "text", 10, "wif-rbac-field-" + suffix());

        mockMvc.perform(get(fieldsPath(spaceId, typeId)).header("Authorization", bearer(admin.token())))
            .andExpect(status().isOk());
        createField(admin.token(), spaceId, typeId, "points", "Points", "number", 20, "wif-admin-field-" + suffix());

        for (TestUser readonly : List.of(ordinary, guest)) {
            mockMvc.perform(get(fieldsPath(spaceId, typeId)).header("Authorization", bearer(readonly.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
            mockMvc.perform(get(basePath(spaceId) + "/field-types").header("Authorization", bearer(readonly.token())))
                .andExpect(status().isForbidden());
        }
        for (TestUser hidden : List.of(outsider, root)) {
            mockMvc.perform(get(fieldsPath(spaceId, typeId)).header("Authorization", bearer(hidden.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"));
        }

        UUID secondSpaceId = createSpace(owner.token(), "wif-second");
        UUID secondTypeId = UUID.fromString(createType(owner.token(), secondSpaceId, "delivery", "Delivery").get("id").asText());
        mockMvc.perform(get(fieldsPath(secondSpaceId, secondTypeId) + "/" + field.get("id").asText())
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"));
    }

    @Test
    void requestReplayStableErrorsAndOptimisticLockConverge() throws Exception {
        TestUser root = root("wif-idem-root");
        TestUser owner = member(root.token(), "wifowner");
        UUID spaceId = createSpace(owner.token(), "wif-idem");
        UUID typeId = UUID.fromString(createType(owner.token(), spaceId, "delivery", "Delivery").get("id").asText());
        String requestId = "wif-replay-" + suffix();

        JsonNode first = createField(owner.token(), spaceId, typeId, "title", "Title", "text", 10, requestId);
        JsonNode replay = createField(owner.token(), spaceId, typeId, "title", "Title", "text", 10, requestId);
        assertEquals(first.get("id").asText(), replay.get("id").asText());
        assertEquals(1, count("project_work_item_field_definitions", spaceId));
        assertEquals(1, count("project_work_item_field_commands", spaceId));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from audit_logs where target_id=? and action='work_item_field.created'",
            Integer.class,
            UUID.fromString(first.get("id").asText())
        ));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> contenderA = executor.submit(() -> concurrentCreateField(
                owner.token(), spaceId, typeId, "race_field", "Race A", "wif-race-a-" + suffix(), ready, start
            ));
            Future<MvcResult> contenderB = executor.submit(() -> concurrentCreateField(
                owner.token(), spaceId, typeId, "race_field", "Race B", "wif-race-b-" + suffix(), ready, start
            ));
            ready.await();
            start.countDown();
            List<Integer> statuses = List.of(
                contenderA.get().getResponse().getStatus(), contenderB.get().getResponse().getStatus()
            );
            assertEquals(1, statuses.stream().filter(value -> value == 200).count());
            assertEquals(1, statuses.stream().filter(value -> value == 409).count());
        }
        assertEquals(2, count("project_work_item_field_definitions", spaceId));

        mockMvc.perform(post(fieldsPath(spaceId, typeId))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldKey":"other","name":"Different","fieldType":"text","config":{},"sortOrder":10}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("field_idempotency_conflict"));

        mockMvc.perform(post(fieldsPath(spaceId, typeId))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif-duplicate-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldKey":"title","name":"Duplicate","fieldType":"text","config":{}}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("field_key_conflict"));

        for (String body : List.of(
            "{\"fieldKey\":\"cost\",\"name\":\"Cost\",\"fieldType\":\"currency\",\"config\":{}}",
            "{\"fieldKey\":\"priority\",\"name\":\"Priority\",\"fieldType\":\"single_select\",\"config\":{\"options\":[]}}"
        )) {
            mockMvc.perform(post(fieldsPath(spaceId, typeId))
                    .header("Authorization", bearer(owner.token()))
                    .header("X-Colla-Request-Id", "wif-invalid-" + suffix())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        mockMvc.perform(patch(fieldPath(spaceId, typeId, first))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif-update-first-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Summary\",\"config\":{},\"aggregateVersion\":0}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch(fieldPath(spaceId, typeId, first))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif-update-stale-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Stale\",\"config\":{},\"aggregateVersion\":0}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("version_conflict"));
    }

    @Test
    void reorderRollsBackAtomicallyAndOpenApiPublishesOnlyTheFieldFoundation() throws Exception {
        TestUser root = root("wif-contract-root");
        TestUser owner = member(root.token(), "wifowner");
        UUID spaceId = createSpace(owner.token(), "wif-contract");
        UUID typeId = UUID.fromString(createType(owner.token(), spaceId, "delivery", "Delivery").get("id").asText());
        JsonNode first = createField(owner.token(), spaceId, typeId, "first", "First", "text", 10, "wif-first-" + suffix());
        JsonNode second = createField(owner.token(), spaceId, typeId, "second", "Second", "text", 20, "wif-second-" + suffix());
        mockMvc.perform(patch(fieldPath(spaceId, typeId, second))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif-touch-second-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Second updated\",\"config\":{},\"aggregateVersion\":0}"))
            .andExpect(status().isOk());

        mockMvc.perform(put(fieldsPath(spaceId, typeId) + ":reorder")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wif-stale-reorder-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[
                      {"fieldId":"%s","sortOrder":30,"aggregateVersion":0},
                      {"fieldId":"%s","sortOrder":40,"aggregateVersion":0}
                    ]}
                    """.formatted(first.get("id").asText(), second.get("id").asText())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("version_conflict"));
        assertEquals(10, jdbcTemplate.queryForObject(
            "select sort_order from project_work_item_field_definitions where id=?",
            Integer.class,
            UUID.fromString(first.get("id").asText())
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from audit_logs where target_id=? and action='work_item_field.reordered'",
            Integer.class,
            UUID.fromString(first.get("id").asText())
        ));

        JsonNode openApi = json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/field-types"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}/fields"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}/fields/{fieldId}"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}/fields:reorder"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}/fields/{fieldId}:disable"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}/fields/{fieldId}:restore"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}/fields/{fieldId}:retire"));
        assertFalse(openApi.path("paths").has("/api/work-items"));
        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name='project_work_items'",
            Integer.class
        ));
    }

    @Test
    @Transactional
    void syntheticConfigurationCatalogStaysWithinBudgetAndUsesTheScopedIndex() throws Exception {
        TestUser root = root("wif-budget-root");
        TestUser owner = member(root.token(), "wifbudget");
        UUID spaceId = createSpace(owner.token(), "wif-budget");
        UUID typeId = UUID.fromString(
            createType(owner.token(), spaceId, "large_catalog", "Large catalog").get("id").asText()
        );

        for (int fieldIndex = 0; fieldIndex < 120; fieldIndex++) {
            FieldDefinition field = definitionService.create(new CreateFieldDefinition(
                owner.workspaceId(),
                spaceId,
                typeId,
                owner.id(),
                "select_" + fieldIndex,
                "Synthetic select " + fieldIndex,
                "Performance fixture",
                "single_select",
                objectMapper.createObjectNode(),
                fieldIndex * 10,
                false
            ));
            for (int optionIndex = 0; optionIndex < 20; optionIndex++) {
                optionRepository.insert(
                    UUID.randomUUID(),
                    owner.workspaceId(),
                    spaceId,
                    typeId,
                    field.id(),
                    new ConfigureFieldOption(
                        "option_" + optionIndex,
                        "Option " + optionIndex,
                        "#2563EB",
                        optionIndex * 10,
                        "active"
                    ),
                    owner.id()
                );
            }
        }

        assertTimeout(Duration.ofSeconds(3), () ->
            mockMvc.perform(get(fieldsPath(spaceId, typeId))
                    .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(120))
                .andExpect(jsonPath("$.items[0].options.length()").value(20))
                .andReturn()
        );

        jdbcTemplate.execute("analyze project_work_item_field_definitions");
        jdbcTemplate.execute("set local enable_seqscan = off");
        String plan = String.join("\n", jdbcTemplate.queryForList(
            """
                explain select id
                  from project_work_item_field_definitions
                 where workspace_id = ? and space_id = ? and type_definition_id = ?
                   and status = 'active'
                 order by sort_order, field_key, id
                """,
            String.class,
            owner.workspaceId(),
            spaceId,
            typeId
        ));
        assertTrue(
            plan.contains("Index Scan") || plan.contains("Bitmap Index Scan"),
            () -> "Expected an indexed field catalog query plan, got: " + plan
        );
        assertTrue(
            plan.contains("workspace_id") && plan.contains("space_id")
                && plan.contains("type_definition_id") && plan.contains("status"),
            () -> "Expected the catalog plan to retain all scope predicates, got: " + plan
        );
        assertEquals(0, jdbcTemplate.queryForObject(
            """
                select count(*)
                  from information_schema.tables
                 where table_schema = 'public'
                   and (table_name like 'project_work_item_field_value_%'
                        or table_name like 'project_work_item_dynamic_%')
                """,
            Integer.class
        ));
    }

    private JsonNode transition(
        String token,
        UUID spaceId,
        UUID typeId,
        JsonNode field,
        String action,
        long version,
        String expectedStatus
    ) throws Exception {
        return json(mockMvc.perform(post(fieldPath(spaceId, typeId, field) + ":" + action)
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wif-" + action + "-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aggregateVersion\":" + version + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(expectedStatus))
            .andReturn());
    }

    private MvcResult concurrentCreateField(
        String token,
        UUID spaceId,
        UUID typeId,
        String key,
        String name,
        String requestId,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post(fieldsPath(spaceId, typeId))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldKey":"%s","name":"%s","fieldType":"text","config":{},"sortOrder":30}
                    """.formatted(key, name)))
            .andReturn();
    }

    private JsonNode createField(
        String token,
        UUID spaceId,
        UUID typeId,
        String key,
        String name,
        String fieldType,
        int sortOrder,
        String requestId
    ) throws Exception {
        return json(mockMvc.perform(post(fieldsPath(spaceId, typeId))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fieldKey":"%s","name":"%s","description":"test","fieldType":"%s","config":{},"sortOrder":%d}
                    """.formatted(key, name, fieldType, sortOrder)))
            .andExpect(status().isOk())
            .andReturn());
    }

    private JsonNode createType(String token, UUID spaceId, String key, String name) throws Exception {
        return json(mockMvc.perform(post(basePath(spaceId) + "/types")
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wif-type-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"typeKey\":\"" + key + "\",\"name\":\"" + name + "\",\"sortOrder\":10}"))
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
        UUID id = UUID.fromString(response.get("id").asText());
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
            .andReturn()).get("accessToken").asText();
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

    private int count(String table, UUID spaceId) {
        if (!List.of("project_work_item_field_definitions", "project_work_item_field_commands").contains(table)) {
            throw new IllegalArgumentException("Unsupported test table");
        }
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where space_id=?", Integer.class, spaceId);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String fieldPath(UUID spaceId, UUID typeId, JsonNode field) {
        return fieldsPath(spaceId, typeId) + "/" + field.get("id").asText();
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
