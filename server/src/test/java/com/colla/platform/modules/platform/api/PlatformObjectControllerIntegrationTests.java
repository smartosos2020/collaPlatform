package com.colla.platform.modules.platform.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class PlatformObjectControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void resolvesObjectSummaryAndInternalLinks() throws Exception {
        String token = login("admin", "admin123456", "platform-object-device-" + UUID.randomUUID());
        UUID workspaceId = currentWorkspaceId();
        UUID issueId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into object_links
                    (id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, created_at, updated_at)
                values (?, ?, 'issue', ?, ?, ?, 'Login fails after submit', now(), now())
                """,
            UUID.randomUUID(),
            workspaceId,
            issueId,
            "/issues/" + issueId,
            "colla://issue/" + issueId
        );

        mockMvc.perform(get("/api/platform/objects/issue/" + issueId + "/summary")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("available"))
            .andExpect(jsonPath("$.title").value("Login fails after submit"))
            .andExpect(jsonPath("$.webPath").value("/issues/" + issueId));

        mockMvc.perform(post("/api/platform/links/resolve")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\":\"/issues/" + issueId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resolved").value(true))
            .andExpect(jsonPath("$.summary.title").value("Login fails after submit"));

        mockMvc.perform(get("/api/platform/objects/unknown/" + UUID.randomUUID() + "/summary")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessState").value("invalid"));
    }

    @Test
    void fileDownloadRequiresOwnerOrTargetObjectAccess() throws Exception {
        String adminToken = login("admin", "admin123456", "file-admin-device-" + UUID.randomUUID());
        UUID workspaceId = currentWorkspaceId();
        UUID issueId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into object_links
                    (id, workspace_id, object_type, object_id, web_path, deep_link, title_snapshot, created_at, updated_at)
                values (?, ?, 'issue', ?, ?, ?, 'Attachment target', now(), now())
                """,
            UUID.randomUUID(),
            workspaceId,
            issueId,
            "/issues/" + issueId,
            "colla://issue/" + issueId
        );

        String uploadResponse = mockMvc.perform(post("/api/files/upload-url")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "fileName": "evidence.txt",
                          "contentType": "text/plain",
                          "sizeBytes": 32,
                          "targetType": "issue",
                          "targetId": "%s"
                        }
                        """.formatted(issueId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadUrl", not(blankOrNullString())))
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID fileId = UUID.fromString(objectMapper.readTree(uploadResponse).get("uploadId").asText());

        mockMvc.perform(post("/api/files/complete")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "fileId": "%s",
                          "targetType": "issue",
                          "targetId": "%s"
                        }
                        """.formatted(fileId, issueId)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("completed"));

        mockMvc.perform(get("/api/files/" + fileId + "/download-url")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.downloadUrl", not(blankOrNullString())));

        mockMvc.perform(post("/api/files/upload-url")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "fileName": "forbidden.txt",
                          "contentType": "text/plain",
                          "sizeBytes": 32,
                          "targetType": "issue",
                          "targetId": "%s"
                        }
                        """.formatted(UUID.randomUUID())
                ))
            .andExpect(status().isForbidden());
    }

    private UUID currentWorkspaceId() {
        return jdbcTemplate.queryForObject("select id from workspaces where slug = 'default'", UUID.class);
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
}
