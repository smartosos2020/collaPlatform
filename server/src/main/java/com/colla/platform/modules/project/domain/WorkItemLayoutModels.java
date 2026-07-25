package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class WorkItemLayoutModels {
    public static final int CONDITION_SCHEMA_VERSION = 1;
    public static final int POLICY_SCHEMA_VERSION = 1;
    public static final int MAX_NODES = 120;
    public static final int MAX_POLICIES = 120;
    public static final int MAX_DEPTH = 4;
    public static final int MAX_COLUMNS_PER_PARENT = 4;
    private static final Pattern STABLE_KEY = Pattern.compile("[a-z][a-z0-9_]*");

    private WorkItemLayoutModels() {
    }

    public enum LayoutKind {
        create,
        detail;

        public static LayoutKind parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw failure("INVALID_LAYOUT_KIND", "Layout kind must be create or detail");
            }
        }
    }

    public enum NodeType {
        section,
        tab,
        column,
        field,
        summary;

        public static NodeType parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw failure("INVALID_LAYOUT_NODE", "Unsupported layout node type");
            }
        }
    }

    public record LayoutDefinition(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String layoutKind,
        String configHash,
        String status,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        long aggregateVersion
    ) {
    }

    public record LayoutNode(
        UUID id,
        UUID parentId,
        String nodeKey,
        String nodeType,
        UUID fieldId,
        String fieldKey,
        int sortOrder,
        JsonNode config,
        JsonNode visibilityCondition
    ) {
    }

    public record FieldAccessPolicy(
        UUID id,
        UUID fieldId,
        String fieldKey,
        String policyKey,
        JsonNode policy,
        String configHash
    ) {
    }

    public record LayoutDiagnostic(
        String code,
        String nodeKey,
        String fieldKey,
        String message
    ) {
    }

    public record LayoutAggregate(
        LayoutDefinition definition,
        List<LayoutNode> nodes,
        List<FieldAccessPolicy> policies,
        List<LayoutDiagnostic> diagnostics,
        List<String> availableActions
    ) {
    }

    public record SaveLayout(
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String layoutKind,
        List<LayoutNode> nodes,
        List<FieldAccessPolicy> policies,
        long expectedAggregateVersion,
        UUID actorId
    ) {
    }

    public static String stableKey(String value, String code, String label) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64 || !STABLE_KEY.matcher(normalized).matches()) {
            throw failure(code, label + " must match [a-z][a-z0-9_]* and be at most 64 characters");
        }
        return normalized;
    }

    public static WorkItemLayoutException failure(String code, String message) {
        return new WorkItemLayoutException(code, message);
    }

    public static WorkItemLayoutException failure(String code, String message, Throwable cause) {
        return new WorkItemLayoutException(code, message, cause);
    }

    public static final class WorkItemLayoutException extends RuntimeException {
        private final String code;

        public WorkItemLayoutException(String code, String message) {
            super(message);
            this.code = code;
        }

        public WorkItemLayoutException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
