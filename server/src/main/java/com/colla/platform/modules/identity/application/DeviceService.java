package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.identity.domain.AuthModels.DeviceSummary;
import com.colla.platform.modules.identity.domain.AuthModels.PushTokenSummary;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.JwtTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceService {
    private final IdentityRepository identityRepository;
    private final JwtTokenService jwtTokenService;

    public DeviceService(IdentityRepository identityRepository, JwtTokenService jwtTokenService) {
        this.identityRepository = identityRepository;
        this.jwtTokenService = jwtTokenService;
    }

    public List<DeviceSummary> listDevices(CurrentUser currentUser) {
        return identityRepository.listDevices(currentUser.workspaceId(), currentUser.id(), currentUser.deviceId());
    }

    @Transactional
    public void revokeDevice(CurrentUser currentUser, UUID deviceId) {
        DeviceSummary device = identityRepository.findDevice(currentUser.workspaceId(), deviceId, currentUser.deviceId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        boolean ownsDevice = identityRepository.listDevices(currentUser.workspaceId(), currentUser.id(), currentUser.deviceId())
            .stream()
            .anyMatch(item -> item.id().equals(device.id()));
        if (!ownsDevice && !currentUser.hasRole("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot revoke another user's device");
        }

        Instant revokedAt = Instant.now();
        identityRepository.revokeDevice(currentUser.workspaceId(), deviceId, revokedAt);
        identityRepository.revokeDeviceSessions(currentUser.workspaceId(), deviceId, revokedAt);
    }

    @Transactional
    public PushTokenSummary registerPushToken(CurrentUser currentUser, UUID deviceId, String provider, String token) {
        DeviceSummary device = identityRepository.findDevice(currentUser.workspaceId(), deviceId, currentUser.deviceId())
            .filter(item -> item.revokedAt() == null)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active device not found"));
        boolean ownsDevice = identityRepository.listDevices(currentUser.workspaceId(), currentUser.id(), currentUser.deviceId())
            .stream()
            .anyMatch(item -> item.id().equals(device.id()));
        if (!ownsDevice) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot bind push token to another user's device");
        }

        String normalizedProvider = provider.toLowerCase(Locale.ROOT);
        String tokenHash = jwtTokenService.tokenHash(normalizedProvider + ":" + token);
        return identityRepository.upsertPushToken(currentUser.workspaceId(), currentUser.id(), deviceId, normalizedProvider, tokenHash);
    }

    public List<PushTokenSummary> listPushTokens(CurrentUser currentUser, UUID deviceId) {
        return identityRepository.listPushTokens(currentUser.workspaceId(), currentUser.id(), deviceId);
    }

    public PushProbeResult probePush(CurrentUser currentUser, UUID deviceId) {
        List<PushTokenSummary> tokens = listPushTokens(currentUser, deviceId).stream()
            .filter(token -> token.enabled() && token.revokedAt() == null)
            .toList();
        return new PushProbeResult(deviceId, tokens.size(), !tokens.isEmpty(), Instant.now());
    }

    public record PushProbeResult(UUID deviceId, int enabledTokenCount, boolean deliverable, Instant checkedAt) {
    }
}
