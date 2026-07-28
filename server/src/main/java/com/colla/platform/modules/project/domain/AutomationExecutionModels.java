package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AutomationExecutionModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_STEPS = 8;
    public static final int MAX_RECENT_RUNS = 100;

    private AutomationExecutionModels() {
    }

    public record ExecuteRuleCommand(
        int schemaVersion,
        String requestId,
        boolean dryRun,
        JsonNode event
    ) {
    }

    public record AutomationStep(
        UUID id,
        int stepNumber,
        String actionType,
        String status,
        String inputHash,
        JsonNode result,
        String errorCode,
        Instant startedAt,
        Instant completedAt
    ) {
    }

    public record AutomationRun(
        UUID id,
        UUID ruleId,
        int ruleVersion,
        String sourceType,
        String sourceKey,
        UUID actorId,
        String status,
        boolean dryRun,
        String inputHash,
        List<AutomationStep> steps,
        String errorCode,
        long fencingToken,
        int attempt,
        Instant startedAt,
        Instant completedAt
    ) {
    }

    public record ExecutionFoundation(
        List<AutomationRun> runs,
        boolean truncated
    ) {
    }
}
