package com.colla.platform.modules.knowledge.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class KnowledgeContentControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void canonicalKnowledgeApiCoversContentAndCollaborationCapabilities() throws Exception {
        String token = login("admin", "admin123456");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode space = createSpace(token, "KB Name M2 " + suffix, "kb-name-m2-" + suffix);
        UUID spaceId = uuid(space, "space", "id");
        UUID rootItemId = uuid(space, "space", "rootItemId");
        UUID homeItemId = uuid(space, "space", "homeItemId");

        String createResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", rootItemId,
                    "title", "Canonical Runbook " + suffix,
                    "contentType", "markdown",
                    "content", "# Runbook\nInitial content"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.title").value("Canonical Runbook " + suffix))
            .andExpect(jsonPath("$.item.contentType").value("markdown"))
            .andExpect(jsonPath("$.context.rootItemId").value(rootItemId.toString()))
            .andExpect(jsonPath("$..documentId").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID itemId = uuid(created, "item", "id");
        UUID adminId = uuid(created, "item", "createdBy");
        int versionNo = created.path("item").path("currentVersionNo").asInt();

        mockMvc.perform(get(itemPath(spaceId, itemId))
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.id").value(itemId.toString()))
            .andExpect(jsonPath("$.context.homeItemId").value(homeItemId.toString()))
            .andExpect(jsonPath("$..documentId").doesNotExist());

        String blocksResponse = mockMvc.perform(get(itemPath(spaceId, itemId) + "/blocks")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].itemId").value(itemId.toString()))
            .andExpect(jsonPath("$..documentId").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        JsonNode initialBlocks = objectMapper.readTree(blocksResponse);
        org.junit.jupiter.api.Assertions.assertFalse(initialBlocks.isEmpty());

        String insertResponse = mockMvc.perform(post(itemPath(spaceId, itemId) + "/blocks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "baseVersionNo", versionNo,
                    "afterSortOrder", initialBlocks.get(initialBlocks.size() - 1).path("sortOrder").asInt(),
                    "block", Map.of(
                        "blockType", "paragraph",
                        "content", "Inserted through canonical API",
                        "sortOrder", initialBlocks.size(),
                        "schemaVersion", 2,
                        "attrs", Map.of(),
                        "richContent", Map.of(),
                        "deleted", false
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocks[*].content", hasItem("Inserted through canonical API")))
            .andExpect(jsonPath("$..documentId").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        JsonNode afterInsert = objectMapper.readTree(insertResponse);
        versionNo = afterInsert.path("item").path("currentVersionNo").asInt();
        JsonNode insertedBlock = findBlock(afterInsert.path("blocks"), "Inserted through canonical API");
        UUID blockId = UUID.fromString(insertedBlock.path("id").asText());

        String updateResponse = mockMvc.perform(patch(itemPath(spaceId, itemId) + "/blocks/" + blockId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "baseVersionNo", versionNo,
                    "block", Map.of(
                        "id", blockId,
                        "blockType", "paragraph",
                        "content", "Updated through canonical API",
                        "sortOrder", insertedBlock.path("sortOrder").asInt(),
                        "schemaVersion", 2,
                        "attrs", Map.of(),
                        "richContent", Map.of(),
                        "deleted", false
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocks[*].content", hasItem("Updated through canonical API")))
            .andReturn().getResponse().getContentAsString();
        JsonNode afterUpdate = objectMapper.readTree(updateResponse);
        versionNo = afterUpdate.path("item").path("currentVersionNo").asInt();
        List<String> blockIds = java.util.stream.StreamSupport.stream(afterUpdate.path("blocks").spliterator(), false)
            .map(block -> block.path("id").asText())
            .toList();

        String reorderResponse = mockMvc.perform(post(itemPath(spaceId, itemId) + "/blocks/reorder")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("baseVersionNo", versionNo, "blockIds", blockIds))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        versionNo = objectMapper.readTree(reorderResponse).path("item").path("currentVersionNo").asInt();

        String deleteResponse = mockMvc.perform(delete(itemPath(spaceId, itemId) + "/blocks/" + blockId)
                .header("Authorization", bearer(token))
                .param("baseVersionNo", Integer.toString(versionNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocks[*].id", not(hasItem(blockId.toString()))))
            .andReturn().getResponse().getContentAsString();
        versionNo = objectMapper.readTree(deleteResponse).path("item").path("currentVersionNo").asInt();

        String saveResponse = mockMvc.perform(patch(itemPath(spaceId, itemId))
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "baseVersionNo", versionNo,
                    "title", "Canonical Runbook " + suffix,
                    "content", "# Runbook\nSaved through canonical API"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", containsString("Saved through canonical API")))
            .andReturn().getResponse().getContentAsString();
        versionNo = objectMapper.readTree(saveResponse).path("item").path("currentVersionNo").asInt();

        String commentResponse = mockMvc.perform(post(itemPath(spaceId, itemId) + "/comments")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Canonical comment\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].itemId").value(itemId.toString()))
            .andReturn().getResponse().getContentAsString();
        UUID commentId = uuid(objectMapper.readTree(commentResponse).path("comments").get(0), "id");

        mockMvc.perform(post(itemPath(spaceId, itemId) + "/comments/" + commentId + "/replies")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Canonical reply\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].replies[0].content").value("Canonical reply"));
        mockMvc.perform(post(itemPath(spaceId, itemId) + "/comments/" + commentId + "/resolve")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].resolved").value(true));
        mockMvc.perform(post(itemPath(spaceId, itemId) + "/comments/" + commentId + "/reopen")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].resolved").value(false));

        mockMvc.perform(post(itemPath(spaceId, itemId) + "/versions/named")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionName\":\"M2 baseline\",\"summary\":\"Canonical version\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(get(itemPath(spaceId, itemId) + "/versions")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].itemId", hasItem(itemId.toString())))
            .andExpect(jsonPath("$..documentId").doesNotExist());
        mockMvc.perform(get(itemPath(spaceId, itemId) + "/versions/diff")
                .header("Authorization", bearer(token))
                .param("fromVersionNo", "1")
                .param("toVersionNo", Integer.toString(versionNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itemId").value(itemId.toString()));

        mockMvc.perform(post(itemPath(spaceId, itemId) + "/permissions")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user", "subjectId", adminId, "permissionLevel", "owner"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[*].itemId", hasItem(itemId.toString())));
        mockMvc.perform(post(itemPath(spaceId, itemId) + "/share-link")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"workspace\",\"permissionLevel\":\"view\",\"enabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itemId").value(itemId.toString()));

        mockMvc.perform(post(itemPath(spaceId, itemId) + "/relations")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetType", "knowledge_content", "targetId", homeItemId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[*].itemId", hasItem(itemId.toString())));

        mockMvc.perform(get(itemPath(spaceId, itemId) + "/path").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(itemId.toString())));
        mockMvc.perform(get(itemPath(spaceId, itemId) + "/performance").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itemId").value(itemId.toString()));
        mockMvc.perform(get(itemPath(spaceId, itemId) + "/migration-preview").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itemId").value(itemId.toString()));
        mockMvc.perform(get(itemPath(spaceId, itemId) + "/collaboration/health").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itemId").value(itemId.toString()));
        mockMvc.perform(get(itemPath(spaceId, itemId) + "/export/markdown").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Saved through canonical API")));
        mockMvc.perform(get(itemPath(spaceId, itemId) + "/export/html").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Saved through canonical API")));

        String templateResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/templates")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M2 Template\",\"category\":\"runbook\",\"content\":\"# Template\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.knowledgeBaseId").value(spaceId.toString()))
            .andReturn().getResponse().getContentAsString();
        UUID templateId = UUID.fromString(objectMapper.readTree(templateResponse).path("id").asText());
        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/from-template")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "templateId", templateId, "parentId", rootItemId, "title", "From M2 Template"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.title").value("From M2 Template"));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/" + itemId + "/move")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("parentId", rootItemId, "sortOrder", 2))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.item.sortOrder").value(2));
        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/" + itemId + "/archive")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.item.archived").value(true));
        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/" + itemId + "/restore")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.item.archived").value(false));
    }

    @Test
    void structuredBlockSavePersistsTitleAndBlockProjectionTogether() throws Exception {
        String token = login("admin", "admin123456");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode space = createSpace(token, "M3 Blocks " + suffix, "m3-blocks-" + suffix);
        UUID spaceId = uuid(space, "space", "id");
        UUID rootItemId = uuid(space, "space", "rootItemId");
        String createResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", rootItemId,
                    "title", "M3 Draft " + suffix,
                    "contentType", "markdown",
                    "content", "Initial body"
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID itemId = uuid(created, "item", "id");
        int versionNo = created.path("item").path("currentVersionNo").asInt();

        mockMvc.perform(patch(itemPath(spaceId, itemId) + "/blocks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "baseVersionNo", versionNo,
                    "title", "M3 Autosaved Title " + suffix,
                    "blocks", List.of(
                        Map.of(
                            "blockType", "heading",
                            "content", "M3 Structured Heading",
                            "sortOrder", 0,
                            "schemaVersion", 2,
                            "attrs", Map.of(),
                            "richContent", Map.of(),
                            "deleted", false
                        ),
                        Map.of(
                            "blockType", "paragraph",
                            "content", "M3 Structured Paragraph",
                            "sortOrder", 1,
                            "schemaVersion", 2,
                            "attrs", Map.of(),
                            "richContent", Map.of(),
                            "deleted", false
                        )
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.title").value("M3 Autosaved Title " + suffix))
            .andExpect(jsonPath("$.blocks[*].content", hasItem("M3 Structured Heading")))
            .andExpect(jsonPath("$.blocks[*].content", hasItem("M3 Structured Paragraph")));
    }

    @Test
    void embeddedObjectBlocksResolveBaseProjectAndKnowledgeContentSafely() throws Exception {
        String token = login("admin", "admin123456");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode space = createSpace(token, "M3 Objects " + suffix, "m3-objects-" + suffix);
        UUID spaceId = uuid(space, "space", "id");
        UUID rootItemId = uuid(space, "space", "rootItemId");
        JsonNode target = createMarkdownItem(token, spaceId, rootItemId, "M3 Object Target " + suffix, "Target content");
        JsonNode host = createMarkdownItem(token, spaceId, rootItemId, "M3 Object Host " + suffix, "Host content");
        UUID targetItemId = uuid(target, "item", "id");
        UUID hostItemId = uuid(host, "item", "id");
        UUID baseId = createBaseObject(token, "M3 Object Base " + suffix);
        UUID projectId = createProjectObject(token, "M3 Object Project " + suffix, "M3" + suffix.toUpperCase());

        String response = mockMvc.perform(patch(itemPath(spaceId, hostItemId) + "/blocks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "baseVersionNo", host.path("item").path("currentVersionNo").asInt(),
                    "blocks", List.of(
                        blockDraft("embed_object", Map.of("objectType", "base", "objectId", baseId.toString()), 0),
                        blockDraft("embed_object", Map.of("objectType", "project", "objectId", projectId.toString()), 1),
                        blockDraft("embed_object", Map.of("objectType", "knowledge_content", "objectId", targetItemId.toString()), 2),
                        blockDraft("embed_object", Map.of("objectType", "base", "objectId", UUID.randomUUID().toString()), 3)
                    )
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode blocks = objectMapper.readTree(response).path("blocks");

        org.junit.jupiter.api.Assertions.assertEquals("available", findEmbeddedObject(blocks, "base").path("embedSummary").path("accessState").asText());
        org.junit.jupiter.api.Assertions.assertEquals("M3 Object Base " + suffix, findEmbeddedObject(blocks, "base").path("embedSummary").path("title").asText());
        org.junit.jupiter.api.Assertions.assertEquals("available", findEmbeddedObject(blocks, "project").path("embedSummary").path("accessState").asText());
        org.junit.jupiter.api.Assertions.assertEquals("M3 Object Project " + suffix, findEmbeddedObject(blocks, "project").path("embedSummary").path("title").asText());
        org.junit.jupiter.api.Assertions.assertEquals("available", findEmbeddedObject(blocks, "knowledge_content").path("embedSummary").path("accessState").asText());
        org.junit.jupiter.api.Assertions.assertEquals("not_found", blocks.get(3).path("embedSummary").path("accessState").asText());

        mockMvc.perform(get("/api/platform/objects/project/" + projectId + "/navigation")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.objectType").value("project"))
            .andExpect(jsonPath("$.summary.title").value("M3 Object Project " + suffix))
            .andExpect(jsonPath("$.webPath").value("/projects/" + projectId));
    }

    @Test
    void canonicalKnowledgeApiDoesNotRevealCrossSpaceItems() throws Exception {
        String token = login("admin", "admin123456");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode first = createSpace(token, "M2 First " + suffix, "m2-first-" + suffix);
        JsonNode second = createSpace(token, "M2 Second " + suffix, "m2-second-" + suffix);
        UUID firstSpaceId = uuid(first, "space", "id");
        UUID firstRootItemId = uuid(first, "space", "rootItemId");
        UUID secondSpaceId = uuid(second, "space", "id");

        String title = "Secret cross-space title " + suffix;
        String response = mockMvc.perform(post("/api/knowledge-bases/" + firstSpaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", firstRootItemId,
                    "title", title,
                    "contentType", "markdown",
                    "content", "secret"
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        UUID itemId = uuid(objectMapper.readTree(response), "item", "id");

        mockMvc.perform(get(itemPath(secondSpaceId, itemId)).header("Authorization", bearer(token)))
            .andExpect(status().isNotFound())
            .andExpect(content().string(not(containsString(title))))
            .andExpect(content().string(not(containsString(itemId.toString()))));
        mockMvc.perform(patch(itemPath(secondSpaceId, itemId))
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseVersionNo\":1,\"content\":\"probe\"}"))
            .andExpect(status().isNotFound())
            .andExpect(content().string(not(containsString(title))));
        mockMvc.perform(post("/api/knowledge-bases/" + secondSpaceId + "/items/" + itemId + "/archive")
                .header("Authorization", bearer(token)))
            .andExpect(status().isNotFound())
            .andExpect(content().string(not(containsString(title))));
    }

    @Test
    void objectReferenceRequiresAnAvailableTargetAndPersistsItsCanonicalRoute() throws Exception {
        String token = login("admin", "admin123456");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode space = createSpace(token, "Reference integrity " + suffix, "reference-integrity-" + suffix);
        UUID spaceId = uuid(space, "space", "id");
        UUID rootItemId = uuid(space, "space", "rootItemId");

        String targetResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", rootItemId,
                    "title", "Reference target " + suffix,
                    "contentType", "markdown",
                    "content", "Target content"
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        UUID targetItemId = uuid(objectMapper.readTree(targetResponse), "item", "id");
        String canonicalRoute = "/knowledge-bases/" + spaceId + "/items/" + targetItemId;

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", rootItemId,
                    "title", "Canonical reference " + suffix,
                    "contentType", "object_ref",
                    "targetObjectType", "knowledge_content",
                    "targetObjectId", targetItemId,
                    "targetRoute", "/docs/legacy-target"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.targetRoute").value(canonicalRoute));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", rootItemId,
                    "title", "Missing reference " + suffix,
                    "contentType", "object_ref",
                    "targetObjectType", "knowledge_content",
                    "targetObjectId", UUID.randomUUID(),
                    "targetRoute", "/knowledge-bases/missing/items/missing"
                ))))
            .andExpect(status().isNotFound());
    }

    @Test
    void retiredDocsSurfaceIsAbsentAndOpenApiPublishesCanonicalContract() throws Exception {
        String token = login("admin", "admin123456");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode space = createSpace(token, "M2 Compat " + suffix, "m2-compat-" + suffix);
        UUID homeItemId = uuid(space, "space", "homeItemId");

        mockMvc.perform(get("/api/docs/" + homeItemId).header("Authorization", bearer(token)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/docs/acceptance/v1").header("Authorization", bearer(token)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/knowledge-content/acceptance/v1").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.frozen").value(true));
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/api/knowledge-bases/{spaceId}/items/{itemId}")))
            .andExpect(content().string(containsString("KnowledgeContentDetailView")))
            .andExpect(content().string(containsString("KnowledgeContentBlockView")))
            .andExpect(content().string(not(containsString("/api/docs/acceptance/v1"))));
    }

    private JsonNode createSpace(String token, String name, String code) throws Exception {
        String response = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", name,
                    "code", code,
                    "description", "M2 canonical API test",
                    "visibility", "private",
                    "defaultPermissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.rootItemId").isNotEmpty())
            .andExpect(jsonPath("$.space.homeItemId").isNotEmpty())
            .andExpect(jsonPath("$.rootItem").exists())
            .andExpect(jsonPath("$.homeItem").exists())
            .andExpect(jsonPath("$..documentId").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode createMarkdownItem(String token, UUID spaceId, UUID parentId, String title, String content) throws Exception {
        String response = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", parentId,
                    "title", title,
                    "contentType", "markdown",
                    "content", content
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createBaseObject(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/bases")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "description", "M3 object-card test"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("base").path("id").asText());
    }

    private UUID createProjectObject(String token, String name, String projectKey) throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "projectKey", projectKey,
                    "name", name,
                    "description", "M3 object-card test",
                    "memberIds", List.of()
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("id").asText());
    }

    private Map<String, Object> blockDraft(String blockType, Map<String, String> content, int sortOrder) throws Exception {
        return Map.of(
            "blockType", blockType,
            "content", objectMapper.writeValueAsString(content),
            "sortOrder", sortOrder,
            "schemaVersion", 2,
            "attrs", Map.of(),
            "richContent", Map.of(),
            "deleted", false
        );
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username,
                    "password", password,
                    "deviceType", "web",
                    "deviceFingerprint", "kb-name-m2-" + UUID.randomUUID(),
                    "deviceName", "MockMvc",
                    "appVersion", "test"
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("accessToken").asText();
    }

    private JsonNode findBlock(JsonNode blocks, String content) {
        return java.util.stream.StreamSupport.stream(blocks.spliterator(), false)
            .filter(block -> content.equals(block.path("content").asText()))
            .findFirst()
            .orElseThrow();
    }

    private JsonNode findEmbeddedObject(JsonNode blocks, String objectType) {
        return java.util.stream.StreamSupport.stream(blocks.spliterator(), false)
            .filter(block -> objectType.equals(block.path("embedSummary").path("objectType").asText()))
            .findFirst()
            .orElseThrow();
    }

    private UUID uuid(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            current = current.path(part);
        }
        return UUID.fromString(current.asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String itemPath(UUID spaceId, UUID itemId) {
        return "/api/knowledge-bases/" + spaceId + "/items/" + itemId;
    }
}

