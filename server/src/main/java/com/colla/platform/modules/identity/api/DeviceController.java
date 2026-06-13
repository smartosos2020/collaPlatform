package com.colla.platform.modules.identity.api;

import com.colla.platform.modules.identity.application.DeviceService;
import com.colla.platform.modules.identity.application.DeviceService.PushProbeResult;
import com.colla.platform.modules.identity.domain.AuthModels.DeviceSummary;
import com.colla.platform.modules.identity.domain.AuthModels.PushTokenSummary;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public List<DeviceSummary> list(Authentication authentication) {
        return deviceService.listDevices(currentUser(authentication));
    }

    @DeleteMapping("/{deviceId}")
    public void revoke(@PathVariable UUID deviceId, Authentication authentication) {
        deviceService.revokeDevice(currentUser(authentication), deviceId);
    }

    @PostMapping("/{deviceId}/push-token")
    public PushTokenSummary registerPushToken(
        @PathVariable UUID deviceId,
        @Valid @RequestBody RegisterPushTokenRequest request,
        Authentication authentication
    ) {
        return deviceService.registerPushToken(currentUser(authentication), deviceId, request.provider(), request.token());
    }

    @GetMapping("/{deviceId}/push-tokens")
    public List<PushTokenSummary> listPushTokens(@PathVariable UUID deviceId, Authentication authentication) {
        return deviceService.listPushTokens(currentUser(authentication), deviceId);
    }

    @PostMapping("/{deviceId}/push-probe")
    public PushProbeResult probePush(@PathVariable UUID deviceId, Authentication authentication) {
        return deviceService.probePush(currentUser(authentication), deviceId);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record RegisterPushTokenRequest(@NotBlank String provider, @NotBlank String token) {
    }
}
