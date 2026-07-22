package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class WorkItemTypeModels {
    public static final int CONFIG_SCHEMA_VERSION = 1;
    private static final Pattern TYPE_KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    private WorkItemTypeModels() {
    }

    public enum WorkItemTypeStatus {
        active,
        disabled,
        retired;

        public static WorkItemTypeStatus parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw validation("INVALID_STATUS", "Invalid work item type status");
            }
        }

        public boolean canTransitionTo(WorkItemTypeStatus target) {
            if (this == target) {
                return true;
            }
            return switch (this) {
                case active -> Set.of(disabled, retired).contains(target);
                case disabled -> Set.of(active, retired).contains(target);
                case retired -> false;
            };
        }
    }

    public enum WorkItemTypeVersionStatus {
        draft,
        published,
        superseded;

        public static WorkItemTypeVersionStatus parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw validation("INVALID_VERSION_STATUS", "Invalid work item type version status");
            }
        }

        public boolean immutable() {
            return this == published || this == superseded;
        }
    }

    public record WorkItemTypeDefinition(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        String typeKey,
        String name,
        String icon,
        String description,
        int sortOrder,
        String status,
        boolean system,
        UUID currentVersionId,
        int currentVersionNumber,
        String currentVersionStatus,
        String currentConfigHash,
        JsonNode currentConfig,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        long aggregateVersion
    ) {
        public WorkItemTypeStatus parsedStatus() {
            return WorkItemTypeStatus.parse(status);
        }
    }

    public record WorkItemTypeVersion(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        int versionNumber,
        String configHash,
        String status,
        JsonNode config,
        UUID createdBy,
        Instant createdAt,
        UUID publishedBy,
        Instant publishedAt
    ) {
        public WorkItemTypeVersionStatus parsedStatus() {
            return WorkItemTypeVersionStatus.parse(status);
        }
    }

    public record CreateWorkItemType(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String typeKey,
        String name,
        String icon,
        String description,
        int sortOrder,
        boolean system
    ) {
    }

    public static String normalizeTypeKey(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64 || !TYPE_KEY_PATTERN.matcher(normalized).matches()) {
            throw validation("INVALID_TYPE_KEY", "Work item type key must match [a-z][a-z0-9_]* and be at most 64 characters");
        }
        return normalized;
    }

    public static String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw validation("INVALID_NAME", "Work item type name must contain 1 to 128 characters");
        }
        return normalized;
    }

    public static String normalizeIcon(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 64) {
            throw validation("INVALID_ICON", "Work item type icon must be at most 64 characters");
        }
        return normalized;
    }

    public static String normalizeDescription(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 2000) {
            throw validation("INVALID_DESCRIPTION", "Work item type description must be at most 2000 characters");
        }
        return normalized;
    }

    public static int normalizeSortOrder(int value) {
        if (value < 0) {
            throw validation("INVALID_SORT_ORDER", "Work item type sort order must be non-negative");
        }
        return value;
    }

    public static WorkItemTypeException validation(String code, String message) {
        return new WorkItemTypeException(code, message);
    }

    public static final class WorkItemTypeException extends RuntimeException {
        private final String code;

        public WorkItemTypeException(String code, String message) {
            super(message);
            this.code = code;
        }

        public WorkItemTypeException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
