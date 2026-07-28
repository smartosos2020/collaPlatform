package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.AutomationRuleService;
import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationFoundation;
import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationRule;
import com.colla.platform.modules.project.domain.AutomationRuleModels.RuleLifecycleCommand;
import com.colla.platform.modules.project.domain.AutomationRuleModels.RuleVersion;
import com.colla.platform.modules.project.domain.AutomationRuleModels.SaveRuleCommand;
import com.colla.platform.modules.project.application.AutomationExecutionService;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.AutomationRun;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.ExecuteRuleCommand;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.ExecutionFoundation;
import com.colla.platform.modules.project.application.AutomationConnectorService;
import com.colla.platform.modules.project.domain.AutomationConnectorModels.*;
import com.colla.platform.modules.project.application.AutomationManagementService;
import com.colla.platform.modules.project.domain.AutomationManagementModels.*;
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
@RequestMapping("/api/project-spaces/{spaceId}/automation")
public final class UserAutomationRuleController {
    private final AutomationRuleService service;
    private final AutomationExecutionService executions;
    private final AutomationConnectorService connectors;
    private final AutomationManagementService management;

    public UserAutomationRuleController(
        AutomationRuleService service,
        AutomationExecutionService executions,
        AutomationConnectorService connectors,
        AutomationManagementService management
    ) {
        this.service = service;
        this.executions = executions;
        this.connectors = connectors;
        this.management = management;
    }

    @GetMapping
    public AutomationFoundation get(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return service.get(currentUser(authentication), spaceId);
    }

    @PostMapping("/rules")
    public AutomationRule save(
        @PathVariable UUID spaceId,
        @RequestBody SaveRuleCommand command,
        Authentication authentication
    ) {
        return service.save(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/rules/{ruleId}/publish")
    public RuleVersion publish(
        @PathVariable UUID spaceId,
        @PathVariable UUID ruleId,
        @RequestBody RuleLifecycleCommand command,
        Authentication authentication
    ) {
        return service.publish(currentUser(authentication), spaceId, ruleId, command);
    }

    @PostMapping("/rules/{ruleId}/lifecycle")
    public AutomationRule lifecycle(
        @PathVariable UUID spaceId,
        @PathVariable UUID ruleId,
        @RequestBody RuleLifecycleCommand command,
        Authentication authentication
    ) {
        return service.lifecycle(currentUser(authentication), spaceId, ruleId, command);
    }

    @GetMapping("/runs")
    public ExecutionFoundation runs(
        @PathVariable UUID spaceId, Authentication authentication
    ) {
        return executions.list(currentUser(authentication), spaceId);
    }

    @PostMapping("/rules/{ruleId}/execute")
    public AutomationRun execute(
        @PathVariable UUID spaceId,
        @PathVariable UUID ruleId,
        @RequestBody ExecuteRuleCommand command,
        Authentication authentication
    ) {
        return executions.execute(
            currentUser(authentication), spaceId, ruleId, command
        );
    }

    @GetMapping("/connectors")
    public ConnectorFoundation connectors(@PathVariable UUID spaceId, Authentication authentication) {
        return connectors.get(currentUser(authentication), spaceId);
    }

    @PostMapping("/connectors")
    public Connector saveConnector(@PathVariable UUID spaceId, @RequestBody SaveConnectorCommand command,
                                   Authentication authentication) {
        return connectors.save(currentUser(authentication), spaceId, command);
    }

    @PostMapping("/connectors/{connectorId}/test")
    public Delivery testConnector(@PathVariable UUID spaceId, @PathVariable UUID connectorId,
                                  @RequestBody TestDeliveryCommand command, Authentication authentication) {
        return connectors.test(currentUser(authentication), spaceId, connectorId, command);
    }

    @PostMapping("/deliveries/{deliveryId}/govern")
    public Delivery governDelivery(@PathVariable UUID spaceId, @PathVariable UUID deliveryId,
                                   @RequestBody DeliveryGovernanceCommand command, Authentication authentication) {
        return connectors.govern(currentUser(authentication), spaceId, deliveryId, command);
    }

    @GetMapping("/management")
    public ManagementFoundation management(@PathVariable UUID spaceId,Authentication authentication) {
        return management.get(currentUser(authentication),spaceId);
    }

    @PostMapping("/management/preference")
    public ManagementPreference saveManagementPreference(
        @PathVariable UUID spaceId,@RequestBody SavePreferenceCommand command,Authentication authentication
    ) {
        return management.savePreference(currentUser(authentication),spaceId,command);
    }

    @PostMapping("/management/quota")
    public QuotaState governQuota(
        @PathVariable UUID spaceId,@RequestBody QuotaGovernanceCommand command,Authentication authentication
    ) {
        return management.govern(currentUser(authentication),spaceId,command);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }
}
