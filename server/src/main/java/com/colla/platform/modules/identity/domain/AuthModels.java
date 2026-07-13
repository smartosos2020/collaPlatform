package com.colla.platform.modules.identity.domain;

import com.colla.platform.shared.auth.ClientType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AuthModels {
    private AuthModels() {
    }

    public record UserAccount(
        UUID id,
        UUID workspaceId,
        String username,
        String passwordHash,
        String displayName,
        UUID avatarFileId,
        String email,
        String status
    ) {
    }

    public record DeviceRegistration(
        ClientType deviceType,
        String deviceFingerprint,
        String deviceName,
        String appVersion
    ) {
    }

    public record AuthTokens(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UUID deviceId
    ) {
    }

    public record CurrentUserProfile(
        UUID id,
        UUID workspaceId,
        String username,
        String displayName,
        UUID avatarFileId,
        String email,
        Set<String> roles,
        Set<String> permissions
    ) {
    }

    public record MemberSummary(
        UUID id,
        String username,
        String displayName,
        UUID avatarFileId,
        String email,
        String status,
        Instant lastLoginAt,
        Instant createdAt,
        Set<String> roles,
        List<MemberDepartment> departments
    ) {
    }

    public record MemberDepartment(
        UUID departmentId,
        String departmentCode,
        String departmentName,
        String relationType
    ) {
    }

    public record DeviceSummary(
        UUID id,
        ClientType deviceType,
        String deviceName,
        String deviceFingerprint,
        String appVersion,
        Instant lastActiveAt,
        Instant createdAt,
        Instant revokedAt,
        int activeSessionCount,
        int enabledPushTokenCount,
        boolean current
    ) {
    }

    public record PushTokenSummary(
        UUID id,
        UUID deviceId,
        String provider,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Instant revokedAt
    ) {
    }
}
