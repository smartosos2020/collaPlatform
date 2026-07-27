package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registered, bounded query language for observable WorkItem projections.
 *
 * <p>The contract intentionally contains no SQL, scripts, class names, or arbitrary
 * JSON paths. Fields and operators are resolved by {@code WorkItemQueryCanonicalizer}.
 */
public final class WorkItemQueryModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_CANDIDATES = 200;

    private WorkItemQueryModels() {
    }

    public record QueryDefinition(
        int schemaVersion,
        UUID typeId,
        FilterNode filter,
        List<SortSpec> sorts,
        GroupSpec group,
        List<String> select,
        int limit,
        String cursor
    ) {
    }

    public record FilterNode(
        String kind,
        String field,
        String operator,
        JsonNode value,
        List<FilterNode> children
    ) {
    }

    public record SortSpec(String field, String direction, String nulls) {
    }

    public record GroupSpec(String field, List<AggregateSpec> aggregates) {
    }

    public record AggregateSpec(String function, String field, String alias) {
    }

    public record QueryCursor(String queryHash, UUID anchorId, Instant issuedAt) {
    }

    public record QueryItem(
        UUID id,
        UUID spaceId,
        UUID typeDefinitionId,
        String displayKey,
        String title,
        String status,
        long version,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        JsonNode fields,
        Map<String, Object> selected,
        List<String> availableActions
    ) {
    }

    public record GroupBucket(String key, long count, Map<String, Object> aggregates) {
    }

    public record QueryResult(
        String queryHash,
        List<QueryItem> items,
        List<GroupBucket> groups,
        String nextCursor,
        int evaluatedCandidates,
        boolean candidateBoundReached
    ) {
    }

    public record QueryPlan(
        String queryHash,
        QueryDefinition normalized,
        List<String> capabilities,
        int candidateBudget,
        boolean executable
    ) {
    }
}
