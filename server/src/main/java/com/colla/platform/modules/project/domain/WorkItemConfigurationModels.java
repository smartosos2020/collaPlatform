package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class WorkItemConfigurationModels {
    public static final int SNAPSHOT_SCHEMA_VERSION = 1;
    public static final int LEGACY_PARTIAL_SNAPSHOT_SCHEMA_VERSION = 0;
    public static final int COMMAND_RESPONSE_SCHEMA_VERSION = 1;
    public static final int MAX_FIELDS = 120;
    public static final int MAX_OPTIONS = 2400;

    private WorkItemConfigurationModels() {
    }

    public enum DraftStatus {
        editing,
        validating,
        valid,
        invalid,
        abandoned;

        public static DraftStatus parse(String value) {
            try {
                return valueOf(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw failure("INVALID_DRAFT_STATUS", "Invalid work item configuration draft status");
            }
        }

        public boolean active() {
            return this != abandoned;
        }

        public boolean canTransitionTo(DraftStatus target) {
            if (this == target) {
                return true;
            }
            return switch (this) {
                case editing -> Set.of(validating, abandoned).contains(target);
                case validating -> Set.of(valid, invalid, editing, abandoned).contains(target);
                case valid -> Set.of(editing, validating, abandoned).contains(target);
                case invalid -> Set.of(editing, validating, abandoned).contains(target);
                case abandoned -> false;
            };
        }
    }

    public enum DiagnosticSeverity {
        warning,
        error
    }

    public record ConfigurationDiagnostic(
        String code,
        DiagnosticSeverity severity,
        String keyPath,
        String message
    ) {
    }

    public record ConfigurationSnapshot(
        int schemaVersion,
        JsonNode payload,
        String configHash
    ) {
    }

    public record ConfigurationDraft(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String status,
        int snapshotSchemaVersion,
        String configHash,
        JsonNode snapshot,
        List<ConfigurationDiagnostic> diagnostics,
        long aggregateVersion,
        UUID sourceLegacyVersionId,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt
    ) {
        public DraftStatus parsedStatus() {
            return DraftStatus.parse(status);
        }

        public boolean active() {
            return parsedStatus().active();
        }
    }

    public record ValidationResult(
        boolean valid,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        public static ValidationResult of(List<ConfigurationDiagnostic> diagnostics) {
            List<ConfigurationDiagnostic> stable = List.copyOf(diagnostics == null ? List.of() : diagnostics);
            return new ValidationResult(
                stable.stream().noneMatch(value -> value.severity() == DiagnosticSeverity.error),
                stable
            );
        }
    }

    public record DraftCommandReceipt(
        UUID id,
        UUID workspaceId,
        UUID spaceId,
        UUID typeDefinitionId,
        String requestId,
        String operation,
        String requestHash,
        String status,
        int responseSchemaVersion,
        UUID responseDraftId,
        Long responseAggregateVersion,
        String responseConfigHash,
        JsonNode responsePayload,
        UUID createdBy,
        Instant createdAt,
        Instant completedAt
    ) {
    }

    public static WorkItemConfigurationException failure(String code, String message) {
        return new WorkItemConfigurationException(code, message);
    }

    public static WorkItemConfigurationException failure(String code, String message, Throwable cause) {
        return new WorkItemConfigurationException(code, message, cause);
    }

    public static final class WorkItemConfigurationException extends RuntimeException {
        private final String code;

        public WorkItemConfigurationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public WorkItemConfigurationException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
