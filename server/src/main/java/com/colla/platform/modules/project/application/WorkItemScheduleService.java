package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;
import static com.colla.platform.modules.project.domain.WorkItemScheduleModels.MAX_BASELINES;
import static com.colla.platform.modules.project.domain.WorkItemScheduleModels.MAX_BASELINE_ENTRIES;
import static com.colla.platform.modules.project.domain.WorkItemScheduleModels.MAX_TIMELINE_EVENTS;
import static com.colla.platform.modules.project.domain.WorkItemScheduleModels.RETENTION_DAYS;

import com.colla.platform.modules.audit.contract.AuditTimelineQuery;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.DependencyLine;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttRequest;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttResult;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.HierarchyRow;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineCreateCommand;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineDeleteCommand;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineDependency;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineDiff;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineEntry;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSnapshot;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.BaselineSummary;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.EntryDiff;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.TimelineEvent;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.TimelineRequest;
import com.colla.platform.modules.project.domain.WorkItemScheduleModels.TimelineResult;
import com.colla.platform.modules.project.infrastructure.WorkItemScheduleRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemScheduleService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");

    private final WorkItemService workItems;
    private final WorkItemGanttService gantts;
    private final WorkItemScheduleRepository repository;
    private final AuditTimelineQuery auditTimeline;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public WorkItemScheduleService(
        WorkItemService workItems,
        WorkItemGanttService gantts,
        WorkItemScheduleRepository repository,
        AuditTimelineQuery auditTimeline,
        ObjectMapper objectMapper
    ) {
        this(
            workItems, gantts, repository, auditTimeline, objectMapper, Clock.systemUTC()
        );
    }

    WorkItemScheduleService(
        WorkItemService workItems,
        WorkItemGanttService gantts,
        WorkItemScheduleRepository repository,
        AuditTimelineQuery auditTimeline,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.workItems = workItems;
        this.gantts = gantts;
        this.repository = repository;
        this.auditTimeline = auditTimeline;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public List<BaselineSummary> list(CurrentUser user, UUID spaceId) {
        workItems.requireQueryScope(user, spaceId);
        return repository.listBaselines(
            user.workspaceId(), spaceId, user.id(), MAX_BASELINES
        );
    }

    @Transactional
    public BaselineSnapshot create(
        CurrentUser user, UUID spaceId, BaselineCreateCommand command
    ) {
        validate(command);
        workItems.requireQueryScope(user, spaceId);
        String requestHash = hash(command);
        Optional<WorkItemScheduleRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "create", command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), BaselineSnapshot.class);
        }
        if (repository.listBaselines(
            user.workspaceId(), spaceId, user.id(), MAX_BASELINES
        ).size() >= MAX_BASELINES) {
            throw failure("SCHEDULE_BASELINE_LIMIT_REACHED", "Baseline limit reached");
        }
        GanttResult current = gantts.render(user, spaceId, command.request());
        if (current.rows().size() > MAX_BASELINE_ENTRIES) {
            throw failure("SCHEDULE_BASELINE_ENTRY_BUDGET_EXCEEDED", "Baseline entry budget exceeded");
        }
        List<BaselineEntry> entries = current.rows().stream().map(row -> new BaselineEntry(
            row.workItemId(),
            row.bar().workItemVersion(),
            row.bar().startDate(),
            row.bar().endDate(),
            row.parentWorkItemId(),
            row.depth()
        )).toList();
        List<BaselineDependency> dependencies = current.dependencies().stream()
            .map(line -> new BaselineDependency(
                line.relationId(),
                line.relationVersion(),
                line.sourceWorkItemId(),
                line.targetWorkItemId()
            )).toList();
        return repository.createBaseline(
            user.workspaceId(),
            spaceId,
            user.id(),
            command.requestId(),
            requestHash,
            "{}",
            command.name().trim(),
            current.queryHash(),
            json(command.request().binding()),
            current.window().startDate(),
            current.window().endDate(),
            clock.instant().plus(RETENTION_DAYS, ChronoUnit.DAYS),
            entries,
            dependencies
        );
    }

    public BaselineDiff compare(
        CurrentUser user, UUID spaceId, UUID baselineId, GanttRequest request
    ) {
        workItems.requireQueryScope(user, spaceId);
        BaselineSnapshot baseline = requireBaseline(user, spaceId, baselineId);
        GanttResult current = gantts.render(user, spaceId, request);
        Map<UUID, BaselineEntry> before = new LinkedHashMap<>();
        baseline.entries().forEach(entry -> before.put(entry.workItemId(), entry));
        Map<UUID, HierarchyRow> now = new LinkedHashMap<>();
        current.rows().forEach(row -> now.put(row.workItemId(), row));
        Set<UUID> visible = now.keySet();
        List<EntryDiff> diffs = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(visible)) {
            BaselineEntry old = before.get(id);
            HierarchyRow row = now.get(id);
            if (old == null) {
                diffs.add(diff(id, "added", null, row));
            } else if (!same(old, row)) {
                diffs.add(diff(id, "changed", old, row));
            }
        }
        // A removed row is disclosed only if it remains currently visible in the query result;
        // hidden/revoked baseline identities are intentionally omitted.
        Set<UUID> beforeDependencies = baseline.dependencies().stream()
            .filter(edge -> visible.contains(edge.sourceWorkItemId())
                && visible.contains(edge.targetWorkItemId()))
            .map(BaselineDependency::relationId)
            .collect(java.util.stream.Collectors.toSet());
        Set<UUID> currentDependencies = current.dependencies().stream()
            .map(DependencyLine::relationId)
            .collect(java.util.stream.Collectors.toSet());
        int added = (int) currentDependencies.stream()
            .filter(id -> !beforeDependencies.contains(id)).count();
        int removed = (int) beforeDependencies.stream()
            .filter(id -> !currentDependencies.contains(id)).count();
        diffs.sort(Comparator.comparing(value -> value.workItemId().toString()));
        return new BaselineDiff(
            baselineId, List.copyOf(diffs), added, removed, current.truncated()
        );
    }

    @Transactional
    public BaselineSummary delete(
        CurrentUser user,
        UUID spaceId,
        UUID baselineId,
        BaselineDeleteCommand command
    ) {
        requireRequestId(command.requestId());
        workItems.requireQueryScope(user, spaceId);
        String requestHash = hash(List.of(baselineId, command.expectedVersion()));
        Optional<WorkItemScheduleRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "delete", command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return read(replay.get().responseJson(), BaselineSummary.class);
        }
        requireBaseline(user, spaceId, baselineId);
        return repository.deleteBaseline(
            user.workspaceId(), spaceId, user.id(), baselineId,
            command.requestId(), requestHash, command.expectedVersion(), "{}"
        );
    }

    public TimelineResult timeline(
        CurrentUser user, UUID spaceId, TimelineRequest request
    ) {
        if (request == null || request.schemaVersion() != 1
            || request.request() == null
            || request.limit() < 1 || request.limit() > MAX_TIMELINE_EVENTS) {
            throw failure("WORK_ITEM_TIMELINE_INVALID", "Timeline request is invalid");
        }
        GanttResult current = gantts.render(user, spaceId, request.request());
        List<UUID> visibleIds = current.rows().stream()
            .map(HierarchyRow::workItemId).toList();
        List<TimelineEvent> candidates = new ArrayList<>(repository.timeline(
            user.workspaceId(), spaceId, visibleIds, request.limit() + 1
        ));
        auditTimeline.workItemEntries(
            user.workspaceId(), visibleIds, request.limit()
        ).forEach(entry -> candidates.add(new TimelineEvent(
            entry.id(), "audit", entry.id(), entry.workItemId(),
            entry.action(), entry.actorId(), entry.occurredAt()
        )));
        candidates.sort(Comparator.comparing(TimelineEvent::occurredAt).reversed()
            .thenComparing(event -> event.id().toString()));
        LinkedHashMap<String, TimelineEvent> deduplicated = new LinkedHashMap<>();
        candidates.forEach(event -> deduplicated.putIfAbsent(
            event.sourceKind() + ":" + event.sourceId(), event
        ));
        boolean truncated = deduplicated.size() > request.limit();
        List<TimelineEvent> result = deduplicated.values().stream()
            .limit(request.limit()).toList();
        repository.replaceTimelineIndex(
            user.workspaceId(),
            spaceId,
            user.id(),
            request.request().viewKey(),
            result
        );
        return new TimelineResult(result, truncated);
    }

    private BaselineSnapshot requireBaseline(
        CurrentUser user, UUID spaceId, UUID baselineId
    ) {
        return repository.findBaseline(
            user.workspaceId(), spaceId, user.id(), baselineId
        ).orElseThrow(() -> failure(
            "SCHEDULE_BASELINE_NOT_FOUND", "Baseline was not found"
        ));
    }

    private void validate(BaselineCreateCommand command) {
        if (command == null || command.schemaVersion() != 1 || command.request() == null
            || command.name() == null || command.name().trim().isEmpty()
            || command.name().trim().length() > 120) {
            throw failure("SCHEDULE_BASELINE_INVALID", "Baseline command is invalid");
        }
        requireRequestId(command.requestId());
    }

    private void requireRequestId(String value) {
        if (value == null || !REQUEST_ID.matcher(value).matches()) {
            throw failure("SCHEDULE_BASELINE_REQUEST_ID_INVALID", "Request ID is invalid");
        }
    }

    private void requireHash(
        WorkItemScheduleRepository.CommandRecord command, String expected
    ) {
        if (!expected.equals(command.requestHash())) {
            throw failure(
                "SCHEDULE_BASELINE_REQUEST_CONFLICT",
                "Request ID was reused with different input"
            );
        }
    }

    private boolean same(BaselineEntry old, HierarchyRow row) {
        return java.util.Objects.equals(old.startDate(), row.bar().startDate())
            && java.util.Objects.equals(old.endDate(), row.bar().endDate())
            && java.util.Objects.equals(old.parentWorkItemId(), row.parentWorkItemId())
            && old.workItemVersion() == row.bar().workItemVersion();
    }

    private EntryDiff diff(
        UUID id, String change, BaselineEntry old, HierarchyRow row
    ) {
        return new EntryDiff(
            id,
            change,
            old == null ? null : old.startDate(),
            row == null ? null : row.bar().startDate(),
            old == null ? null : old.endDate(),
            row == null ? null : row.bar().endDate(),
            old == null ? null : old.parentWorkItemId(),
            row == null ? null : row.parentWorkItemId()
        );
    }

    private String hash(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(json(value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize schedule value", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read schedule command receipt", exception);
        }
    }
}
