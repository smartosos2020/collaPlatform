package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.MetricRiskService;
import com.colla.platform.modules.project.domain.MetricRiskModels.EvaluateRisksCommand;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskFoundation;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicy;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicyLifecycleCommand;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskPolicyVersion;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskSignal;
import com.colla.platform.modules.project.domain.MetricRiskModels.RiskSignalActionCommand;
import com.colla.platform.modules.project.domain.MetricRiskModels.SaveRiskPolicyCommand;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/metric-risks")
public final class UserMetricRiskController {
    private final MetricRiskService service;

    public UserMetricRiskController(MetricRiskService service) {
        this.service = service;
    }

    @GetMapping
    public RiskFoundation foundation(
        @PathVariable UUID spaceId,
        Authentication authentication
    ) {
        return service.foundation(currentUser(authentication), spaceId);
    }

    @PostMapping("/policies")
    public RiskPolicy save(
        @PathVariable UUID spaceId,
        @RequestBody SaveRiskPolicyCommand command,
        Authentication authentication
    ) {
        return service.save(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/policies/{policyId}/publish")
    public RiskPolicyVersion publish(
        @PathVariable UUID spaceId,
        @PathVariable UUID policyId,
        @RequestBody RiskPolicyLifecycleCommand command,
        Authentication authentication
    ) {
        return service.publish(currentUser(authentication), spaceId, policyId, command);
    }

    @PostMapping("/evaluate")
    public List<RiskSignal> evaluate(
        @PathVariable UUID spaceId,
        @RequestBody EvaluateRisksCommand command,
        Authentication authentication
    ) {
        return service.evaluate(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/signals/{signalId}/actions")
    public RiskSignal act(
        @PathVariable UUID spaceId,
        @PathVariable UUID signalId,
        @RequestBody RiskSignalActionCommand command,
        Authentication authentication
    ) {
        return service.act(currentUser(authentication), spaceId, signalId, command);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
