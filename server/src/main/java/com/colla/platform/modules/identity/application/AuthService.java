package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.file.domain.FileModels.FileMetadata;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.identity.domain.AuthModels.AuthTokens;
import com.colla.platform.modules.identity.domain.AuthModels.CurrentUserProfile;
import com.colla.platform.modules.identity.domain.AuthModels.DeviceRegistration;
import com.colla.platform.modules.identity.domain.AuthModels.UserAccount;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository.SessionRecord;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.JwtTokenService;
import com.colla.platform.shared.auth.PasswordHasher;
import com.colla.platform.shared.auth.PasswordPolicy;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final IdentityRepository identityRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenService jwtTokenService;
    private final AuditService auditService;
    private final PasswordPolicy passwordPolicy;
    private final FileRepository fileRepository;

    public AuthService(
        IdentityRepository identityRepository,
        PasswordHasher passwordHasher,
        JwtTokenService jwtTokenService,
        AuditService auditService,
        PasswordPolicy passwordPolicy,
        FileRepository fileRepository
    ) {
        this.identityRepository = identityRepository;
        this.passwordHasher = passwordHasher;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        this.passwordPolicy = passwordPolicy;
        this.fileRepository = fileRepository;
    }

    @Transactional
    public AuthTokens login(String username, String password, DeviceRegistration device, String userAgent, String ipAddress) {
        UserAccount user = identityRepository.findUserByUsername(username)
            .orElse(null);
        if (user == null || !"active".equals(user.status())) {
            auditLoginFailure(null, username, "not_found_or_inactive", userAgent, ipAddress);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        if (!passwordHasher.matches(password, user.passwordHash())) {
            auditLoginFailure(user, username, "invalid_password", userAgent, ipAddress);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        UUID deviceId = identityRepository.upsertDevice(
            user.workspaceId(),
            user.id(),
            device.deviceType(),
            device.deviceFingerprint(),
            device.deviceName(),
            device.appVersion()
        );
        identityRepository.updateLastLoginAt(user.id(), Instant.now());

        AuthTokens tokens = issueTokens(user, deviceId, userAgent, ipAddress);
        auditService.log(
            user.workspaceId(),
            user.id(),
            "auth.login.success",
            "user",
            user.id(),
            ipAddress,
            userAgent,
            Map.of("deviceType", device.deviceType().name(), "deviceId", deviceId.toString())
        );
        return tokens;
    }

    @Transactional
    public AuthTokens refresh(String refreshToken, String userAgent, String ipAddress) {
        JwtTokenService.JwtClaims claims;
        try {
            claims = jwtTokenService.parseRefreshToken(refreshToken);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        String tokenHash = jwtTokenService.tokenHash(refreshToken);
        SessionRecord session = identityRepository.findActiveSessionByRefreshTokenHash(tokenHash)
            .filter(record -> record.revokedAt() == null)
            .filter(record -> record.expiresAt().isAfter(Instant.now()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        UserAccount user = identityRepository.findUserById(claims.userId())
            .filter(account -> "active".equals(account.status()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        identityRepository.revokeSession(session.id(), Instant.now());
        return issueTokens(user, session.deviceId(), userAgent, ipAddress);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String tokenHash = jwtTokenService.tokenHash(refreshToken);
        identityRepository.findActiveSessionByRefreshTokenHash(tokenHash)
            .filter(record -> record.revokedAt() == null)
            .ifPresent(record -> identityRepository.revokeSession(record.id(), Instant.now()));
    }

    public CurrentUserProfile me(CurrentUser currentUser) {
        UserAccount user = identityRepository.findUserById(currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
        return new CurrentUserProfile(
            user.id(),
            user.workspaceId(),
            user.username(),
            user.displayName(),
            user.avatarFileId(),
            user.email(),
            currentUser.roles(),
            currentUser.permissions()
        );
    }

    @Transactional
    public CurrentUserProfile updateProfile(CurrentUser currentUser, String displayName, String email, UUID avatarFileId) {
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        if (normalizedDisplayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is required");
        }
        if (normalizedDisplayName.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is too long");
        }
        String normalizedEmail = email == null || email.isBlank() ? null : email.trim();
        if (normalizedEmail != null && normalizedEmail.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is too long");
        }
        if (avatarFileId != null) {
            FileMetadata avatar = fileRepository.find(currentUser.workspaceId(), avatarFileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar file is not available"));
            if (!currentUser.id().equals(avatar.uploadedBy()) || !"completed".equals(avatar.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar file is not available");
            }
        }
        identityRepository.updateProfile(currentUser.workspaceId(), currentUser.id(), normalizedDisplayName, normalizedEmail, avatarFileId, currentUser.id());
        auditService.log(currentUser, "user.profile.updated", "user", currentUser.id(), Map.of());
        return me(currentUser);
    }

    @Transactional
    public void changePassword(CurrentUser currentUser, String currentPassword, String newPassword) {
        UserAccount user = identityRepository.findUserById(currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
        if (currentPassword == null || !passwordHasher.matches(currentPassword, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        passwordPolicy.validate(newPassword);
        identityRepository.updatePassword(currentUser.workspaceId(), currentUser.id(), passwordHasher.hash(newPassword), currentUser.id());
        auditService.log(currentUser, "user.password.changed", "user", currentUser.id(), Map.of());
    }

    private AuthTokens issueTokens(UserAccount user, UUID deviceId, String userAgent, String ipAddress) {
        String accessToken = jwtTokenService.createAccessToken(user.id(), user.workspaceId(), deviceId, user.username());
        String refreshToken = jwtTokenService.createRefreshToken(user.id(), user.workspaceId(), deviceId, user.username());
        JwtTokenService.JwtClaims accessClaims = jwtTokenService.parseAccessToken(accessToken);
        JwtTokenService.JwtClaims refreshClaims = jwtTokenService.parseRefreshToken(refreshToken);
        identityRepository.createSession(
            user.workspaceId(),
            user.id(),
            deviceId,
            jwtTokenService.tokenHash(refreshToken),
            userAgent,
            ipAddress,
            refreshClaims.expiresAt()
        );
        return new AuthTokens("Bearer", accessToken, accessClaims.expiresAt(), refreshToken, refreshClaims.expiresAt(), deviceId);
    }

    private void auditLoginFailure(UserAccount user, String username, String reason, String userAgent, String ipAddress) {
        UUID workspaceId = user == null ? identityRepository.findDefaultWorkspaceId().orElse(null) : user.workspaceId();
        auditService.log(
            workspaceId,
            user == null ? null : user.id(),
            "auth.login.failed",
            "auth",
            user == null ? null : user.id(),
            ipAddress,
            userAgent,
            Map.of("username", username == null ? "" : username, "reason", reason)
        );
    }
}
