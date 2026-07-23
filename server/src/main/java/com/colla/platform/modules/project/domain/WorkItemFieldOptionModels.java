package com.colla.platform.modules.project.domain;

import static com.colla.platform.modules.project.domain.WorkItemFieldModels.validation;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class WorkItemFieldOptionModels {
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern COLOR_PATTERN = Pattern.compile("#[0-9A-F]{6}");

    private WorkItemFieldOptionModels() {
    }

    public record FieldOption(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        UUID fieldDefinitionId,
        String optionKey,
        String name,
        String color,
        int sortOrder,
        String status,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }

    public record ConfigureFieldOption(
        String optionKey,
        String name,
        String color,
        int sortOrder,
        String status
    ) {
    }

    public static ConfigureFieldOption normalize(ConfigureFieldOption option) {
        if (option == null) {
            throw validation("INVALID_FIELD_OPTION", "Field option is required");
        }
        String key = option.optionKey() == null ? "" : option.optionKey().trim().toLowerCase(Locale.ROOT);
        String name = option.name() == null ? "" : option.name().trim();
        String color = option.color() == null ? "" : option.color().trim().toUpperCase(Locale.ROOT);
        String status = option.status() == null ? "" : option.status().trim().toLowerCase(Locale.ROOT);
        if (key.length() > 64 || !KEY_PATTERN.matcher(key).matches()) {
            throw validation("INVALID_FIELD_OPTION", "Option key must match [a-z][a-z0-9_]* and be at most 64 characters");
        }
        if (name.isEmpty() || name.length() > 128) {
            throw validation("INVALID_FIELD_OPTION", "Option name must contain 1 to 128 characters");
        }
        if (!COLOR_PATTERN.matcher(color).matches()) {
            throw validation("INVALID_FIELD_OPTION", "Option color must use #RRGGBB");
        }
        if (option.sortOrder() < 0) {
            throw validation("INVALID_FIELD_OPTION", "Option sort order must be non-negative");
        }
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw validation("INVALID_FIELD_OPTION", "Option status must be active or disabled");
        }
        return new ConfigureFieldOption(key, name, color, option.sortOrder(), status);
    }
}
