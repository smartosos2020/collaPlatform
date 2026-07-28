package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ResourcePlanningModels.MAX_ESTIMATES;
import static com.colla.platform.modules.project.domain.ResourcePlanningModels.MAX_EXCEPTIONS;
import static com.colla.platform.modules.project.domain.ResourcePlanningModels.MAX_SCHEDULE_DAYS;
import static com.colla.platform.modules.project.domain.ResourcePlanningModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.CalendarException;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.Estimate;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.PlanningFoundation;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.SaveCalendarCommand;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.SaveEstimateCommand;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.ScheduleProjection;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.WorkCalendar;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.ResourcePlanningRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
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
public class ResourcePlanningService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Set<String> UNITS = Set.of("hour", "day", "point");
    private final ResourcePlanningRepository repository;
    private final ProjectSpaceRepository spaces;
    private final WorkItemService workItems;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ResourcePlanningService(
        ResourcePlanningRepository repository,
        ProjectSpaceRepository spaces,
        WorkItemService workItems,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this(
            repository, spaces, workItems, auditLog, outbox,
            objectMapper, Clock.systemUTC()
        );
    }

    ResourcePlanningService(
        ResourcePlanningRepository repository,
        ProjectSpaceRepository spaces,
        WorkItemService workItems,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.spaces = spaces;
        this.workItems = workItems;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PlanningFoundation get(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        WorkCalendar calendar = repository.findCalendar(
            user.workspaceId(), spaceId
        ).orElseGet(this::defaultCalendar);
        List<Estimate> estimates = repository.listEstimates(
            user.workspaceId(), spaceId, MAX_ESTIMATES
        ).stream().filter(value -> visible(user, spaceId, value.workItemId())).toList();
        List<ScheduleProjection> schedule = estimates.stream()
            .map(value -> schedule(calendar, value)).toList();
        return new PlanningFoundation(calendar, estimates, schedule);
    }

    @Transactional
    public WorkCalendar saveCalendar(
        CurrentUser user, UUID spaceId, SaveCalendarCommand command
    ) {
        requireWritable(user, spaceId);
        validateCalendar(command);
        String hash = hash(command);
        Optional<ResourcePlanningRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(),
                "save_calendar", command.requestId()
            );
        if (replay.isPresent()) {
            requireHash(replay.get(), hash);
            return read(replay.get().responseJson(), WorkCalendar.class);
        }
        WorkCalendar result = repository.saveCalendar(
            user.workspaceId(), spaceId, user.id(), command, hash
        );
        emit(user, spaceId, "calendar", result.id(), result.version(), command.requestId());
        return result;
    }

    @Transactional
    public Estimate saveEstimate(
        CurrentUser user, UUID spaceId, SaveEstimateCommand command
    ) {
        requireWritable(user, spaceId);
        validateEstimate(command);
        var item = workItems.get(user, spaceId, command.workItemId()).item();
        String hash = hash(command);
        Optional<ResourcePlanningRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(),
                "save_estimate", command.requestId()
            );
        if (replay.isPresent()) {
            requireHash(replay.get(), hash);
            return read(replay.get().responseJson(), Estimate.class);
        }
        Estimate result = repository.saveEstimate(
            user.workspaceId(), spaceId, user.id(), command.workItemId(),
            item.version(), command.unit(), command.amount(),
            command.expectedVersion(), command.requestId(), hash
        );
        emit(user, spaceId, "estimate", result.id(), result.version(), command.requestId());
        return result;
    }

    private ScheduleProjection schedule(WorkCalendar calendar, Estimate estimate) {
        if ("point".equals(estimate.unit())) {
            return new ScheduleProjection(
                estimate.workItemId(), estimate.version(), false, 0,
                null, null, false, "Story points are intentionally not converted to time"
            );
        }
        BigDecimal minutes = "day".equals(estimate.unit())
            ? estimate.amount().multiply(BigDecimal.valueOf(calendar.dailyMinutes()))
            : estimate.amount().multiply(BigDecimal.valueOf(60));
        int required = minutes.setScale(0, RoundingMode.CEILING).intValueExact();
        LocalDate start = LocalDate.now(clock.withZone(ZoneId.of(calendar.timezone())));
        LocalDate date = start;
        int remaining = required;
        int scanned = 0;
        Map<LocalDate, Integer> exceptions = calendar.exceptions().stream()
            .collect(java.util.stream.Collectors.toMap(
                CalendarException::date, CalendarException::availableMinutes
            ));
        while (remaining > 0 && scanned < MAX_SCHEDULE_DAYS) {
            int available = exceptions.getOrDefault(
                date,
                calendar.workDays().contains(date.getDayOfWeek().getValue())
                    ? calendar.dailyMinutes() : 0
            );
            remaining -= available;
            if (remaining > 0) date = date.plusDays(1);
            scanned++;
        }
        boolean truncated = remaining > 0;
        return new ScheduleProjection(
            estimate.workItemId(), estimate.version(), true, required,
            start, truncated ? null : date, truncated,
            truncated ? "Calendar horizon exceeded"
                : "Derived from current estimate and work calendar"
        );
    }

    private WorkCalendar defaultCalendar() {
        return new WorkCalendar(
            null, "UTC",
            List.of(
                DayOfWeek.MONDAY.getValue(), DayOfWeek.TUESDAY.getValue(),
                DayOfWeek.WEDNESDAY.getValue(), DayOfWeek.THURSDAY.getValue(),
                DayOfWeek.FRIDAY.getValue()
            ),
            480, List.of(), 0, null, Instant.EPOCH
        );
    }

    private boolean visible(CurrentUser user, UUID spaceId, UUID workItemId) {
        try {
            workItems.get(user, spaceId, workItemId);
            return true;
        } catch (WorkItemRuntimeException exception) {
            return false;
        }
    }

    private void validateCalendar(SaveCalendarCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 0
            || command.timezone() == null || command.workDays() == null
            || command.workDays().isEmpty() || command.workDays().size() > 7
            || new HashSet<>(command.workDays()).size() != command.workDays().size()
            || command.workDays().stream().anyMatch(value -> value < 1 || value > 7)
            || command.dailyMinutes() < 1 || command.dailyMinutes() > 1440
            || command.exceptions() == null
            || command.exceptions().size() > MAX_EXCEPTIONS
            || !validZone(command.timezone())
            || !validExceptions(command)) {
            throw failure("RESOURCE_CALENDAR_INVALID", "Resource calendar is invalid");
        }
    }

    private boolean validExceptions(SaveCalendarCommand command) {
        Set<UUID> ids = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        return command.exceptions().stream().allMatch(value ->
            value != null && value.id() != null && value.date() != null
                && value.availableMinutes() >= 0
                && value.availableMinutes() <= 1440
                && value.note() != null && value.note().length() <= 240
                && ids.add(value.id()) && dates.add(value.date())
        );
    }

    private void validateEstimate(SaveEstimateCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 0
            || command.workItemId() == null || !UNITS.contains(command.unit())
            || command.amount() == null
            || command.amount().scale() > 2
            || command.amount().compareTo(new BigDecimal("0.01")) < 0
            || command.amount().compareTo(new BigDecimal("100000")) > 0) {
            throw failure("RESOURCE_ESTIMATE_INVALID", "Resource estimate is invalid");
        }
    }

    private ProjectSpaceSummary requireVisible(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaces.findById(
            user.workspaceId(), spaceId, user.id()
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "Project space is not available"
        ));
        if (!space.isMember() || "archived".equals(space.status())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space is not available");
        }
        return space;
    }

    private void requireWritable(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        if ("guest".equals(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Guest project space members have read-only resource access");
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
    }

    private void emit(
        CurrentUser user, UUID spaceId, String kind,
        UUID aggregateId, long version, String requestId
    ) {
        auditLog.log(
            user, "project_resource." + kind + "_saved",
            "project_resource_" + kind, aggregateId,
            Map.of("space_id", spaceId.toString(), "version", version)
        );
        outbox.append(
            user.workspaceId(), "project.resource.changed",
            "project_resource_" + kind, aggregateId, user.id(),
            Map.of("kind", kind, "version", version),
            "project-resource:" + kind + ":" + requestId
        );
    }

    private void requireHash(
        ResourcePlanningRepository.CommandRecord record, String hash
    ) {
        if (!hash.equals(record.requestHash())) {
            throw failure(
                "RESOURCE_REQUEST_CONFLICT",
                "Request ID was reused with different input"
            );
        }
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private boolean validZone(String value) {
        try {
            ZoneId.of(value);
            return value.length() <= 80;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String hash(Object value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    json(value).getBytes(StandardCharsets.UTF_8)
                )
            );
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
