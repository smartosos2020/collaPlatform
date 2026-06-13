package com.colla.platform.modules.identity.api;

import com.colla.platform.modules.identity.application.AuthService;
import com.colla.platform.modules.identity.domain.AuthModels.AuthTokens;
import com.colla.platform.modules.identity.domain.AuthModels.CurrentUserProfile;
import com.colla.platform.modules.identity.domain.AuthModels.DeviceRegistration;
import com.colla.platform.shared.auth.ClientType;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthTokens login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(
            request.username(),
            request.password(),
            request.toDeviceRegistration(),
            httpRequest.getHeader(HttpHeaders.USER_AGENT),
            clientIp(httpRequest)
        );
    }

    @PostMapping("/refresh")
    public AuthTokens refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request.refreshToken(), httpRequest.getHeader(HttpHeaders.USER_AGENT), clientIp(httpRequest));
    }

    @PostMapping("/logout")
    public void logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
    }

    @GetMapping("/me")
    public CurrentUserProfile me(Authentication authentication) {
        return authService.me((CurrentUser) authentication.getPrincipal());
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotNull ClientType deviceType,
        @NotBlank String deviceFingerprint,
        String deviceName,
        String appVersion
    ) {
        DeviceRegistration toDeviceRegistration() {
            return new DeviceRegistration(deviceType, deviceFingerprint, deviceName, appVersion);
        }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }
}
