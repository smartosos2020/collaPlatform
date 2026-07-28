package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.MetricSemanticModels.MAX_DIMENSIONS;
import static com.colla.platform.modules.project.domain.MetricSemanticModels.MAX_METRICS;
import static com.colla.platform.modules.project.domain.MetricSemanticModels.MAX_SAMPLES;
import static com.colla.platform.modules.project.domain.MetricSemanticModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.MetricSemanticModels.Dimension;
import com.colla.platform.modules.project.domain.MetricSemanticModels.Measure;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricDefinition;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricExpression;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricFoundation;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricLifecycleCommand;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricResult;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricSample;
import com.colla.platform.modules.project.domain.MetricSemanticModels.MetricVersion;
import com.colla.platform.modules.project.domain.MetricSemanticModels.PreviewMetricCommand;
import com.colla.platform.modules.project.domain.MetricSemanticModels.SaveMetricCommand;
import com.colla.platform.modules.project.domain.MetricSemanticModels.SourceEvidence;
import com.colla.platform.modules.project.domain.MetricSemanticModels.Window;
import com.colla.platform.modules.project.domain.MetricSemanticModels.WindowBounds;
import com.colla.platform.modules.project.infrastructure.MetricSemanticRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricSemanticService {
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{8,120}");
    private static final Pattern SOURCE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,120}");
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.-]{1,63}");
    private static final Set<String> AGGREGATIONS =
        Set.of("count", "sum", "average", "ratio");
    private static final Set<String> WINDOW_KINDS = Set.of("rolling", "fixed");
    private static final Set<String> WINDOW_UNITS = Set.of("day", "week", "month");
    private static final Set<String> COMPARISONS = Set.of("none", "previous_period");
    private static final Set<String> UNITS =
        Set.of("count", "hours", "days", "percent", "points");
    private static final List<Measure> MEASURES = List.of(
        new Measure("work_item.count", "工作项数量", "decimal", "count",
            "WorkItemQueryService.execute", false),
        new Measure("worklog.hours", "登记工时", "decimal", "hours",
            "ResourceWorklogService.get", true),
        new Measure("capacity.hours", "可用产能", "decimal", "hours",
            "ResourceCapacityService.get", true),
        new Measure("automation.run_count", "自动化运行数", "decimal", "count",
            "AutomationManagementService.get", true),
        new Measure("cross_space.fact_count", "跨空间受权事实数", "decimal", "count",
            "CrossTeamPanoramaService.get", true)
    );

    private final MetricSemanticRepository repository;
    private final WorkItemRelationAccessDecisionService access;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public MetricSemanticService(
        MetricSemanticRepository repository,
        WorkItemRelationAccessDecisionService access,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.access = access;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public MetricFoundation foundation(CurrentUser user, UUID spaceId) {
        access.requireVisible(user, spaceId);
        List<MetricDefinition> metrics =
            repository.list(user.workspaceId(), spaceId, MAX_METRICS + 1);
        boolean truncated = metrics.size() > MAX_METRICS;
        return new MetricFoundation(
            SCHEMA_VERSION, MEASURES, repository.dimensions(user.workspaceId(), spaceId),
            truncated ? metrics.subList(0, MAX_METRICS) : metrics, truncated,
            List.of("ready", "unknown", "no_sample", "suppressed", "stale", "truncated"),
            List.of("arbitrary_sql", "script", "template", "reflection",
                "private_table", "personal_ranking", "performance_scoring")
        );
    }

    @Transactional
    public MetricDefinition save(
        CurrentUser user, UUID spaceId, SaveMetricCommand command
    ) {
        access.requireManager(user, spaceId);
        validateSave(command, repository.dimensions(user.workspaceId(), spaceId));
        String requestHash = hash(command);
        Optional<MetricSemanticRepository.CommandRecord> replay =
            repository.findCommand(user.workspaceId(), spaceId, user.id(),
                "save_metric", command.requestId());
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), MetricDefinition.class);
        }
        MetricDefinition result = repository.save(
            user.workspaceId(), spaceId, user.id(), command.metricId(),
            command.metricKey(), command.name().trim(),
            command.description() == null ? "" : command.description().trim(),
            command.unit(), command.expression(), command.window(),
            command.expectedVersion(), command.requestId(), requestHash
        );
        emit(user, spaceId, result.id(), result.version(), "saved", command.requestId());
        return result;
    }

    @Transactional
    public MetricVersion publish(
        CurrentUser user, UUID spaceId, UUID metricId,
        MetricLifecycleCommand command
    ) {
        access.requireManager(user, spaceId);
        validateLifecycle(command, "publish");
        String requestHash = hash(command);
        Optional<MetricSemanticRepository.CommandRecord> replay =
            repository.findCommand(user.workspaceId(), spaceId, user.id(),
                "publish_metric", command.requestId());
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), MetricVersion.class);
        }
        MetricDefinition metric = requireMetric(user, spaceId, metricId);
        if (metric.version() != command.expectedVersion()) {
            throw failure("METRIC_VERSION_CONFLICT", "Metric changed; refresh before publishing");
        }
        String definitionHash = hash(Map.of(
            "metricKey", metric.metricKey(), "unit", metric.unit(),
            "expression", metric.draftExpression(), "window", metric.draftWindow()
        ));
        MetricVersion result = repository.publish(
            user.workspaceId(), spaceId, user.id(), metricId,
            command.expectedVersion(), definitionHash,
            command.requestId(), requestHash
        );
        emit(user, spaceId, metricId, result.versionNumber(), "published", command.requestId());
        return result;
    }

    @Transactional
    public MetricDefinition lifecycle(
        CurrentUser user, UUID spaceId, UUID metricId,
        MetricLifecycleCommand command
    ) {
        access.requireManager(user, spaceId);
        validateLifecycle(command, command == null ? "" : command.action());
        if (!Set.of("disable", "revise", "archive").contains(command.action())) {
            throw failure("METRIC_COMMAND_INVALID", "Metric lifecycle action is invalid");
        }
        requireMetric(user, spaceId, metricId);
        String operation = command.action() + "_metric";
        String requestHash = hash(command);
        Optional<MetricSemanticRepository.CommandRecord> replay =
            repository.findCommand(user.workspaceId(), spaceId, user.id(),
                operation, command.requestId());
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), MetricDefinition.class);
        }
        MetricDefinition result = repository.lifecycle(
            user.workspaceId(), spaceId, user.id(), metricId, command.action(),
            command.expectedVersion(), command.requestId(), requestHash
        );
        emit(user, spaceId, metricId, result.version(), command.action() + "d",
            command.requestId());
        return result;
    }

    public MetricResult preview(
        CurrentUser user, UUID spaceId, UUID metricId, PreviewMetricCommand command
    ) {
        access.requireVisible(user, spaceId);
        MetricDefinition metric = requireMetric(user, spaceId, metricId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || command.anchor() == null || command.samples() == null
            || command.samples().size() > MAX_SAMPLES
            || command.samples().stream().anyMatch(
                sample -> !validSample(sample, metric.draftExpression())
            )) {
            throw failure("METRIC_PREVIEW_INVALID", "Metric preview is invalid or exceeds its bound");
        }
        return evaluate(
            metric,
            metric.draftExpression(),
            metric.draftWindow(),
            metric.publishedVersion() == null
                ? null : metric.publishedVersion().versionNumber(),
            command.anchor(),
            command.samples()
        );
    }

    public MetricResult evaluatePublished(
        CurrentUser user,
        UUID spaceId,
        UUID metricId,
        int metricVersion,
        Instant anchor,
        List<MetricSample> samples
    ) {
        MetricVersion published = publishedVersion(user, spaceId, metricId, metricVersion);
        MetricDefinition metric = requireMetric(user, spaceId, metricId);
        List<MetricSample> normalized = samples == null ? null : samples.stream()
            .map(sample -> sample == null ? null : new MetricSample(
                sample.occurredAt(),
                sample.value(),
                sample.numerator(),
                sample.denominator(),
                sample.dimensions().entrySet().stream()
                    .filter(entry -> published.expression().dimensionKeys()
                        .contains(entry.getKey()))
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                    )),
                sample.sourceIdentity(),
                sample.sourceVersion(),
                sample.authorized(),
                sample.suppressed(),
                sample.stale(),
                sample.truncated()
            )).toList();
        if (anchor == null || normalized == null || normalized.size() > MAX_SAMPLES
            || normalized.stream().anyMatch(
                sample -> !validSample(sample, published.expression())
            )) {
            throw failure(
                "METRIC_PREVIEW_INVALID",
                "Dashboard metric input is invalid or exceeds its bound"
            );
        }
        return evaluate(
            metric,
            published.expression(),
            published.window(),
            published.versionNumber(),
            anchor,
            normalized
        );
    }

    public MetricVersion publishedVersion(
        CurrentUser user,
        UUID spaceId,
        UUID metricId,
        int metricVersion
    ) {
        access.requireVisible(user, spaceId);
        MetricVersion published = requireMetric(user, spaceId, metricId).publishedVersion();
        if (published == null || published.versionNumber() != metricVersion) {
            throw failure(
                "METRIC_VERSION_UNAVAILABLE",
                "Dashboard metric version is unavailable; refresh its binding"
            );
        }
        return published;
    }

    private MetricResult evaluate(
        MetricDefinition metric,
        MetricExpression expression,
        Window window,
        Integer metricVersion,
        Instant anchor,
        List<MetricSample> samples
    ) {
        WindowBounds bounds = resolve(window, anchor);
        List<MetricSample> inWindow = samples.stream()
            .filter(sample -> sample != null && sample.occurredAt() != null
                && !sample.occurredAt().isBefore(bounds.startInclusive())
                && sample.occurredAt().isBefore(bounds.endExclusive()))
            .toList();
        boolean unauthorized = inWindow.stream().anyMatch(sample -> !sample.authorized());
        boolean explicitlySuppressed = inWindow.stream().anyMatch(MetricSample::suppressed);
        boolean stale = inWindow.stream().anyMatch(MetricSample::stale);
        boolean truncated = inWindow.stream().anyMatch(MetricSample::truncated);
        List<MetricSample> usable = inWindow.stream()
            .filter(MetricSample::authorized)
            .filter(sample -> !sample.suppressed() && !sample.stale())
            .toList();
        boolean minimumSampleSuppressed = !usable.isEmpty() && usable.size() < 3;
        boolean suppressed = explicitlySuppressed || minimumSampleSuppressed;
        String status = explicitlySuppressed ? "suppressed" : stale ? "stale"
            : truncated ? "truncated" : usable.isEmpty() ? "no_sample"
            : minimumSampleSuppressed ? "suppressed" : "ready";
        BigDecimal numerator = null;
        BigDecimal denominator = null;
        BigDecimal value = null;
        if ("ready".equals(status)) {
            var calculation = calculate(expression, usable);
            value = calculation[0];
            numerator = calculation[1];
            denominator = calculation[2];
            if (value == null) status = "unknown";
        }
        return new MetricResult(
            SCHEMA_VERSION, metric.id(),
            metricVersion,
            status, value, numerator, denominator, metric.unit(), bounds,
            List.of(new SourceEvidence(
                source(expression),
                "ready".equals(status) ? usable.stream()
                    .map(sample -> sample.sourceIdentity() + "@" + sample.sourceVersion())
                    .distinct().sorted().limit(50).toList() : List.of(),
                "ready".equals(status) ? (int) inWindow.stream()
                    .filter(MetricSample::authorized).count() : 0,
                "ready".equals(status) ? usable.size() : 0,
                stale, truncated, unauthorized ? "unauthorized samples excluded before aggregation"
                    : suppressed ? "minimum sample policy suppressed result"
                    : "current authorized bounded sample"
            )),
            usable.size(), suppressed, stale, truncated, Instant.now(),
            switch (status) {
                case "ready" -> "deterministic bounded preview";
                case "no_sample" -> "no authorized sample in window";
                case "suppressed" -> "minimum sample threshold not met";
                case "stale" -> "source freshness exceeded";
                case "truncated" -> "source result was truncated";
                default -> "result cannot be determined";
            }
        );
    }

    public WindowBounds resolve(Window window, Instant anchor) {
        validateWindow(window);
        ZoneId zone = ZoneId.of(window.timeZone());
        ZonedDateTime end;
        if ("rolling".equals(window.kind())) {
            end = anchor.atZone(zone);
        } else {
            LocalDate date = anchor.atZone(zone).toLocalDate();
            end = switch (window.unit()) {
                case "day" -> date.plusDays(1).atStartOfDay(zone);
                case "week" -> date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    .atStartOfDay(zone);
                default -> date.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone);
            };
        }
        ZonedDateTime start = subtract(end, window.amount(), window.unit());
        ZonedDateTime comparisonStart = null;
        ZonedDateTime comparisonEnd = null;
        if ("previous_period".equals(window.comparison())) {
            comparisonEnd = start;
            comparisonStart = subtract(comparisonEnd, window.amount(), window.unit());
        }
        return new WindowBounds(
            start.toInstant(), end.toInstant(),
            comparisonStart == null ? null : comparisonStart.toInstant(),
            comparisonEnd == null ? null : comparisonEnd.toInstant(),
            zone.getId(), "IANA zone calendar arithmetic; DST resolved by java.time"
        );
    }

    private BigDecimal[] calculate(MetricExpression expression, List<MetricSample> samples) {
        if ("count".equals(expression.aggregation())) {
            return new BigDecimal[]{BigDecimal.valueOf(samples.size()), null, null};
        }
        if ("ratio".equals(expression.aggregation())) {
            BigDecimal numerator = samples.stream().map(MetricSample::numerator)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal denominator = samples.stream().map(MetricSample::denominator)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (denominator.signum() == 0) return new BigDecimal[]{null, numerator, denominator};
            return new BigDecimal[]{
                numerator.divide(denominator, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(6, RoundingMode.HALF_UP),
                numerator, denominator
            };
        }
        List<BigDecimal> values = samples.stream().map(MetricSample::value)
            .filter(java.util.Objects::nonNull).toList();
        if (values.isEmpty()) return new BigDecimal[]{null, null, null};
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal result = "average".equals(expression.aggregation())
            ? sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP) : sum;
        return new BigDecimal[]{result, null, null};
    }

    private void validateSave(SaveMetricCommand command, List<Dimension> dimensions) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 0
            || !key(command.metricKey()) || command.name() == null
            || command.name().trim().length() < 2 || command.name().trim().length() > 160
            || command.description() == null || command.description().length() > 2000
            || !UNITS.contains(command.unit())
            || (command.metricId() == null && command.expectedVersion() != 0)
            || (command.metricId() != null && command.expectedVersion() == 0)) {
            throw failure("METRIC_DEFINITION_INVALID", "Metric definition is invalid");
        }
        validateExpression(command.expression(), command.unit(), dimensions);
        validateWindow(command.window());
        if (json(command).length() > 32_768) {
            throw failure("METRIC_DEFINITION_INVALID", "Metric definition exceeds its bound");
        }
    }

    private void validateExpression(
        MetricExpression expression, String resultUnit, List<Dimension> dimensions
    ) {
        if (expression == null || expression.schemaVersion() != SCHEMA_VERSION
            || !AGGREGATIONS.contains(expression.aggregation())
            || expression.dimensionKeys() == null
            || expression.dimensionKeys().size() > MAX_DIMENSIONS
            || new HashSet<>(expression.dimensionKeys()).size() != expression.dimensionKeys().size()) {
            throw failure("METRIC_EXPRESSION_INVALID", "Metric expression is invalid");
        }
        Set<String> measureKeys = MEASURES.stream().map(Measure::key)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> dimensionKeys = dimensions.stream().map(Dimension::key)
            .collect(java.util.stream.Collectors.toSet());
        if (!dimensionKeys.containsAll(expression.dimensionKeys())) {
            throw failure("METRIC_EXPRESSION_INVALID", "Metric uses an unregistered dimension");
        }
        if ("ratio".equals(expression.aggregation())) {
            if (!measureKeys.contains(expression.numeratorMeasureKey())
                || !measureKeys.contains(expression.denominatorMeasureKey())
                || expression.measureKey() != null) {
                throw failure("METRIC_EXPRESSION_INVALID", "Ratio requires registered numerator and denominator");
            }
            if (!"percent".equals(resultUnit)) {
                throw failure("METRIC_EXPRESSION_INVALID", "Ratio metrics must use percent unit");
            }
        } else if (!measureKeys.contains(expression.measureKey())
            || expression.numeratorMeasureKey() != null
            || expression.denominatorMeasureKey() != null) {
            throw failure("METRIC_EXPRESSION_INVALID", "Aggregation requires one registered measure");
        }
        if ("count".equals(expression.aggregation()) && !"count".equals(resultUnit)) {
            throw failure("METRIC_EXPRESSION_INVALID", "Count aggregation must use count unit");
        }
        if (Set.of("sum", "average").contains(expression.aggregation())) {
            String measureUnit = MEASURES.stream()
                .filter(value -> value.key().equals(expression.measureKey()))
                .findFirst().map(Measure::unit).orElse("");
            if (!measureUnit.equals(resultUnit)) {
                throw failure("METRIC_EXPRESSION_INVALID", "Metric result unit must match its measure");
            }
        }
        String serialized = json(expression).toLowerCase(Locale.ROOT);
        if (List.of("sql", "script", "template", "reflect", "project_", "select ")
            .stream().anyMatch(serialized::contains)) {
            throw failure("METRIC_EXPRESSION_INVALID", "Executable or private-table expression is forbidden");
        }
    }

    private void validateWindow(Window window) {
        try {
            if (window == null || window.schemaVersion() != SCHEMA_VERSION
                || !WINDOW_KINDS.contains(window.kind())
                || window.amount() < 1 || window.amount() > 366
                || !WINDOW_UNITS.contains(window.unit())
                || !"iso8601".equals(window.calendarKey())
                || !COMPARISONS.contains(window.comparison())) {
                throw failure("METRIC_WINDOW_INVALID", "Metric window is invalid");
            }
            ZoneId.of(window.timeZone());
        } catch (RuntimeException exception) {
            if (exception instanceof com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException) {
                throw exception;
            }
            throw failure("METRIC_WINDOW_INVALID", "Metric window timezone is invalid");
        }
    }

    private boolean validSample(MetricSample sample, MetricExpression expression) {
        if (sample == null || sample.occurredAt() == null
            || sample.sourceIdentity() == null
            || !SOURCE_ID.matcher(sample.sourceIdentity()).matches()
            || sample.sourceVersion() < 0 || sample.dimensions() == null
            || sample.dimensions().size() > MAX_DIMENSIONS
            || !expression.dimensionKeys().containsAll(sample.dimensions().keySet())) {
            return false;
        }
        return sample.dimensions().entrySet().stream().allMatch(entry ->
            entry.getKey() != null && entry.getValue() != null
                && entry.getValue().length() <= 120
        );
    }

    private void validateLifecycle(MetricLifecycleCommand command, String action) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 1
            || !Set.of("publish", "disable", "revise", "archive").contains(action)) {
            throw failure("METRIC_COMMAND_INVALID", "Metric command is invalid");
        }
    }

    private MetricDefinition requireMetric(CurrentUser user, UUID spaceId, UUID metricId) {
        return repository.find(user.workspaceId(), spaceId, metricId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Metric is not available"));
    }

    private ZonedDateTime subtract(ZonedDateTime value, int amount, String unit) {
        return switch (unit) {
            case "day" -> value.minusDays(amount);
            case "week" -> value.minusWeeks(amount);
            default -> value.minusMonths(amount);
        };
    }

    private String source(MetricExpression expression) {
        String key = "ratio".equals(expression.aggregation())
            ? expression.numeratorMeasureKey() : expression.measureKey();
        return MEASURES.stream().filter(value -> value.key().equals(key))
            .findFirst().map(Measure::sourceContract).orElse("unknown");
    }

    private void emit(
        CurrentUser user, UUID spaceId, UUID metricId, long version,
        String change, String requestId
    ) {
        auditLog.log(user, "project_metric." + change, "project_metric", metricId,
            Map.of("space_id", spaceId.toString(), "version", version));
        outbox.append(user.workspaceId(), "project.metric.changed",
            "project_metric", metricId, user.id(),
            Map.of("spaceId", spaceId.toString(), "version", version, "change", change),
            "project-metric:" + requestId);
        outbox.append(user.workspaceId(), "project_space.changed",
            "project_space", spaceId, user.id(),
            Map.of("metricId", metricId.toString(), "version", version, "change", change),
            "project-metric-space:" + requestId);
    }

    private void requireHash(MetricSemanticRepository.CommandRecord record, String hash) {
        if (!hash.equals(record.requestHash())) {
            throw failure("METRIC_REQUEST_CONFLICT", "Request ID was reused with different input");
        }
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private boolean key(String value) {
        return value != null && KEY.matcher(value).matches();
    }

    private String hash(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(json(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
