package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.AutomationExecutionModels.AutomationRun;
import com.colla.platform.modules.project.domain.AutomationExecutionModels.AutomationStep;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationExecutionRepository {
    StartResult begin(
        UUID workspaceId, UUID spaceId, UUID ruleId, int ruleVersion,
        String sourceType, String sourceKey, UUID actorId, boolean dryRun,
        String inputHash
    );

    AutomationStep startStep(
        UUID workspaceId, UUID spaceId, UUID runId, int stepNumber,
        String actionType, String inputHash
    );

    void completeStep(
        UUID workspaceId, UUID spaceId, UUID runId, int stepNumber,
        String status, JsonNode result, String errorCode
    );

    void completeRun(
        UUID workspaceId, UUID spaceId, UUID runId,
        String status, JsonNode output, String errorCode
    );

    Optional<ActionReceipt> findActionReceipt(
        UUID workspaceId, UUID spaceId, UUID ruleId, int ruleVersion,
        int actionIndex, String idempotencyKey
    );

    void saveActionReceipt(
        UUID workspaceId, UUID spaceId, UUID ruleId, int ruleVersion,
        int actionIndex, String idempotencyKey, String inputHash,
        JsonNode response
    );

    AutomationRun get(UUID workspaceId, UUID spaceId, UUID runId);

    List<AutomationRun> list(UUID workspaceId, UUID spaceId, int limit);

    record StartResult(AutomationRun run, boolean replay) {
    }

    record ActionReceipt(String inputHash, JsonNode response) {
    }
}
