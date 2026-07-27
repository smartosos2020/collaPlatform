package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ColumnSpec;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkItemSavedViewModels {
    public static final int SCHEMA_VERSION = 1;

    private WorkItemSavedViewModels() {
    }

    public record PresentationConfig(
        int schemaVersion,
        String mode,
        String density,
        List<ColumnSpec> columns,
        String relationKey,
        int maxDepth
    ) {
    }

    public record ViewShare(
        UUID subjectUserId,
        String permission,
        String status,
        long version,
        Instant sharedAt,
        Instant revokedAt
    ) {
    }

    public record SavedView(
        UUID id,
        UUID spaceId,
        UUID ownerUserId,
        String scope,
        String name,
        String description,
        String status,
        long aggregateVersion,
        long versionNumber,
        String configHash,
        QueryDefinition query,
        PresentationConfig presentation,
        List<ViewShare> shares,
        boolean canUse,
        boolean canManage,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record CreateCommand(
        String requestId,
        String name,
        String description,
        String scope,
        QueryDefinition query,
        PresentationConfig presentation
    ) {
    }

    public record UpdateCommand(
        String requestId,
        long expectedVersion,
        String name,
        String description,
        String scope,
        QueryDefinition query,
        PresentationConfig presentation
    ) {
    }

    public record CopyCommand(String requestId, String name) {
    }

    public record ShareCommand(
        String requestId,
        long expectedVersion,
        UUID subjectUserId,
        String permission
    ) {
    }

    public record RevokeShareCommand(
        String requestId,
        long expectedVersion,
        UUID subjectUserId
    ) {
    }

    public record TransferCommand(
        String requestId,
        long expectedVersion,
        UUID newOwnerUserId
    ) {
    }

    public record DeleteCommand(String requestId, long expectedVersion) {
    }

    public record SavedViewExecution(SavedView view, Object result) {
    }
}
