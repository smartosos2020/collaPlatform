package com.colla.platform.modules.doc.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.colla.platform.modules.event.application.DomainEventWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

        mockMvc.perform(post("/api/docs/" + documentId + "/comments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"please review @" + editorUsername + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].content").value("please review @" + editorUsername));

        domainEventWorker.processPendingEvents();
        mockMvc.perform(get("/api/notifications?unreadOnly=true")
                .header("Authorization", "Bearer " + editorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)));

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

        mockMvc.perform(post("/api/docs/" + documentId + "/comments/" + commentId + "/resolve")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments[0].resolved").value(true))
            .andExpect(jsonPath("$.comments[0].resolvedByName").value("Administrator"));
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
