package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ResourceCapacityModels.MAX_ALLOCATIONS;
import static com.colla.platform.modules.project.domain.ResourceCapacityModels.MAX_BUCKETS;
import static com.colla.platform.modules.project.domain.ResourceCapacityModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.Allocation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.CapacityFoundation;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.CapacityRule;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.LoadBucket;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.MutateAllocationCommand;
import com.colla.platform.modules.project.domain.ResourceCapacityModels.SaveCapacityRuleCommand;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.CalendarException;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.WorkCalendar;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.Worklog;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.ResourceCapacityRepository;
import com.colla.platform.modules.project.infrastructure.ResourcePlanningRepository;
import com.colla.platform.modules.project.infrastructure.ResourceWorklogRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
public class ResourceCapacityService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Set<String> OPERATIONS =
        Set.of("create", "update", "end", "archive");
    private final ResourceCapacityRepository repository;
    private final ResourcePlanningRepository planning;
    private final ResourceWorklogRepository worklogs;
    private final ProjectSpaceRepository spaces;
    private final ProjectSpaceMembershipRepository members;
    private final WorkItemService workItems;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ResourceCapacityService(
        ResourceCapacityRepository repository,
        ResourcePlanningRepository planning,
        ResourceWorklogRepository worklogs,
        ProjectSpaceRepository spaces,
        ProjectSpaceMembershipRepository members,
        WorkItemService workItems,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this(
            repository, planning, worklogs, spaces, members, workItems,
            auditLog, outbox, objectMapper, Clock.systemUTC()
        );
    }

    ResourceCapacityService(
        ResourceCapacityRepository repository,
        ResourcePlanningRepository planning,
        ResourceWorklogRepository worklogs,
        ProjectSpaceRepository spaces,
        ProjectSpaceMembershipRepository members,
        WorkItemService workItems,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.planning = planning;
        this.worklogs = worklogs;
        this.spaces = spaces;
        this.members = members;
        this.workItems = workItems;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public CapacityFoundation get(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        Set<UUID> activeUsers = members.listMembers(
            user.workspaceId(), spaceId
        ).stream().filter(ProjectSpaceMember::effective)
            .map(ProjectSpaceMember::userId).collect(java.util.stream.Collectors.toSet());
        List<Allocation> source = repository.listAllocations(
            user.workspaceId(), spaceId, MAX_ALLOCATIONS + 1
        );
        boolean truncated = source.size() > MAX_ALLOCATIONS;
        List<Allocation> allocations = source.stream().limit(MAX_ALLOCATIONS)
            .filter(value -> activeUsers.contains(value.userId()))
            .filter(value -> visible(user, spaceId, value.workItemId())).toList();
        List<CapacityRule> rules = repository.listRules(
            user.workspaceId(), spaceId, MAX_ALLOCATIONS
        ).stream().filter(value -> activeUsers.contains(value.userId())).toList();
        WorkCalendar calendar = planning.findCalendar(
            user.workspaceId(), spaceId
        ).orElse(defaultCalendar());
        List<Worklog> actual = worklogs.list(
            user.workspaceId(), spaceId, 200, 1
        ).stream().filter(value -> visible(user, spaceId, value.workItemId()))
            .filter(value -> activeUsers.contains(value.userId()))
            .filter(value -> "submitted".equals(value.approvalState())).toList();
        return new CapacityFoundation(
            allocations, rules, buckets(allocations, rules, calendar, actual), truncated
        );
    }

    @Transactional
    public Allocation mutate(
        CurrentUser user, UUID spaceId, MutateAllocationCommand command
    ) {
        requireManager(user, spaceId);
        validate(command);
        if ("create".equals(command.operation())) {
            requireActiveMember(user, spaceId, command.userId());
            workItems.get(user, spaceId, command.workItemId());
        } else {
            Allocation current = repository.listAllocations(
                user.workspaceId(), spaceId, MAX_ALLOCATIONS
            ).stream().filter(value -> value.id().equals(command.allocationId()))
                .findFirst().orElseThrow(() ->
                    failure("NOT_FOUND_OR_HIDDEN", "Allocation is not available"));
            requireActiveMember(user, spaceId, current.userId());
            workItems.get(user, spaceId, current.workItemId());
        }
        String hash = hash(command);
        Optional<ResourceCapacityRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), command.operation(), command.requestId()
        );
        if (replay.isPresent()) return replay(replay.get(), hash, Allocation.class);
        Allocation result = repository.mutateAllocation(
            user.workspaceId(), spaceId, user.id(), command, hash
        );
        emit(user, spaceId, result.id(), command.operation(), result.version());
        return result;
    }

    @Transactional
    public CapacityRule saveRule(
        CurrentUser user, UUID spaceId, SaveCapacityRuleCommand command
    ) {
        requireManager(user, spaceId);
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 0
            || command.userId() == null || command.dailyMinutes() < 1
            || command.dailyMinutes() > 1440 || command.warningPercent() == null
            || command.warningPercent().compareTo(BigDecimal.ONE) < 0
            || command.warningPercent().compareTo(BigDecimal.valueOf(100)) > 0) {
            invalid();
        }
        requireActiveMember(user, spaceId, command.userId());
        String hash = hash(command);
        Optional<ResourceCapacityRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "save_rule", command.requestId()
        );
        if (replay.isPresent()) return replay(replay.get(), hash, CapacityRule.class);
        CapacityRule result = repository.saveRule(
            user.workspaceId(), spaceId, user.id(), command, hash
        );
        emit(user, spaceId, result.id(), "save_rule", result.version());
        return result;
    }

    private List<LoadBucket> buckets(
        List<Allocation> allocations,
        List<CapacityRule> rules,
        WorkCalendar calendar,
        List<Worklog> worklogs
    ) {
        LocalDate windowStart = LocalDate.now(clock).minusDays(30);
        LocalDate windowEnd = windowStart.plusDays(MAX_BUCKETS - 1L);
        Map<UUID, CapacityRule> ruleByUser = rules.stream().collect(
            java.util.stream.Collectors.toMap(CapacityRule::userId, value -> value)
        );
        Map<String, Integer> allocated = new HashMap<>();
        Map<String, Integer> actual = new HashMap<>();
        Map<LocalDate, Integer> exceptions = calendar.exceptions().stream().collect(
            java.util.stream.Collectors.toMap(
                CalendarException::date, CalendarException::availableMinutes
            )
        );
        for (Allocation allocation : allocations) {
            if (!"active".equals(allocation.status())) continue;
            LocalDate start = allocation.startDate().isAfter(windowStart)
                ? allocation.startDate() : windowStart;
            LocalDate end = allocation.endDate().isBefore(windowEnd)
                ? allocation.endDate() : windowEnd;
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                int base = capacity(date, allocation.userId(), calendar, exceptions, ruleByUser);
                int minutes = allocation.allocationPercent()
                    .multiply(BigDecimal.valueOf(base))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).intValue();
                allocated.merge(key(allocation.userId(), date), minutes, Integer::sum);
            }
        }
        for (Worklog worklog : worklogs) {
            if (!worklog.workDate().isBefore(windowStart)
                && !worklog.workDate().isAfter(windowEnd)) {
                actual.merge(
                    key(worklog.userId(), worklog.workDate()),
                    worklog.durationMinutes(), Integer::sum
                );
            }
        }
        List<LoadBucket> result = new ArrayList<>();
        Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(allocated.keySet());
        keys.addAll(actual.keySet());
        for (String composite : keys) {
            String[] parts = composite.split("\\|");
            UUID userId = UUID.fromString(parts[0]);
            LocalDate date = LocalDate.parse(parts[1]);
            int capacity = capacity(date, userId, calendar, exceptions, ruleByUser);
            int load = allocated.getOrDefault(composite, 0);
            int spent = actual.getOrDefault(composite, 0);
            BigDecimal warning = ruleByUser.getOrDefault(
                userId, defaultRule(userId, calendar.dailyMinutes())
            ).warningPercent();
            BigDecimal ratio = capacity == 0
                ? (load == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(999))
                : BigDecimal.valueOf(load).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
            String signal = ratio.compareTo(BigDecimal.valueOf(100)) > 0
                ? "overloaded"
                : ratio.compareTo(warning) >= 0 ? "full" : "underloaded";
            result.add(new LoadBucket(
                userId, date, capacity, load, spent, signal,
                "overloaded".equals(signal),
                "Current calendar/rule capacity versus active allocations; submitted actual is contextual"
            ));
        }
        return result.stream().limit(MAX_BUCKETS).toList();
    }

    private int capacity(
        LocalDate date,
        UUID userId,
        WorkCalendar calendar,
        Map<LocalDate, Integer> exceptions,
        Map<UUID, CapacityRule> rules
    ) {
        int base = exceptions.getOrDefault(
            date, calendar.workDays().contains(date.getDayOfWeek().getValue())
                ? calendar.dailyMinutes() : 0
        );
        CapacityRule rule = rules.get(userId);
        return rule == null || base == 0 ? base : Math.min(base, rule.dailyMinutes());
    }

    private CapacityRule defaultRule(UUID userId, int minutes) {
        return new CapacityRule(
            null, userId, minutes, BigDecimal.valueOf(80), 0, null,
            java.time.Instant.EPOCH
        );
    }

    private WorkCalendar defaultCalendar() {
        return new WorkCalendar(
            null, "UTC", List.of(1, 2, 3, 4, 5), 480, List.of(),
            0, null, java.time.Instant.EPOCH
        );
    }

    private String key(UUID userId, LocalDate date) {
        return userId + "|" + date;
    }

    private void validate(MutateAllocationCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || !OPERATIONS.contains(command.operation())
            || command.expectedVersion() < 0 || command.reason() == null
            || command.reason().length() > 500) {
            invalid();
        }
        if ("create".equals(command.operation())) {
            if (command.allocationId() != null || command.expectedVersion() != 0
                || command.workItemId() == null || command.userId() == null
                || invalidWindow(command) || command.reason().isBlank()) invalid();
        } else {
            if (command.allocationId() == null || command.expectedVersion() < 1
                || command.reason().isBlank()) invalid();
            if ("update".equals(command.operation()) && invalidWindow(command)) invalid();
        }
    }

    private boolean invalidWindow(MutateAllocationCommand command) {
        return command.startDate() == null || command.endDate() == null
            || command.endDate().isBefore(command.startDate())
            || command.endDate().isAfter(command.startDate().plusDays(365))
            || command.allocationPercent() == null
            || command.allocationPercent().compareTo(new BigDecimal("0.01")) < 0
            || command.allocationPercent().compareTo(BigDecimal.valueOf(100)) > 0;
    }

    private void requireActiveMember(CurrentUser user, UUID spaceId, UUID userId) {
        ProjectSpaceMember member = members.findMemberByUser(
            user.workspaceId(), spaceId, userId
        ).orElseThrow(() ->
            failure("RESOURCE_MEMBER_INVALID", "Allocation member is not active"));
        if (!member.effective()) {
            throw failure("RESOURCE_MEMBER_INVALID", "Allocation member is not active");
        }
    }

    private ProjectSpaceSummary requireVisible(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = spaces.findById(
            user.workspaceId(), spaceId, user.id()
        ).orElseThrow(() ->
            failure("NOT_FOUND_OR_HIDDEN", "Project space is not available"));
        if (!space.isMember() || "archived".equals(space.status())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Project space is not available");
        }
        return space;
    }

    private void requireManager(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        if (!space.canManage()) {
            throw failure("FORBIDDEN", "Only space owner/admin can adjust resource capacity");
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
    }

    private boolean visible(CurrentUser user, UUID spaceId, UUID itemId) {
        try {
            workItems.get(user, spaceId, itemId);
            return true;
        } catch (WorkItemRuntimeException exception) {
            return false;
        }
    }

    private void emit(
        CurrentUser user, UUID spaceId, UUID id, String operation, long version
    ) {
        auditLog.log(
            user, "project_resource.capacity_" + operation,
            "project_resource_capacity", id,
            Map.of("space_id", spaceId.toString(), "version", version)
        );
        outbox.append(
            user.workspaceId(), "project.resource.capacity.changed",
            "project_resource_capacity", id, user.id(),
            Map.of("operation", operation, "version", version),
            "project-resource:capacity:" + operation + ":" + id + ":" + version
        );
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private void invalid() {
        throw failure("RESOURCE_CAPACITY_INVALID", "Resource capacity input is invalid");
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
        ResourceCapacityRepository.CommandRecord record, String hash, Class<T> type
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
