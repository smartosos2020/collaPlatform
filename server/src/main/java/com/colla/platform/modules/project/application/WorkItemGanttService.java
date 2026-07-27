package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemGanttModels.MAX_DEPENDENCIES;
import static com.colla.platform.modules.project.domain.WorkItemGanttModels.MAX_DEPTH;
import static com.colla.platform.modules.project.domain.WorkItemGanttModels.MAX_EXPANDED;
import static com.colla.platform.modules.project.domain.WorkItemGanttModels.MAX_ROWS;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.contract.WorkItemDependencyProjectionProvider;
import com.colla.platform.modules.project.contract.WorkItemDependencyProjectionProvider.DependencyEdge;
import com.colla.platform.modules.project.contract.WorkItemHierarchyProjectionProvider;
import com.colla.platform.modules.project.contract.WorkItemHierarchyProjectionProvider.AncestorRef;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarEvent;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarRequest;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarResult;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutation;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutationResult;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.DependencyLine;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttPreference;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttRequest;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.GanttResult;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.HierarchyRow;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.ScheduleBar;
import com.colla.platform.modules.project.domain.WorkItemGanttModels.ScheduleIndexEntry;
import com.colla.platform.modules.project.domain.WorkItemRelationModels;
import com.colla.platform.modules.project.infrastructure.WorkItemGanttRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemGanttService {
    private static final Pattern VIEW_KEY = Pattern.compile("^[a-z][a-z0-9._-]{0,79}$");
    private static final Set<String> ZOOMS = Set.of("day", "week", "month");

    private final WorkItemCalendarService calendars;
    private final WorkItemService workItems;
    private final WorkItemHierarchyProjectionProvider hierarchy;
    private final WorkItemDependencyProjectionProvider dependencies;
    private final WorkItemGanttRepository repository;

    public WorkItemGanttService(
        WorkItemCalendarService calendars,
        WorkItemService workItems,
        WorkItemHierarchyProjectionProvider hierarchy,
        WorkItemDependencyProjectionProvider dependencies,
        WorkItemGanttRepository repository
    ) {
        this.calendars = calendars;
        this.workItems = workItems;
        this.hierarchy = hierarchy;
        this.dependencies = dependencies;
        this.repository = repository;
    }

    public Optional<GanttPreference> preference(
        CurrentUser user, UUID spaceId, String viewKey
    ) {
        workItems.requireQueryScope(user, spaceId);
        return repository.findPreference(
            user.workspaceId(), spaceId, user.id(), requireViewKey(viewKey)
        );
    }

    @Transactional
    public GanttPreference savePreference(
        CurrentUser user,
        UUID spaceId,
        String viewKey,
        GanttPreferenceCommand command
    ) {
        workItems.requireQueryScope(user, spaceId);
        validatePreference(command);
        String key = requireViewKey(viewKey);
        GanttPreference result = repository.savePreference(
            user.workspaceId(), spaceId, user.id(), key, command
        );
        String calendarKey = calendarKey(key);
        long calendarVersion = calendars.preference(user, spaceId, calendarKey)
            .map(value -> value.version()).orElse(0L);
        calendars.savePreference(
            user,
            spaceId,
            calendarKey,
            new CalendarPreferenceCommand(
                "gantt-calendar-" + command.requestId(),
                calendarVersion,
                command.binding(),
                command.timezone(),
                "month"
            )
        );
        return result;
    }

    public GanttResult render(CurrentUser user, UUID spaceId, GanttRequest request) {
        validate(request);
        CalendarResult calendar = calendars.render(
            user,
            spaceId,
            new CalendarRequest(
                1,
                calendarKey(request.viewKey()),
                request.binding(),
                request.window(),
                request.query()
            )
        );
        LinkedHashMap<UUID, CalendarEvent> visible = new LinkedHashMap<>();
        calendar.days().stream().flatMap(day -> day.events().stream())
            .forEach(event -> visible.putIfAbsent(event.workItemId(), event));
        calendar.noDateEvents().forEach(event -> visible.putIfAbsent(event.workItemId(), event));
        if (visible.size() > MAX_ROWS) {
            throw failure("GANTT_ROW_BUDGET_EXCEEDED", "Gantt row budget exceeded");
        }
        List<UUID> visibleIds = List.copyOf(visible.keySet());
        Map<UUID, List<AncestorRef>> ancestry = hierarchy.ancestors(
            user.workspaceId(),
            spaceId,
            requireRelationKey(request.hierarchyRelationKey()),
            visibleIds
        );
        Map<UUID, UUID> parents = nearestVisibleParents(visibleIds, ancestry);
        Map<UUID, Integer> depths = depths(visibleIds, parents);
        List<DependencyEdge> rawEdges = dependencies.edges(
            user.workspaceId(), spaceId, visibleIds, MAX_DEPENDENCIES
        );
        CriticalAnalysis analysis = request.criticalPath()
            ? analyzeCriticalPath(visible, rawEdges)
            : CriticalAnalysis.disabled("not_requested");
        Set<UUID> expanded = Set.copyOf(request.expandedNodeIds());
        List<HierarchyRow> rows = new ArrayList<>();
        for (CalendarEvent event : visible.values()) {
            UUID parent = parents.get(event.workItemId());
            int depth = depths.getOrDefault(event.workItemId(), 0);
            if (depth > MAX_DEPTH || !ancestorsExpanded(event.workItemId(), parents, expanded)) {
                continue;
            }
            boolean expandable = parents.containsValue(event.workItemId());
            rows.add(new HierarchyRow(
                event.workItemId(),
                parent,
                depth,
                expandable,
                expanded.contains(event.workItemId()),
                new ScheduleBar(
                    event.workItemId(),
                    event.displayKey(),
                    event.title(),
                    event.workItemVersion(),
                    event.displayStartDate(),
                    event.displayEndDate(),
                    event.allDay(),
                    analysis.criticalNodes().contains(event.workItemId()),
                    analysis.floatDays().getOrDefault(event.workItemId(), 0L),
                    event.availableActions()
                )
            ));
        }
        List<DependencyLine> lines = rawEdges.stream().map(edge -> new DependencyLine(
            edge.relationId(),
            edge.relationKey(),
            edge.sourceWorkItemId(),
            edge.targetWorkItemId(),
            edge.relationVersion(),
            analysis.criticalEdges().contains(edge.relationId())
        )).toList();
        if (repository.findPreference(
            user.workspaceId(), spaceId, user.id(), request.viewKey()
        ).isPresent()) {
            repository.replaceScheduleIndex(
                user.workspaceId(),
                spaceId,
                user.id(),
                request.viewKey(),
                rows.stream().map(row -> new ScheduleIndexEntry(
                    row.workItemId(),
                    row.bar().workItemVersion(),
                    row.bar().startDate(),
                    row.bar().endDate(),
                    row.parentWorkItemId(),
                    row.depth()
                )).toList()
            );
        }
        repository.recordRender(
            user.workspaceId(),
            spaceId,
            request.viewKey(),
            rows.size(),
            lines.size(),
            rows.stream().mapToInt(HierarchyRow::depth).max().orElse(0)
        );
        return new GanttResult(
            1,
            request.viewKey(),
            calendar.queryHash(),
            request.binding(),
            request.window(),
            List.copyOf(rows),
            lines,
            analysis.available(),
            analysis.reason(),
            calendar.nextCursor() != null || calendar.candidateBoundReached()
        );
    }

    public DateMutationResult mutateDate(
        CurrentUser user,
        UUID spaceId,
        String viewKey,
        UUID workItemId,
        DateMutation mutation
    ) {
        return calendars.mutateDate(
            user, spaceId, calendarKey(requireViewKey(viewKey)), workItemId, mutation
        );
    }

    private CriticalAnalysis analyzeCriticalPath(
        Map<UUID, CalendarEvent> events,
        List<DependencyEdge> edges
    ) {
        Map<UUID, List<DependencyEdge>> outgoing = new HashMap<>();
        Map<UUID, Integer> indegree = new HashMap<>();
        events.keySet().forEach(id -> indegree.put(id, 0));
        for (DependencyEdge edge : edges) {
            // "source depends on target": schedule flows target -> source.
            outgoing.computeIfAbsent(edge.targetWorkItemId(), ignored -> new ArrayList<>())
                .add(edge);
            indegree.compute(edge.sourceWorkItemId(), (id, value) -> value == null ? 1 : value + 1);
        }
        ArrayDeque<UUID> ready = new ArrayDeque<>();
        indegree.forEach((id, value) -> {
            if (value == 0) ready.add(id);
        });
        List<UUID> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            UUID id = ready.removeFirst();
            order.add(id);
            for (DependencyEdge edge : outgoing.getOrDefault(id, List.of())) {
                int next = indegree.computeIfPresent(
                    edge.sourceWorkItemId(), (ignored, value) -> value - 1
                );
                if (next == 0) ready.add(edge.sourceWorkItemId());
            }
        }
        if (order.size() != events.size()) {
            return CriticalAnalysis.disabled("relation_cycle");
        }
        Map<UUID, Long> longest = new HashMap<>();
        Map<UUID, DependencyEdge> previous = new HashMap<>();
        for (UUID id : order) {
            long finish = longest.getOrDefault(id, 0L) + duration(events.get(id));
            longest.put(id, finish);
            for (DependencyEdge edge : outgoing.getOrDefault(id, List.of())) {
                UUID successor = edge.sourceWorkItemId();
                if (finish > longest.getOrDefault(successor, 0L)) {
                    longest.put(successor, finish);
                    previous.put(successor, edge);
                }
            }
        }
        UUID tail = order.stream().max(Comparator.comparingLong(
            id -> longest.getOrDefault(id, 0L)
        )).orElse(null);
        long maximum = tail == null ? 0 : longest.getOrDefault(tail, 0L);
        Set<UUID> criticalNodes = new HashSet<>();
        Set<UUID> criticalEdges = new HashSet<>();
        UUID current = tail;
        while (current != null && criticalNodes.add(current)) {
            DependencyEdge edge = previous.get(current);
            if (edge == null) break;
            criticalEdges.add(edge.relationId());
            current = edge.targetWorkItemId();
        }
        Map<UUID, Long> floatDays = new HashMap<>();
        events.keySet().forEach(id -> floatDays.put(
            id, Math.max(0, maximum - longest.getOrDefault(id, 0L))
        ));
        return new CriticalAnalysis(
            true, "ok", Set.copyOf(criticalNodes), Set.copyOf(criticalEdges),
            Map.copyOf(floatDays)
        );
    }

    private static long duration(CalendarEvent event) {
        if (event == null || event.displayStartDate() == null) return 0;
        LocalDate end = event.displayEndDate() == null
            ? event.displayStartDate() : event.displayEndDate();
        return Math.max(1, ChronoUnit.DAYS.between(event.displayStartDate(), end) + 1);
    }

    private static Map<UUID, UUID> nearestVisibleParents(
        List<UUID> visibleIds,
        Map<UUID, List<AncestorRef>> ancestry
    ) {
        Set<UUID> visible = Set.copyOf(visibleIds);
        Map<UUID, UUID> parents = new HashMap<>();
        for (UUID id : visibleIds) {
            UUID parent = ancestry.getOrDefault(id, List.of()).stream()
                .filter(value -> visible.contains(value.workItemId()))
                .min(Comparator.comparingInt(AncestorRef::depth))
                .map(AncestorRef::workItemId)
                .orElse(null);
            parents.put(id, parent);
        }
        return parents;
    }

    private static Map<UUID, Integer> depths(
        List<UUID> ids, Map<UUID, UUID> parents
    ) {
        Map<UUID, Integer> result = new HashMap<>();
        for (UUID id : ids) depth(id, parents, result, new LinkedHashSet<>());
        return result;
    }

    private static int depth(
        UUID id,
        Map<UUID, UUID> parents,
        Map<UUID, Integer> result,
        Set<UUID> path
    ) {
        if (result.containsKey(id)) return result.get(id);
        if (!path.add(id)) {
            throw failure("GANTT_HIERARCHY_CYCLE", "Gantt hierarchy contains a cycle");
        }
        UUID parent = parents.get(id);
        int value = parent == null ? 0 : depth(parent, parents, result, path) + 1;
        path.remove(id);
        result.put(id, value);
        return value;
    }

    private static boolean ancestorsExpanded(
        UUID id, Map<UUID, UUID> parents, Set<UUID> expanded
    ) {
        UUID parent = parents.get(id);
        Set<UUID> seen = new HashSet<>();
        while (parent != null) {
            if (!seen.add(parent)) return false;
            if (!expanded.contains(parent)) return false;
            parent = parents.get(parent);
        }
        return true;
    }

    private void validate(GanttRequest request) {
        if (request == null || request.schemaVersion() != 1 || request.binding() == null
            || request.window() == null || request.query() == null) {
            throw failure("INVALID_GANTT_CONFIGURATION", "Gantt schema version 1 is required");
        }
        requireViewKey(request.viewKey());
        requireRelationKey(request.hierarchyRelationKey());
        if (request.expandedNodeIds() == null
            || request.expandedNodeIds().size() > MAX_EXPANDED
            || new HashSet<>(request.expandedNodeIds()).size() != request.expandedNodeIds().size()) {
            throw failure("GANTT_EXPANSION_BUDGET_EXCEEDED", "Gantt expansion budget exceeded");
        }
    }

    private void validatePreference(GanttPreferenceCommand command) {
        if (command == null || command.requestId() == null || command.requestId().isBlank()
            || command.requestId().length() > 120 || command.expectedVersion() < 0
            || command.binding() == null || command.expandedNodeIds() == null
            || command.expandedNodeIds().size() > MAX_EXPANDED
            || !ZOOMS.contains(command.zoom())) {
            throw failure("INVALID_GANTT_PREFERENCE", "Gantt preference is invalid");
        }
        requireRelationKey(command.hierarchyRelationKey());
        try {
            ZoneId.of(command.timezone());
        } catch (DateTimeException exception) {
            throw failure("INVALID_GANTT_TIMEZONE", "Gantt timezone is invalid");
        }
    }

    private String requireViewKey(String value) {
        if (value == null || !VIEW_KEY.matcher(value).matches()) {
            throw failure("INVALID_GANTT_CONFIGURATION", "Gantt view key is invalid");
        }
        return value;
    }

    private String requireRelationKey(String value) {
        if (value == null || !WorkItemRelationModels.SEMANTIC_KEY.matcher(value).matches()) {
            throw failure("INVALID_RELATION_KEY", "Gantt hierarchy relation key is invalid");
        }
        return value;
    }

    private static String calendarKey(String viewKey) {
        return "gantt-" + viewKey;
    }

    private record CriticalAnalysis(
        boolean available,
        String reason,
        Set<UUID> criticalNodes,
        Set<UUID> criticalEdges,
        Map<UUID, Long> floatDays
    ) {
        private static CriticalAnalysis disabled(String reason) {
            return new CriticalAnalysis(false, reason, Set.of(), Set.of(), Map.of());
        }
    }
}
