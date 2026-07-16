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
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeContentControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        mockMvc.perform(post(itemPath(spaceId, itemId) + "/permissions")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user", "subjectId", adminId, "permissionLevel", "owner"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[?(@.subjectId == '" + adminId + "')].permissionLevel", hasItem("owner")));
        String shareResponse = mockMvc.perform(post(itemPath(spaceId, itemId) + "/share-link")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"workspace\",\"permissionLevel\":\"view\",\"enabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itemId").value(itemId.toString()))
            .andReturn().getResponse().getContentAsString();
        String originalShareToken = objectMapper.readTree(shareResponse).path("token").asText();
        mockMvc.perform(post(itemPath(spaceId, itemId) + "/share-link/revoke")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.token").value(not(originalShareToken)));

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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itemId").value(itemId.toString()))
            .andExpect(jsonPath("$.budgetTier").value(100))
            .andExpect(jsonPath("$.snapshotBytes").isNumber())
            .andExpect(jsonPath("$.inputBudgetMs").value(100));
        mockMvc.perform(get(itemPath(spaceId, itemId) + "/diagnostics").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redacted").value(true))
            .andExpect(jsonPath("$.blockCount").isNumber())
            .andExpect(jsonPath("$..content").doesNotExist());
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
        String fromTemplateResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/from-template")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "templateId", templateId, "parentId", rootItemId, "title", "From M2 Template"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.title").value("From M2 Template"))
            .andReturn().getResponse().getContentAsString();
        UUID fromTemplateItemId = uuid(objectMapper.readTree(fromTemplateResponse), "item", "id");
        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/templates/" + templateId + "/upgrade")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"# Template v2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versionNo").value(2))
            .andExpect(jsonPath("$.supersedesTemplateId").value(templateId.toString()));
        mockMvc.perform(get(itemPath(spaceId, fromTemplateItemId)).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", containsString("Template")))
            .andExpect(jsonPath("$.content", not(containsString("Template v2"))));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/" + itemId + "/copy")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("parentId", rootItemId, "title", "Canonical copy " + suffix))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.title").value("Canonical copy " + suffix))
            .andExpect(jsonPath("$.content", containsString("Saved through canonical API")));

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
    void m11PermissionTransitionAndRequestApprovalUseTheCentralPermissionModel() throws Exception {
        String adminToken = login("admin", "admin123456");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode space = createSpace(adminToken, "M11 Permissions " + suffix, "m11-perm-" + suffix);
        UUID spaceId = uuid(space, "space", "id");
        UUID rootItemId = uuid(space, "space", "rootItemId");
        UUID folderA = uuid(createFolder(adminToken, spaceId, rootItemId, "M11 A " + suffix), "item", "id");
        UUID folderB = uuid(createFolder(adminToken, spaceId, rootItemId, "M11 B " + suffix), "item", "id");
        UUID memberId = createMember(adminToken, "m11member" + suffix, "M11 Member " + suffix);

        mockMvc.perform(post("/api/resource-permissions/knowledge_content/" + folderA)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user",
                    "subjectId", memberId,
                    "permissionLevel", "view",
                    "confirmHighRisk", false
                ))))
            .andExpect(status().isOk());
        UUID childId = uuid(createMarkdownItem(adminToken, spaceId, folderA, "M11 child " + suffix, "permission transition"), "item", "id");

        mockMvc.perform(get("/api/resource-permissions/knowledge_content/" + childId + "/transition-preview")
                .header("Authorization", bearer(adminToken))
                .param("targetParentId", folderB.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentParentId").value(folderA.toString()))
            .andExpect(jsonPath("$.targetParentId").value(folderB.toString()))
            .andExpect(jsonPath("$.removedInherited[*].subjectId", hasItem(memberId.toString())))
            .andExpect(jsonPath("$.addedInherited").isEmpty());

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/" + childId + "/move")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("parentId", folderB))))
            .andExpect(status().isOk());
        Integer staleInherited = jdbcTemplate.queryForObject(
            """
                select count(*) from resource_permissions
                where resource_type = 'knowledge_content' and resource_id = ? and subject_type = 'user'
                  and subject_id = ? and source_type = 'inherited' and status = 'active'
                """,
            Integer.class,
            childId,
            memberId
        );
        org.junit.jupiter.api.Assertions.assertEquals(0, staleInherited);

        String memberToken = login("m11member" + suffix, "member123456");
        String requestResponse = mockMvc.perform(post(itemPath(spaceId, childId) + "/permission-requests")
                .header("Authorization", bearer(memberToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionLevel\":\"view\",\"reason\":\"Need the runbook\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("submitted"))
            .andReturn().getResponse().getContentAsString();
        UUID requestId = UUID.fromString(objectMapper.readTree(requestResponse).path("requestId").asText());

        mockMvc.perform(get("/api/resource-permissions/knowledge_content/" + childId + "/requests")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(requestId.toString())));
        mockMvc.perform(post("/api/resource-permissions/requests/" + requestId + "/approve")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"approved for M11\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("approved"));
        mockMvc.perform(get(itemPath(spaceId, childId)).header("Authorization", bearer(memberToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.item.id").value(childId.toString()));
    }

    @Test
    void m10CommentsRelationsAndImportExportRemainBoundToCanonicalBlocks() throws Exception {
        String token = login("admin", "admin123456");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode space = createSpace(token, "M10 Content " + suffix, "m10-content-" + suffix);
        UUID spaceId = uuid(space, "space", "id");
        UUID rootItemId = uuid(space, "space", "rootItemId");
        JsonNode source = createMarkdownItem(token, spaceId, rootItemId, "M10 Source " + suffix, "前缀 目标文本 后缀");
        JsonNode target = createMarkdownItem(token, spaceId, rootItemId, "M10 Target " + suffix, "Target body");
        UUID sourceId = uuid(source, "item", "id");
        UUID targetId = uuid(target, "item", "id");
        JsonNode sourceBlock = source.path("blocks").get(0);
        UUID sourceBlockId = UUID.fromString(sourceBlock.path("id").asText());

        String commentResponse = mockMvc.perform(post(itemPath(spaceId, sourceId) + "/comments")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "blockId", sourceBlockId,
                    "anchorType", "selection",
                    "anchorStart", 3,
                    "anchorEnd", 7,
                    "anchorText", "目标文本",
                    "anchorPrefix", "前缀 ",
                    "anchorSuffix", " 后缀",
                    "content", "@admin 请复核锚点"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].anchorState").value("active"))
            .andReturn().getResponse().getContentAsString();
        UUID commentId = UUID.fromString(objectMapper.readTree(commentResponse).path("comments").get(0).path("id").asText());

        String shiftedResponse = mockMvc.perform(patch(itemPath(spaceId, sourceId) + "/blocks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "baseVersionNo", source.path("item").path("currentVersionNo").asInt(),
                    "title", "M10 Source " + suffix,
                    "blocks", List.of(Map.of(
                        "id", sourceBlockId,
                        "blockType", "paragraph",
                        "content", "新增 前缀 目标文本 后缀",
                        "sortOrder", 0,
                        "schemaVersion", 2,
                        "attrs", Map.of(),
                        "richContent", Map.of(),
                        "deleted", false
                    ))
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].anchorStart").value(6))
            .andExpect(jsonPath("$.comments[0].anchorEnd").value(10))
            .andExpect(jsonPath("$.comments[0].anchorState").value("active"))
            .andReturn().getResponse().getContentAsString();
        int shiftedVersion = objectMapper.readTree(shiftedResponse).path("item").path("currentVersionNo").asInt();

        String deletedResponse = mockMvc.perform(delete(itemPath(spaceId, sourceId) + "/blocks/" + sourceBlockId)
                .header("Authorization", bearer(token))
                .param("baseVersionNo", Integer.toString(shiftedVersion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].anchorState").value("detached"))
            .andExpect(jsonPath("$.comments[0].anchorInvalidReason").value("block_deleted"))
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(objectMapper.readTree(deletedResponse).path("comments").get(0).path("content").asText().isBlank());

        mockMvc.perform(post(itemPath(spaceId, sourceId) + "/versions/" + shiftedVersion + "/restore")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].id").value(commentId.toString()))
            .andExpect(jsonPath("$.comments[0].anchorState").value("active"))
            .andExpect(jsonPath("$.comments[0].anchorInvalidReason").doesNotExist());

        mockMvc.perform(post(itemPath(spaceId, sourceId) + "/relations")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetType", "knowledge_content", "targetId", targetId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[?(@.targetId == '" + targetId + "')].targetType").value(hasItem("knowledge_content")));
        mockMvc.perform(get(itemPath(spaceId, targetId)).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[?(@.targetId == '" + sourceId + "')].targetType").value(hasItem("knowledge_content")));
        mockMvc.perform(delete(itemPath(spaceId, sourceId) + "/relations")
                .header("Authorization", bearer(token))
                .param("targetType", "knowledge_content")
                .param("targetId", targetId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[?(@.targetId == '" + targetId + "')]").isEmpty());
        mockMvc.perform(get(itemPath(spaceId, targetId)).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[?(@.targetId == '" + sourceId + "')]").isEmpty());

        String markdown = """
            # 导入验收
            | 名称 | 状态 |
            | --- | --- |
            | 知识库 | 正常 |
            ```java
            System.out.println("m10");
            ```
            ![架构图](https://example.com/architecture.png)
            """;
        mockMvc.perform(post(itemPath(spaceId, sourceId) + "/import/preview")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("format", "markdown", "source", markdown))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.convertedFeatures", hasItem("table")))
            .andExpect(jsonPath("$.convertedFeatures", hasItem("code_block")))
            .andExpect(jsonPath("$.convertedFeatures", hasItem("image")))
            .andExpect(jsonPath("$.safeToApply").value(true));
        mockMvc.perform(post(itemPath(spaceId, sourceId) + "/import/markdown")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "M10 Imported", "content", markdown))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocks[*].blockType", hasItem("table")))
            .andExpect(jsonPath("$.blocks[*].blockType", hasItem("code_block")))
            .andExpect(jsonPath("$.blocks[*].blockType", hasItem("image")));
        mockMvc.perform(get(itemPath(spaceId, sourceId) + "/export/markdown").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("| 知识库 | 正常 |")))
            .andExpect(content().string(containsString("![架构图](https://example.com/architecture.png)")));
        mockMvc.perform(get(itemPath(spaceId, sourceId) + "/export/manifest")
                .header("Authorization", bearer(token)).param("format", "markdown"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attachmentCount").value(1))
            .andExpect(jsonPath("$.fingerprint").isNotEmpty());
        mockMvc.perform(post(itemPath(spaceId, sourceId) + "/import/preview")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("format", "html", "source", "<script>alert(1)</script>"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.degradedFeatures", hasItem("unsafe_html_omitted")));
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

    private JsonNode createFolder(String token, UUID spaceId, UUID parentId, String title) throws Exception {
        String response = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", parentId,
                    "title", title,
                    "contentType", "folder"
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createMember(String token, String username, String displayName) throws Exception {
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username,
                    "password", "member123456",
                    "displayName", displayName,
                    "email", username + "@example.com",
                    "roleCode", "member"
                ))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).path("id").asText());
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

