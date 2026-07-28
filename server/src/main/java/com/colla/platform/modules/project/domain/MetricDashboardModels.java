package com.colla.platform.modules.project.domain;

import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MetricDashboardModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_DASHBOARDS = 50;
    public static final int MAX_BINDINGS = 12;
    public static final int MAX_CHARTS = 24;
    public static final int MAX_SERIES = 50;
    public static final int MAX_POINTS = 500;
    public static final int MAX_DRILLDOWN = 100;
    public static final int MAX_SOURCE_SPACES = 20;

    private MetricDashboardModels() {
    }

    public record DataSourceBinding(
        int schemaVersion,
        String bindingKey,
        String kind,
        List<UUID> spaceIds,
        UUID savedViewId,
        UUID metricId,
        int metricVersion
    ) {
    }

    public record ChartDefinition(
        UUID id,
        String chartKey,
        String name,
        String visualization,
        String bindingKey,
        UUID metricId,
        int metricVersion,
        List<String> dimensionKeys,
        Map<String, String> filters,
        int seriesLimit,
        int pointLimit,
        boolean drilldown,
        long version
    ) {
    }

    public record Layout(
        String chartKey,
        int column,
        int row,
        int width,
        int height
    ) {
    }

    public record Filter(
        String key,
        String dimensionKey,
        String operator,
        List<String> values
    ) {
    }

    public record DashboardConfig(
        int schemaVersion,
        List<DataSourceBinding> dataSources,
        List<ChartDefinition> charts,
        List<Layout> layout,
        List<Filter> filters
    ) {
    }

    public record DashboardVersion(
        UUID id,
        UUID dashboardId,
        int versionNumber,
        String definitionHash,
        DashboardConfig config,
        Instant publishedAt,
        UUID publishedBy
    ) {
    }

    public record Dashboard(
        UUID id,
        String dashboardKey,
        String name,
        String description,
        String status,
        String sharingScope,
        long version,
        DashboardConfig draftConfig,
        DashboardVersion publishedVersion,
        Instant updatedAt
    ) {
    }

    public record DashboardPreference(
        UUID dashboardId,
        boolean compact,
        Map<String, String> filterValues,
        long version
    ) {
    }

    public record DashboardFoundation(
        int schemaVersion,
        List<Dashboard> dashboards,
        List<String> visualizations,
        List<String> sourceKinds,
        List<String> resultStatuses,
        boolean truncated,
        Map<String, Integer> budgets
    ) {
    }

    public record SaveDashboardCommand(
        int schemaVersion,
        String requestId,
        UUID dashboardId,
        long expectedVersion,
        String dashboardKey,
        String name,
        String description,
        DashboardConfig config
    ) {
    }

    public record DashboardLifecycleCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        String action
    ) {
    }

    public record SavePreferenceCommand(
        int schemaVersion,
        String requestId,
        long expectedVersion,
        boolean compact,
        Map<String, String> filterValues
    ) {
    }

    public record QueryDashboardCommand(
        int schemaVersion,
        Instant anchor,
        Map<String, String> filterValues
    ) {
    }

    public record ChartPoint(
        String key,
        String label,
        BigDecimal value,
        BigDecimal numerator,
        BigDecimal denominator,
        int sampleCount,
        String drilldownKey
    ) {
    }

    public record ChartSeries(
        String key,
        String label,
        List<ChartPoint> points
    ) {
    }

    public record ChartQueryResult(
        String chartKey,
        String name,
        String visualization,
        String status,
        String unit,
        List<ChartSeries> series,
        List<String> facets,
        int visibleSampleCount,
        boolean stale,
        boolean truncated,
        List<String> sourceVersions,
        String diagnostic
    ) {
    }

    public record DashboardQueryResult(
        int schemaVersion,
        UUID dashboardId,
        int dashboardVersion,
        List<ChartQueryResult> charts,
        String status,
        boolean stale,
        boolean truncated,
        Instant observedAt,
        String diagnostic
    ) {
    }

    public record ResolvedSource(
        List<MetricSemanticModels.MetricSample> samples,
        List<String> sourceVersions,
        boolean stale,
        boolean truncated,
        String diagnostic
    ) {
    }

    public record DrilldownCommand(
        int schemaVersion,
        Instant anchor,
        String chartKey,
        String seriesKey,
        String pointKey,
        int offset
    ) {
    }

    public record DrilldownItem(
        String sourceIdentity,
        long sourceVersion,
        Instant occurredAt
    ) {
    }

    public record DrilldownResult(
        List<DrilldownItem> items,
        Integer nextOffset,
        boolean truncated,
        String diagnostic
    ) {
    }

    public record EvaluatedMetric(
        MetricResult result,
        ResolvedSource source
    ) {
    }
}
