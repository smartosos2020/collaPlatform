package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.AutomationRuleModels.AutomationRule;
import com.colla.platform.modules.project.domain.AutomationRuleModels.RuleVersion;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationRuleRepository {
    List<AutomationRule> list(UUID workspaceId, UUID spaceId, int limit);

    Optional<AutomationRule> find(UUID workspaceId, UUID spaceId, UUID ruleId);

    Optional<RuleVersion> findVersion(
        UUID workspaceId, UUID spaceId, UUID ruleId, int versionNumber
    );

    Optional<CommandRecord> findCommand(
        UUID workspaceId, UUID spaceId, UUID actorId,
        String operation, String requestId
    );

    AutomationRule save(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID ruleId,
        String name, JsonNode trigger, JsonNode condition, JsonNode actions,
        long expectedVersion, String requestId, String requestHash
    );

    AutomationRule changeLifecycle(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID ruleId,
        String action, long expectedVersion, String requestId, String requestHash
    );

    RuleVersion publish(
        UUID workspaceId, UUID spaceId, UUID actorId, UUID ruleId,
        long expectedVersion, String definitionHash,
        JsonNode definition, String requestId, String requestHash
    );

    record CommandRecord(String requestHash, String responseJson) {
    }
}
