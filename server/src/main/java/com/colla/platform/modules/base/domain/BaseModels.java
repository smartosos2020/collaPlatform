package com.colla.platform.modules.base.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BaseModels {
    private BaseModels() {
    }

    public record BaseSummary(
        UUID id,
        String name,
        String description,
        String status,
        String permissionLevel,
        int tableCount,
        int recordCount,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        UUID updatedBy,
        String updatedByName,
        Instant updatedAt
    ) {
    }

    public record BaseDetail(BaseSummary base, List<BaseTableSummary> tables, List<BaseMember> members) {
    }

    public record BaseMember(
        UUID id,
        UUID userId,
        String username,
        String displayName,
        String permissionLevel,
        Instant createdAt
    ) {
    }

    public record BaseTableSummary(
        UUID id,
        UUID baseId,
        String name,
        UUID primaryFieldId,
        int fieldCount,
        int recordCount,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record BaseTableDetail(
        BaseTableSummary table,
        List<BaseField> fields,
        List<BaseView> views
    ) {
    }

    public record BaseField(
        UUID id,
        UUID tableId,
        String fieldKey,
        String name,
        String fieldType,
        Map<String, Object> config,
        boolean required,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record BaseRecord(
        UUID id,
        UUID tableId,
        int recordNo,
        String primaryText,
        Map<String, Object> values,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        UUID updatedBy,
        String updatedByName,
        Instant updatedAt
    ) {
    }

    public record BaseRecordPage(List<BaseRecord> items, int total, int limit, int offset) {
    }

    public record BaseKanbanView(UUID tableId, UUID groupFieldId, List<BaseKanbanColumn> columns) {
    }

    public record BaseKanbanColumn(String key, String title, List<BaseRecord> records) {
    }

    public record BaseCalendarView(UUID tableId, UUID dateFieldId, List<BaseCalendarBucket> buckets) {
    }

    public record BaseCalendarBucket(String date, List<BaseRecord> records) {
    }

    public record BaseView(
        UUID id,
        UUID tableId,
        String name,
        List<BaseFilter> filters,
        List<BaseSort> sorts,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record BaseFilter(UUID fieldId, String operator, Object value) {
    }

    public record BaseSort(UUID fieldId, String direction) {
    }
}
