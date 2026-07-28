package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.MetricDashboardService;
import com.colla.platform.modules.project.domain.MetricDashboardModels.Dashboard;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardFoundation;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardLifecycleCommand;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardPreference;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardQueryResult;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardVersion;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DrilldownCommand;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DrilldownResult;
import com.colla.platform.modules.project.domain.MetricDashboardModels.QueryDashboardCommand;
import com.colla.platform.modules.project.domain.MetricDashboardModels.SaveDashboardCommand;
import com.colla.platform.modules.project.domain.MetricDashboardModels.SavePreferenceCommand;
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
@RequestMapping("/api/project-spaces/{spaceId}/metric-dashboards")
public final class UserMetricDashboardController {
    private final MetricDashboardService service;

    public UserMetricDashboardController(MetricDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardFoundation foundation(
        @PathVariable UUID spaceId,
        Authentication authentication
    ) {
        return service.foundation(currentUser(authentication), spaceId);
    }

    @PostMapping
    public Dashboard save(
        @PathVariable UUID spaceId,
        @RequestBody SaveDashboardCommand command,
        Authentication authentication
    ) {
        return service.save(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/{dashboardId}/publish")
    public DashboardVersion publish(
        @PathVariable UUID spaceId,
        @PathVariable UUID dashboardId,
        @RequestBody DashboardLifecycleCommand command,
        Authentication authentication
    ) {
        return service.publish(
            currentUser(authentication), spaceId, dashboardId, command
        );
    }

    @PostMapping("/{dashboardId}/lifecycle")
    public Dashboard lifecycle(
        @PathVariable UUID spaceId,
        @PathVariable UUID dashboardId,
        @RequestBody DashboardLifecycleCommand command,
        Authentication authentication
    ) {
        return service.lifecycle(
            currentUser(authentication), spaceId, dashboardId, command
        );
    }

    @PostMapping("/{dashboardId}/query")
    public DashboardQueryResult query(
        @PathVariable UUID spaceId,
        @PathVariable UUID dashboardId,
        @RequestBody QueryDashboardCommand command,
        Authentication authentication
    ) {
        return service.query(
            currentUser(authentication), spaceId, dashboardId, command
        );
    }

    @PostMapping("/{dashboardId}/drilldown")
    public DrilldownResult drilldown(
        @PathVariable UUID spaceId,
        @PathVariable UUID dashboardId,
        @RequestBody DrilldownCommand command,
        Authentication authentication
    ) {
        return service.drilldown(
            currentUser(authentication), spaceId, dashboardId, command
        );
    }

    @GetMapping("/{dashboardId}/preference")
    public DashboardPreference preference(
        @PathVariable UUID spaceId,
        @PathVariable UUID dashboardId,
        Authentication authentication
    ) {
        return service.preference(
            currentUser(authentication), spaceId, dashboardId
        );
    }

    @PostMapping("/{dashboardId}/preference")
    public DashboardPreference savePreference(
        @PathVariable UUID spaceId,
        @PathVariable UUID dashboardId,
        @RequestBody SavePreferenceCommand command,
        Authentication authentication
    ) {
        return service.savePreference(
            currentUser(authentication), spaceId, dashboardId, command
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
