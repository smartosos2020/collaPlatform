package com.colla.platform.shared.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtTokenProperties properties;
    private final ObjectMapper objectMapper;

    public JwtTokenService(JwtTokenProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String createAccessToken(UUID userId, UUID workspaceId, UUID deviceId, String username) {
        Instant expiresAt = Instant.now().plusSeconds(properties.getAccessTokenTtlMinutes() * 60);
        return createToken(userId, workspaceId, deviceId, username, ACCESS_TOKEN_TYPE, expiresAt, properties.getAccessSecret());
    }

    public String createRefreshToken(UUID userId, UUID workspaceId, UUID deviceId, String username) {
        Instant expiresAt = Instant.now().plusSeconds(properties.getRefreshTokenTtlDays() * 24 * 60 * 60);
        return createToken(userId, workspaceId, deviceId, username, REFRESH_TOKEN_TYPE, expiresAt, properties.getRefreshSecret());
    }

    public JwtClaims parseAccessToken(String token) {
        return parseToken(token, ACCESS_TOKEN_TYPE, properties.getAccessSecret());
    }

    public JwtClaims parseRefreshToken(String token) {
        return parseToken(token, REFRESH_TOKEN_TYPE, properties.getRefreshSecret());
    }

    public String tokenHash(String token) {
        return base64Url(sha256(token.getBytes(StandardCharsets.UTF_8)));
    }

    private String createToken(UUID userId, UUID workspaceId, UUID deviceId, String username, String type, Instant expiresAt, String secret) {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", userId.toString());
            payload.put("workspaceId", workspaceId.toString());
            payload.put("deviceId", deviceId.toString());
            payload.put("username", username);
            payload.put("type", type);
            payload.put("jti", UUID.randomUUID().toString());
            payload.put("exp", expiresAt.getEpochSecond());
            payload.put("iat", Instant.now().getEpochSecond());

            String headerPart = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadPart = base64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = headerPart + "." + payloadPart;
            String signature = base64Url(hmacSha256(signingInput, secret));
            return signingInput + "." + signature;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create JWT", exception);
        }
    }

    private JwtClaims parseToken(String token, String expectedType, String secret) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token format");
            }

            String signingInput = parts[0] + "." + parts[1];
            String expectedSignature = base64Url(hmacSha256(signingInput, secret));
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, new TypeReference<>() {});
            String type = payload.get("type").toString();
            if (!expectedType.equals(type)) {
                throw new IllegalArgumentException("Invalid token type");
            }

            long expiresAtEpoch = ((Number) payload.get("exp")).longValue();
            Instant expiresAt = Instant.ofEpochSecond(expiresAtEpoch);
            if (expiresAt.isBefore(Instant.now())) {
                throw new IllegalArgumentException("Token expired");
            }

            return new JwtClaims(
                UUID.fromString(payload.get("sub").toString()),
                UUID.fromString(payload.get("workspaceId").toString()),
                UUID.fromString(payload.get("deviceId").toString()),
                payload.get("username").toString(),
                type,
                expiresAt
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid token", exception);
        }
    }

    private byte[] hmacSha256(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash token", exception);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record JwtClaims(UUID userId, UUID workspaceId, UUID deviceId, String username, String type, Instant expiresAt) {
    }
}
