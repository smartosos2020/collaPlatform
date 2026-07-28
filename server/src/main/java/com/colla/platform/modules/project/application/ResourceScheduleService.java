package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ResourceScheduleModels.MAX_BARS;
import static com.colla.platform.modules.project.domain.ResourceScheduleModels.MAX_MARKERS;
import static com.colla.platform.modules.project.domain.ResourceScheduleModels.MAX_ROWS;
import static com.colla.platform.modules.project.domain.ResourceScheduleModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.Allocation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.CapacityFoundation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.LoadBucket;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.MutateAllocationCommand;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.AdjustmentCommand;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.AdjustmentResult;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.AssignmentBar;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.ConflictMarker;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.ResourceRow;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.ResourceSchedule;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.SavePreferenceCommand;
import com.colla.platform.modules.project.domain.ResourceScheduleModels.SchedulePreference;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.ResourceScheduleRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
public class ResourceScheduleService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Set<String> ZOOMS = Set.of("day", "week", "month");
    private final ResourceScheduleRepository repository;
    private final ResourceCapacityService capacity;
    private final ProjectSpaceRepository spaces;
    private final AuditLog audit;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ResourceScheduleService(
        ResourceScheduleRepository repository,
        ResourceCapacityService capacity,
        ProjectSpaceRepository spaces,
        AuditLog audit,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this(repository, capacity, spaces, audit, outbox, objectMapper, Clock.systemUTC());
    }

    ResourceScheduleService(
        ResourceScheduleRepository repository,
        ResourceCapacityService capacity,
        ProjectSpaceRepository spaces,
        AuditLog audit,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.capacity = capacity;
        this.spaces = spaces;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ResourceSchedule get(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        LocalDate today = LocalDate.now(clock);
        SchedulePreference preference = repository.findPreference(
            user.workspaceId(), spaceId, user.id()
        ).orElse(new SchedulePreference(
            null, today.minusDays(7), today.plusDays(30), "week", 0, null
        ));
        CapacityFoundation source = capacity.get(user, spaceId);
        List<AssignmentBar> bars = source.allocations().stream()
            .filter(value -> "active".equals(value.status()))
            .filter(value -> overlaps(
                value.startDate(), value.endDate(),
                preference.windowStart(), preference.windowEnd()
            ))
            .limit(MAX_BARS)
            .map(value -> new AssignmentBar(
                value.id(), value.workItemId(), value.userId(),
                value.startDate(), value.endDate(),
                value.allocationPercent(), value.version()
            )).toList();
        List<LoadBucket> buckets = source.buckets().stream()
            .filter(value -> !value.date().isBefore(preference.windowStart())
                && !value.date().isAfter(preference.windowEnd()))
            .toList();
        Map<UUID, int[]> totals = new LinkedHashMap<>();
        for (LoadBucket bucket : buckets) {
            int[] value = totals.computeIfAbsent(bucket.userId(), ignored -> new int[4]);
            value[0] += bucket.capacityMinutes();
            value[1] += bucket.allocatedMinutes();
            value[2] += bucket.actualMinutes();
            if (bucket.conflict()) value[3]++;
        }
        for (AssignmentBar bar : bars) totals.computeIfAbsent(bar.userId(), ignored -> new int[4]);
        List<ResourceRow> rows = totals.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .limit(MAX_ROWS)
            .map(value -> new ResourceRow(
                value.getKey(), value.getValue()[0], value.getValue()[1],
                value.getValue()[2], value.getValue()[3]
            )).toList();
        List<ConflictMarker> markers = buckets.stream()
            .filter(LoadBucket::conflict)
            .limit(MAX_MARKERS)
            .map(value -> new ConflictMarker(
                value.userId(), value.date(), value.signal(),
                value.capacityMinutes(), value.allocatedMinutes(), value.explanation()
            )).toList();
        boolean truncated = source.truncated() || totals.size() > MAX_ROWS
            || source.allocations().size() > MAX_BARS || markers.size() >= MAX_MARKERS;
        return new ResourceSchedule(
            preference.windowStart(), preference.windowEnd(), preference.zoom(),
            rows, bars, markers, preference, truncated
        );
    }

    @Transactional
    public SchedulePreference savePreference(
        CurrentUser user, UUID spaceId, SavePreferenceCommand command
    ) {
        requireVisible(user, spaceId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 0
            || command.windowStart() == null || command.windowEnd() == null
            || command.windowEnd().isBefore(command.windowStart())
            || command.windowStart().plusDays(366).isBefore(command.windowEnd())
            || !ZOOMS.contains(command.zoom())) {
            invalid();
        }
        String hash = hash(command);
        Optional<ResourceScheduleRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "save_preference", command.requestId()
        );
        if (replay.isPresent()) return replay(replay.get(), hash, SchedulePreference.class);
        SchedulePreference result = repository.savePreference(
            user.workspaceId(), spaceId, user.id(), command, hash
        );
        emit(user, spaceId, result.id(), "preference_saved", result.version());
        return result;
    }

    @Transactional
    public AdjustmentResult adjust(
        CurrentUser user, UUID spaceId, AdjustmentCommand command
    ) {
        requireManager(user, spaceId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.allocationId() == null
            || command.expectedVersion() < 1 || command.startDate() == null
            || command.endDate() == null || command.endDate().isBefore(command.startDate())
            || command.startDate().plusDays(366).isBefore(command.endDate())
            || command.allocationPercent() == null
            || command.allocationPercent().signum() <= 0
            || command.allocationPercent().compareTo(java.math.BigDecimal.valueOf(100)) > 0
            || command.reason() == null || command.reason().isBlank()
            || command.reason().length() > 500) {
            invalid();
        }
        String hash = hash(command);
        if (!command.preview()) {
            Optional<ResourceScheduleRepository.CommandRecord> replay = repository.findCommand(
                user.workspaceId(), spaceId, user.id(),
                "adjust_allocation", command.requestId()
            );
            if (replay.isPresent()) {
                return replay(replay.get(), hash, AdjustmentResult.class);
            }
        }
        Allocation current = capacity.get(user, spaceId).allocations().stream()
            .filter(value -> value.id().equals(command.allocationId()))
            .findFirst().orElseThrow(() ->
                failure("NOT_FOUND_OR_HIDDEN", "Allocation is not available"));
        if (current.version() != command.expectedVersion()) {
            throw failure(
                "RESOURCE_ALLOCATION_VERSION_CONFLICT",
                "Resource allocation changed concurrently"
            );
        }
        AdjustmentResult preview = new AdjustmentResult(
            command.preview(), false, current.id(), command.startDate(), command.endDate(),
            command.allocationPercent(), current.version(), "canonical-allocation:update"
        );
        if (command.preview()) return preview;
        Allocation result = capacity.mutate(
            user, spaceId, new MutateAllocationCommand(
                SCHEMA_VERSION, "schedule:" + command.requestId(), "update",
                current.id(), current.version(), null, null,
                command.startDate(), command.endDate(),
                command.allocationPercent(), command.reason()
            )
        );
        AdjustmentResult committed = new AdjustmentResult(
            false, true, result.id(), result.startDate(), result.endDate(),
            result.allocationPercent(), result.version(), "canonical-allocation:update"
        );
        repository.saveCommand(
            user.workspaceId(), spaceId, user.id(), "adjust_allocation",
            command.requestId(), hash, committed
        );
        return committed;
    }

    private ProjectSpaceSummary requireVisible(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaces.findById(
            user.workspaceId(), spaceId, user.id()
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Project space is not available"));
        if (!space.isMember() || "archived".equals(space.status())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space is not available");
        }
        return space;
    }

    private void requireManager(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        if (!space.canManage()) {
            throw failure("FORBIDDEN", "Only space owner/admin can adjust resource schedule");
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
    }

    private void emit(
        CurrentUser user, UUID spaceId, UUID id, String operation, long version
    ) {
        audit.log(
            user, "project_resource.schedule_" + operation,
            "project_resource_schedule", id,
            Map.of("space_id", spaceId.toString(), "version", version)
        );
        outbox.append(
            user.workspaceId(), "project.resource.schedule.changed",
            "project_resource_schedule", id, user.id(),
            Map.of("operation", operation, "version", version),
            "project-resource:schedule:" + operation + ":" + id + ":" + version
        );
    }

    private boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        return !aEnd.isBefore(bStart) && !aStart.isAfter(bEnd);
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private void invalid() {
        throw failure("RESOURCE_SCHEDULE_INVALID", "Resource schedule input is invalid");
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

    private <T> T replay(
        ResourceScheduleRepository.CommandRecord record, String hash, Class<T> type
    ) {
        if (!hash.equals(record.requestHash())) {
            throw failure("RESOURCE_REQUEST_CONFLICT", "Request ID was reused");
        }
        try {
            return objectMapper.readValue(record.responseJson(), type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
