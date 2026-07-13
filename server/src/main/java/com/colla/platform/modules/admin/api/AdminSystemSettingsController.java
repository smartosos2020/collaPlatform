package com.colla.platform.modules.admin.api;

import com.colla.platform.modules.admin.application.AdminSystemSettingsService;
import com.colla.platform.modules.admin.application.AdminSystemSettingsService.AdminSystemSettingsView;
import com.colla.platform.shared.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system-settings")
public class AdminSystemSettingsController {
    private final AdminSystemSettingsService settingsService;

    public AdminSystemSettingsController(AdminSystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public AdminSystemSettingsView view(Authentication authentication) {
        return settingsService.view((CurrentUser) authentication.getPrincipal());
    }
}
