package com.colla.platform.modules.doc.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.event.application.DomainEventWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
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
class DocumentControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainEventWorker domainEventWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void knowledgeBaseSpaceProductLayerKeepsLegacyDocumentTreeCompatibility() throws Exception {
        String adminToken = login("admin", "admin123456", "kb-m1-admin-device-" + UUID.randomUUID());
        String viewerUsername = "kbm1" + UUID.randomUUID().toString().substring(0, 8);
        UUID viewerId = createMember(adminToken, viewerUsername, "KB M1 Viewer");
        String viewerToken = login(viewerUsername, "member123456", "kb-m1-viewer-device-" + UUID.randomUUID());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID legacyRootId = createKnowledgeBaseDocument(adminToken, "Legacy KB " + suffix);

        mockMvc.perform(get("/api/knowledge-bases")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].rootDocumentId", hasItem(legacyRootId.toString())));

        String createResponse = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "KB M1 Space " + suffix,
                    "code", "kb-m1-" + suffix,
                    "description", "Knowledge base product layer",
                    "icon", "book",
                    "coverUrl", "https://example.com/kb-cover.png",
                    "visibility", "private",
                    "defaultPermissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.status").value("active"))
            .andExpect(jsonPath("$.space.code").value("kb-m1-" + suffix))
            .andExpect(jsonPath("$.rootDocument.docType").value("space"))
            .andExpect(jsonPath("$.rootDocument.knowledgeBase").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID spaceId = UUID.fromString(created.get("space").get("id").asText());
        UUID rootDocumentId = UUID.fromString(created.get("space").get("rootDocumentId").asText());
        UUID homeDocumentId = UUID.fromString(created.get("space").get("homeDocumentId").asText());
        org.hamcrest.MatcherAssert.assertThat(homeDocumentId, not(rootDocumentId));

        mockMvc.perform(get("/api/docs/" + rootDocumentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.id").value(rootDocumentId.toString()))
            .andExpect(jsonPath("$.document.knowledgeBase").value(true));

        mockMvc.perform(get("/api/docs/" + homeDocumentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.docType").value("markdown"))
            .andExpect(jsonPath("$.document.parentId").value(rootDocumentId.toString()))
            .andExpect(jsonPath("$.document.title").value("首页"));

        mockMvc.perform(patch("/api/knowledge-bases/" + spaceId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "description", "Updated without changing home"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.homeDocumentId").value(homeDocumentId.toString()));

        mockMvc.perform(post("/api/docs/" + rootDocumentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + viewerId + "\",\"permissionLevel\":\"edit\"}"))
            .andExpect(status().isOk());

        UUID viewerChildId = createDocument(viewerToken, rootDocumentId, "Viewer Child " + suffix, "markdown", "Before disable");
        mockMvc.perform(get("/api/docs/" + viewerChildId)
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.parentId").value(rootDocumentId.toString()));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/disable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.status").value("disabled"));

        mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", rootDocumentId.toString(),
                    "title", "Blocked Child " + suffix,
                    "content", "Blocked"
                ))))
            .andExpect(status().isForbidden());

        UUID adminChildId = createDocument(adminToken, rootDocumentId, "Admin Governance Child " + suffix, "markdown", "Allowed");
        mockMvc.perform(get("/api/docs/" + adminChildId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/knowledge-bases/" + spaceId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "homeDocumentId", adminChildId.toString()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.homeDocumentId").value(adminChildId.toString()))
            .andExpect(jsonPath("$.homeDocument.id").value(adminChildId.toString()));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.status").value("active"));

        mockMvc.perform(delete("/api/knowledge-bases/" + spaceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.status").value("archived"))
            .andExpect(jsonPath("$.rootDocument.archived").value(true));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.status").value("active"))
            .andExpect(jsonPath("$.rootDocument.archived").value(false));

        mockMvc.perform(get("/api/admin/audit-logs?action=knowledge_base.disabled&targetType=knowledge_base&targetId=" + spaceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void knowledgeBaseItemApisMirrorLegacyDocumentCompatibility() throws Exception {
        String adminToken = login("admin", "admin123456", "kb-clean-m3-admin-device-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        JsonNode firstSpace = createKnowledgeBaseSpace(adminToken, "KB Clean M3 Space " + suffix, "kb-clean-m3-" + suffix);
        UUID spaceId = UUID.fromString(firstSpace.get("space").get("id").asText());
        UUID rootDocumentId = UUID.fromString(firstSpace.get("space").get("rootDocumentId").asText());
        UUID homeDocumentId = UUID.fromString(firstSpace.get("space").get("homeDocumentId").asText());
        JsonNode secondSpace = createKnowledgeBaseSpace(adminToken, "KB Clean M3 Other " + suffix, "kb-clean-m3-other-" + suffix);
        UUID otherSpaceId = UUID.fromString(secondSpace.get("space").get("id").asText());
        UUID otherRootDocumentId = UUID.fromString(secondSpace.get("space").get("rootDocumentId").asText());

        String folderResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", rootDocumentId.toString(),
                    "title", "M3 API Folder " + suffix,
                    "docType", "folder",
                    "content", ""
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.parentId").value(rootDocumentId.toString()))
            .andExpect(jsonPath("$.knowledgeContext.spaceId").value(spaceId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID folderId = UUID.fromString(objectMapper.readTree(folderResponse).get("document").get("id").asText());

        String itemResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", folderId.toString(),
                    "title", "M3 API Runbook " + suffix,
                    "docType", "markdown",
                    "content", "# M3\nKnowledge API semantic route"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.parentId").value(folderId.toString()))
            .andExpect(jsonPath("$.knowledgeContext.spaceId").value(spaceId.toString()))
            .andExpect(jsonPath("$.knowledgeContext.pathText", containsString("M3 API Folder " + suffix)))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode itemJson = objectMapper.readTree(itemResponse);
        UUID itemId = UUID.fromString(itemJson.get("document").get("id").asText());

        mockMvc.perform(get("/api/docs/" + itemId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.title").value("M3 API Runbook " + suffix))
            .andExpect(jsonPath("$.knowledgeContext.spaceId").value(spaceId.toString()));

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/items/tree?includeArchived=true")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("M3 API Folder " + suffix)))
            .andExpect(content().string(containsString("M3 API Runbook " + suffix)));

        String templateResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/templates")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "title", "M3 Template " + suffix,
                    "description", "Knowledge semantic template",
                    "category", "sop",
                    "content", "# Template M3"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.knowledgeBaseId").value(spaceId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID templateId = UUID.fromString(objectMapper.readTree(templateResponse).get("id").asText());

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/templates")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(templateId.toString())));

        String templatedResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/from-template")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "templateId", templateId.toString(),
                    "parentId", homeDocumentId.toString(),
                    "title", "M3 From Template " + suffix
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.parentId").value(homeDocumentId.toString()))
            .andExpect(jsonPath("$.document.title").value("M3 From Template " + suffix))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID templatedId = UUID.fromString(objectMapper.readTree(templatedResponse).get("document").get("id").asText());

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/" + itemId + "/move")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("parentId", homeDocumentId.toString(), "sortOrder", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.parentId").value(homeDocumentId.toString()));

        mockMvc.perform(post("/api/knowledge-bases/" + otherSpaceId + "/items/" + itemId + "/move")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("parentId", otherRootDocumentId.toString()))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/" + templatedId + "/archive")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.archived").value(true));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/items/" + templatedId + "/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.archived").value(false));
    }

    @Test
    void knowledgeBaseSpaceSettingsUseSpaceTableAndRootMetadataIsDeprecated() throws Exception {
        String adminToken = login("admin", "admin123456", "kb-clean-m4-admin-device-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        String createResponse = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "KB Clean M4 Space " + suffix,
                    "code", "kb-clean-m4-" + suffix,
                    "description", "Space table description",
                    "icon", "book",
                    "coverUrl", "https://example.com/m4-cover.png",
                    "visibility", "workspace",
                    "defaultPermissionLevel", "edit"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.description").value("Space table description"))
            .andExpect(jsonPath("$.space.coverUrl").value("https://example.com/m4-cover.png"))
            .andExpect(jsonPath("$.space.defaultPermissionLevel").value("edit"))
            .andExpect(jsonPath("$.rootDocument.title").value("根目录"))
            .andExpect(jsonPath("$.rootDocument.description").value(blankOrNullString()))
            .andExpect(jsonPath("$.rootDocument.coverUrl").value(blankOrNullString()))
            .andExpect(jsonPath("$.rootDocument.defaultPermissionLevel").value("view"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID spaceId = UUID.fromString(created.get("space").get("id").asText());
        UUID rootDocumentId = UUID.fromString(created.get("space").get("rootDocumentId").asText());

        mockMvc.perform(patch("/api/knowledge-bases/" + spaceId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "KB Clean M4 Renamed " + suffix,
                    "description", "Updated only in knowledge_base_spaces",
                    "coverUrl", "https://example.com/m4-cover-updated.png",
                    "defaultPermissionLevel", "comment"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.name").value("KB Clean M4 Renamed " + suffix))
            .andExpect(jsonPath("$.space.description").value("Updated only in knowledge_base_spaces"))
            .andExpect(jsonPath("$.space.coverUrl").value("https://example.com/m4-cover-updated.png"))
            .andExpect(jsonPath("$.space.defaultPermissionLevel").value("comment"));

        mockMvc.perform(get("/api/docs/" + rootDocumentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.title").value("根目录"))
            .andExpect(jsonPath("$.document.description").value(blankOrNullString()))
            .andExpect(jsonPath("$.document.coverUrl").value(blankOrNullString()))
            .andExpect(jsonPath("$.document.defaultPermissionLevel").value("view"))
            .andExpect(jsonPath("$.document.knowledgeBase").value(true));

        mockMvc.perform(post("/api/docs/" + rootDocumentId + "/knowledge-base")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "description", "Deprecated docs API should not win",
                    "coverUrl", "https://example.com/deprecated.png",
                    "defaultPermissionLevel", "edit",
                    "knowledgeBase", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.description").value(blankOrNullString()))
            .andExpect(jsonPath("$.document.coverUrl").value(blankOrNullString()))
            .andExpect(jsonPath("$.document.defaultPermissionLevel").value("view"));

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.description").value("Updated only in knowledge_base_spaces"))
            .andExpect(jsonPath("$.space.coverUrl").value("https://example.com/m4-cover-updated.png"))
            .andExpect(jsonPath("$.space.defaultPermissionLevel").value("comment"));

        Integer shadowFieldCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from documents
                where id = ?
                  and doc_type = 'space'
                  and knowledge_base = true
                  and description is null
                  and cover_url is null
                  and default_permission_level = 'view'
                """,
            Integer.class,
            rootDocumentId
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, shadowFieldCount);

        Integer spaceRowCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from knowledge_base_spaces
                where id = ?
                  and root_document_id = ?
                  and description = 'Updated only in knowledge_base_spaces'
                  and cover_url = 'https://example.com/m4-cover-updated.png'
                  and default_permission_level = 'comment'
                  and deleted_at is null
                """,
            Integer.class,
            spaceId,
            rootDocumentId
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, spaceRowCount);
    }

    @Test
    void legacyDocumentPermissionEndpointWritesUnifiedResourceAclOnly() throws Exception {
        String adminToken = login("admin", "admin123456", "kb-clean-m5-admin-device-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String viewerUsername = "kbm5u" + suffix;
        UUID viewerId = createMember(adminToken, viewerUsername, "KB M5 Unified Viewer");
        String viewerToken = login(viewerUsername, "member123456", "kb-clean-m5-viewer-device-" + UUID.randomUUID());
        UUID documentId = createDocument(adminToken, null, "KB M5 Unified ACL " + suffix, "markdown", "# Unified ACL");

        Integer legacyBefore = jdbcTemplate.queryForObject(
            "select count(*) from document_permissions where document_id = ?",
            Integer.class,
            documentId
        );

        mockMvc.perform(post("/api/docs/" + documentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + viewerId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[*].subjectId", hasItem(viewerId.toString())))
            .andExpect(jsonPath("$.permissions[*].permissionLevel", hasItem("view")));

        Integer legacyAfter = jdbcTemplate.queryForObject(
            "select count(*) from document_permissions where document_id = ?",
            Integer.class,
            documentId
        );
        Integer resourceCount = jdbcTemplate.queryForObject(
            """
                select count(*)
                from resource_permissions
                where resource_type = 'document'
                  and resource_id = ?
                  and subject_type = 'user'
                  and subject_id = ?
                  and permission_level = 'view'
                  and status = 'active'
                """,
            Integer.class,
            documentId,
            viewerId
        );

        org.junit.jupiter.api.Assertions.assertEquals(legacyBefore, legacyAfter);
        org.junit.jupiter.api.Assertions.assertEquals(1, resourceCount);
        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.permissionLevel").value("view"));
    }

    @Test
    void knowledgeMetadataTemplatesImportExportAndReviewReminderFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "kb-m4-admin-device-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID maintainerId = createMember(adminToken, "kbm4" + suffix, "KB M4 Maintainer");

        String createResponse = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "KB M4 Space " + suffix,
                    "code", "kb-m4-" + suffix,
                    "description", "Knowledge maintenance",
                    "visibility", "private",
                    "defaultPermissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID spaceId = UUID.fromString(created.get("space").get("id").asText());
        UUID homeDocumentId = UUID.fromString(created.get("space").get("homeDocumentId").asText());

        String templateResponse = mockMvc.perform(post("/api/docs/templates")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "knowledgeBaseId", spaceId.toString(),
                    "title", "M4 Runbook " + suffix,
                    "description", "Space scoped runbook",
                    "category", "sop",
                    "content", "# Runbook\n\n## Steps"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scopeType").value("knowledge_base"))
            .andExpect(jsonPath("$.knowledgeBaseId").value(spaceId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID templateId = UUID.fromString(objectMapper.readTree(templateResponse).get("id").asText());

        mockMvc.perform(get("/api/docs/templates?knowledgeBaseId=" + spaceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].category", hasItem("faq")))
            .andExpect(jsonPath("$[*].id", hasItem(templateId.toString())));

        mockMvc.perform(post("/api/docs/from-template")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "templateId", templateId.toString(),
                    "parentId", homeDocumentId.toString(),
                    "title", "M4 Template Doc " + suffix
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.parentId").value(homeDocumentId.toString()))
            .andExpect(jsonPath("$.document.title").value("M4 Template Doc " + suffix));

        LocalDate yesterday = LocalDate.now().minusDays(1);
        String metadataResponse = mockMvc.perform(patch("/api/docs/" + homeDocumentId + "/knowledge-metadata")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "maintainerId", maintainerId.toString(),
                    "tags", List.of("onboarding", "faq"),
                    "category", "faq",
                    "knowledgeStatus", "verified",
                    "reviewDueAt", yesterday.toString()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.maintainerId").value(maintainerId.toString()))
            .andExpect(jsonPath("$.document.tags", hasItem("onboarding")))
            .andExpect(jsonPath("$.document.category").value("faq"))
            .andExpect(jsonPath("$.document.knowledgeStatus").value("verified"))
            .andExpect(jsonPath("$.document.verifiedAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID metadataDocumentId = UUID.fromString(objectMapper.readTree(metadataResponse).get("document").get("id").asText());

        String importResponse = mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/import/markdown-batch")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "parentId", homeDocumentId.toString(),
                    "items", List.of(
                        Map.of("title", "M4 Imported FAQ " + suffix, "content", "# Imported FAQ\n\nAnswer", "category", "faq", "tags", List.of("imported")),
                        Map.of("title", "M4 Imported SOP " + suffix, "content", "# Imported SOP\n\nSteps", "category", "sop", "tags", List.of("runbook"))
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.importedCount").value(2))
            .andExpect(jsonPath("$.documents.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID importedDocId = UUID.fromString(objectMapper.readTree(importResponse).get("documents").get(0).get("id").asText());

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/export/markdown")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("KB M4 Space " + suffix)))
            .andExpect(content().string(containsString("M4 Imported FAQ " + suffix)))
            .andExpect(content().string(containsString("knowledgeStatus")));

        mockMvc.perform(post("/api/docs/knowledge-review-reminders/run")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "beforeDate", LocalDate.now().toString(),
                    "limit", 20
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notifiedCount", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/docs/" + metadataDocumentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.knowledgeStatus").value("needs_review"));

        mockMvc.perform(get("/api/docs/" + importedDocId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.tags", hasItem("imported")));
    }

    @Test
    void knowledgeSearchDiscoverySubscriptionAndAclFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "kb-m5-admin-device-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID maintainerId = createMember(adminToken, "kbm5m" + suffix, "KB M5 Maintainer");
        UUID followerId = createMember(adminToken, "kbm5f" + suffix, "KB M5 Follower");
        String followerToken = login("kbm5f" + suffix, "member123456", "kb-m5-follower-device-" + UUID.randomUUID());
        createMember(adminToken, "kbm5o" + suffix, "KB M5 Outsider");
        String outsiderToken = login("kbm5o" + suffix, "member123456", "kb-m5-outsider-device-" + UUID.randomUUID());

        String createResponse = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "KB M5 Space " + suffix,
                    "code", "kb-m5-" + suffix,
                    "description", "Knowledge discovery",
                    "visibility", "private",
                    "defaultPermissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID spaceId = UUID.fromString(created.get("space").get("id").asText());
        UUID rootDocumentId = UUID.fromString(created.get("space").get("rootDocumentId").asText());

        mockMvc.perform(post("/api/docs/" + rootDocumentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + followerId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk());

        UUID folderId = createDocument(adminToken, rootDocumentId, "M5 Discovery Folder " + suffix, "folder", "");
        String uniqueTerm = "M5KnowledgeNeedle" + suffix;
        UUID docId = createDocument(adminToken, folderId, "M5 Searchable Runbook " + suffix, "markdown", "# Runbook\n" + uniqueTerm + "\nBody block");
        mockMvc.perform(patch("/api/docs/" + docId + "/knowledge-metadata")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "maintainerId", maintainerId.toString(),
                    "tags", List.of("m5", "runbook"),
                    "category", "sop",
                    "knowledgeStatus", "verified",
                    "reviewDueAt", LocalDate.now().plusDays(3).toString()
                ))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/search/reindex")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/search?q=" + uniqueTerm
                    + "&knowledgeBaseId=" + spaceId
                    + "&directoryId=" + folderId
                    + "&docType=markdown"
                    + "&tags=m5"
                    + "&maintainerId=" + maintainerId
                    + "&knowledgeStatus=verified")
                .header("Authorization", "Bearer " + followerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].objectId", hasItem(docId.toString())))
            .andExpect(jsonPath("$.items[0].knowledgeBaseId").value(spaceId.toString()))
            .andExpect(jsonPath("$.items[0].knowledgeBaseName").value("KB M5 Space " + suffix))
            .andExpect(jsonPath("$.items[0].webPath", containsString("/knowledge-bases/" + spaceId + "?docId=" + docId)))
            .andExpect(jsonPath("$.items[0].tags", hasItem("m5")))
            .andExpect(jsonPath("$.items[0].maintainerName").value("KB M5 Maintainer"))
            .andExpect(jsonPath("$.items[0].knowledgeStatus").value("verified"))
            .andExpect(jsonPath("$.items[0].directoryPath", containsString("M5 Discovery Folder " + suffix)));

        mockMvc.perform(get("/api/search?q=" + uniqueTerm)
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].objectId", not(hasItem(docId.toString()))))
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(content().string(not(containsString("M5 Searchable Runbook " + suffix))));

        mockMvc.perform(post("/api/platform/objects/document/" + docId + "/access")
                .header("Authorization", "Bearer " + followerToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/platform/objects/document/" + docId + "/favorite")
                .header("Authorization", "Bearer " + followerToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/subscriptions")
                .header("Authorization", "Bearer " + followerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"knowledge_base\",\"targetId\":\"" + spaceId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscribed").value(true));

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/discovery")
                .header("Authorization", "Bearer " + followerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spaceSubscribed").value(true))
            .andExpect(jsonPath("$.recentAccessed[*].id", hasItem(docId.toString())))
            .andExpect(jsonPath("$.favorites[*].id", hasItem(docId.toString())))
            .andExpect(jsonPath("$.subscribedDocuments[*].id", hasItem(docId.toString())))
            .andExpect(jsonPath("$.recommended[*].id", hasItem(docId.toString())));

        mockMvc.perform(patch("/api/docs/" + docId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "baseVersionNo", 1,
                    "title", "M5 Searchable Runbook Updated " + suffix,
                    "content", "# Runbook\n" + uniqueTerm + "\nUpdated"
                ))))
            .andExpect(status().isOk());
        for (int index = 0; index < 4; index++) {
            domainEventWorker.processPendingEvents();
        }

        mockMvc.perform(get("/api/notifications?unreadOnly=true")
                .header("Authorization", "Bearer " + followerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].notificationType", hasItem("knowledge_subscription_updated")))
            .andExpect(jsonPath("$[*].webPath", hasItem("/knowledge-bases/" + spaceId + "?docId=" + docId)));
    }

    @Test
    void knowledgeCollaborationContextMentionIssueAndObjectCardFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "kb-m6-admin-device-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID reviewerId = createMember(adminToken, "kbm6r" + suffix, "KB M6 Reviewer");
        String reviewerToken = login("kbm6r" + suffix, "member123456", "kb-m6-reviewer-device-" + UUID.randomUUID());

        String createResponse = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "KB M6 Space " + suffix,
                    "code", "kb-m6-" + suffix,
                    "description", "Collaboration polish",
                    "visibility", "private",
                    "defaultPermissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID spaceId = UUID.fromString(created.get("space").get("id").asText());
        UUID rootDocumentId = UUID.fromString(created.get("space").get("rootDocumentId").asText());

        mockMvc.perform(post("/api/docs/" + rootDocumentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + reviewerId + "\",\"permissionLevel\":\"comment\"}"))
            .andExpect(status().isOk());

        UUID folderId = createDocument(adminToken, rootDocumentId, "M6 Collaboration Folder " + suffix, "folder", "");
        UUID documentId = createDocument(adminToken, folderId, "M6 Collaboration Runbook " + suffix, "markdown", "# M6\nEscalate this paragraph");

        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + reviewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.knowledgeContext.spaceId").value(spaceId.toString()))
            .andExpect(jsonPath("$.knowledgeContext.pathText", containsString("M6 Collaboration Folder " + suffix)))
            .andExpect(jsonPath("$.knowledgeContext.webPath").value("/knowledge-bases/" + spaceId + "?docId=" + documentId));

        String commentResponse = mockMvc.perform(post("/api/docs/" + documentId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "anchorType": "selection",
                          "anchorStart": 5,
                          "anchorEnd": 13,
                          "anchorText": "Escalate",
                          "content": "please review @%s"
                        }
                        """.formatted("kbm6r" + suffix)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.knowledgeContext.spaceName").value("KB M6 Space " + suffix))
            .andExpect(jsonPath("$.comments[0].anchorType").value("selection"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID commentId = UUID.fromString(objectMapper.readTree(commentResponse).get("comments").get(0).get("id").asText());

        for (int index = 0; index < 4; index++) {
            domainEventWorker.processPendingEvents();
        }
        mockMvc.perform(get("/api/notifications?unreadOnly=true")
                .header("Authorization", "Bearer " + reviewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].notificationType", hasItem("document_comment_mention")))
            .andExpect(jsonPath("$[*].webPath", hasItem("/knowledge-bases/" + spaceId + "?docId=" + documentId + "&commentId=" + commentId)))
            .andExpect(content().string(containsString("KB M6 Space " + suffix)));

        UUID projectId = createProject(adminToken, reviewerId, "M6 Project " + suffix);
        String issueResponse = mockMvc.perform(post("/api/docs/" + documentId + "/issues/from-selection")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "projectId", projectId.toString(),
                    "issueType", "task",
                    "title", "Follow up M6 " + suffix,
                    "priority", "high",
                    "assigneeId", reviewerId.toString(),
                    "dueAt", LocalDate.now().plusDays(5).toString(),
                    "anchorStart", 5,
                    "anchorEnd", 13,
                    "anchorText", "Escalate"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.description", containsString("来源知识库：KB M6 Space " + suffix)))
            .andExpect(jsonPath("$.relations[0].target.objectType").value("document"))
            .andExpect(jsonPath("$.relations[0].target.metadata.knowledgeBaseId").value(spaceId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID issueId = UUID.fromString(objectMapper.readTree(issueResponse).get("issue").get("id").asText());

        mockMvc.perform(get("/api/platform/objects/document/" + documentId + "/summary")
                .header("Authorization", "Bearer " + reviewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata.sourceModule").value("knowledge"))
            .andExpect(jsonPath("$.metadata.knowledgePath", containsString("M6 Collaboration Runbook " + suffix)))
            .andExpect(jsonPath("$.metadata.backReferencePath").value("/knowledge-bases/" + spaceId + "?docId=" + documentId))
            .andExpect(jsonPath("$.webPath").value("/knowledge-bases/" + spaceId + "?docId=" + documentId));

        mockMvc.perform(get("/api/platform/objects/issue/" + issueId + "/summary")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata.sourceModule").value("project"))
            .andExpect(jsonPath("$.metadata.updatedAt").exists())
            .andExpect(jsonPath("$.metadata.backReferencePath").value("/issues/" + issueId + "#relations"));
    }

    @Test
    void knowledgeGovernanceMetricsBulkAuditAndPermissionRiskFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "kb-m7-admin-device-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID disabledUserId = createMember(adminToken, "kbm7d" + suffix, "KB M7 Disabled User");
        UUID maintainerId = createMember(adminToken, "kbm7m" + suffix, "KB M7 Maintainer");

        String createResponse = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "KB M7 Space " + suffix,
                    "code", "kb-m7-" + suffix,
                    "description", "Governance analytics",
                    "visibility", "workspace",
                    "defaultPermissionLevel", "edit"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        UUID spaceId = UUID.fromString(created.get("space").get("id").asText());
        UUID rootDocumentId = UUID.fromString(created.get("space").get("rootDocumentId").asText());

        mockMvc.perform(post("/api/resource-permissions/knowledge_base/" + spaceId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subjectType", "user",
                    "subjectId", disabledUserId.toString(),
                    "permissionLevel", "view"
                ))))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/users/" + disabledUserId + "/disable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        UUID documentId = createDocument(adminToken, rootDocumentId, "M7 Outdated Runbook " + suffix, "markdown", "# M7\nGovern this document");
        mockMvc.perform(patch("/api/docs/" + documentId + "/knowledge-metadata")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "knowledgeStatus", "outdated",
                    "reviewDueAt", LocalDate.now().minusDays(1).toString(),
                    "tags", List.of("legacy")
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.knowledgeStatus").value("outdated"));
        mockMvc.perform(post("/api/platform/objects/document/" + documentId + "/access")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/search?q=NoSuchM7Term" + suffix + "&knowledgeBaseId=" + spaceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/governance")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.health.documentCount", greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.health.outdatedDocumentCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.health.unmaintainedDocumentCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.risks[*].ruleCode", hasItem("document_without_maintainer")))
            .andExpect(jsonPath("$.risks[*].ruleCode", hasItem("document_review_overdue")))
            .andExpect(jsonPath("$.risks[*].ruleCode", hasItem("disabled_subject_permission")))
            .andExpect(jsonPath("$.risks[*].ruleCode", hasItem("knowledge_base_broad_visibility")))
            .andExpect(jsonPath("$.accessStats.visitorCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.accessStats.noResultTerms[*].query", hasItem("NoSuchM7Term" + suffix)));

        mockMvc.perform(get("/api/admin/permission-governance/risks?knowledgeBaseId=" + spaceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].ruleCode", hasItem("disabled_user_active_permission")));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/governance/bulk")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "documentIds", List.of(documentId.toString()),
                    "maintainerId", maintainerId.toString(),
                    "tags", List.of("governed"),
                    "requestReview", true,
                    "reviewDueAt", LocalDate.now().plusDays(3).toString()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCount").value(1))
            .andExpect(jsonPath("$.reviewRequestedCount").value(1));
        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.maintainerId").value(maintainerId.toString()))
            .andExpect(jsonPath("$.document.tags", hasItem("governed")))
            .andExpect(jsonPath("$.document.knowledgeStatus").value("needs_review"));

        mockMvc.perform(post("/api/knowledge-bases/" + spaceId + "/governance/bulk")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "documentIds", List.of(documentId.toString()),
                    "archive", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedCount").value(1));

        mockMvc.perform(get("/api/knowledge-bases/" + spaceId + "/governance/export")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("highRiskPermissionCount")))
            .andExpect(content().string(containsString("disabled_subject_permission")));
        mockMvc.perform(get("/api/admin/audit-logs?action=knowledge_base.governance.bulk_updated&targetType=knowledge_base&targetId=" + spaceId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void documentAcceptanceReportExposesFrozenV1Criteria() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-m50-device-" + UUID.randomUUID());

        mockMvc.perform(get("/api/docs/acceptance/v1")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("document-v1"))
            .andExpect(jsonPath("$.status").value("frozen"))
            .andExpect(jsonPath("$.frozen").value(true))
            .andExpect(jsonPath("$.openP0").value(0))
            .andExpect(jsonPath("$.openP1").value(0))
            .andExpect(jsonPath("$.scenarios.length()").value(10))
            .andExpect(jsonPath("$.gates.length()").value(8))
            .andExpect(jsonPath("$.scenarios[*].key", hasItem("meeting-notes")))
            .andExpect(jsonPath("$.scenarios[*].key", hasItem("workbench")))
            .andExpect(jsonPath("$.gates[*].key", hasItem("concurrent-editing")))
            .andExpect(jsonPath("$.gates[*].key", hasItem("v1-freeze")));
    }

    @Test
    void documentPerformanceMigrationAndCollaborationHealthFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-m49-device-" + UUID.randomUUID());
        StringBuilder content = new StringBuilder("# M49 baseline");
        for (int i = 0; i < 1000; i++) {
            content.append("\n").append("M49 performance block ").append(i);
        }
        String docResponse = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "title", "M49 Performance Baseline",
                    "content", content.toString()
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(docResponse).get("document").get("id").asText());

        mockMvc.perform(get("/api/docs/" + documentId + "/performance")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blockCount").value(1001))
            .andExpect(jsonPath("$.largeDocument").value(true))
            .andExpect(jsonPath("$.recommendedMode").value("lazy-preview"));

        mockMvc.perform(get("/api/docs/" + documentId + "/migration-preview")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentBlockCount").value(1001))
            .andExpect(jsonPath("$.storedBlockCount").value(1001))
            .andExpect(jsonPath("$.blockProjectionCurrent").value(true))
            .andExpect(jsonPath("$.migrationMode").value("verify-existing-blocks"));

        mockMvc.perform(get("/api/docs/" + documentId + "/collaboration/health")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeUsers").value(0))
            .andExpect(jsonPath("$.dirty").value(false));
    }

    @Test
    void documentVersionPermissionRelationAndShareCardFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-admin-device-" + UUID.randomUUID());
        String editorUsername = "dedit" + UUID.randomUUID().toString().substring(0, 8);
        UUID editorId = createMember(adminToken, editorUsername, "Document Editor");
        String editorToken = login(editorUsername, "member123456", "doc-editor-device-" + UUID.randomUUID());
        String viewerUsername = "dview" + UUID.randomUUID().toString().substring(0, 8);
        UUID viewerId = createMember(adminToken, viewerUsername, "Document Viewer");
        String viewerToken = login(viewerUsername, "member123456", "doc-viewer-device-" + UUID.randomUUID());

        String docResponse = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "title": "M5 Design Note",
                          "content": "# M5\\nInitial"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.title").value("M5 Design Note"))
            .andExpect(jsonPath("$.document.currentVersionNo").value(1))
            .andExpect(jsonPath("$.content").value("# M5\nInitial"))
            .andExpect(jsonPath("$.blocks[0].blockType").value("heading"))
            .andExpect(jsonPath("$.blocks[0].content").value("M5"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(docResponse).get("document").get("id").asText());

        mockMvc.perform(post("/api/docs/" + documentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + editorId + "\",\"permissionLevel\":\"edit\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions.length()", greaterThanOrEqualTo(2)));

        mockMvc.perform(post("/api/docs/" + documentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + viewerId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + editorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "baseVersionNo": 1,
                          "title": "M5 Design Note",
                          "content": "# M5\\nUpdated"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(2))
            .andExpect(jsonPath("$.content").value("# M5\nUpdated"))
            .andExpect(jsonPath("$.blocks[1].content").value("Updated"));

        mockMvc.perform(patch("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + editorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "baseVersionNo": 1,
                          "title": "Stale",
                          "content": "stale"
                        }
                        """
                ))
            .andExpect(status().isConflict());

        UUID issueId = createIssue(adminToken, editorId);
        mockMvc.perform(post("/api/docs/" + documentId + "/relations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"issue\",\"targetId\":\"" + issueId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[0].targetType").value("issue"))
            .andExpect(jsonPath("$.relations[0].title", not(blankOrNullString())));

        mockMvc.perform(get("/api/platform/objects/document/" + documentId + "/summary")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("available"))
            .andExpect(jsonPath("$.title").value("M5 Design Note"));

        UUID conversationId = createConversation(adminToken, viewerId);
        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "clientMessageId": "%s",
                          "messageType": "text",
                          "content": "请看 /docs/%s"
                        }
                        """.formatted(UUID.randomUUID(), documentId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.links[0].summary.title").value("M5 Design Note"));

        String mentionCommentResponse = mockMvc.perform(post("/api/docs/" + documentId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "anchorType": "selection",
                          "anchorStart": 3,
                          "anchorEnd": 5,
                          "anchorText": "M5",
                          "anchorPrefix": "# ",
                          "anchorSuffix": "\\nUpdated",
                          "content": "please review @%s"
                        }
                        """.formatted(editorUsername)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].content").value("please review @" + editorUsername))
            .andExpect(jsonPath("$.comments[0].anchorType").value("selection"))
            .andExpect(jsonPath("$.comments[0].anchorText").value("M5"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID mentionCommentId = UUID.fromString(objectMapper.readTree(mentionCommentResponse).get("comments").get(0).get("id").asText());

        for (int index = 0; index < 4; index++) {
            domainEventWorker.processPendingEvents();
        }
        mockMvc.perform(get("/api/notifications?unreadOnly=true")
                .header("Authorization", "Bearer " + editorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[*].webPath", hasItem("/docs/" + documentId + "?commentId=" + mentionCommentId)));

        mockMvc.perform(post("/api/docs/" + documentId + "/versions/1/restore")
                .header("Authorization", "Bearer " + editorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(3))
            .andExpect(jsonPath("$.content").value("# M5\nInitial"));
    }

    @Test
    void documentBlocksCanBeReadAndSaved() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-block-admin-device-" + UUID.randomUUID());

        String docResponse = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Block Doc\",\"content\":\"# Plan\\nFirst step\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocks.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(docResponse).get("document").get("id").asText());

        mockMvc.perform(get("/api/docs/" + documentId + "/blocks")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].blockType").value("heading"))
            .andExpect(jsonPath("$[1].content").value("First step"));

        String blocksResponse = mockMvc.perform(patch("/api/docs/" + documentId + "/blocks")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "baseVersionNo": 1,
                          "blocks": [
                            {"blockType": "heading", "content": "Block Plan"},
                            {"blockType": "task", "content": "Ship API"}
                          ]
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(2))
            .andExpect(jsonPath("$.content").value("# Block Plan\n- [ ] Ship API"))
            .andExpect(jsonPath("$.blocks[1].blockType").value("task"))
            .andExpect(jsonPath("$.blocks[1].sortOrder").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode blocksJson = objectMapper.readTree(blocksResponse);
        UUID taskBlockId = UUID.fromString(blocksJson.get("blocks").get(1).get("id").asText());

        String commentResponse = mockMvc.perform(post("/api/docs/" + documentId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "blockId": "%s",
                          "content": "This block needs a clearer owner"
                        }
                        """.formatted(taskBlockId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].blockId").value(taskBlockId.toString()))
            .andExpect(jsonPath("$.comments[0].resolved").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID commentId = UUID.fromString(objectMapper.readTree(commentResponse).get("comments").get(0).get("id").asText());

        mockMvc.perform(post("/api/docs/" + documentId + "/comments/" + commentId + "/replies")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Owner added in the table\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].threadId").value(commentId.toString()))
            .andExpect(jsonPath("$.comments[0].root").value(true))
            .andExpect(jsonPath("$.comments[0].replies[0].content").value("Owner added in the table"))
            .andExpect(jsonPath("$.comments[0].replies[0].root").value(false));

        mockMvc.perform(post("/api/docs/" + documentId + "/comments/" + commentId + "/resolve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].resolved").value(true))
            .andExpect(jsonPath("$.comments[0].replies[0].resolved").value(true))
            .andExpect(jsonPath("$.comments[0].resolvedByName").value("Administrator"));

        mockMvc.perform(post("/api/docs/" + documentId + "/comments/" + commentId + "/reopen")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].resolved").value(false))
            .andExpect(jsonPath("$.comments[0].reopenedByName").value("Administrator"));
    }

    @Test
    void selectionCommentAnchorRebasesAcrossEditsAndKeepsThreadDeepLink() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-m45-admin-device-" + UUID.randomUUID());
        String reviewerUsername = "dm45" + UUID.randomUUID().toString().substring(0, 8);
        UUID reviewerId = createMember(adminToken, reviewerUsername, "Document M45 Reviewer");
        String reviewerToken = login(reviewerUsername, "member123456", "doc-m45-reviewer-device-" + UUID.randomUUID());

        String docResponse = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M45 Anchor Doc\",\"content\":\"Alpha target Beta\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(docResponse).get("document").get("id").asText());

        mockMvc.perform(post("/api/docs/" + documentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + reviewerId + "\",\"permissionLevel\":\"comment\"}"))
            .andExpect(status().isOk());

        String commentResponse = mockMvc.perform(post("/api/docs/" + documentId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "anchorType": "selection",
                          "anchorStart": 6,
                          "anchorEnd": 12,
                          "anchorText": "target",
                          "anchorPrefix": "Alpha ",
                          "anchorSuffix": " Beta",
                          "content": "请复核 @%s"
                        }
                        """.formatted(reviewerUsername)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].anchorType").value("selection"))
            .andExpect(jsonPath("$.comments[0].anchorStart").value(6))
            .andExpect(jsonPath("$.comments[0].anchorEnd").value(12))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID commentId = UUID.fromString(objectMapper.readTree(commentResponse).get("comments").get(0).get("id").asText());

        mockMvc.perform(post("/api/docs/" + documentId + "/comments/" + commentId + "/replies")
                .header("Authorization", "Bearer " + reviewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"已看到选区评论\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].replies[0].content").value("已看到选区评论"));

        mockMvc.perform(post("/api/docs/" + documentId + "/comments/" + commentId + "/resolve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].resolved").value(true));

        mockMvc.perform(post("/api/docs/" + documentId + "/comments/" + commentId + "/reopen")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].resolved").value(false));

        for (int index = 0; index < 4; index++) {
            domainEventWorker.processPendingEvents();
        }
        mockMvc.perform(get("/api/notifications?unreadOnly=true")
                .header("Authorization", "Bearer " + reviewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].webPath", hasItem("/docs/" + documentId + "?commentId=" + commentId)));

        mockMvc.perform(patch("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "baseVersionNo": 1,
                          "title": "M45 Anchor Doc",
                          "content": "Inserted context\\nAlpha target Beta"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].anchorStart").value(23))
            .andExpect(jsonPath("$.comments[0].anchorEnd").value(29))
            .andExpect(jsonPath("$.comments[0].replies[0].anchorStart").value(23))
            .andExpect(jsonPath("$.comments[0].replies[0].anchorEnd").value(29));
    }

    @Test
    void documentCheckpointCreatesVersionWithoutBaseVersionNo() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-checkpoint-admin-device-" + UUID.randomUUID());

        String docResponse = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Checkpoint Doc\",\"content\":\"checkpoint source\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(docResponse).get("document").get("id").asText());

        mockMvc.perform(post("/api/docs/" + documentId + "/versions/checkpoint")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(2))
            .andExpect(jsonPath("$.content").value("checkpoint source"))
            .andExpect(jsonPath("$.blocks[0].content").value("checkpoint source"));

        mockMvc.perform(get("/api/docs/" + documentId + "/versions")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].versionNo").value(2))
            .andExpect(jsonPath("$[0].content").value("checkpoint source"))
            .andExpect(jsonPath("$[1].versionNo").value(1));
    }

    @Test
    void documentEnhancedBlocksHydratePlatformObjectsAndSurviveRestore() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-embed-admin-device-" + UUID.randomUUID());
        String teammateUsername = "dembed" + UUID.randomUUID().toString().substring(0, 8);
        UUID teammateId = createMember(adminToken, teammateUsername, "Document Embed Teammate");
        String outsiderUsername = "dembedout" + UUID.randomUUID().toString().substring(0, 6);
        UUID outsiderId = createMember(adminToken, outsiderUsername, "Document Embed Outsider");
        String outsiderToken = login(outsiderUsername, "member123456", "doc-embed-outsider-device-" + UUID.randomUUID());

        JsonNode base = createBase(adminToken, "M33 数据看板", "Document embed base");
        UUID baseId = UUID.fromString(base.get("base").get("id").asText());
        JsonNode table = createBaseTable(adminToken, baseId, "M33 看板视图");
        UUID tableId = UUID.fromString(table.get("table").get("id").asText());
        UUID issueId = createIssue(adminToken, teammateId);
        UUID conversationId = createConversation(adminToken, teammateId);
        UUID messageId = createMessage(adminToken, conversationId, "M33 嵌入消息上下文");

        String docResponse = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M33 Embed Doc\",\"content\":\"M33\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(docResponse).get("document").get("id").asText());

        mockMvc.perform(post("/api/docs/" + documentId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + outsiderId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk());

        String tableContent = objectMapper.writeValueAsString(Map.of(
            "columns", List.of("指标", "状态"),
            "rows", List.of(List.of("北区漏斗", "进行中"))
        ));
        String baseViewContent = objectMapper.writeValueAsString(Map.of("objectId", tableId.toString(), "viewId", "m33-kanban"));
        String issueContent = objectMapper.writeValueAsString(Map.of("objectId", issueId.toString()));
        String messageContent = objectMapper.writeValueAsString(Map.of("objectId", messageId.toString()));

        String blocksResponse = mockMvc.perform(patch("/api/docs/" + documentId + "/blocks")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "baseVersionNo", 1,
                    "blocks", List.of(
                        Map.of("blockType", "heading", "content", "M33 嵌入块"),
                        Map.of("blockType", "table", "content", tableContent),
                        Map.of("blockType", "base_view", "content", baseViewContent),
                        Map.of("blockType", "issue_embed", "content", issueContent),
                        Map.of("blockType", "message_embed", "content", messageContent)
                    )
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(2))
            .andExpect(jsonPath("$.blocks[1].blockType").value("table"))
            .andExpect(jsonPath("$.blocks[1].metadata.columns[0]").value("指标"))
            .andExpect(jsonPath("$.blocks[2].blockType").value("base_view"))
            .andExpect(jsonPath("$.blocks[2].metadata.objectType").value("base_table"))
            .andExpect(jsonPath("$.blocks[2].metadata.viewId").value("m33-kanban"))
            .andExpect(jsonPath("$.blocks[2].embedSummary.objectType").value("base_table"))
            .andExpect(jsonPath("$.blocks[2].embedSummary.title").value("M33 看板视图"))
            .andExpect(jsonPath("$.blocks[3].embedSummary.objectType").value("issue"))
            .andExpect(jsonPath("$.blocks[3].embedSummary.title", containsString("Document related task")))
            .andExpect(jsonPath("$.blocks[4].embedSummary.objectType").value("message"))
            .andExpect(jsonPath("$.blocks[4].embedSummary.subtitle").value("M33 嵌入消息上下文"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        UUID tableBlockId = UUID.fromString(objectMapper.readTree(blocksResponse).get("blocks").get(1).get("id").asText());
        mockMvc.perform(post("/api/docs/" + documentId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("blockId", tableBlockId.toString(), "content", "表格块口径需要复核"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].blockId").value(tableBlockId.toString()));

        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocks[2].embedSummary.accessState").value("forbidden"))
            .andExpect(jsonPath("$.blocks[3].embedSummary.accessState").value("forbidden"))
            .andExpect(jsonPath("$.blocks[4].embedSummary.accessState").value("not_found"));

        mockMvc.perform(post("/api/docs/" + documentId + "/versions/2/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(3))
            .andExpect(jsonPath("$.blocks[1].blockType").value("table"))
            .andExpect(jsonPath("$.blocks[2].blockType").value("base_view"))
            .andExpect(jsonPath("$.blocks[2].embedSummary.title").value("M33 看板视图"));
    }

    @Test
    void rejectsDocumentAccessForUnauthorizedUser() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-owner-device-" + UUID.randomUUID());
        String outsiderUsername = "dout" + UUID.randomUUID().toString().substring(0, 8);
        createMember(adminToken, outsiderUsername, "Document Outsider");
        String outsiderToken = login(outsiderUsername, "member123456", "doc-outsider-device-" + UUID.randomUUID());

        String docResponse = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Private Doc\",\"content\":\"secret\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(docResponse).get("document").get("id").asText());

        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/objects/document/" + documentId + "/summary")
                .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("forbidden"));
    }

    @Test
    void documentSharingPermissionRequestAndKnowledgeBaseFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-m46-admin-device-" + UUID.randomUUID());
        String commenterUsername = "dcomment" + UUID.randomUUID().toString().substring(0, 8);
        UUID commenterId = createMember(adminToken, commenterUsername, "Document Commenter");
        String commenterToken = login(commenterUsername, "member123456", "doc-m46-commenter-device-" + UUID.randomUUID());
        String linkUserUsername = "dlink" + UUID.randomUUID().toString().substring(0, 8);
        createMember(adminToken, linkUserUsername, "Document Link User");
        String linkUserToken = login(linkUserUsername, "member123456", "doc-m46-link-device-" + UUID.randomUUID());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID spaceId = createDocument(adminToken, null, "M46 KB " + suffix, "space", "Knowledge base");
        mockMvc.perform(post("/api/docs/" + spaceId + "/knowledge-base")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "description": "M46 knowledge base entry",
                          "coverUrl": "https://example.test/cover.png",
                          "defaultPermissionLevel": "comment",
                          "knowledgeBase": true
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.knowledgeBase").value(true))
            .andExpect(jsonPath("$.document.defaultPermissionLevel").value("comment"))
            .andExpect(jsonPath("$.document.description").value("M46 knowledge base entry"));

        mockMvc.perform(post("/api/docs/" + spaceId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + commenterId + "\",\"permissionLevel\":\"comment\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit-logs?action=permission.granted&targetType=document&targetId=" + spaceId + "&limit=5")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("permission.granted")));

        UUID documentId = createDocument(adminToken, spaceId, "M46 Shared Doc " + suffix, "markdown", "M46 draft");
        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.permissionLevel").value("owner"))
            .andExpect(jsonPath("$.permissions[*].sourceType", hasItem("inherited")))
            .andExpect(jsonPath("$.permissions[*].sourceTitle", hasItem("M46 KB " + suffix)));

        mockMvc.perform(post("/api/docs/" + documentId + "/comments")
                .header("Authorization", "Bearer " + commenterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Comment-only users can review\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].content").value("Comment-only users can review"));

        mockMvc.perform(patch("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + commenterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseVersionNo\":1,\"title\":\"Denied\",\"content\":\"Denied\"}"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + linkUserToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/docs/" + documentId + "/share-link")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"workspace\",\"permissionLevel\":\"edit\",\"enabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.permissionLevel").value("edit"))
            .andExpect(jsonPath("$.token", not(blankOrNullString())));

        mockMvc.perform(get("/api/admin/audit-logs?action=document.share_link.updated&targetType=document&targetId=" + documentId + "&limit=5")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("document.share_link.updated")));

        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + linkUserToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.permissionLevel").value("edit"));

        mockMvc.perform(get("/api/admin/audit-logs?action=document.share_link.accessed&targetType=document&targetId=" + documentId + "&limit=5")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("document.share_link.accessed")));

        mockMvc.perform(patch("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + linkUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseVersionNo\":1,\"title\":\"M46 Shared Doc Edited\",\"content\":\"Edited through org link\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(2))
            .andExpect(jsonPath("$.content").value("Edited through org link"));

        mockMvc.perform(post("/api/docs/" + documentId + "/share-link/disable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/admin/audit-logs?action=document.share_link.disabled&targetType=document&targetId=" + documentId + "&limit=5")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("document.share_link.disabled")));

        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + linkUserToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/docs/" + documentId + "/permission-requests")
                .header("Authorization", "Bearer " + linkUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissionLevel\":\"view\",\"reason\":\"Need to review M46\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("submitted"))
            .andExpect(jsonPath("$.notifiedCount", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/admin/audit-logs?action=document.permission.requested&targetType=document&targetId=" + documentId + "&limit=5")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("document.permission.requested")));

        for (int index = 0; index < 3; index++) {
            domainEventWorker.processPendingEvents();
        }
        mockMvc.perform(get("/api/notifications?unreadOnly=true")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].notificationType", hasItem("document_permission_request")))
            .andExpect(jsonPath("$[*].webPath", hasItem("/docs/" + documentId)));
    }

    @Test
    void documentTemplatesNamedVersionsImportAndBlockSearchFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-m47-admin-device-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(get("/api/docs/templates")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].title", hasItem("会议纪要")))
            .andExpect(jsonPath("$[*].category", hasItem("meeting")));

        String createdResponse = mockMvc.perform(post("/api/docs/from-template")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "templateId": "00000000-0000-0000-0000-000000004701",
                          "title": "M47 Template Doc %s"
                        }
                        """.formatted(suffix)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.title").value("M47 Template Doc " + suffix))
            .andExpect(jsonPath("$.content", containsString("会议纪要")))
            .andExpect(jsonPath("$.blocks[*].blockType", hasItem("heading")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(createdResponse).get("document").get("id").asText());

        mockMvc.perform(post("/api/docs/" + documentId + "/versions/named")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionName\":\"M47 Baseline\",\"summary\":\"Template baseline\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(2));

        String uniqueSearchTerm = "UniqueM47Search" + suffix;
        mockMvc.perform(post("/api/docs/" + documentId + "/import/markdown")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "title", "M47 Imported " + suffix,
                    "content", "# Imported\n" + uniqueSearchTerm + "\n- [ ] Verify imported task"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(3))
            .andExpect(jsonPath("$.document.title").value("M47 Imported " + suffix))
            .andExpect(jsonPath("$.blocks[*].content", hasItem(uniqueSearchTerm)));

        mockMvc.perform(get("/api/docs/" + documentId + "/versions")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].versionType").value("import"))
            .andExpect(jsonPath("$[0].versionName").value("Markdown 导入"))
            .andExpect(jsonPath("$[1].versionType").value("named"))
            .andExpect(jsonPath("$[1].versionName").value("M47 Baseline"));

        mockMvc.perform(get("/api/docs/" + documentId + "/versions/diff?fromVersionNo=2&toVersionNo=3")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lines[*].scope", hasItem("block")))
            .andExpect(jsonPath("$.lines[*].blockType", hasItem("heading")))
            .andExpect(jsonPath("$.lines[*].content", hasItem(uniqueSearchTerm)));

        mockMvc.perform(post("/api/docs/" + documentId + "/versions/2/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.currentVersionNo").value(4))
            .andExpect(jsonPath("$.content", containsString("会议纪要")));

        mockMvc.perform(get("/api/docs/" + documentId + "/versions")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].versionType").value("restore"))
            .andExpect(jsonPath("$[0].sourceVersionNo").value(2));

        mockMvc.perform(post("/api/docs/" + documentId + "/import/markdown")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", "# Searchable\n" + uniqueSearchTerm))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/docs/" + documentId + "/export/markdown")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("# Searchable")))
            .andExpect(content().string(containsString(uniqueSearchTerm)));

        mockMvc.perform(get("/api/docs/" + documentId + "/export/html")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<h1>Searchable</h1>")))
            .andExpect(content().string(containsString(uniqueSearchTerm)));

        mockMvc.perform(post("/api/search/reindex")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/search?q=" + uniqueSearchTerm)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].webPath", hasItem(containsString("/docs/" + documentId))));
    }

    @Test
    void crossModuleMessageDocumentIssueAndReverseReferenceFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-m48-admin-device-" + UUID.randomUUID());
        String teammateUsername = "dm48" + UUID.randomUUID().toString().substring(0, 8);
        UUID teammateId = createMember(adminToken, teammateUsername, "Document M48 Teammate");
        UUID conversationId = createConversation(adminToken, teammateId);
        UUID messageId = createMessage(adminToken, conversationId, "M48 customer escalation needs a project plan");

        String convertedDocumentResponse = mockMvc.perform(post("/api/conversations/" + conversationId + "/messages/" + messageId + "/convert-to-document")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"M48 Message Note\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.title").value("M48 Message Note"))
            .andExpect(jsonPath("$.content", containsString("/im?conversationId=" + conversationId + "&messageId=" + messageId)))
            .andExpect(jsonPath("$.relations[*].targetType", hasItem("message")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(convertedDocumentResponse).get("document").get("id").asText());

        UUID projectId = createProject(adminToken, teammateId, "M48 Cross Module " + UUID.randomUUID().toString().substring(0, 6));
        String issueResponse = mockMvc.perform(post("/api/docs/" + documentId + "/issues/from-selection")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "projectId", projectId.toString(),
                    "issueType", "task",
                    "title", "Follow up M48 escalation",
                    "priority", "medium",
                    "anchorStart", 1,
                    "anchorEnd", 24,
                    "anchorText", "customer escalation"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issue.title").value("Follow up M48 escalation"))
            .andExpect(jsonPath("$.issue.description", containsString("customer escalation")))
            .andExpect(jsonPath("$.relations[*].targetType", hasItem("document")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID issueId = UUID.fromString(objectMapper.readTree(issueResponse).get("issue").get("id").asText());

        mockMvc.perform(get("/api/docs/" + documentId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[*].targetType", hasItem("issue")))
            .andExpect(jsonPath("$.relations[*].targetId", hasItem(issueId.toString())));

        JsonNode base = createBase(adminToken, "M48 Base " + UUID.randomUUID().toString().substring(0, 6), "M48 search closure base");
        UUID baseId = UUID.fromString(base.get("base").get("id").asText());
        JsonNode table = createBaseTable(adminToken, baseId, "M48 Search Table");
        UUID tableId = UUID.fromString(table.get("table").get("id").asText());
        mockMvc.perform(post("/api/docs/" + documentId + "/relations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"base_table\",\"targetId\":\"" + tableId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.relations[*].targetType", hasItem("base_table")));

        String blockSearchTerm = "M48BlockSearch" + UUID.randomUUID().toString().substring(0, 8);
        String blockResponse = mockMvc.perform(patch("/api/docs/" + documentId + "/blocks")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "baseVersionNo", 1,
                    "blocks", List.of(
                        Map.of("blockType", "heading", "content", "M48 Search Closure"),
                        Map.of("blockType", "paragraph", "content", blockSearchTerm)
                    )
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID blockId = UUID.fromString(objectMapper.readTree(blockResponse).get("blocks").get(1).get("id").asText());

        String commentSearchTerm = "M48CommentSearch" + UUID.randomUUID().toString().substring(0, 8);
        String commentResponse = mockMvc.perform(post("/api/docs/" + documentId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("content", "Searchable comment " + commentSearchTerm))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID commentId = UUID.fromString(objectMapper.readTree(commentResponse).get("comments").get(0).get("id").asText());

        mockMvc.perform(post("/api/search/reindex")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/search?q=" + blockSearchTerm)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].webPath", hasItem("/docs/" + documentId + "#doc-block-" + blockId)));
        mockMvc.perform(get("/api/search?q=" + commentSearchTerm)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].webPath", hasItem("/docs/" + documentId + "?commentId=" + commentId)));
    }

    @Test
    void documentTreeMoveArchiveRestoreAndInheritedVisibilityFlow() throws Exception {
        String adminToken = login("admin", "admin123456", "doc-tree-admin-device-" + UUID.randomUUID());
        String viewerUsername = "dtree" + UUID.randomUUID().toString().substring(0, 8);
        UUID viewerId = createMember(adminToken, viewerUsername, "Document Tree Viewer");
        String viewerToken = login(viewerUsername, "member123456", "doc-tree-viewer-device-" + UUID.randomUUID());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID rootId = createDocument(adminToken, null, "M32 Space " + suffix, "space", "Root space");
        mockMvc.perform(post("/api/docs/" + rootId + "/permissions")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + viewerId + "\",\"permissionLevel\":\"view\"}"))
            .andExpect(status().isOk());

        UUID folderId = createDocument(adminToken, rootId, "Specs " + suffix, "folder", "");
        UUID docAId = createDocument(adminToken, folderId, "PRD " + suffix, "markdown", "A");
        UUID docBId = createDocument(adminToken, folderId, "QA " + suffix, "markdown", "B");

        mockMvc.perform(get("/api/docs/" + docAId + "/path")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(rootId.toString()))
            .andExpect(jsonPath("$[1].id").value(folderId.toString()))
            .andExpect(jsonPath("$[2].id").value(docAId.toString()));

        mockMvc.perform(get("/api/docs/tree?includeArchived=true")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$..document.id", hasItem(rootId.toString())))
            .andExpect(jsonPath("$..document.id", hasItem(folderId.toString())))
            .andExpect(jsonPath("$..document.id", hasItem(docAId.toString())))
            .andExpect(jsonPath("$..path", hasItem("M32 Space " + suffix + " / Specs " + suffix + " / PRD " + suffix)));

        mockMvc.perform(get("/api/docs/tree")
                .header("Authorization", "Bearer " + viewerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$..document.id", hasItem(folderId.toString())))
            .andExpect(jsonPath("$..document.id", hasItem(docAId.toString())));

        mockMvc.perform(post("/api/docs/" + docAId + "/move")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":\"" + rootId + "\",\"sortOrder\":-10}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.parentId").value(rootId.toString()))
            .andExpect(jsonPath("$.document.sortOrder").value(-10));

        mockMvc.perform(get("/api/docs/" + docAId + "/path")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(rootId.toString()))
            .andExpect(jsonPath("$[1].id").value(docAId.toString()));

        mockMvc.perform(post("/api/docs/" + rootId + "/move")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":\"" + docAId + "\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/docs/" + folderId + "/archive")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.archived").value(true));

        mockMvc.perform(get("/api/docs/tree")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$..document.id", not(hasItem(folderId.toString()))))
            .andExpect(jsonPath("$..document.id", not(hasItem(docBId.toString()))));

        mockMvc.perform(get("/api/docs/" + docBId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.archived").value(true));

        mockMvc.perform(post("/api/docs/" + folderId + "/restore")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.archived").value(false));

        mockMvc.perform(get("/api/docs/tree")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$..document.id", hasItem(folderId.toString())))
            .andExpect(jsonPath("$..document.id", hasItem(docBId.toString())));
    }

    private UUID createIssue(String token, UUID assigneeId) throws Exception {
        String projectKey = projectKey("DOC");
        String projectResponse = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "Doc Project",
                          "memberIds": ["%s"]
                        }
                        """.formatted(projectKey, assigneeId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID projectId = UUID.fromString(objectMapper.readTree(projectResponse).get("id").asText());

        String issueResponse = mockMvc.perform(post("/api/projects/" + projectId + "/issues")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "issueType": "task",
                          "title": "Document related task",
                          "priority": "medium",
                          "assigneeId": "%s"
                        }
                        """.formatted(assigneeId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(issueResponse).get("issue").get("id").asText());
    }

    private UUID createProject(String token, UUID memberId, String name) throws Exception {
        String projectResponse = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "projectKey": "%s",
                          "name": "%s",
                          "memberIds": ["%s"]
                        }
                        """.formatted(projectKey("M48"), name, memberId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(projectResponse).get("id").asText());
    }

    private UUID createConversation(String token, UUID memberId) throws Exception {
        String response = mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "conversationType": "group",
                          "title": "Document Review",
                          "memberIds": ["%s"]
                        }
                        """.formatted(memberId)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createMessage(String token, UUID conversationId, String content) throws Exception {
        String response = mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "clientMessageId", UUID.randomUUID().toString(),
                    "messageType", "text",
                    "content", content
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private JsonNode createBase(String token, String name, String description) throws Exception {
        String response = mockMvc.perform(post("/api/bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "description", description))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.base.name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode createBaseTable(String token, UUID baseId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/bases/" + baseId + "/tables")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.table.name").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createDocument(String token, UUID parentId, String title, String docType, String content) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        if (parentId != null) {
            request.put("parentId", parentId.toString());
        }
        request.put("title", title);
        request.put("docType", docType);
        request.put("content", content);
        String response = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("document").get("id").asText());
    }

    private JsonNode createKnowledgeBaseSpace(String token, String name, String code) throws Exception {
        String response = mockMvc.perform(post("/api/knowledge-bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", name,
                    "code", code,
                    "description", "Knowledge base API semantic test",
                    "visibility", "private",
                    "defaultPermissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.space.code").value(code))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createKnowledgeBaseDocument(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/docs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "docType", "space",
                    "content", "Legacy root",
                    "knowledgeBase", true,
                    "defaultPermissionLevel", "view"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("document").get("id").asText());
    }

    private UUID createMember(String token, String username, String displayName) throws Exception {
        String response = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "%s",
                          "password": "member123456",
                          "displayName": "%s",
                          "email": "%s@example.com",
                          "roleCode": "member"
                        }
                        """.formatted(username, displayName, username)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String login(String username, String password, String deviceFingerprint) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "%s",
                          "password": "%s",
                          "deviceType": "web",
                          "deviceFingerprint": "%s",
                          "deviceName": "MockMvc",
                          "appVersion": "test"
                        }
                        """.formatted(username, password, deviceFingerprint)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String projectKey(String prefix) {
        return (prefix + UUID.randomUUID().toString().replace("-", ""))
            .substring(0, 10)
            .toUpperCase(Locale.ROOT);
    }
}
