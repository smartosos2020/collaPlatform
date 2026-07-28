package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AutomationRuleModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_RULES = 100;
    public static final int MAX_ACTIONS = 8;
    public static final int MAX_CONDITION_NODES = 64;
    public static final int MAX_CONDITION_DEPTH = 8;

    private AutomationRuleModels() {
    }

    public record SaveRuleCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        UUID ruleId,
        String name,
        JsonNode trigger,
        JsonNode condition,
        JsonNode actions
    ) {
    }

    public record RuleLifecycleCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String action
    ) {
    }

    public record AutomationRule(
        UUID id,
        String name,
        String status,
        JsonNode trigger,
        JsonNode condition,
        JsonNode actions,
        long version,
        Integer publishedVersion,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record RuleVersion(
        UUID id,
        UUID ruleId,
        int versionNumber,
        String definitionHash,
        JsonNode definition,
        UUID publishedBy,
        Instant publishedAt
    ) {
    }

    public record EventCatalogEntry(
        String eventType,
        int eventVersion,
        List<String> allowedFields
    ) {
    }

    public record ActionCatalogEntry(
        String actionType,
        int actionVersion,
        boolean sideEffecting,
        String owner
    ) {
    }

    public record AutomationFoundation(
        int schemaVersion,
        List<EventCatalogEntry> events,
        List<ActionCatalogEntry> actions,
        List<AutomationRule> rules,
        boolean truncated
    ) {
    }
}
