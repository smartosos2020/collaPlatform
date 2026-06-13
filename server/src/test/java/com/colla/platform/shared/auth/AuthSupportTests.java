package com.colla.platform.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthSupportTests {

    @Test
    void passwordHasherStoresOneWayHashes() {
        PasswordHasher hasher = new PasswordHasher();
        String credential = "unit-credential-123456";

        String hash = hasher.hash(credential);

        assertThat(hash).isNotEqualTo(credential);
        assertThat(hash).startsWith("$2");
        assertThat(hasher.matches(credential, hash)).isTrue();
        assertThat(hasher.matches("wrong-credential", hash)).isFalse();
    }

    @Test
    void jwtServiceCreatesTypedTokensAndRejectsWrongTypeOrTampering() {
        JwtTokenService jwtService = new JwtTokenService(properties(), new ObjectMapper());
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        String accessJwt = jwtService.createAccessToken(userId, workspaceId, deviceId, "unit-user");
        String refreshJwt = jwtService.createRefreshToken(userId, workspaceId, deviceId, "unit-user");

        JwtTokenService.JwtClaims accessClaims = jwtService.parseAccessToken(accessJwt);
        assertThat(accessClaims.userId()).isEqualTo(userId);
        assertThat(accessClaims.workspaceId()).isEqualTo(workspaceId);
        assertThat(accessClaims.deviceId()).isEqualTo(deviceId);
        assertThat(accessClaims.username()).isEqualTo("unit-user");
        assertThat(accessClaims.type()).isEqualTo(JwtTokenService.ACCESS_TOKEN_TYPE);
        assertThat(jwtService.tokenHash(refreshJwt)).isNotEqualTo(refreshJwt);

        assertThatThrownBy(() -> jwtService.parseRefreshToken(accessJwt))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtService.parseAccessToken(refreshJwt))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtService.parseAccessToken(accessJwt.substring(0, accessJwt.length() - 2) + "xx"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private JwtTokenProperties properties() {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setAccessSecret("unit-access-signing-key-with-enough-length");
        properties.setRefreshSecret("unit-refresh-signing-key-with-enough-length");
        properties.setAccessTokenTtlMinutes(5);
        properties.setRefreshTokenTtlDays(1);
        return properties;
    }
}
