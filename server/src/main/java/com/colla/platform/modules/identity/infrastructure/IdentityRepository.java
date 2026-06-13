package com.colla.platform.modules.identity.infrastructure;

import com.colla.platform.modules.identity.domain.AuthModels.UserAccount;
import com.colla.platform.modules.identity.domain.AuthModels.MemberSummary;
import com.colla.platform.modules.identity.domain.AuthModels.DeviceSummary;
import com.colla.platform.modules.identity.domain.AuthModels.PushTokenSummary;
import com.colla.platform.shared.auth.ClientType;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository {
    Optional<UserAccount> findUserByUsername(String username);

    Optional<UserAccount> findUserById(UUID userId);

    Optional<CurrentUser> findCurrentUser(UUID userId, UUID deviceId);

    UUID upsertDevice(UUID workspaceId, UUID userId, ClientType deviceType, String deviceFingerprint, String deviceName, String appVersion);

    UUID createSession(UUID workspaceId, UUID userId, UUID deviceId, String refreshTokenHash, String userAgent, String ipAddress, Instant expiresAt);

    Optional<SessionRecord> findActiveSessionByRefreshTokenHash(String refreshTokenHash);

    void revokeSession(UUID sessionId, Instant revokedAt);

    boolean isDeviceActive(UUID workspaceId, UUID userId, UUID deviceId);

    void updateLastLoginAt(UUID userId, Instant at);

    boolean hasAnyUser();

    UUID createUser(UUID workspaceId, String username, String passwordHash, String displayName, String email, UUID createdBy);

    void assignRole(UUID workspaceId, UUID userId, String roleCode, UUID createdBy);

    List<MemberSummary> listMembers(UUID workspaceId);

    void updateUserStatus(UUID workspaceId, UUID userId, String status, UUID updatedBy);

    void updatePassword(UUID workspaceId, UUID userId, String passwordHash, UUID updatedBy);

    Optional<UUID> findDefaultWorkspaceId();

    List<DeviceSummary> listDevices(UUID workspaceId, UUID userId, UUID currentDeviceId);

    Optional<DeviceSummary> findDevice(UUID workspaceId, UUID deviceId, UUID currentDeviceId);

    void revokeDevice(UUID workspaceId, UUID deviceId, Instant revokedAt);

    void revokeDeviceSessions(UUID workspaceId, UUID deviceId, Instant revokedAt);

    PushTokenSummary upsertPushToken(UUID workspaceId, UUID userId, UUID deviceId, String provider, String tokenHash);

    List<PushTokenSummary> listPushTokens(UUID workspaceId, UUID userId, UUID deviceId);

    record SessionRecord(UUID id, UUID workspaceId, UUID userId, UUID deviceId, Instant expiresAt, Instant revokedAt) {
    }
}
