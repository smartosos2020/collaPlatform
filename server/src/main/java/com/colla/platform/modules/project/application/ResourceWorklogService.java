package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ResourceWorklogModels.MAX_REVISIONS;
import static com.colla.platform.modules.project.domain.ResourceWorklogModels.MAX_WORKLOGS;
import static com.colla.platform.modules.project.domain.ResourceWorklogModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.Estimate;
import com.colla.platform.modules.project.domain.ResourcePlanningModels.WorkCalendar;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.MutateWorklogCommand;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.Variance;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.Worklog;
import com.colla.platform.modules.project.domain.ResourceWorklogModels.WorklogFoundation;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.ResourcePlanningRepository;
import com.colla.platform.modules.project.infrastructure.ResourceWorklogRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
public class ResourceWorklogService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Set<String> OPERATIONS =
        Set.of("create", "update", "submit", "withdraw", "void");
    private static final Set<String> SOURCES = Set.of("manual", "import", "proxy");
    private final ResourceWorklogRepository repository;
    private final ResourcePlanningRepository planning;
    private final ProjectSpaceRepository spaces;
    private final WorkItemService workItems;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ResourceWorklogService(
        ResourceWorklogRepository repository,
        ResourcePlanningRepository planning,
        ProjectSpaceRepository spaces,
        WorkItemService workItems,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this(
            repository, planning, spaces, workItems, auditLog, outbox,
            objectMapper, Clock.systemUTC()
        );
    }

    ResourceWorklogService(
        ResourceWorklogRepository repository,
        ResourcePlanningRepository planning,
        ProjectSpaceRepository spaces,
        WorkItemService workItems,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.planning = planning;
        this.spaces = spaces;
        this.workItems = workItems;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public WorklogFoundation get(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        List<Worklog> source = repository.list(
            user.workspaceId(), spaceId, MAX_WORKLOGS + 1, MAX_REVISIONS
        );
        boolean truncated = source.size() > MAX_WORKLOGS;
        List<Worklog> visible = source.stream().limit(MAX_WORKLOGS)
            .filter(value -> visible(user, spaceId, value.workItemId())).toList();
        WorkCalendar calendar = planning.findCalendar(
            user.workspaceId(), spaceId
        ).orElse(null);
        List<Estimate> estimates = planning.listEstimates(
            user.workspaceId(), spaceId, 200
        ).stream().filter(value -> visible(user, spaceId, value.workItemId())).toList();
        return new WorklogFoundation(
            visible, variance(visible, estimates, calendar), truncated
        );
    }

    @Transactional
    public Worklog mutate(
        CurrentUser user, UUID spaceId, MutateWorklogCommand command
    ) {
        ProjectSpaceSummary space = requireWritable(user, spaceId);
        validate(command);
        Worklog current = null;
        UUID itemId = command.workItemId();
        if (!"create".equals(command.operation())) {
            current = repository.list(
                user.workspaceId(), spaceId, MAX_WORKLOGS, 1
            ).stream().filter(value -> value.id().equals(command.worklogId()))
                .findFirst().orElseThrow(() ->
                    failure("NOT_FOUND_OR_HIDDEN", "Worklog is not available"));
            itemId = current.workItemId();
        }
        workItems.get(user, spaceId, itemId);
        UUID effectiveUser = command.userId() == null ? user.id() : command.userId();
        if (!effectiveUser.equals(user.id())
            && (!Set.of("owner", "admin").contains(space.currentUserRole())
                || command.reason().isBlank())) {
            throw failure(
                "RESOURCE_WORKLOG_PROXY_FORBIDDEN",
                "Proxy worklog mutation requires owner/admin and an immutable reason"
            );
        }
        String hash = hash(command);
        Optional<ResourceWorklogRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(),
                command.operation(), command.requestId()
            );
        if (replay.isPresent()) {
            if (!hash.equals(replay.get().requestHash())) {
                throw failure(
                    "RESOURCE_REQUEST_CONFLICT",
                    "Request ID was reused with different input"
                );
            }
            return read(replay.get().responseJson(), Worklog.class);
        }
        Worklog result = repository.mutate(
            user.workspaceId(), spaceId, user.id(),
            command, effectiveUser, hash
        );
        auditLog.log(
            user, "project_resource.worklog_" + command.operation(),
            "project_resource_worklog", result.id(),
            Map.of(
                "space_id", spaceId.toString(),
                "version", result.version(),
                "revision", result.currentRevision()
            )
        );
        outbox.append(
            user.workspaceId(), "project.resource.worklog.changed",
            "project_resource_worklog", result.id(), user.id(),
            Map.of(
                "operation", command.operation(),
                "version", result.version(),
                "state", result.approvalState()
            ),
            "project-resource:worklog:" + command.requestId()
        );
        return result;
    }

    private List<Variance> variance(
        List<Worklog> worklogs, List<Estimate> estimates, WorkCalendar calendar
    ) {
        Map<UUID, Integer> actual = new HashMap<>();
        worklogs.stream().filter(value -> "submitted".equals(value.approvalState()))
            .forEach(value -> actual.merge(
                value.workItemId(), value.durationMinutes(), Integer::sum
            ));
        return estimates.stream().map(estimate -> {
            boolean comparable = !"point".equals(estimate.unit()) && calendar != null;
            int estimated = 0;
            if (comparable) {
                estimated = ("day".equals(estimate.unit())
                    ? estimate.amount().multiply(
                        java.math.BigDecimal.valueOf(calendar.dailyMinutes())
                    )
                    : estimate.amount().multiply(java.math.BigDecimal.valueOf(60)))
                    .setScale(0, RoundingMode.CEILING).intValue();
            }
            int consumed = actual.getOrDefault(estimate.workItemId(), 0);
            return new Variance(
                estimate.workItemId(), estimate.unit(), estimated, consumed,
                comparable, comparable ? consumed - estimated : 0,
                comparable
                    ? "Submitted actual minutes minus current comparable estimate"
                    : "Point estimate or missing calendar is intentionally incomparable"
            );
        }).toList();
    }

    private void validate(MutateWorklogCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || command.requestId() == null
            || !REQUEST_ID.matcher(command.requestId()).matches()
            || !OPERATIONS.contains(command.operation())
            || command.expectedVersion() < 0
            || command.reason() == null || command.reason().length() > 500) {
            invalid();
        }
        if ("create".equals(command.operation())) {
            if (command.worklogId() != null || command.expectedVersion() != 0
                || command.workItemId() == null || command.workDate() == null
                || command.durationMinutes() < 1 || command.durationMinutes() > 1440
                || !SOURCES.contains(command.source())
                || command.workDate().isAfter(LocalDate.now(clock))) {
                invalid();
            }
        } else {
            if (command.worklogId() == null || command.expectedVersion() < 1) invalid();
            if ("update".equals(command.operation())
                && (command.workDate() == null
                    || command.workDate().isAfter(LocalDate.now(clock))
                    || command.durationMinutes() < 1
                    || command.durationMinutes() > 1440
                    || !SOURCES.contains(command.source())
                    || command.reason().isBlank())) {
                invalid();
            }
            if (Set.of("withdraw", "void").contains(command.operation())
                && command.reason().isBlank()) {
                invalid();
            }
        }
    }

    private void invalid() {
        throw failure("RESOURCE_WORKLOG_INVALID", "Resource worklog is invalid");
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

    private ProjectSpaceSummary requireWritable(CurrentUser user, UUID spaceId) {
        ProjectSpaceSummary space = requireVisible(user, spaceId);
        if ("guest".equals(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Guest project space members have read-only worklog access");
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
        return space;
    }

    private boolean visible(CurrentUser user, UUID spaceId, UUID workItemId) {
        try {
            workItems.get(user, spaceId, workItemId);
            return true;
        } catch (WorkItemRuntimeException exception) {
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
