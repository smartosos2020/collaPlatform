package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class WorkItemFieldModels {
    public static final int CONFIG_SCHEMA_VERSION = 1;
    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    private WorkItemFieldModels() {
    }

    public enum FieldStatus {
        active,
        disabled,
        retired;

        public static FieldStatus parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw validation("INVALID_FIELD_STATUS", "Invalid work item field status");
            }
        }

        public boolean canTransitionTo(FieldStatus target) {
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

    public record FieldDefinition(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String fieldKey,
        String name,
        String description,
        String fieldType,
        JsonNode config,
        String configHash,
        int sortOrder,
        String status,
        boolean system,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        long aggregateVersion
    ) {
        public FieldStatus parsedStatus() {
            return FieldStatus.parse(status);
        }
    }

    public record CreateFieldDefinition(
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID actorId,
        String fieldKey,
        String name,
        String description,
        String fieldType,
        JsonNode config,
        int sortOrder,
        boolean system
    ) {
    }

    public static String normalizeFieldKey(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64 || !FIELD_KEY_PATTERN.matcher(normalized).matches()) {
            throw validation("INVALID_FIELD_KEY", "Field key must match [a-z][a-z0-9_]* and be at most 64 characters");
        }
        return normalized;
    }

    public static String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw validation("INVALID_FIELD_NAME", "Field name must contain 1 to 128 characters");
        }
        return normalized;
    }

    public static String normalizeDescription(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 2000) {
            throw validation("INVALID_FIELD_DESCRIPTION", "Field description must be at most 2000 characters");
        }
        return normalized;
    }

    public static int normalizeSortOrder(int value) {
        if (value < 0) {
            throw validation("INVALID_FIELD_SORT_ORDER", "Field sort order must be non-negative");
        }
        return value;
    }

    public static WorkItemFieldException validation(String code, String message) {
        return new WorkItemFieldException(code, message);
    }

    public static final class WorkItemFieldException extends RuntimeException {
        private final String code;

        public WorkItemFieldException(String code, String message) {
            super(message);
            this.code = code;
        }

        public WorkItemFieldException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
