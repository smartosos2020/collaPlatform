package com.colla.platform.modules.admin.api;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class AdminApplicationGovernanceControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanReadApplicationGovernanceModules() throws Exception {
        String adminToken = login("admin", "admin123456", "app-governance-admin-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/application-governance")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modules[*].key", hasItems("base", "project", "message", "approval")))
            .andExpect(jsonPath("$.modules[?(@.key == 'base')].adminRoute").value(hasItems("/admin/app-governance?module=base")))
            .andExpect(jsonPath("$.modules[?(@.key == 'project')].policies[0]").exists())
            .andExpect(jsonPath("$.modules[?(@.key == 'message')].risks[0].severity").exists())
            .andExpect(jsonPath("$.modules[?(@.key == 'approval')].boundaryRules[0]").exists());
    }

    @Test
    void regularMemberCannotReadApplicationGovernance() throws Exception {
        String adminToken = login("admin", "admin123456", "app-governance-member-admin-" + UUID.randomUUID());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String username = "appgov" + suffix;
        createMember(adminToken, username, "App Governance Member " + suffix);
        String memberToken = login(username, "member123456", "app-governance-member-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/application-governance")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }

    private void createMember(String token, String username, String displayName) throws Exception {
        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username,
                    "password", "member123456",
                    "displayName", displayName,
                    "email", username + "@colla.local",
                    "roleCode", "member"
                ))))
            .andExpect(status().isOk());
    }

    private String login(String username, String password, String deviceFingerprint) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", username,
                    "password", password,
                    "deviceType", "web",
                    "deviceFingerprint", deviceFingerprint,
                    "deviceName", "JUnit",
                    "appVersion", "test"
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
