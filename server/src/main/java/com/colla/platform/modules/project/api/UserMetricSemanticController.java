package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.MetricSemanticService;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricDefinition;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricFoundation;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricLifecycleCommand;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricResult;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricVersion;
import com.colla.platform.modules.project.domain.MetricSemanticModels.PreviewMetricCommand;
import com.colla.platform.modules.project.domain.MetricSemanticModels.SaveMetricCommand;
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
@RequestMapping("/api/project-spaces/{spaceId}/metrics")
public final class UserMetricSemanticController {
    private final MetricSemanticService service;

    public UserMetricSemanticController(MetricSemanticService service) {
        this.service = service;
    }

    @GetMapping
    public MetricFoundation foundation(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.foundation(currentUser(authentication), spaceId);
    }

    @PostMapping
    public MetricDefinition save(
        @PathVariable UUID spaceId, @RequestBody SaveMetricCommand command,
        Authentication authentication
    ) {
        return service.save(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/{metricId}/publish")
    public MetricVersion publish(
        @PathVariable UUID spaceId, @PathVariable UUID metricId,
        @RequestBody MetricLifecycleCommand command, Authentication authentication
    ) {
        return service.publish(currentUser(authentication), spaceId, metricId, command);
    }

    @PostMapping("/{metricId}/lifecycle")
    public MetricDefinition lifecycle(
        @PathVariable UUID spaceId, @PathVariable UUID metricId,
        @RequestBody MetricLifecycleCommand command, Authentication authentication
    ) {
        return service.lifecycle(currentUser(authentication), spaceId, metricId, command);
    }

    @PostMapping("/{metricId}/preview")
    public MetricResult preview(
        @PathVariable UUID spaceId, @PathVariable UUID metricId,
        @RequestBody PreviewMetricCommand command, Authentication authentication
    ) {
        return service.preview(currentUser(authentication), spaceId, metricId, command);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
