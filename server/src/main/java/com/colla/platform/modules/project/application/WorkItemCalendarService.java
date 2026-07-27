package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemCalendarModels.MAX_EVENTS;
import static com.colla.platform.modules.project.domain.WorkItemCalendarModels.MAX_OVERLAP_LANES;
import static com.colla.platform.modules.project.domain.WorkItemCalendarModels.MAX_WINDOW_DAYS;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarDay;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarEvent;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreference;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarRequest;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.CalendarResult;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateBinding;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutation;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.DateMutationResult;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.RangeWindow;
import com.colla.platform.modules.project.domain.WorkItemCalendarModels.WindowIndexEntry;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.modules.project.infrastructure.WorkItemCalendarRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemCalendarRepository.CommandRecord;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemCalendarService {
    private static final Pattern VIEW_KEY = Pattern.compile("^[a-z][a-z0-9._-]{0,79}$");
    private static final Pattern FIELD_KEY = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final List<String> MODES = List.of("month", "week", "day");

    private final WorkItemCalendarRepository repository;
    private final WorkItemQueryService queries;
    private final WorkItemQueryCanonicalizer canonicalizer;
    private final WorkItemService workItems;
    private final ObjectMapper objectMapper;

    public WorkItemCalendarService(
        WorkItemCalendarRepository repository,
        WorkItemQueryService queries,
        WorkItemQueryCanonicalizer canonicalizer,
        WorkItemService workItems,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.queries = queries;
        this.canonicalizer = canonicalizer;
        this.workItems = workItems;
        this.objectMapper = objectMapper;
        meterRegistry.counter("colla.project.work_item_calendar.registered");
    }

    public Optional<CalendarPreference> preference(
        CurrentUser user,
        UUID spaceId,
        String viewKey
    ) {
        workItems.requireQueryScope(user, spaceId);
        return repository.findPreference(
            user.workspaceId(), spaceId, user.id(), requireViewKey(viewKey)
        );
    }

    public CalendarPreference savePreference(
        CurrentUser user,
        UUID spaceId,
        String viewKey,
        CalendarPreferenceCommand command
    ) {
        workItems.requireQueryScope(user, spaceId);
        validatePreference(command);
        return repository.savePreference(
            user.workspaceId(), spaceId, user.id(), requireViewKey(viewKey), command
        );
    }

    public CalendarResult render(CurrentUser user, UUID spaceId, CalendarRequest request) {
        validateRequest(request);
        ZoneId zone = requireZone(request.window().timezone());
        FieldTypes types = requireBinding(
            user, spaceId, request.query().typeId(), request.binding()
        );
        QueryDefinition query = withCalendarFields(request.query(), request.binding());
        QueryResult result = queries.execute(user, spaceId, query);
        Instant windowStart = request.window().startDate().atStartOfDay(zone).toInstant();
        Instant windowEnd = request.window().endDate().plusDays(1).atStartOfDay(zone).toInstant();
        List<CalendarEvent> noDate = new ArrayList<>();
        List<EventDraft> drafts = new ArrayList<>();
        for (QueryItem item : result.items()) {
            String startValue = text(item.selected().get(field(request.binding().startField())));
            String endValue = request.binding().endField() == null
                ? null
                : text(item.selected().get(field(request.binding().endField())));
            if (startValue == null) {
                noDate.add(noDate(item, request.binding()));
                continue;
            }
            EventDraft draft = event(item, request.binding(), types, startValue, endValue, zone);
            if (draft.endInstant().isAfter(windowStart) && draft.startInstant().isBefore(windowEnd)) {
                drafts.add(draft);
            }
        }
        drafts.sort(Comparator.comparing(EventDraft::startInstant)
            .thenComparing(EventDraft::endInstant)
            .thenComparing(value -> value.item().id().toString()));
        List<Instant> laneEnds = new ArrayList<>();
        List<CalendarEvent> events = new ArrayList<>();
        for (EventDraft draft : drafts) {
            int lane = overlapLane(laneEnds, draft.startInstant(), draft.endInstant());
            events.add(toEvent(draft, lane));
        }
        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate date = request.window().startDate();
             !date.isAfter(request.window().endDate());
             date = date.plusDays(1)) {
            LocalDate current = date;
            days.add(new CalendarDay(
                current,
                events.stream().filter(event ->
                    !event.displayStartDate().isAfter(current)
                        && !event.displayEndDate().isBefore(current)
                ).toList()
            ));
        }
        if (repository.findPreference(
            user.workspaceId(), spaceId, user.id(), request.viewKey()
        ).isPresent()) {
            repository.replaceWindowIndex(
                user.workspaceId(), spaceId, user.id(), request.viewKey(),
                events.stream().map(event -> new WindowIndexEntry(
                    event.workItemId(), event.workItemVersion(),
                    event.displayStartDate(), event.displayEndDate(), event.allDay()
                )).toList()
            );
        }
        repository.recordRender(
            user.workspaceId(), spaceId, request.viewKey(), days.size(), events.size(),
            Math.min(MAX_OVERLAP_LANES, laneEnds.size())
        );
        return new CalendarResult(
            1,
            request.viewKey(),
            result.queryHash(),
            request.binding(),
            request.window(),
            List.copyOf(days),
            List.copyOf(noDate),
            events.size(),
            result.nextCursor(),
            false
        );
    }

    @Transactional
    public DateMutationResult mutateDate(
        CurrentUser user,
        UUID spaceId,
        String viewKey,
        UUID workItemId,
        DateMutation mutation
    ) {
        String normalizedViewKey = requireViewKey(viewKey);
        validateMutation(mutation);
        workItems.requireQueryScope(user, spaceId);
        CalendarPreference preference = repository.findPreference(
            user.workspaceId(), spaceId, user.id(), normalizedViewKey
        ).orElseThrow(() -> failure(
            "CALENDAR_PREFERENCE_REQUIRED",
            "Save the calendar preference before changing dates"
        ));
        WorkItemView current = workItems.get(user, spaceId, workItemId);
        FieldTypes types = requireBinding(
            user, spaceId, current.item().typeDefinitionId(), preference.binding()
        );
        ZoneId zone = requireZone(mutation.timezone());
        canonicalMutationValues(
            preference.binding(), types, mutation.startValue(), mutation.endValue(), zone
        );
        String operation = normalizeOperation(mutation.operation());
        String requestHash = sha256(json(List.of(
            normalizedViewKey,
            workItemId,
            mutation.expectedWorkItemVersion(),
            operation,
            mutation.startValue() == null ? "" : mutation.startValue(),
            mutation.endValue() == null ? "" : mutation.endValue(),
            mutation.timezone()
        )));
        Optional<CommandRecord> existing = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), operation, mutation.requestId()
        );
        if (existing.isPresent()) return replay(existing.get(), requestHash);
        if (current.item().version() != mutation.expectedWorkItemVersion()) {
            throw failure(
                "CALENDAR_WORK_ITEM_VERSION_CONFLICT",
                "Work item changed; refresh and retry"
            );
        }
        CommandRecord command = repository.beginCommand(
            user.workspaceId(), spaceId, user.id(), normalizedViewKey, workItemId,
            operation, mutation.requestId(), requestHash, mutation.expectedWorkItemVersion()
        );
        if (!command.requestHash().equals(requestHash)) {
            throw failure(
                "CALENDAR_REQUEST_CONFLICT",
                "Calendar request ID was reused with different input"
            );
        }
        if ("completed".equals(command.status())) return replay(command, requestHash);
        ObjectNode patch = objectMapper.createObjectNode();
        put(patch, preference.binding().startField(), mutation.startValue());
        if (preference.binding().endField() != null) {
            put(patch, preference.binding().endField(), mutation.endValue());
        }
        WorkItemView updated = workItems.update(
            user,
            spaceId,
            workItemId,
            null,
            patch,
            mutation.expectedWorkItemVersion(),
            "calendar:" + mutation.requestId()
        );
        DateMutationResult result = new DateMutationResult(
            workItemId,
            normalizedViewKey,
            value(updated.item().fieldValues(), preference.binding().startField()),
            preference.binding().endField() == null
                ? null
                : value(updated.item().fieldValues(), preference.binding().endField()),
            updated.item().version(),
            false
        );
        repository.completeCommand(command.id(), json(result));
        return result;
    }

    private FieldTypes requireBinding(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        DateBinding binding
    ) {
        if (typeId == null) {
            throw failure(
                "CALENDAR_TYPE_REQUIRED",
                "Calendar date fields require one bound work item type"
            );
        }
        RuntimeConfiguration startConfiguration = workItems.requireQueryCapability(
            user, spaceId, typeId, binding.startField(), "between", "none"
        );
        String startType = fieldType(startConfiguration, binding.startField());
        String endType = startType;
        if (binding.endField() != null) {
            RuntimeConfiguration endConfiguration = workItems.requireQueryCapability(
                user, spaceId, typeId, binding.endField(), "between", "none"
            );
            endType = fieldType(endConfiguration, binding.endField());
        }
        if (!List.of("date", "datetime").contains(startType)
            || !startType.equals(endType)
            || binding.allDay() != "date".equals(startType)) {
            throw failure(
                "CALENDAR_DATE_CAPABILITY_UNAVAILABLE",
                "Calendar bindings require matching published date or datetime fields"
            );
        }
        return new FieldTypes(startType, endType);
    }

    private String fieldType(RuntimeConfiguration configuration, String fieldKey) {
        for (JsonNode candidate : configuration.snapshot().path("fields")) {
            if (fieldKey.equals(candidate.path("fieldKey").asText())
                && "active".equals(candidate.path("status").asText())) {
                return candidate.path("fieldType").asText();
            }
        }
        throw failure(
            "CALENDAR_DATE_CAPABILITY_UNAVAILABLE",
            "Calendar field is not in the bound published snapshot"
        );
    }

    private QueryDefinition withCalendarFields(QueryDefinition source, DateBinding binding) {
        LinkedHashSet<String> select = new LinkedHashSet<>(source.select());
        select.add(field(binding.startField()));
        if (binding.endField() != null) select.add(field(binding.endField()));
        QueryDefinition augmented = new QueryDefinition(
            source.schemaVersion(),
            source.typeId(),
            source.filter(),
            source.sorts(),
            source.group(),
            List.copyOf(select),
            Math.min(MAX_EVENTS, Math.max(1, source.limit())),
            source.cursor()
        );
        return canonicalizer.canonicalize(augmented).definition();
    }

    private EventDraft event(
        QueryItem item,
        DateBinding binding,
        FieldTypes types,
        String startValue,
        String endValue,
        ZoneId zone
    ) {
        if ("date".equals(types.startType())) {
            LocalDate start = parseDate(startValue);
            LocalDate displayEnd = endValue == null ? start : parseDate(endValue);
            if (displayEnd.isBefore(start)) invalidRange();
            return new EventDraft(
                item, startValue, endValue,
                start.atStartOfDay(zone).toInstant(),
                displayEnd.plusDays(1).atStartOfDay(zone).toInstant(),
                start, displayEnd, true
            );
        }
        Instant start = parseInstant(startValue);
        Instant end = endValue == null ? start.plus(1, ChronoUnit.HOURS) : parseInstant(endValue);
        if (end.isBefore(start)) invalidRange();
        Instant effectiveEnd = end.equals(start) ? start.plusMillis(1) : end;
        return new EventDraft(
            item, startValue, endValue, start, effectiveEnd,
            start.atZone(zone).toLocalDate(), effectiveEnd.minusMillis(1).atZone(zone).toLocalDate(),
            false
        );
    }

    private void canonicalMutationValues(
        DateBinding binding,
        FieldTypes types,
        String startValue,
        String endValue,
        ZoneId zone
    ) {
        if (startValue == null) {
            if (endValue != null) invalidRange();
            return;
        }
        event(
            new QueryItem(
                new UUID(0, 0), new UUID(0, 0), new UUID(0, 0), "", "", "active", 0,
                new UUID(0, 0), Instant.EPOCH, Instant.EPOCH, objectMapper.createObjectNode(),
                java.util.Map.of(), List.of()
            ),
            binding, types, startValue, endValue, zone
        );
    }

    private CalendarEvent noDate(QueryItem item, DateBinding binding) {
        return new CalendarEvent(
            item.id(), item.displayKey(), item.title(), item.version(),
            null, null, null, null, null, null, binding.allDay(), 0, item.availableActions()
        );
    }

    private CalendarEvent toEvent(EventDraft draft, int lane) {
        return new CalendarEvent(
            draft.item().id(),
            draft.item().displayKey(),
            draft.item().title(),
            draft.item().version(),
            draft.startValue(),
            draft.endValue(),
            draft.startInstant(),
            draft.endInstant(),
            draft.displayStartDate(),
            draft.displayEndDate(),
            draft.allDay(),
            lane,
            draft.item().availableActions()
        );
    }

    private int overlapLane(List<Instant> laneEnds, Instant start, Instant end) {
        for (int index = 0; index < laneEnds.size(); index++) {
            if (!laneEnds.get(index).isAfter(start)) {
                laneEnds.set(index, end);
                return index;
            }
        }
        if (laneEnds.size() < MAX_OVERLAP_LANES) {
            laneEnds.add(end);
            return laneEnds.size() - 1;
        }
        return MAX_OVERLAP_LANES - 1;
    }

    private void validateRequest(CalendarRequest request) {
        if (request == null || request.schemaVersion() != 1 || request.query() == null
            || request.binding() == null || request.window() == null) {
            throw failure(
                "INVALID_CALENDAR_CONFIGURATION",
                "Calendar schema version 1 and complete request are required"
            );
        }
        requireViewKey(request.viewKey());
        validateBinding(request.binding());
        validateWindow(request.window());
    }

    private void validatePreference(CalendarPreferenceCommand command) {
        if (command == null || command.requestId() == null || command.requestId().isBlank()
            || command.requestId().length() > 120 || command.expectedVersion() < 0
            || command.binding() == null) {
            throw failure("INVALID_CALENDAR_CONFIGURATION", "Calendar preference is invalid");
        }
        validateBinding(command.binding());
        requireZone(command.timezone());
        requireMode(command.mode());
    }

    private void validateBinding(DateBinding binding) {
        requireFieldKey(binding.startField());
        if (binding.endField() != null) {
            requireFieldKey(binding.endField());
            if (binding.startField().equals(binding.endField())) {
                throw failure(
                    "INVALID_CALENDAR_CONFIGURATION",
                    "Calendar start and end fields must be different"
                );
            }
        }
    }

    private void validateWindow(RangeWindow window) {
        requireZone(window.timezone());
        requireMode(window.mode());
        if (window.startDate() == null || window.endDate() == null
            || window.endDate().isBefore(window.startDate())) {
            throw failure("INVALID_CALENDAR_WINDOW", "Calendar window is invalid");
        }
        long days = ChronoUnit.DAYS.between(
            window.startDate(), window.endDate().plusDays(1)
        );
        if (days < 1 || days > MAX_WINDOW_DAYS) {
            throw failure("CALENDAR_WINDOW_BUDGET_EXCEEDED", "Calendar window exceeds 62 days");
        }
    }

    private void validateMutation(DateMutation mutation) {
        if (mutation == null || mutation.requestId() == null || mutation.requestId().isBlank()
            || mutation.requestId().length() > 120 || mutation.expectedWorkItemVersion() < 0) {
            throw failure("INVALID_CALENDAR_MUTATION", "Calendar date mutation is invalid");
        }
        normalizeOperation(mutation.operation());
        requireZone(mutation.timezone());
    }

    private String normalizeOperation(String operation) {
        return switch (operation == null ? "" : operation.trim().toLowerCase(Locale.ROOT)) {
            case "move", "move_date" -> "move_date";
            case "resize", "resize_date" -> "resize_date";
            default -> throw failure(
                "INVALID_CALENDAR_MUTATION",
                "Calendar operation must be move or resize"
            );
        };
    }

    private DateMutationResult replay(CommandRecord record, String requestHash) {
        if (!record.requestHash().equals(requestHash)) {
            throw failure(
                "CALENDAR_REQUEST_CONFLICT",
                "Calendar request ID was reused with different input"
            );
        }
        if (!"completed".equals(record.status())) {
            throw failure("CALENDAR_REQUEST_IN_PROGRESS", "Calendar request is already in progress");
        }
        try {
            DateMutationResult value = objectMapper.readValue(
                record.responseJson(), DateMutationResult.class
            );
            return new DateMutationResult(
                value.workItemId(), value.viewKey(), value.startValue(), value.endValue(),
                value.workItemVersion(), true
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored calendar response is invalid", exception);
        }
    }

    private String requireViewKey(String value) {
        if (value == null || !VIEW_KEY.matcher(value).matches()) {
            throw failure("INVALID_CALENDAR_CONFIGURATION", "Calendar view key is invalid");
        }
        return value;
    }

    private String requireFieldKey(String value) {
        if (value == null || !FIELD_KEY.matcher(value).matches()) {
            throw failure("INVALID_CALENDAR_CONFIGURATION", "Calendar field key is invalid");
        }
        return value;
    }

    private ZoneId requireZone(String value) {
        try {
            return ZoneId.of(value == null ? "" : value);
        } catch (DateTimeException exception) {
            throw failure("INVALID_CALENDAR_TIMEZONE", "Calendar timezone is invalid");
        }
    }

    private String requireMode(String value) {
        String mode = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw failure("INVALID_CALENDAR_WINDOW", "Calendar mode is invalid");
        }
        return mode;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw failure("INVALID_CALENDAR_DATE", "Calendar date must use ISO-8601");
        }
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeException exception) {
            throw failure("INVALID_CALENDAR_DATE", "Calendar datetime must be an ISO-8601 instant");
        }
    }

    private void invalidRange() {
        throw failure("INVALID_CALENDAR_RANGE", "Calendar end must not be before start");
    }

    private void put(ObjectNode patch, String field, String value) {
        if (value == null) patch.putNull(field);
        else patch.put(field, value);
    }

    private String value(JsonNode values, String field) {
        JsonNode value = values.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String text(Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode node) return node.isNull() ? null : node.asText();
        return String.valueOf(value);
    }

    private String field(String value) {
        return "field." + value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure("INVALID_CALENDAR_CONFIGURATION", "Calendar input is invalid", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record FieldTypes(String startType, String endType) {
    }

    private record EventDraft(
        QueryItem item,
        String startValue,
        String endValue,
        Instant startInstant,
        Instant endInstant,
        LocalDate displayStartDate,
        LocalDate displayEndDate,
        boolean allDay
    ) {
    }
}
