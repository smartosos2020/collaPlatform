package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.MetricGovernanceService;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.AuditReport;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ExportReceipt;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ExportReportCommand;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.GovernanceFoundation;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.ReportRun;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.RunReportCommand;
import com.colla.platform.modules.project.domain.MetricGovernanceModels.SaveReportCommand;
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
@RequestMapping("/api/project-spaces/{spaceId}/metric-governance")
public final class UserMetricGovernanceController {
    private final MetricGovernanceService service;

    public UserMetricGovernanceController(MetricGovernanceService service) {
        this.service = service;
    }

    @GetMapping
    public GovernanceFoundation foundation(
        @PathVariable UUID spaceId,
        Authentication authentication
    ) {
        return service.foundation(currentUser(authentication), spaceId);
    }

    @PostMapping("/reports")
    public AuditReport save(
        @PathVariable UUID spaceId,
        @RequestBody SaveReportCommand command,
        Authentication authentication
    ) {
        return service.save(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/reports/{reportId}/runs")
    public ReportRun run(
        @PathVariable UUID spaceId,
        @PathVariable UUID reportId,
        @RequestBody RunReportCommand command,
        Authentication authentication
    ) {
        return service.run(currentUser(authentication), spaceId, reportId, command);
    }

    @PostMapping("/runs/{runId}/exports")
    public ExportReceipt export(
        @PathVariable UUID spaceId,
        @PathVariable UUID runId,
        @RequestBody ExportReportCommand command,
        Authentication authentication
    ) {
        return service.export(currentUser(authentication), spaceId, runId, command);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
