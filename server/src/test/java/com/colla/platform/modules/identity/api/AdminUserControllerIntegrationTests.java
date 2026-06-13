package com.colla.platform.modules.identity.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanManageMembers() throws Exception {
        String adminToken = login("admin", "admin123456", "admin-device-" + UUID.randomUUID());
        String username = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String initialCredential = "member123456";
        String rotatedCredential = "member654321";

        String createResponse = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "%s",
                          "password": "%s",
                          "displayName": "Member User",
                          "email": "%s@colla.local",
                          "roleCode": "member"
                        }
                        """.formatted(username, initialCredential, username)
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.roles", hasItem("member")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode member = objectMapper.readTree(createResponse);
        String memberId = member.get("id").asText();

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].username", hasItem(username)));

        mockMvc.perform(post("/api/admin/users/" + memberId + "/disable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(username, initialCredential, "disabled-device")))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/users/" + memberId + "/enable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/" + memberId + "/password")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"" + rotatedCredential + "\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(username, rotatedCredential, "reset-device")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())));
    }

    @Test
    void memberCannotManageUsers() throws Exception {
        String adminToken = login("admin", "admin123456", "admin-device-" + UUID.randomUUID());
        String username = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String memberCredential = "member123456";

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "%s",
                          "password": "%s",
                          "displayName": "Member User",
                          "email": "%s@colla.local",
                          "roleCode": "member"
                        }
                        """.formatted(username, memberCredential, username)
                ))
            .andExpect(status().isOk());

        String memberToken = login(username, memberCredential, "member-device-" + UUID.randomUUID());

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminUserEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "unauthenticated-member",
                          "password": "member123456",
                          "displayName": "Unauthenticated Member",
                          "roleCode": "member"
                        }
                        """
                ))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotCreateDuplicateUsernameOrDisableSelf() throws Exception {
        String adminToken = login("admin", "admin123456", "admin-device-" + UUID.randomUUID());
        String adminProfile = mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String adminId = objectMapper.readTree(adminProfile).get("id").asText();

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "admin",
                          "password": "member123456",
                          "displayName": "Duplicate Admin",
                          "roleCode": "member"
                        }
                        """
                ))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/users/" + adminId + "/disable")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminUserRequestsValidatePayloads() throws Exception {
        String adminToken = login("admin", "admin123456", "admin-device-" + UUID.randomUUID());

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "",
                          "password": "short",
                          "displayName": "",
                          "email": "not-an-email",
                          "roleCode": "member"
                        }
                        """
                ))
            .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/users/" + UUID.randomUUID() + "/password")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"short\"}"))
            .andExpect(status().isBadRequest());
    }

    private String login(String username, String password, String deviceFingerprint) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(username, password, deviceFingerprint)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String loginBody(String username, String password, String deviceFingerprint) {
        return """
            {
              "username": "%s",
              "password": "%s",
              "deviceType": "web",
              "deviceFingerprint": "%s",
              "deviceName": "MockMvc",
              "appVersion": "test"
            }
            """.formatted(username, password, deviceFingerprint);
    }
}
