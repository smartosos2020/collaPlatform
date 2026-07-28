package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.MetricDashboardModels.MAX_BINDINGS;
import static com.colla.platform.modules.project.domain.MetricDashboardModels.MAX_CHARTS;
import static com.colla.platform.modules.project.domain.MetricDashboardModels.MAX_DASHBOARDS;
import static com.colla.platform.modules.project.domain.MetricDashboardModels.MAX_DRILLDOWN;
import static com.colla.platform.modules.project.domain.MetricDashboardModels.MAX_POINTS;
import static com.colla.platform.modules.project.domain.MetricDashboardModels.MAX_SERIES;
import static com.colla.platform.modules.project.domain.MetricDashboardModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.MetricDashboardModels.ChartDefinition;
import com.colla.platform.modules.project.domain.MetricDashboardModels.ChartPoint;
import com.colla.platform.modules.project.domain.MetricDashboardModels.ChartQueryResult;
import com.colla.platform.modules.project.domain.MetricDashboardModels.ChartSeries;
import com.colla.platform.modules.project.domain.MetricDashboardModels.Dashboard;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardConfig;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardFoundation;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardLifecycleCommand;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardPreference;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardQueryResult;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DashboardVersion;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DataSourceBinding;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DrilldownCommand;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DrilldownItem;
import com.colla.platform.modules.project.domain.MetricDashboardModels.DrilldownResult;
import com.colla.platform.modules.project.domain.MetricDashboardModels.Filter;
import com.colla.platform.modules.project.domain.MetricDashboardModels.Layout;
import com.colla.platform.modules.project.domain.MetricDashboardModels.QueryDashboardCommand;
import com.colla.platform.modules.project.domain.MetricDashboardModels.ResolvedSource;
import com.colla.platform.modules.project.domain.MetricDashboardModels.SaveDashboardCommand;
import com.colla.platform.modules.project.domain.MetricDashboardModels.SavePreferenceCommand;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricResult;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricSample;
import com.colla.platform.modules.project.infrastructure.MetricDashboardRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricDashboardService {
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.-]{1,63}");
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{8,120}");
    private static final Set<String> SOURCE_KINDS = Set.of(
        "work_item_query", "saved_view", "cross_space_panorama"
    );
    private static final List<String> VISUALIZATIONS = List.of(
        "table", "metric_card", "line", "bar", "stacked_bar", "distribution"
    );
    private static final Set<String> FILTER_OPERATORS = Set.of("eq", "in");

    private final MetricDashboardRepository repository;
    private final MetricDataSourceResolver sources;
    private final MetricSemanticService metrics;
    private final WorkItemRelationAccessDecisionService access;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper json;

    public MetricDashboardService(
        MetricDashboardRepository repository,
        MetricDataSourceResolver sources,
        MetricSemanticService metrics,
        WorkItemRelationAccessDecisionService access,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper json
    ) {
        this.repository = repository;
        this.sources = sources;
        this.metrics = metrics;
        this.access = access;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.json = json;
    }

    public DashboardFoundation foundation(CurrentUser user, UUID spaceId) {
        access.requireVisible(user, spaceId);
        List<Dashboard> values = repository.list(
            user.workspaceId(), spaceId, MAX_DASHBOARDS + 1
        );
        boolean truncated = values.size() > MAX_DASHBOARDS;
        return new DashboardFoundation(
            SCHEMA_VERSION,
            truncated ? values.subList(0, MAX_DASHBOARDS) : values,
            VISUALIZATIONS,
            SOURCE_KINDS.stream().sorted().toList(),
            List.of("ready", "unknown", "no_sample", "suppressed", "stale", "truncated"),
            truncated,
            Map.of(
                "dashboards", MAX_DASHBOARDS,
                "bindings", MAX_BINDINGS,
                "charts", MAX_CHARTS,
                "series", MAX_SERIES,
                "points", MAX_POINTS,
                "drilldown", MAX_DRILLDOWN
            )
        );
    }

    @Transactional
    public Dashboard save(
        CurrentUser user,
        UUID spaceId,
        SaveDashboardCommand command
    ) {
        access.requireManager(user, spaceId);
        validateSave(command, spaceId);
        UUID dashboardId = command.dashboardId() == null
            ? stableId(user, spaceId, "dashboard", command.requestId())
            : command.dashboardId();
        DashboardConfig normalized = normalize(dashboardId, command.config());
        String requestHash = hash(Map.of(
            "dashboardId", dashboardId,
            "dashboardKey", command.dashboardKey(),
            "name", command.name(),
            "description", command.description(),
            "expectedVersion", command.expectedVersion(),
            "config", normalized
        ));
        Optional<MetricDashboardRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(),
                "save_dashboard", command.requestId()
            );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), Dashboard.class);
        }
        Dashboard result = repository.save(
            user.workspaceId(), spaceId, user.id(), dashboardId,
            command.dashboardKey(), command.name().trim(),
            command.description() == null ? "" : command.description().trim(),
            normalized, command.expectedVersion(),
            command.requestId(), requestHash
        );
        emit(user, spaceId, dashboardId, result.version(), "saved", command.requestId());
        return result;
    }

    @Transactional
    public DashboardVersion publish(
        CurrentUser user,
        UUID spaceId,
        UUID dashboardId,
        DashboardLifecycleCommand command
    ) {
        access.requireManager(user, spaceId);
        validateLifecycle(command, "publish");
        Dashboard dashboard = requireDashboard(user, spaceId, dashboardId);
        if (dashboard.version() != command.expectedVersion()) {
            throw failure("DASHBOARD_VERSION_CONFLICT", "Dashboard changed; refresh before publishing");
        }
        String requestHash = hash(command);
        Optional<MetricDashboardRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(),
                "publish_dashboard", command.requestId()
            );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), DashboardVersion.class);
        }
        verifyPublishedMetrics(user, spaceId, dashboard.draftConfig());
        String definitionHash = hash(Map.of(
            "dashboardKey", dashboard.dashboardKey(),
            "config", dashboard.draftConfig()
        ));
        DashboardVersion result = repository.publish(
            user.workspaceId(), spaceId, user.id(), dashboardId,
            command.expectedVersion(), definitionHash,
            command.requestId(), requestHash
        );
        emit(
            user, spaceId, dashboardId, result.versionNumber(),
            "published", command.requestId()
        );
        return result;
    }

    @Transactional
    public Dashboard lifecycle(
        CurrentUser user,
        UUID spaceId,
        UUID dashboardId,
        DashboardLifecycleCommand command
    ) {
        access.requireManager(user, spaceId);
        validateLifecycle(command, command == null ? "" : command.action());
        if (!Set.of("disable", "revise", "archive", "share", "unshare")
            .contains(command.action())) {
            throw failure("DASHBOARD_COMMAND_INVALID", "Dashboard lifecycle action is invalid");
        }
        requireDashboard(user, spaceId, dashboardId);
        String operation = command.action() + "_dashboard";
        String requestHash = hash(command);
        Optional<MetricDashboardRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(), operation, command.requestId()
            );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), Dashboard.class);
        }
        Dashboard result = repository.lifecycle(
            user.workspaceId(), spaceId, user.id(), dashboardId,
            command.action(), command.expectedVersion(),
            command.requestId(), requestHash
        );
        emit(
            user, spaceId, dashboardId, result.version(),
            command.action() + "d", command.requestId()
        );
        return result;
    }

    public DashboardQueryResult query(
        CurrentUser user,
        UUID spaceId,
        UUID dashboardId,
        QueryDashboardCommand command
    ) {
        access.requireVisible(user, spaceId);
        Dashboard dashboard = requireDashboard(user, spaceId, dashboardId);
        if (dashboard.publishedVersion() == null
            || !"active".equals(dashboard.status())) {
            throw failure("DASHBOARD_VERSION_UNAVAILABLE", "Published dashboard is unavailable");
        }
        validateQuery(command);
        DashboardConfig config = dashboard.publishedVersion().config();
        Map<String, DataSourceBinding> bindings = bindingMap(config);
        List<ChartQueryResult> charts = new ArrayList<>();
        for (ChartDefinition chart : config.charts()) {
            DataSourceBinding binding = bindings.get(chart.bindingKey());
            ResolvedSource source = sources.resolve(user, spaceId, binding);
            List<MetricSample> visible = filter(
                source.samples(), chart.filters(), config.filters(), command.filterValues()
            );
            charts.add(chart(
                user, spaceId, chart, binding, visible, source, command.anchor()
            ));
        }
        boolean stale = charts.stream().anyMatch(ChartQueryResult::stale);
        boolean truncated = charts.stream().anyMatch(ChartQueryResult::truncated);
        String status = charts.stream().allMatch(value -> "ready".equals(value.status()))
            ? "ready"
            : truncated ? "truncated"
            : stale ? "stale"
            : charts.stream().anyMatch(value -> "suppressed".equals(value.status()))
                ? "suppressed" : "unknown";
        return new DashboardQueryResult(
            SCHEMA_VERSION,
            dashboard.id(),
            dashboard.publishedVersion().versionNumber(),
            List.copyOf(charts),
            status,
            stale,
            truncated,
            Instant.now(),
            "all sources recalibrated through current owner contracts; cache does not authorize"
        );
    }

    public DrilldownResult drilldown(
        CurrentUser user,
        UUID spaceId,
        UUID dashboardId,
        DrilldownCommand command
    ) {
        access.requireVisible(user, spaceId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || command.anchor() == null || command.offset() < 0
            || command.chartKey() == null) {
            throw failure("DASHBOARD_DRILLDOWN_INVALID", "Dashboard drilldown is invalid");
        }
        Dashboard dashboard = requireDashboard(user, spaceId, dashboardId);
        DashboardConfig config = requirePublished(dashboard).config();
        ChartDefinition chart = config.charts().stream()
            .filter(value -> value.chartKey().equals(command.chartKey()))
            .findFirst()
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Chart is not available"));
        if (!chart.drilldown()) {
            throw failure("DASHBOARD_DRILLDOWN_DISABLED", "Chart drilldown is disabled");
        }
        ResolvedSource source = sources.resolve(
            user, spaceId, bindingMap(config).get(chart.bindingKey())
        );
        List<MetricSample> matched = source.samples().stream()
            .filter(sample -> matchesGroup(
                sample,
                chart.dimensionKeys(),
                command.seriesKey(),
                command.pointKey()
            ))
            .toList();
        int start = Math.min(command.offset(), matched.size());
        int end = Math.min(start + MAX_DRILLDOWN, matched.size());
        List<DrilldownItem> items = matched.subList(start, end).stream()
            .map(sample -> new DrilldownItem(
                sample.sourceIdentity(), sample.sourceVersion(), sample.occurredAt()
            )).toList();
        return new DrilldownResult(
            items,
            end < matched.size() ? end : null,
            source.truncated() || end < matched.size(),
            "opaque identities from the current authorized source projection only"
        );
    }

    public DashboardPreference preference(
        CurrentUser user,
        UUID spaceId,
        UUID dashboardId
    ) {
        access.requireVisible(user, spaceId);
        requireDashboard(user, spaceId, dashboardId);
        return repository.preference(
            user.workspaceId(), spaceId, dashboardId, user.id()
        );
    }

    @Transactional
    public DashboardPreference savePreference(
        CurrentUser user,
        UUID spaceId,
        UUID dashboardId,
        SavePreferenceCommand command
    ) {
        access.requireVisible(user, spaceId);
        requireDashboard(user, spaceId, dashboardId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 0
            || command.filterValues() == null || command.filterValues().size() > 12) {
            throw failure("DASHBOARD_PREFERENCE_INVALID", "Dashboard preference is invalid");
        }
        return repository.savePreference(
            user.workspaceId(), spaceId, dashboardId, user.id(),
            command.compact(), Map.copyOf(command.filterValues()),
            command.expectedVersion()
        );
    }

    private ChartQueryResult chart(
        CurrentUser user,
        UUID spaceId,
        ChartDefinition chart,
        DataSourceBinding binding,
        List<MetricSample> samples,
        ResolvedSource source,
        Instant anchor
    ) {
        MetricResult total = metrics.evaluatePublished(
            user, spaceId, binding.metricId(), binding.metricVersion(), anchor, samples
        );
        Map<String, Map<String, List<MetricSample>>> grouped = new LinkedHashMap<>();
        for (MetricSample sample : samples) {
            String seriesKey = dimension(sample, chart.dimensionKeys(), 0);
            String pointKey = dimension(sample, chart.dimensionKeys(), 1);
            grouped.computeIfAbsent(seriesKey, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(pointKey, ignored -> new ArrayList<>())
                .add(sample);
        }
        List<ChartSeries> series = new ArrayList<>();
        for (var seriesEntry : grouped.entrySet()) {
            if (series.size() >= chart.seriesLimit()) break;
            List<ChartPoint> points = new ArrayList<>();
            for (var pointEntry : seriesEntry.getValue().entrySet()) {
                if (points.size() >= chart.pointLimit()) break;
                MetricResult value = metrics.evaluatePublished(
                    user, spaceId, binding.metricId(), binding.metricVersion(),
                    anchor, pointEntry.getValue()
                );
                points.add(new ChartPoint(
                    pointEntry.getKey(),
                    pointEntry.getKey(),
                    value.value(),
                    value.numerator(),
                    value.denominator(),
                    value.sampleCount(),
                    chart.drilldown()
                        ? chart.chartKey() + ":" + seriesEntry.getKey() + ":" + pointEntry.getKey()
                        : null
                ));
            }
            series.add(new ChartSeries(
                seriesEntry.getKey(), seriesEntry.getKey(), List.copyOf(points)
            ));
        }
        boolean shapeTruncated = grouped.size() > chart.seriesLimit()
            || grouped.values().stream().anyMatch(value -> value.size() > chart.pointLimit());
        boolean truncated = source.truncated() || shapeTruncated
            || "truncated".equals(total.status());
        String status = truncated ? "truncated" : total.status();
        List<String> facets = samples.stream()
            .flatMap(sample -> sample.dimensions().entrySet().stream())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .distinct().sorted().limit(MAX_SERIES).toList();
        return new ChartQueryResult(
            chart.chartKey(),
            chart.name(),
            chart.visualization(),
            status,
            total.unit(),
            "ready".equals(status) ? List.copyOf(series) : List.of(),
            "ready".equals(status) ? facets : List.of(),
            "ready".equals(status) ? samples.size() : 0,
            source.stale() || total.stale(),
            truncated,
            "ready".equals(status) ? source.sourceVersions() : List.of(),
            "ready".equals(status)
                ? source.diagnostic()
                : "incomplete evidence hides series, facets, counts and source versions"
        );
    }

    private List<MetricSample> filter(
        List<MetricSample> samples,
        Map<String, String> chartFilters,
        List<Filter> definitions,
        Map<String, String> requestFilters
    ) {
        Map<String, String> merged = new LinkedHashMap<>(chartFilters);
        for (Filter definition : definitions) {
            String requested = requestFilters.get(definition.key());
            if (requested != null && definition.values().contains(requested)) {
                merged.put(definition.dimensionKey(), requested);
            }
        }
        return samples.stream().filter(sample -> merged.entrySet().stream()
            .allMatch(entry -> entry.getValue().equals(
                sample.dimensions().get(entry.getKey())
            ))).toList();
    }

    private boolean matchesGroup(
        MetricSample sample,
        List<String> dimensions,
        String seriesKey,
        String pointKey
    ) {
        return dimension(sample, dimensions, 0).equals(normalizeGroup(seriesKey))
            && dimension(sample, dimensions, 1).equals(normalizeGroup(pointKey));
    }

    private String dimension(MetricSample sample, List<String> dimensions, int index) {
        if (dimensions.size() <= index) return "all";
        return normalizeGroup(sample.dimensions().get(dimensions.get(index)));
    }

    private String normalizeGroup(String value) {
        return value == null || value.isBlank() ? "(unknown)" : value;
    }

    private DashboardConfig normalize(UUID dashboardId, DashboardConfig config) {
        List<ChartDefinition> charts = config.charts().stream().map(chart ->
            new ChartDefinition(
                chart.id() == null
                    ? UUID.nameUUIDFromBytes(
                        (dashboardId + ":" + chart.chartKey())
                            .getBytes(StandardCharsets.UTF_8)
                    )
                    : chart.id(),
                chart.chartKey(),
                chart.name().trim(),
                chart.visualization(),
                chart.bindingKey(),
                chart.metricId(),
                chart.metricVersion(),
                List.copyOf(chart.dimensionKeys()),
                Map.copyOf(chart.filters()),
                chart.seriesLimit(),
                chart.pointLimit(),
                chart.drilldown(),
                chart.version()
            )
        ).toList();
        return new DashboardConfig(
            SCHEMA_VERSION,
            List.copyOf(config.dataSources()),
            charts,
            List.copyOf(config.layout()),
            List.copyOf(config.filters())
        );
    }

    private void validateSave(SaveDashboardCommand command, UUID spaceId) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 0
            || !key(command.dashboardKey()) || command.name() == null
            || command.name().trim().length() < 2 || command.name().trim().length() > 160
            || command.description() == null || command.description().length() > 2000
            || (command.dashboardId() == null && command.expectedVersion() != 0)
            || (command.dashboardId() != null && command.expectedVersion() == 0)
            || command.config() == null
            || command.config().schemaVersion() != SCHEMA_VERSION
            || command.config().dataSources() == null
            || command.config().dataSources().isEmpty()
            || command.config().dataSources().size() > MAX_BINDINGS
            || command.config().charts() == null
            || command.config().charts().isEmpty()
            || command.config().charts().size() > MAX_CHARTS
            || command.config().layout() == null
            || command.config().filters() == null
            || command.config().filters().size() > 12) {
            throw failure("DASHBOARD_DEFINITION_INVALID", "Dashboard definition is invalid");
        }
        Set<String> bindingKeys = new HashSet<>();
        for (DataSourceBinding binding : command.config().dataSources()) {
            if (binding == null || binding.schemaVersion() != SCHEMA_VERSION
                || !key(binding.bindingKey()) || !bindingKeys.add(binding.bindingKey())
                || !SOURCE_KINDS.contains(binding.kind())
                || binding.spaceIds() == null || binding.spaceIds().isEmpty()
                || binding.spaceIds().size() > 20
                || !binding.spaceIds().contains(spaceId)
                || binding.metricId() == null || binding.metricVersion() < 1
                || ("saved_view".equals(binding.kind()) && binding.savedViewId() == null)
                || (!"saved_view".equals(binding.kind()) && binding.savedViewId() != null)) {
                throw failure("DASHBOARD_SOURCE_INVALID", "Dashboard source binding is invalid");
            }
        }
        Set<String> chartKeys = new HashSet<>();
        for (ChartDefinition chart : command.config().charts()) {
            if (chart == null || !key(chart.chartKey()) || !chartKeys.add(chart.chartKey())
                || chart.name() == null || chart.name().isBlank() || chart.name().length() > 160
                || !VISUALIZATIONS.contains(chart.visualization())
                || !bindingKeys.contains(chart.bindingKey())
                || chart.metricId() == null || chart.metricVersion() < 1
                || chart.dimensionKeys() == null || chart.dimensionKeys().size() > 4
                || new HashSet<>(chart.dimensionKeys()).size() != chart.dimensionKeys().size()
                || chart.filters() == null || chart.filters().size() > 12
                || chart.seriesLimit() < 1 || chart.seriesLimit() > MAX_SERIES
                || chart.pointLimit() < 1 || chart.pointLimit() > MAX_POINTS) {
                throw failure("DASHBOARD_CHART_INVALID", "Dashboard chart is invalid");
            }
            DataSourceBinding binding = command.config().dataSources().stream()
                .filter(value -> value.bindingKey().equals(chart.bindingKey()))
                .findFirst().orElseThrow();
            if (!binding.metricId().equals(chart.metricId())
                || binding.metricVersion() != chart.metricVersion()) {
                throw failure(
                    "DASHBOARD_METRIC_MISMATCH",
                    "Chart and source must bind the same immutable metric version"
                );
            }
        }
        for (Layout layout : command.config().layout()) {
            if (layout == null || !chartKeys.contains(layout.chartKey())
                || layout.column() < 0 || layout.column() > 11
                || layout.row() < 0 || layout.width() < 1 || layout.width() > 12
                || layout.height() < 1 || layout.height() > 12
                || layout.column() + layout.width() > 12) {
                throw failure("DASHBOARD_LAYOUT_INVALID", "Dashboard layout is invalid");
            }
        }
        Set<String> filterKeys = new HashSet<>();
        for (Filter filter : command.config().filters()) {
            if (filter == null || !key(filter.key()) || !filterKeys.add(filter.key())
                || !key(filter.dimensionKey())
                || !FILTER_OPERATORS.contains(filter.operator())
                || filter.values() == null || filter.values().isEmpty()
                || filter.values().size() > 50) {
                throw failure("DASHBOARD_FILTER_INVALID", "Dashboard filter is invalid");
            }
        }
        if (json(command).length() > 131_072) {
            throw failure("DASHBOARD_DEFINITION_INVALID", "Dashboard definition exceeds its bound");
        }
    }

    private void verifyPublishedMetrics(
        CurrentUser user,
        UUID spaceId,
        DashboardConfig config
    ) {
        for (DataSourceBinding binding : config.dataSources()) {
            var version = metrics.publishedVersion(
                user, spaceId, binding.metricId(), binding.metricVersion()
            );
            for (ChartDefinition chart : config.charts()) {
                if (chart.bindingKey().equals(binding.bindingKey())
                    && !version.expression().dimensionKeys()
                        .containsAll(chart.dimensionKeys())) {
                    throw failure(
                        "DASHBOARD_METRIC_MISMATCH",
                        "Chart dimensions must belong to the bound metric version"
                    );
                }
            }
        }
    }

    private void validateLifecycle(
        DashboardLifecycleCommand command,
        String expectedAction
    ) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 1
            || command.action() == null
            || (!expectedAction.isBlank() && !expectedAction.equals(command.action()))) {
            throw failure("DASHBOARD_COMMAND_INVALID", "Dashboard command is invalid");
        }
    }

    private void validateQuery(QueryDashboardCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || command.anchor() == null || command.filterValues() == null
            || command.filterValues().size() > 12) {
            throw failure("DASHBOARD_QUERY_INVALID", "Dashboard query is invalid");
        }
    }

    private Map<String, DataSourceBinding> bindingMap(DashboardConfig config) {
        Map<String, DataSourceBinding> result = new LinkedHashMap<>();
        config.dataSources().forEach(value -> result.put(value.bindingKey(), value));
        return Map.copyOf(result);
    }

    private Dashboard requireDashboard(
        CurrentUser user,
        UUID spaceId,
        UUID dashboardId
    ) {
        return repository.find(user.workspaceId(), spaceId, dashboardId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Dashboard is not available"));
    }

    private DashboardVersion requirePublished(Dashboard dashboard) {
        if (dashboard.publishedVersion() == null
            || !"active".equals(dashboard.status())) {
            throw failure("DASHBOARD_VERSION_UNAVAILABLE", "Published dashboard is unavailable");
        }
        return dashboard.publishedVersion();
    }

    private void emit(
        CurrentUser user,
        UUID spaceId,
        UUID dashboardId,
        long version,
        String change,
        String requestId
    ) {
        Map<String, Object> metadata = Map.of(
            "spaceId", spaceId,
            "dashboardId", dashboardId,
            "version", version,
            "change", change
        );
        auditLog.log(
            user, "project_dashboard." + change, "project_dashboard",
            dashboardId, metadata
        );
        String eventId = stableId(user, spaceId, "event", requestId).toString();
        outbox.append(
            user.workspaceId(), "project.dashboard.changed",
            "project_dashboard", dashboardId, user.id(), metadata,
            "project-dashboard:" + eventId
        );
        outbox.append(
            user.workspaceId(), "project_space.changed",
            "project_space", spaceId, user.id(),
            Map.of("spaceId", spaceId, "reason", "dashboard_" + change),
            "project-dashboard-space:" + eventId
        );
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private boolean key(String value) {
        return value != null && KEY.matcher(value).matches()
            && !value.contains("sql") && !value.contains("script")
            && !value.contains("project_");
    }

    private UUID stableId(
        CurrentUser user,
        UUID spaceId,
        String kind,
        String requestId
    ) {
        return UUID.nameUUIDFromBytes(
            (user.workspaceId() + ":" + spaceId + ":" + user.id()
                + ":" + kind + ":" + requestId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hash(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(json(value).getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void requireHash(
        MetricDashboardRepository.CommandRecord record,
        String requestHash
    ) {
        if (!record.requestHash().equals(requestHash)) {
            throw failure(
                "IDEMPOTENCY_KEY_REUSED",
                "Request ID was reused with different dashboard input"
            );
        }
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
