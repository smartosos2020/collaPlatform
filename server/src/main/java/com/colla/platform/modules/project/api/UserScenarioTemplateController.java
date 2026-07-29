package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.ScenarioTemplateService;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioFoundation;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioInstallCommand;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioInstallResult;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioValidationResult;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/scenario-templates")
public final class UserScenarioTemplateController {
    private final ScenarioTemplateService service;

    public UserScenarioTemplateController(ScenarioTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ScenarioFoundation foundation(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.foundation(currentUser(authentication), spaceId);
    }

    @GetMapping("/{scenarioKey}/validation")
    public ScenarioValidationResult validate(
        @PathVariable UUID spaceId,
        @PathVariable String scenarioKey,
        Authentication authentication
    ) {
        return service.validate(currentUser(authentication), spaceId, scenarioKey);
    }

    @GetMapping("/{scenarioKey}/installation")
    public ScenarioInstallResult installation(
        @PathVariable UUID spaceId,
        @PathVariable String scenarioKey,
        Authentication authentication
    ) {
        return service.installation(currentUser(authentication), spaceId, scenarioKey);
    }

    @PostMapping("/{scenarioKey}/dry-run")
    public ScenarioInstallResult dryRun(
        @PathVariable UUID spaceId,
        @PathVariable String scenarioKey,
        @RequestBody ScenarioInstallCommand command,
        Authentication authentication
    ) {
        return service.dryRun(currentUser(authentication), spaceId, scenarioKey, command);
    }

    @PostMapping("/{scenarioKey}/install")
    public ScenarioInstallResult install(
        @PathVariable UUID spaceId,
        @PathVariable String scenarioKey,
        @RequestBody ScenarioInstallCommand command,
        Authentication authentication
    ) {
        return service.install(currentUser(authentication), spaceId, scenarioKey, command);
    }

    @PostMapping("/{scenarioKey}/retry")
    public ScenarioInstallResult retry(
        @PathVariable UUID spaceId,
        @PathVariable String scenarioKey,
        @RequestBody ScenarioInstallCommand command,
        Authentication authentication
    ) {
        return service.retry(currentUser(authentication), spaceId, scenarioKey, command);
    }

    @PostMapping("/{scenarioKey}/upgrade")
    public ScenarioInstallResult upgrade(
        @PathVariable UUID spaceId,
        @PathVariable String scenarioKey,
        @RequestBody ScenarioInstallCommand command,
        Authentication authentication
    ) {
        return service.upgrade(currentUser(authentication), spaceId, scenarioKey, command);
    }

    @PostMapping("/{scenarioKey}/detach")
    public ScenarioInstallResult detach(
        @PathVariable UUID spaceId,
        @PathVariable String scenarioKey,
        @RequestBody ScenarioInstallCommand command,
        Authentication authentication
    ) {
        return service.detach(currentUser(authentication), spaceId, scenarioKey, command);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
