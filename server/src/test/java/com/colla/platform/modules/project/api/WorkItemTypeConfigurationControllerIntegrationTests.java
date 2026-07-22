package com.colla.platform.modules.project.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.project.application.WorkItemTypeDefinitionService;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.CreateWorkItemType;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WorkItemTypeConfigurationControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkItemTypeDefinitionService definitionService;

    @Test
    void ownerManagesFullConfigurationWithoutMutatingPublishedV1() throws Exception {
        TestUser root = root("wit-full-root");
        TestUser owner = member(root.token(), "witowner");
        UUID spaceId = createSpace(owner.token(), "wit-full");
        JsonNode task = createType(owner.token(), spaceId, "custom_task", "Task", 20, "wit-create-task-" + suffix());
        JsonNode bug = createType(owner.token(), spaceId, "custom_bug", "Bug", 10, "wit-create-bug-" + suffix());
        String originalHash = task.at("/currentVersion/configHash").asText();

        mockMvc.perform(get(configPath(spaceId)).header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableActions", contains("create", "reorder")))
            .andExpect(jsonPath("$.items[0].typeKey").value("custom_bug"))
            .andExpect(jsonPath("$.items[1].typeKey").value("custom_task"))
            .andExpect(jsonPath("$.items[1].currentVersion.status").value("published"))
            .andExpect(jsonPath("$.items[1].availableActions", hasItem("edit")));

        mockMvc.perform(get(configPath(spaceId) + "/" + systemTypeId(spaceId, "project"))
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("development_preset"))
            .andExpect(jsonPath("$.presetCatalogVersion").value("development-v1"))
            .andExpect(jsonPath("$.availableActions", not(hasItem("edit"))))
            .andExpect(jsonPath("$.availableActions", not(hasItem("retire"))));

        String updateRequestId = "wit-update-task-" + suffix();
        String updateBody = """
            {"name":"Delivery Task","icon":"check","description":"updated display","aggregateVersion":0}
            """;
        JsonNode updated = json(mockMvc.perform(patch(configPath(spaceId) + "/" + task.get("id").asText())
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", updateRequestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Delivery Task"))
            .andExpect(jsonPath("$.aggregateVersion").value(1))
            .andExpect(jsonPath("$.currentVersion.number").value(1))
            .andReturn());
        assertEquals(originalHash, updated.at("/currentVersion/configHash").asText());
        assertEquals("Task", updated.at("/currentVersion/config/display/name").asText());
        mockMvc.perform(patch(configPath(spaceId) + "/" + task.get("id").asText())
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", updateRequestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aggregateVersion").value(1));

        JsonNode copied = json(mockMvc.perform(post(configPath(spaceId) + "/" + task.get("id").asText() + ":copy")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wit-copy-task-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"typeKey":"delivery_task","name":"Delivery Task Copy","sortOrder":30}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.typeKey").value("delivery_task"))
            .andExpect(jsonPath("$.system").value(false))
            .andExpect(jsonPath("$.currentVersion.number").value(1))
            .andReturn());
        assertNotEquals(task.get("id").asText(), copied.get("id").asText());

        mockMvc.perform(put(configPath(spaceId) + ":reorder")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wit-reorder-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[
                      {"typeId":"%s","sortOrder":5,"aggregateVersion":0},
                      {"typeId":"%s","sortOrder":15,"aggregateVersion":1}
                    ]}
                    """.formatted(bug.get("id").asText(), task.get("id").asText())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].typeKey").value("custom_bug"))
            .andExpect(jsonPath("$.items[1].typeKey").value("custom_task"));

        String taskId = task.get("id").asText();
        transition(owner.token(), spaceId, taskId, "disable", 2, "disabled");
        transition(owner.token(), spaceId, taskId, "restore", 3, "active");
        transition(owner.token(), spaceId, taskId, "retire", 4, "retired");
        mockMvc.perform(get(configPath(spaceId) + "/" + taskId).header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableActions", contains("copy")));
        mockMvc.perform(patch(configPath(spaceId) + "/" + taskId)
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wit-edit-retired-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"No","aggregateVersion":5}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("retired_type"));
        List<String> eventTypes = jdbcTemplate.queryForList(
            "select distinct event_type from domain_events where payload->>'spaceId'=?",
            String.class,
            spaceId.toString()
        );
        assertTrue(eventTypes.containsAll(List.of(
            "work_item_type.created", "work_item_type.updated", "work_item_type.copied",
            "work_item_type.reordered", "work_item_type.disabled", "work_item_type.restored",
            "work_item_type.retired"
        )));
    }

    @Test
    void separatesConfigurationSummaryAndEnterpriseGovernanceSurfaces() throws Exception {
        TestUser root = root("wit-rbac-root");
        TestUser owner = member(root.token(), "witowner");
        TestUser admin = member(root.token(), "witadmin");
        TestUser member = member(root.token(), "witmember");
        TestUser guest = member(root.token(), "witguest");
        TestUser outsider = member(root.token(), "witoutside");
        UUID spaceId = createSpace(owner.token(), "wit-rbac");
        addSpaceMember(spaceId, admin.id(), "admin", owner.id());
        addSpaceMember(spaceId, member.id(), "member", owner.id());
        addSpaceMember(spaceId, guest.id(), "guest", owner.id());
        JsonNode active = createType(owner.token(), spaceId, "custom_task", "Task", 10, "wit-rbac-active-" + suffix());
        JsonNode disabled = createType(owner.token(), spaceId, "custom_bug", "Bug", 20, "wit-rbac-disabled-" + suffix());
        transition(owner.token(), spaceId, disabled.get("id").asText(), "disable", 0, "disabled");

        mockMvc.perform(get(configPath(spaceId)).header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk());
        mockMvc.perform(get(configPath(spaceId)).header("Authorization", bearer(admin.token())))
            .andExpect(status().isOk());
        createType(admin.token(), spaceId, "custom_release", "Release", 30, "wit-admin-create-" + suffix());
        for (TestUser readonly : List.of(member, guest)) {
            mockMvc.perform(get(configPath(spaceId)).header("Authorization", bearer(readonly.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
            mockMvc.perform(get("/api/project-spaces/" + spaceId + "/work-item-types")
                    .header("Authorization", bearer(readonly.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].typeKey").value("custom_task"))
                .andExpect(jsonPath("$[*].typeKey", not(hasItem("custom_bug"))))
                .andExpect(jsonPath("$[0].currentVersion").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist());
        }
        for (TestUser hidden : List.of(outsider, root)) {
            mockMvc.perform(get(configPath(spaceId)).header("Authorization", bearer(hidden.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"));
            mockMvc.perform(get("/api/project-spaces/" + spaceId + "/work-item-types")
                    .header("Authorization", bearer(hidden.token())))
                .andExpect(status().isNotFound());
        }

        mockMvc.perform(get("/api/admin/project-spaces/" + spaceId)
                .header("Authorization", bearer(root.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentAccessGranted").value(false))
            .andExpect(jsonPath("$.workItemTypes.total").value(9))
            .andExpect(jsonPath("$.workItemTypes.active").value(8))
            .andExpect(jsonPath("$.workItemTypes.disabled").value(1))
            .andExpect(jsonPath("$.workItemTypes.config").doesNotExist());
        assertEquals(active.get("id").asText(), jdbcTemplate.queryForObject(
            "select id::text from project_work_item_types where space_id=? and type_key='custom_task'", String.class, spaceId
        ));

        UUID secondSpaceId = createSpace(owner.token(), "wit-rbac-second");
        mockMvc.perform(get(configPath(secondSpaceId) + "/" + active.get("id").asText())
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"));

        UUID foreignWorkspaceId = UUID.randomUUID();
        UUID foreignActorId = UUID.randomUUID();
        UUID foreignSpaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id,name,slug,status,created_at,updated_at) values (?, 'Foreign', ?, 'active', now(), now())",
            foreignWorkspaceId, "foreign-" + suffix()
        );
        jdbcTemplate.update(
            """
                insert into users (id,workspace_id,username,password_hash,display_name,status,created_at,updated_at)
                values (?, ?, ?, 'not-used', 'Foreign Actor', 'active', now(), now())
                """,
            foreignActorId, foreignWorkspaceId, "foreign" + suffix()
        );
        jdbcTemplate.update(
            """
                insert into project_spaces
                    (id,workspace_id,space_key,name,description,status,visibility,created_by,created_at,updated_by,updated_at)
                values (?, ?, ?, 'Foreign Space', '', 'active', 'private', ?, now(), ?, now())
                """,
            foreignSpaceId, foreignWorkspaceId, "foreign-space-" + suffix(), foreignActorId, foreignActorId
        );
        definitionService.create(new CreateWorkItemType(
            foreignWorkspaceId, foreignSpaceId, foreignActorId, "foreign_type", "Foreign", "", "", 0, false
        ));
        mockMvc.perform(get(configPath(foreignSpaceId)).header("Authorization", bearer(owner.token())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"));
    }

    @Test
    void requestReplayConvergesAuditOutboxAndResponse() throws Exception {
        TestUser root = root("wit-idem-root");
        TestUser owner = member(root.token(), "witowner");
        UUID spaceId = createSpace(owner.token(), "wit-idem");
        String requestId = "wit-idempotent-" + suffix();

        JsonNode first = createType(owner.token(), spaceId, "custom_requirement", "Requirement", 10, requestId);
        JsonNode replay = createType(owner.token(), spaceId, "custom_requirement", "Requirement", 10, requestId);
        assertEquals(first.get("id").asText(), replay.get("id").asText());
        assertEquals(7, count("project_work_item_types", "space_id", spaceId));
        assertEquals(1, count("project_work_item_type_commands", "space_id", spaceId));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from audit_logs where target_id=? and action='work_item_type.created'", Integer.class,
            UUID.fromString(first.get("id").asText())
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from domain_events where aggregate_id=? and event_type='work_item_type.created'", Integer.class,
            UUID.fromString(first.get("id").asText())
        ));
        assertTrue(jdbcTemplate.queryForObject(
            "select metadata->>'requestId' from audit_logs where target_id=? and action='work_item_type.created'",
            String.class,
            UUID.fromString(first.get("id").asText())
        ).equals(requestId));

        mockMvc.perform(post(configPath(spaceId))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"typeKey":"other","name":"Different","sortOrder":10}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("idempotency_conflict"));
    }

    @Test
    void stableErrorsSystemProtectionAndConcurrentVersionGuardAreEnforced() throws Exception {
        TestUser root = root("wit-errors-root");
        TestUser owner = member(root.token(), "witowner");
        UUID spaceId = createSpace(owner.token(), "wit-errors");

        mockMvc.perform(post(configPath(spaceId))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wit-invalid-key-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"typeKey":"INVALID KEY","name":"Invalid"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("invalid_type_key"));

        createType(owner.token(), spaceId, "custom_task", "Task", 10, "wit-duplicate-one-" + suffix());
        mockMvc.perform(post(configPath(spaceId))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wit-duplicate-two-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"typeKey":"custom_task","name":"Duplicate"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("type_key_conflict"));

        UUID systemId = jdbcTemplate.queryForObject(
            "select id from project_work_item_types where space_id=? and type_key='project'",
            UUID.class,
            spaceId
        );
        WorkItemTypeDefinition system = definitionService.get(owner.workspaceId(), spaceId, systemId);
        mockMvc.perform(post(configPath(spaceId) + "/" + system.id() + ":retire")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wit-retire-system-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"aggregateVersion":0}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("system_type_protected"));

        JsonNode concurrent = createType(owner.token(), spaceId, "custom_release", "Release", 30, "wit-race-create-" + suffix());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> first = executor.submit(() -> concurrentUpdate(
                owner.token(), spaceId, concurrent.get("id").asText(), "Release A", "wit-race-a-" + suffix(), ready, start
            ));
            Future<MvcResult> second = executor.submit(() -> concurrentUpdate(
                owner.token(), spaceId, concurrent.get("id").asText(), "Release B", "wit-race-b-" + suffix(), ready, start
            ));
            ready.await();
            start.countDown();
            List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
            assertEquals(1, statuses.stream().filter(value -> value == 200).count());
            assertEquals(1, statuses.stream().filter(value -> value == 409).count());
            MvcResult conflict = first.get().getResponse().getStatus() == 409 ? first.get() : second.get();
            assertEquals("version_conflict", json(conflict).at("/error/code").asText());
        }

        mockMvc.perform(post("/api/project-spaces/" + spaceId + "/settings/disable")
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk());
        mockMvc.perform(get(configPath(spaceId)).header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableActions").isEmpty())
            .andExpect(jsonPath("$.items[0].availableActions").isEmpty());
        mockMvc.perform(post(configPath(spaceId))
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wit-disabled-space-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"typeKey":"blocked","name":"Blocked"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("space_unavailable"));
    }

    @Test
    void reorderIsAtomicAndOpenApiPublishesTheThreeDtoSurfaces() throws Exception {
        TestUser root = root("wit-contract-root");
        TestUser owner = member(root.token(), "witowner");
        UUID spaceId = createSpace(owner.token(), "wit-contract");
        JsonNode task = createType(owner.token(), spaceId, "custom_task", "Task", 10, "wit-contract-task-" + suffix());
        JsonNode bug = createType(owner.token(), spaceId, "custom_bug", "Bug", 20, "wit-contract-bug-" + suffix());

        mockMvc.perform(put(configPath(spaceId) + ":reorder")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wit-stale-reorder-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[
                      {"typeId":"%s","sortOrder":30,"aggregateVersion":0},
                      {"typeId":"%s","sortOrder":40,"aggregateVersion":99}
                    ]}
                    """.formatted(task.get("id").asText(), bug.get("id").asText())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("version_conflict"));
        assertEquals(10, jdbcTemplate.queryForObject(
            "select sort_order from project_work_item_types where id=?", Integer.class,
            UUID.fromString(task.get("id").asText())
        ));
        assertEquals(0L, jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_types where id=?", Long.class,
            UUID.fromString(task.get("id").asText())
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from audit_logs where target_id=? and action='work_item_type.reordered'",
            Integer.class,
            UUID.fromString(task.get("id").asText())
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from domain_events where aggregate_id=? and event_type='work_item_type.reordered'",
            Integer.class,
            UUID.fromString(task.get("id").asText())
        ));

        mockMvc.perform(put(configPath(spaceId) + ":reorder")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wit-duplicate-order-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"items":[
                      {"typeId":"%s","sortOrder":10,"aggregateVersion":0},
                      {"typeId":"%s","sortOrder":10,"aggregateVersion":0}
                    ]}
                    """.formatted(task.get("id").asText(), bug.get("id").asText())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("invalid_reorder"));

        JsonNode openApi = json(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}:copy"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}:disable"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}:restore"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types/{typeId}:retire"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/configuration/types:reorder"));
        assertTrue(openApi.path("paths").has("/api/project-spaces/{spaceId}/work-item-types"));
        assertTrue(openApi.path("paths").has("/api/admin/project-spaces/{spaceId}"));
        assertFalse(openApi.path("paths").has("/api/work-items"));
        openApi.path("paths").fieldNames()
            .forEachRemaining(path -> assertFalse(path.startsWith("/api/work-items/")));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name='project_work_item_type_commands'",
            Integer.class
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name='project_work_items'",
            Integer.class
        ));
    }

    private MvcResult concurrentUpdate(
        String token,
        UUID spaceId,
        String typeId,
        String name,
        String requestId,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(patch(configPath(spaceId) + "/" + typeId)
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","aggregateVersion":0}
                    """.formatted(name)))
            .andReturn();
    }

    private void transition(String token, UUID spaceId, String typeId, String action, long version, String expected) throws Exception {
        mockMvc.perform(post(configPath(spaceId) + "/" + typeId + ":" + action)
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", "wit-" + action + "-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"aggregateVersion":%d}
                    """.formatted(version)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(expected));
    }

    private JsonNode createType(
        String token,
        UUID spaceId,
        String key,
        String name,
        int sortOrder,
        String requestId
    ) throws Exception {
        return json(mockMvc.perform(post(configPath(spaceId))
                .header("Authorization", bearer(token))
                .header("X-Colla-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"typeKey":"%s","name":"%s","icon":"item","description":"test","sortOrder":%d}
                    """.formatted(key, name, sortOrder)))
            .andExpect(status().isOk())
            .andReturn());
    }

    private UUID createSpace(String token, String prefix) throws Exception {
        String key = prefix + "-" + suffix();
        JsonNode response = json(mockMvc.perform(post("/api/project-spaces")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"spaceKey":"%s","name":"%s","visibility":"private"}
                    """.formatted(key, prefix)))
            .andExpect(status().isOk())
            .andReturn());
        return UUID.fromString(response.get("id").asText());
    }

    private TestUser root(String fingerprint) throws Exception {
        UUID id = jdbcTemplate.queryForObject("select id from users where username='admin'", UUID.class);
        UUID workspaceId = jdbcTemplate.queryForObject("select workspace_id from users where id=?", UUID.class, id);
        return new TestUser(id, workspaceId, "admin", login("admin", "admin123456", fingerprint + "-" + suffix()));
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
        return new TestUser(id, workspaceId, username, login(username, "member123456", prefix + "-" + suffix()));
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        JsonNode response = json(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","deviceType":"web","deviceFingerprint":"%s","deviceName":"MockMvc","appVersion":"test"}
                    """.formatted(username, password, fingerprint)))
            .andExpect(status().isOk())
            .andReturn());
        return response.get("accessToken").asText();
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

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID systemTypeId(UUID spaceId, String typeKey) {
        return jdbcTemplate.queryForObject(
            "select id from project_work_item_types where space_id=? and type_key=?",
            UUID.class,
            spaceId,
            typeKey
        );
    }

    private int count(String table, String column, UUID value) {
        if (!List.of("project_work_item_types", "project_work_item_type_commands").contains(table)
            || !"space_id".equals(column)) {
            throw new IllegalArgumentException("Unsupported test count");
        }
        return jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where " + column + "=?", Integer.class, value
        );
    }

    private String configPath(UUID spaceId) {
        return "/api/project-spaces/" + spaceId + "/configuration/types";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record TestUser(UUID id, UUID workspaceId, String username, String token) {
    }
}
