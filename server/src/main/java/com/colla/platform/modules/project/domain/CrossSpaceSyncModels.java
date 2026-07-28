package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CrossSpaceSyncModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_RULES = 50;
    public static final int MAX_FIELD_MAPPINGS = 32;
    public static final int MAX_STATE_MAPPINGS = 16;
    public static final int MAX_RUNS = 100;
    public static final int MAX_STEPS = 50;
    public static final int MAX_CHAIN_DEPTH = 8;
    public static final int MAX_RETRIES = 5;

    private CrossSpaceSyncModels() {
    }

    public record SaveSyncRuleCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        UUID ruleId,
        UUID grantId,
        UUID policyId,
        UUID canonicalRelationId,
        String name,
        String direction,
        String trigger,
        JsonNode fieldMappings,
        JsonNode stateMappings,
        String conflictStrategy
    ) {
    }

    public record SyncRuleLifecycleCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String action,
        String party,
        String reason
    ) {
    }

    public record ExecuteSyncCommand(
        int schemaVersion,
        String requestId,
        long expectedRuleVersion,
        String direction,
        String originId,
        String causationId,
        int chainDepth,
        long expectedSourceVersion,
        long expectedTargetVersion
    ) {
    }

    public record ResolveConflictCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String resolution,
        String reason
    ) {
    }

    public record SyncRule(
        UUID id,
        UUID grantId,
        UUID policyId,
        UUID canonicalRelationId,
        UUID sourceSpaceId,
        UUID targetSpaceId,
        String name,
        String status,
        int currentVersion,
        UUID sourceConfirmedBy,
        UUID targetConfirmedBy,
        SyncRuleVersion configuration,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record SyncRuleVersion(
        UUID id,
        int versionNumber,
        String direction,
        String trigger,
        JsonNode fieldMappings,
        JsonNode stateMappings,
        String conflictStrategy,
        String configHash,
        UUID createdBy,
        Instant createdAt
    ) {
    }

    public record SyncRun(
        UUID id,
        UUID ruleId,
        UUID ruleVersionId,
        int ruleVersionNumber,
        UUID canonicalRelationId,
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
        long targetVersion,
        String status,
        int retryCount,
        long fencingToken,
        Long resultTargetVersion,
        String failureCode,
        Instant createdAt,
        Instant completedAt
    ) {
    }

    public record SyncStep(
        int index,
        String kind,
        String mappingKey,
        String inputFingerprint,
        String commandRequestId,
        String status,
        long beforeVersion,
        Long afterVersion,
        String errorCode
    ) {
    }

    public record SyncConflict(
        UUID id,
        UUID runId,
        String kind,
        String sourceFingerprint,
        String targetFingerprint,
        String status,
        long version,
        String resolution,
        Instant createdAt,
        Instant resolvedAt
    ) {
    }

    public record SyncRunDetail(
        SyncRun run,
        List<SyncStep> steps,
        SyncConflict conflict
    ) {
    }

    public record SyncFoundation(
        int schemaVersion,
        List<String> directions,
        List<String> triggers,
        List<String> conflictStrategies,
        List<SyncRule> rules,
        List<SyncRun> runs,
        List<SyncConflict> conflicts,
        boolean truncated
    ) {
    }
}
