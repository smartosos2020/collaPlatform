package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncConflict;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRule;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncRun;
import com.colla.platform.modules.project.domain.CrossSpaceSyncModels.SyncStep;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrossSpaceSyncRepository {
    List<SyncRule> listRules(UUID workspaceId, UUID spaceId, int limit);

    List<SyncRun> listRuns(UUID workspaceId, UUID spaceId, int limit);

    List<SyncConflict> listConflicts(UUID workspaceId, UUID spaceId, int limit);

    List<SyncStep> listSteps(UUID workspaceId, UUID runId, int limit);

    Optional<SyncRule> findRule(UUID workspaceId, UUID ruleId, boolean lock);

    Optional<SyncRun> findRun(UUID workspaceId, UUID runId);

    Optional<SyncConflict> findConflict(UUID workspaceId, UUID conflictId, boolean lock);

    SyncRule createRule(NewRule rule);

    SyncRule reviseRule(NewVersion version, long expectedVersion);

    int transitionRule(
        UUID workspaceId, UUID ruleId, long expectedVersion,
        UUID actorId, String action, String party
    );

    SyncRun createRun(NewRun run);

    Optional<SyncRun> findRunByOrigin(
        UUID workspaceId, UUID ruleId, String direction,
        String originId, String fingerprint
    );

    void appendStep(UUID workspaceId, UUID runId, SyncStep step);

    void finishRun(
        UUID workspaceId, UUID runId, String status,
        Long resultTargetVersion, String failureCode
    );

    SyncConflict createConflict(
        UUID workspaceId, UUID runId, String kind,
        String sourceFingerprint, String targetFingerprint
    );

    int resolveConflict(
        UUID workspaceId, UUID conflictId, long expectedVersion,
        UUID actorId, String resolution, String reasonHash
    );

    Optional<CommandReceipt> findReceipt(
        UUID workspaceId, UUID actorId, String operation, String requestId
    );

    void saveReceipt(
        UUID workspaceId, UUID actorId, String operation,
        String requestId, String requestHash, JsonNode response
    );

    record NewRule(
        UUID workspaceId,
        UUID actorId,
        UUID grantId,
        UUID policyId,
        UUID relationId,
        UUID sourceSpaceId,
        UUID targetSpaceId,
        String name,
        String direction,
        String trigger,
        JsonNode fieldMappings,
        JsonNode stateMappings,
        String conflictStrategy,
        String configHash
    ) {
    }

    record NewVersion(
        UUID workspaceId,
        UUID actorId,
        UUID ruleId,
        String name,
        String direction,
        String trigger,
        JsonNode fieldMappings,
        JsonNode stateMappings,
        String conflictStrategy,
        String configHash
    ) {
    }

    record NewRun(
        UUID id,
        UUID workspaceId,
        UUID actorId,
        SyncRule rule,
        String direction,
        String originId,
        String causationId,
        int chainDepth,
        String inputFingerprint,
        UUID sourceSpaceId,
        UUID sourceWorkItemId,
        long sourceVersion,
        UUID targetSpaceId,
        UUID targetWorkItemId,
        long targetVersion
    ) {
    }

    record CommandReceipt(String requestHash, JsonNode response) {
    }
}
