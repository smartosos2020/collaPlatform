package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkItemViewModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_COLUMNS = 20;
    public static final int MAX_SELECTION = 100;
    public static final int MAX_EXPORT_ROWS = 200;

    private WorkItemViewModels() {
    }

    public enum ViewMode {
        table,
        list
    }

    public record ColumnSpec(
        String key,
        String label,
        int width,
        boolean frozen,
        String format
    ) {
    }

    public record CellProjection(
        String columnKey,
        Object value,
        String displayValue,
        String disclosure
    ) {
    }

    public record ViewRow(
        UUID workItemId,
        String displayKey,
        String title,
        long version,
        List<CellProjection> cells,
        List<String> availableActions
    ) {
    }

    public record ViewRequest(
        int schemaVersion,
        ViewMode mode,
        String density,
        List<ColumnSpec> columns,
        QueryDefinition query
    ) {
    }

    public record ViewResult(
        int schemaVersion,
        ViewMode mode,
        String density,
        List<ColumnSpec> columns,
        List<ViewRow> rows,
        String nextCursor,
        String queryHash
    ) {
    }

    public record ViewPreference(
        String viewKey,
        ViewMode mode,
        String density,
        List<ColumnSpec> columns,
        long version,
        Instant updatedAt
    ) {
    }

    public record PreferenceCommand(
        String requestId,
        long expectedVersion,
        ViewMode mode,
        String density,
        List<ColumnSpec> columns
    ) {
    }

    public record BulkSelection(UUID workItemId, long expectedVersion) {
    }

    public record BulkCommand(
        String requestId,
        String action,
        List<BulkSelection> selections
    ) {
    }

    public record BulkItemResult(
        UUID workItemId,
        String status,
        String reasonCode,
        Long version
    ) {
    }

    public record BulkResult(
        String requestId,
        int succeeded,
        int failed,
        List<BulkItemResult> items
    ) {
    }

    public record ExportCommand(
        String requestId,
        QueryDefinition query,
        List<ColumnSpec> columns
    ) {
    }

    public record ExportJob(
        UUID id,
        String status,
        int maxRows,
        Instant expiresAt,
        String downloadPath
    ) {
    }

    public record ExportDownload(
        String fileName,
        String contentType,
        String content
    ) {
    }
}
