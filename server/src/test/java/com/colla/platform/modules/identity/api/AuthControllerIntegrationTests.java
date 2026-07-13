package com.colla.platform.modules.identity.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allowsWebClientCorsPreflightHeaders() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-colla-client,x-colla-retry-attempt"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"))
            .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-colla-client")))
            .andExpect(header().string("Access-Control-Allow-Headers", containsString("x-colla-retry-attempt")));
    }

    @Test
    void loginMeRefreshAndLogout() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "admin",
                          "password": "admin123456",
                          "deviceType": "web",
                          "deviceFingerprint": "test-web-device",
                          "deviceName": "MockMvc",
                          "appVersion": "test"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
            .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginResponse);
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.roles[0]").value("admin"));

        String refreshResponse = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
            .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode refreshJson = objectMapper.readTree(refreshResponse);
        String rotatedRefreshToken = refreshJson.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + refreshJson.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rotatedRefreshToken + "\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rotatedRefreshToken + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "admin",
                          "password": "wrong-password",
                          "deviceType": "web",
                          "deviceFingerprint": "invalid-login-device"
                        }
                        """
                ))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedLoginAndRefreshRequests() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "",
                          "password": "",
                          "deviceType": "web",
                          "deviceFingerprint": ""
                        }
                        """
                ))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"not-a-refresh-jwt\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCanUpdateProfileAndPasswordEndpointProtectsCurrentPassword() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "username": "admin",
                          "password": "admin123456",
                          "deviceType": "web",
                          "deviceFingerprint": "profile-test-device"
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String accessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();

        mockMvc.perform(patch("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Administrator\",\"email\":null,\"avatarFileId\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Administrator"))
            .andExpect(jsonPath("$.avatarFileId").value(nullValue()));

        mockMvc.perform(post("/api/auth/me/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"member123456\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void protectedProfileRequiresValidAccessToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid-access-jwt"))
            .andExpect(status().isForbidden());
    }
}
