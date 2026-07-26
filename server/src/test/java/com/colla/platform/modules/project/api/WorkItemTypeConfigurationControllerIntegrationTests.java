package com.colla.platform.modules.project.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.project.application.WorkItemNodeFlowPresetCatalog;
import com.colla.platform.modules.project.application.WorkItemStateFlowPresetCatalog;
import com.colla.platform.modules.project.application.WorkItemTypeDefinitionService;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.CreateWorkItemType;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    void configurationDraftTracksWritesValidatesReplaysAndAbandonsWithoutLeaking() throws Exception {
        TestUser root = root("wicd-root");
        TestUser owner = member(root.token(), "wicdowner");
        TestUser readonly = member(root.token(), "wicdmember");
        TestUser outsider = member(root.token(), "wicdoutside");
        UUID spaceId = createSpace(owner.token(), "wicd-space");
        addSpaceMember(spaceId, readonly.id(), "member", owner.id());
        JsonNode type = createType(
            owner.token(), spaceId, "wicd_task", "Draft Task", 10, "wicd-create-" + suffix()
        );
        String typeId = type.get("id").asText();
        String draftPath = configPath(spaceId) + "/" + typeId + "/draft";

        JsonNode initial = json(mockMvc.perform(get(draftPath)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("editing"))
            .andExpect(jsonPath("$.snapshotSchemaVersion").value(3))
            .andExpect(jsonPath("$.aggregateVersion").value(0))
            .andExpect(jsonPath("$.snapshot.stateFlow").doesNotExist())
            .andExpect(jsonPath("$.availableActions", contains("save", "validate", "abandon")))
            .andReturn());

        ObjectNode requestedSnapshot = initial.path("snapshot").deepCopy();
        requestedSnapshot.set(
            "stateFlow",
            new WorkItemStateFlowPresetCatalog(objectMapper).stateFlowFor("task").orElseThrow()
        );
        ObjectNode saveBody = objectMapper.createObjectNode()
            .put("expectedAggregateVersion", initial.get("aggregateVersion").asLong());
        saveBody.set("snapshot", requestedSnapshot);
        mockMvc.perform(put(draftPath)
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicd-state-flow-save-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saveBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aggregateVersion").value(1))
            .andExpect(jsonPath("$.snapshot.stateFlow.states[*].stateKey", hasItem("canceled")))
            .andExpect(jsonPath("$.snapshot.stateFlow.actions[*].actionKey", hasItem("complete")));

        mockMvc.perform(patch(configPath(spaceId) + "/" + typeId)
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicd-type-update-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Updated Draft Task","description":"tracked","aggregateVersion":0}
                    """))
            .andExpect(status().isOk());

        JsonNode changed = json(mockMvc.perform(get(draftPath)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aggregateVersion").value(2))
            .andExpect(jsonPath("$.snapshot.typeDefinition.name").value("Updated Draft Task"))
            .andExpect(jsonPath("$.snapshot.stateFlow.states[*].stateKey", hasItem("canceled")))
            .andExpect(jsonPath("$.snapshot.stateFlow.actions[*].actionKey", hasItem("complete")))
            .andReturn());
        assertNotEquals(initial.get("configHash").asText(), changed.get("configHash").asText());

        String validateRequestId = "wicd-validate-" + suffix();
        String validateBody = "{\"expectedAggregateVersion\":2}";
        JsonNode validated = json(mockMvc.perform(post(draftPath + ":validate")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", validateRequestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("valid"))
            .andExpect(jsonPath("$.aggregateVersion").value(3))
            .andReturn());
        JsonNode replayed = json(mockMvc.perform(post(draftPath + ":validate")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", validateRequestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validateBody))
            .andExpect(status().isOk())
            .andReturn());
        assertEquals(validated, replayed);
        mockMvc.perform(post(draftPath + ":validate")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", validateRequestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedAggregateVersion\":3}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("idempotency_key_reused"));

        String abandonedId = json(mockMvc.perform(post(draftPath + ":abandon")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicd-abandon-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedAggregateVersion\":3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("abandoned"))
            .andExpect(jsonPath("$.availableActions").isEmpty())
            .andReturn()).get("id").asText();

        mockMvc.perform(patch(configPath(spaceId) + "/" + typeId)
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicd-type-update-2-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Next Draft Task","description":"new active draft","aggregateVersion":1}
                    """))
            .andExpect(status().isOk());
        JsonNode next = json(mockMvc.perform(get(draftPath)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("editing"))
            .andExpect(jsonPath("$.aggregateVersion").value(0))
            .andReturn());
        assertNotEquals(abandonedId, next.get("id").asText());

        mockMvc.perform(get(draftPath).header("Authorization", bearer(readonly.token())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("forbidden"));
        mockMvc.perform(get(draftPath).header("Authorization", bearer(outsider.token())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"));
    }

    @Test
    void nodeFlowUsesTheExistingDraftValidateAndPublishContractWithoutActivatingRuntime() throws Exception {
        TestUser root = root("wicn-root");
        TestUser owner = member(root.token(), "wicnowner");
        UUID spaceId = createSpace(owner.token(), "wicn-space");
        JsonNode type = createType(
            owner.token(), spaceId, "wicn_project", "Node Project", 10, "wicn-create-" + suffix()
        );
        String typeId = type.get("id").asText();
        String base = configPath(spaceId) + "/" + typeId;
        String draftPath = base + "/draft";

        JsonNode initial = json(mockMvc.perform(get(draftPath)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.snapshotSchemaVersion").value(3))
            .andExpect(jsonPath("$.snapshot.nodeFlow").doesNotExist())
            .andReturn());

        ObjectNode requestedSnapshot = initial.path("snapshot").deepCopy();
        requestedSnapshot.set(
            "nodeFlow",
            new WorkItemNodeFlowPresetCatalog(objectMapper).nodeFlowFor("project").orElseThrow()
        );
        ObjectNode saveBody = objectMapper.createObjectNode()
            .put("expectedAggregateVersion", initial.get("aggregateVersion").asLong());
        saveBody.set("snapshot", requestedSnapshot);
        JsonNode saved = json(mockMvc.perform(put(draftPath)
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicn-node-flow-save-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saveBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.snapshot.stateFlow").doesNotExist())
            .andExpect(jsonPath("$.snapshot.nodeFlow.nodes[*].nodeKey", hasItem("delivery_split")))
            .andExpect(jsonPath("$.snapshot.nodeFlow.joins[*].joinKey", hasItem("delivery_all")))
            .andReturn());

        JsonNode valid = json(mockMvc.perform(post(draftPath + ":validate")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicn-validate-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedAggregateVersion\":" + saved.get("aggregateVersion").asLong() + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("valid"))
            .andReturn());

        JsonNode published = json(mockMvc.perform(post(base + "/draft:publish")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicn-publish-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"expectedDraftAggregateVersion":%d,"breakingConfirmed":true}
                    """.formatted(valid.get("aggregateVersion").asLong())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version.snapshotSchemaVersion").value(3))
            .andReturn());
        JsonNode publishedSnapshot = objectMapper.readTree(jdbcTemplate.queryForObject(
            "select config::text from project_work_item_type_versions where id=?",
            String.class,
            UUID.fromString(published.at("/version/id").asText())
        ));
        assertTrue(
            publishedSnapshot.path("nodeFlow").path("nodes").findValuesAsText("nodeKey")
                .contains("delivery_split")
        );

        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "select count(*) from project_node_workflow_instances where type_definition_id=?",
                Integer.class,
                UUID.fromString(typeId)
            )
        );
    }

    @Test
    void publishesImmutableSnapshotsReplaysAndRollsBackThroughANewHigherVersion() throws Exception {
        TestUser root = root("wicp-root");
        TestUser owner = member(root.token(), "wicpowner");
        TestUser readonly = member(root.token(), "wicpmember");
        TestUser outsider = member(root.token(), "wicpoutside");
        UUID spaceId = createSpace(owner.token(), "wicp-space");
        addSpaceMember(spaceId, readonly.id(), "member", owner.id());
        JsonNode type = createType(
            owner.token(), spaceId, "wicp_task", "Published Task", 10, "wicp-create-" + suffix()
        );
        String typeId = type.get("id").asText();
        String base = configPath(spaceId) + "/" + typeId;
        String draftPath = base + "/draft";

        JsonNode draft = json(mockMvc.perform(get(draftPath)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andReturn());
        JsonNode valid = json(mockMvc.perform(post(draftPath + ":validate")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicp-validate-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedAggregateVersion\":" + draft.get("aggregateVersion").asLong() + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("valid"))
            .andReturn());

        String publishRequestId = "wicp-publish-" + suffix();
        String publishBody = """
            {"expectedDraftAggregateVersion":%d,"breakingConfirmed":false}
            """.formatted(valid.get("aggregateVersion").asLong());
        JsonNode published = json(mockMvc.perform(post(base + "/draft:publish")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", publishRequestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(publishBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version.versionNumber").value(2))
            .andExpect(jsonPath("$.version.snapshotSchemaVersion").value(3))
            .andExpect(jsonPath("$.version.completeSnapshot").value(true))
            .andReturn());
        String version2Id = published.at("/version/id").asText();
        String version2Hash = published.at("/version/configHash").asText();

        mockMvc.perform(post(base + "/draft:publish")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", publishRequestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(publishBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version.id").value(version2Id))
            .andExpect(jsonPath("$.version.configHash").value(version2Hash));
        mockMvc.perform(post(base + "/draft:publish")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", publishRequestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"expectedDraftAggregateVersion":999,"breakingConfirmed":false}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("idempotency_key_reused"));

        mockMvc.perform(get(base + "/versions")
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].versionNumber").value(2))
            .andExpect(jsonPath("$[1].snapshotSchemaVersion").value(0))
            .andExpect(jsonPath("$[1].completeSnapshot").value(false));
        assertThrows(
            org.springframework.dao.DataAccessException.class,
            () -> jdbcTemplate.update(
                "update project_work_item_type_versions set config='{}'::jsonb where id=?",
                UUID.fromString(version2Id)
            )
        );

        JsonNode nextDraft = json(mockMvc.perform(get(draftPath)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andReturn());
        JsonNode rollback = json(mockMvc.perform(post(base + "/versions/" + version2Id + ":prepare-rollback")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicp-rollback-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"expectedDraftAggregateVersion":%d}
                    """.formatted(nextDraft.get("aggregateVersion").asLong())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceVersionId").value(version2Id))
            .andExpect(jsonPath("$.draftStatus").value("valid"))
            .andReturn());
        JsonNode version3 = json(mockMvc.perform(post(base + "/draft:publish")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wicp-republish-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"expectedDraftAggregateVersion":%d,"breakingConfirmed":true}
                    """.formatted(rollback.get("draftAggregateVersion").asLong())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version.versionNumber").value(3))
            .andExpect(jsonPath("$.version.rollbackSourceVersionId").value(version2Id))
            .andReturn());
        assertNotEquals(version2Id, version3.at("/version/id").asText());

        mockMvc.perform(get(base + "/versions").header("Authorization", bearer(readonly.token())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("forbidden"));
        mockMvc.perform(get(base + "/versions").header("Authorization", bearer(outsider.token())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"));
        assertEquals(2, jdbcTemplate.queryForObject(
            """
                select count(*) from audit_logs
                 where workspace_id=? and action='work_item_configuration.published'
                   and target_id in (?, ?)
                """,
            Integer.class,
            owner.workspaceId(),
            UUID.fromString(version2Id),
            UUID.fromString(version3.at("/version/id").asText())
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
            """
                select count(*) from domain_events
                 where workspace_id=? and event_type='work_item_configuration.published'
                   and aggregate_id in (?, ?)
                """,
            Integer.class,
            owner.workspaceId(),
            UUID.fromString(version2Id),
            UUID.fromString(version3.at("/version/id").asText())
        ));
    }

    @Test
    void compatibilityApiEnforcesSixIdentityAndCrossSpaceBoundaries() throws Exception {
        TestUser enterpriseAdmin = root("wic-compat-root");
        TestUser owner = member(enterpriseAdmin.token(), "wiccompatowner");
        TestUser spaceAdmin = member(enterpriseAdmin.token(), "wiccompatadmin");
        TestUser member = member(enterpriseAdmin.token(), "wiccompatmember");
        TestUser guest = member(enterpriseAdmin.token(), "wiccompatguest");
        TestUser outsider = member(enterpriseAdmin.token(), "wiccompatoutside");
        UUID spaceId = createSpace(owner.token(), "wic-compat-space");
        addSpaceMember(spaceId, spaceAdmin.id(), "admin", owner.id());
        addSpaceMember(spaceId, member.id(), "member", owner.id());
        addSpaceMember(spaceId, guest.id(), "guest", owner.id());
        JsonNode type = createType(
            owner.token(), spaceId, "wic_compat_task", "Compatibility Task", 10,
            "wic-compat-create-" + suffix()
        );
        String typeId = type.get("id").asText();
        String base = configPath(spaceId) + "/" + typeId;
        String draftPath = base + "/draft";

        JsonNode draft = json(mockMvc.perform(get(draftPath)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andReturn());
        JsonNode valid = json(mockMvc.perform(post(draftPath + ":validate")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wic-compat-validate-v2-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedAggregateVersion\":" + draft.get("aggregateVersion").asLong() + "}"))
            .andExpect(status().isOk())
            .andReturn());
        JsonNode version2 = json(mockMvc.perform(post(base + "/draft:publish")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wic-compat-publish-v2-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"expectedDraftAggregateVersion":%d,"breakingConfirmed":false}
                    """.formatted(valid.get("aggregateVersion").asLong())))
            .andExpect(status().isOk())
            .andReturn());

        long currentTypeVersion = jdbcTemplate.queryForObject(
            "select aggregate_version from project_work_item_types where id=?",
            Long.class,
            UUID.fromString(typeId)
        );
        mockMvc.perform(patch(configPath(spaceId) + "/" + typeId)
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wic-compat-edit-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Compatibility Task","description":"compatibility changed","aggregateVersion":%d}
                    """.formatted(currentTypeVersion)))
            .andExpect(status().isOk());
        JsonNode changedDraft = json(mockMvc.perform(get(draftPath)
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isOk())
            .andReturn());
        JsonNode changedValid = json(mockMvc.perform(post(draftPath + ":validate")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wic-compat-validate-v3-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedAggregateVersion\":" + changedDraft.get("aggregateVersion").asLong() + "}"))
            .andExpect(status().isOk())
            .andReturn());
        for (TestUser manager : List.of(owner, spaceAdmin)) {
            mockMvc.perform(get(base + "/draft:compatibility")
                    .header("Authorization", bearer(manager.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallImpact").isString());
        }
        JsonNode version3 = json(mockMvc.perform(post(base + "/draft:publish")
                .header("Authorization", bearer(owner.token()))
                .header("X-Colla-Request-Id", "wic-compat-publish-v3-" + suffix())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"expectedDraftAggregateVersion":%d,"breakingConfirmed":true}
                    """.formatted(changedValid.get("aggregateVersion").asLong())))
            .andExpect(status().isOk())
            .andReturn());

        String compatibilityPath = base + "/versions:compatibility?fromVersionId="
            + version2.at("/version/id").asText() + "&toVersionId="
            + version3.at("/version/id").asText();
        for (TestUser manager : List.of(owner, spaceAdmin)) {
            mockMvc.perform(get(compatibilityPath).header("Authorization", bearer(manager.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallImpact").isString())
                .andExpect(jsonPath("$.fromHash").value(version2.at("/version/configHash").asText()))
                .andExpect(jsonPath("$.toHash").value(version3.at("/version/configHash").asText()))
                .andExpect(jsonPath("$.instanceMigrationRequired").isBoolean());
        }
        for (TestUser readonly : List.of(member, guest)) {
            mockMvc.perform(get(compatibilityPath).header("Authorization", bearer(readonly.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));
        }
        for (TestUser hidden : List.of(outsider, enterpriseAdmin)) {
            mockMvc.perform(get(compatibilityPath).header("Authorization", bearer(hidden.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"))
                .andExpect(jsonPath("$.error.message", not(org.hamcrest.Matchers.containsString(
                    version2.at("/version/configHash").asText()
                ))));
        }

        UUID otherSpaceId = createSpace(owner.token(), "wic-compat-other");
        mockMvc.perform(get(configPath(otherSpaceId) + "/" + typeId
                + "/versions:compatibility?fromVersionId=" + version2.at("/version/id").asText()
                + "&toVersionId=" + version3.at("/version/id").asText())
                .header("Authorization", bearer(owner.token())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("not_found_or_hidden"));
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
        assertTrue(openApi.path("paths").has(
            "/api/project-spaces/{spaceId}/work-items/{workItemId}/node-workflow"
        ));
        assertTrue(openApi.path("paths").has(
            "/api/project-spaces/{spaceId}/work-items/{workItemId}/node-workflow/history"
        ));
        assertTrue(openApi.path("paths").has(
            "/api/project-spaces/{spaceId}/work-items/{workItemId}/node-workflow/tasks/{taskId}/actions/{operation}"
        ));
        assertTrue(openApi.path("paths").has("/api/admin/project-spaces/{spaceId}"));
        assertFalse(openApi.path("paths").has("/api/work-items"));
        openApi.path("paths").fieldNames()
            .forEachRemaining(path -> assertFalse(path.startsWith("/api/work-items/")));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name='project_work_item_type_commands'",
            Integer.class
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from project_work_items where space_id=?",
            Integer.class,
            spaceId
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
