package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.MetricDashboardModels.MAX_POINTS;
import static com.colla.platform.modules.project.domain.MetricDashboardModels.MAX_SOURCE_SPACES;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.MetricDashboardModels.DataSourceBinding;
import com.colla.platform.modules.project.domain.MetricDashboardModels.ResolvedSource;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricSample;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.colla.platform.shared.auth.CurrentUser;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MetricDataSourceResolver {
    private final WorkItemQueryService queries;
    private final WorkItemSavedViewService savedViews;
    private final CrossTeamPanoramaService panorama;

    public MetricDataSourceResolver(
        WorkItemQueryService queries,
        WorkItemSavedViewService savedViews,
        CrossTeamPanoramaService panorama
    ) {
        this.queries = queries;
        this.savedViews = savedViews;
        this.panorama = panorama;
    }

    public ResolvedSource resolve(
        CurrentUser user,
        UUID dashboardSpaceId,
        DataSourceBinding binding
    ) {
        requireBinding(binding, dashboardSpaceId);
        return switch (binding.kind()) {
            case "work_item_query" -> workItems(user, binding, null);
            case "saved_view" -> savedView(user, binding);
            case "cross_space_panorama" -> panorama(user, dashboardSpaceId, binding);
            default -> throw failure(
                "DASHBOARD_SOURCE_INVALID",
                "Dashboard source kind is not registered"
            );
        };
    }

    private ResolvedSource savedView(CurrentUser user, DataSourceBinding binding) {
        if (binding.savedViewId() == null || binding.spaceIds().size() != 1) {
            throw failure(
                "DASHBOARD_SOURCE_INVALID",
                "Saved view binding requires exactly one source space and a view identity"
            );
        }
        UUID sourceSpace = binding.spaceIds().getFirst();
        var view = savedViews.get(user, sourceSpace, binding.savedViewId());
        return workItems(user, binding, view.query());
    }

    private ResolvedSource workItems(
        CurrentUser user,
        DataSourceBinding binding,
        QueryDefinition savedQuery
    ) {
        List<MetricSample> samples = new ArrayList<>();
        List<String> versions = new ArrayList<>();
        boolean truncated = false;
        for (UUID sourceSpace : binding.spaceIds()) {
            QueryDefinition query = savedQuery == null ? boundedQuery() : bounded(savedQuery);
            var result = queries.execute(user, sourceSpace, query);
            truncated = truncated || result.candidateBoundReached() || result.nextCursor() != null;
            for (QueryItem item : result.items()) {
                if (samples.size() >= MAX_POINTS) {
                    truncated = true;
                    break;
                }
                Map<String, String> dimensions = new LinkedHashMap<>();
                dimensions.put("status", item.status());
                dimensions.put("type", item.typeDefinitionId().toString());
                dimensions.put("space", item.spaceId().toString());
                dimensions.put(
                    "calendar_day",
                    item.updatedAt().atZone(ZoneOffset.UTC).toLocalDate().toString()
                );
                samples.add(new MetricSample(
                    item.updatedAt(),
                    BigDecimal.ONE,
                    null,
                    null,
                    Map.copyOf(dimensions),
                    item.id().toString(),
                    item.version(),
                    true,
                    false,
                    false,
                    result.candidateBoundReached()
                ));
            }
            versions.add(
                "WorkItemQueryService.execute:" + sourceSpace + ":" + result.queryHash()
            );
        }
        return new ResolvedSource(
            List.copyOf(samples),
            versions.stream().distinct().sorted().toList(),
            false,
            truncated,
            "current S11-scoped WorkItem query output; authorization precedes aggregation"
        );
    }

    private ResolvedSource panorama(
        CurrentUser user,
        UUID dashboardSpaceId,
        DataSourceBinding binding
    ) {
        var result = panorama.get(user, dashboardSpaceId);
        var allowedSpaces = SetSupport.copy(binding.spaceIds());
        var eligible = result.slices().stream()
            .filter(slice -> allowedSpaces.contains(slice.sourceSpaceId())
                || allowedSpaces.contains(slice.targetSpaceId()))
            .filter(slice -> java.util.Set.of("active", "running")
                .contains(slice.status()))
            .toList();
        List<MetricSample> samples = eligible.stream()
            .limit(MAX_POINTS)
            .map(slice -> new MetricSample(
                slice.observedAt(),
                BigDecimal.ONE,
                null,
                null,
                Map.of(
                    "status", slice.status(),
                    "type", slice.kind(),
                    "space", slice.sourceSpaceId().toString(),
                    "calendar_day", slice.observedAt().atZone(ZoneOffset.UTC)
                        .toLocalDate().toString()
                ),
                slice.identity().toString(),
                slice.version(),
                true,
                false,
                false,
                result.health().truncated()
            )).toList();
        boolean truncated = result.health().truncated() || eligible.size() > samples.size();
        return new ResolvedSource(
            samples,
            result.audit().stream()
                .map(value -> value.source() + ":" + value.version())
                .distinct().sorted().limit(50).toList(),
            false,
            truncated,
            "current active-grant panorama projection; no cross-space private table access"
        );
    }

    private QueryDefinition boundedQuery() {
        return new QueryDefinition(
            1,
            null,
            null,
            List.of(new SortSpec("updatedAt", "desc", "last")),
            null,
            List.of("id", "status", "typeId", "updatedAt"),
            100,
            null
        );
    }

    private QueryDefinition bounded(QueryDefinition value) {
        return new QueryDefinition(
            value.schemaVersion(),
            value.typeId(),
            value.filter(),
            value.sorts(),
            value.group(),
            value.select(),
            Math.min(value.limit(), 100),
            null
        );
    }

    private void requireBinding(DataSourceBinding binding, UUID dashboardSpaceId) {
        if (binding == null || binding.schemaVersion() != 1
            || binding.spaceIds() == null || binding.spaceIds().isEmpty()
            || binding.spaceIds().size() > MAX_SOURCE_SPACES
            || binding.spaceIds().stream().anyMatch(java.util.Objects::isNull)
            || !binding.spaceIds().contains(dashboardSpaceId)) {
            throw failure(
                "DASHBOARD_SOURCE_INVALID",
                "Dashboard source must include its current space and remain within its bound"
            );
        }
    }

    private static final class SetSupport {
        private SetSupport() {
        }

        private static java.util.Set<UUID> copy(List<UUID> values) {
            return java.util.Set.copyOf(values);
        }
    }
}
