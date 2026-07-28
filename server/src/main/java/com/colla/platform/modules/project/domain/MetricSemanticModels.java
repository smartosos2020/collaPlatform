package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MetricSemanticModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_METRICS = 100;
    public static final int MAX_DIMENSIONS = 4;
    public static final int MAX_SAMPLES = 500;

    private MetricSemanticModels() {
    }

    public record Measure(
        String key, String label, String valueType, String unit,
        String sourceContract, boolean nullable
    ) {
    }

    public record Dimension(
        String key, int version, String label, String valueType,
        String sourceContract, int cardinalityLimit
    ) {
    }

    public record MetricExpression(
        int schemaVersion, String aggregation, String measureKey,
        String numeratorMeasureKey, String denominatorMeasureKey,
        List<String> dimensionKeys
    ) {
    }

    public record Window(
        int schemaVersion, String kind, int amount, String unit,
        String timeZone, String calendarKey, String comparison
    ) {
    }

    public record WindowBounds(
        Instant startInclusive, Instant endExclusive,
        Instant comparisonStartInclusive, Instant comparisonEndExclusive,
        String timeZone, String diagnostic
    ) {
    }

    public record MetricVersion(
        UUID id, UUID metricId, int versionNumber, String definitionHash,
        MetricExpression expression, Window window, Instant publishedAt,
        UUID publishedBy
    ) {
    }

    public record MetricDefinition(
        UUID id, String metricKey, String name, String description, String unit,
        String status, long version, MetricExpression draftExpression,
        Window draftWindow, MetricVersion publishedVersion, Instant updatedAt
    ) {
    }

    public record MetricFoundation(
        int schemaVersion, List<Measure> measures, List<Dimension> dimensions,
        List<MetricDefinition> metrics, boolean truncated,
        List<String> resultStatuses, List<String> prohibitedCapabilities
    ) {
    }

    public record SaveMetricCommand(
        int schemaVersion, String requestId, UUID metricId, long expectedVersion,
        String metricKey, String name, String description, String unit,
        MetricExpression expression, Window window
    ) {
    }

    public record MetricLifecycleCommand(
        int schemaVersion, String requestId, long expectedVersion, String action
    ) {
    }

    public record MetricSample(
        Instant occurredAt, BigDecimal value, BigDecimal numerator,
        BigDecimal denominator, Map<String, String> dimensions,
        String sourceIdentity, long sourceVersion, boolean authorized,
        boolean suppressed, boolean stale, boolean truncated
    ) {
    }

    public record PreviewMetricCommand(
        int schemaVersion, Instant anchor, List<MetricSample> samples
    ) {
    }

    public record SourceEvidence(
        String sourceContract, List<String> sourceVersions,
        int considered, int included, boolean stale, boolean truncated,
        String diagnostic
    ) {
    }

    public record MetricResult(
        int schemaVersion, UUID metricId, Integer metricVersion,
        String status, BigDecimal value, BigDecimal numerator,
        BigDecimal denominator, String unit, WindowBounds window,
        List<SourceEvidence> sources, int sampleCount,
        boolean suppressed, boolean stale, boolean truncated,
        Instant observedAt, String diagnostic
    ) {
    }

    public record MetricVersionDiff(
        int fromVersion, int toVersion, JsonNode expressionChanges,
        JsonNode windowChanges
    ) {
    }
}
