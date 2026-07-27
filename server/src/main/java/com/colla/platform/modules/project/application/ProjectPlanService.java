package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectPlanModels.MAX_CHANGES;
import static com.colla.platform.modules.project.domain.ProjectPlanModels.MAX_LINKS;
import static com.colla.platform.modules.project.domain.ProjectPlanModels.MAX_MILESTONES;
import static com.colla.platform.modules.project.domain.ProjectPlanModels.MAX_PHASES;
import static com.colla.platform.modules.project.domain.ProjectPlanModels.MAX_PLANS;
import static com.colla.platform.modules.project.domain.ProjectPlanModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectPlanModels.CreateCommand;
import com.colla.platform.modules.project.domain.ProjectPlanModels.LinkInput;
import com.colla.platform.modules.project.domain.ProjectPlanModels.MilestoneInput;
import com.colla.platform.modules.project.domain.ProjectPlanModels.MutateCommand;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PhaseInput;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanLink;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanMilestone;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanProgress;
import com.colla.platform.modules.project.domain.ProjectPlanModels.PlanSummary;
import com.colla.platform.modules.project.domain.ProjectPlanModels.ProjectPlan;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectPlanRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
public class ProjectPlanService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_-]{0,63}$");
    private static final Set<String> ITEM_STATUSES = Set.of("planned", "active", "completed");
    private static final Set<String> OPERATIONS = Set.of("update", "publish", "archive", "restore");

    private final ProjectPlanRepository repository;
    private final ProjectSpaceRepository spaces;
    private final ProjectSpaceMembershipRepository members;
    private final WorkItemService workItems;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ProjectPlanService(
        ProjectPlanRepository repository,
        ProjectSpaceRepository spaces,
        ProjectSpaceMembershipRepository members,
        WorkItemService workItems,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this(
            repository, spaces, members, workItems, auditLog, outbox,
            objectMapper, Clock.systemUTC()
        );
    }

    ProjectPlanService(
        ProjectPlanRepository repository,
        ProjectSpaceRepository spaces,
        ProjectSpaceMembershipRepository members,
        WorkItemService workItems,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.spaces = spaces;
        this.members = members;
        this.workItems = workItems;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public List<PlanSummary> list(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        return repository.list(user.workspaceId(), spaceId, MAX_PLANS);
    }

    public ProjectPlan get(CurrentUser user, UUID spaceId, UUID planId) {
        requireVisible(user, spaceId);
        return projected(
            user,
            spaceId,
            repository.find(user.workspaceId(), spaceId, planId, MAX_CHANGES)
                .orElseThrow(() -> failure(
                    "NOT_FOUND_OR_HIDDEN", "Project plan is not available"
                ))
        );
    }

    @Transactional
    public ProjectPlan create(CurrentUser user, UUID spaceId, CreateCommand command) {
        requireWritable(user, spaceId);
        validateCreate(command);
        if (repository.list(user.workspaceId(), spaceId, MAX_PLANS).stream()
            .filter(plan -> !"archived".equals(plan.status())).count() >= MAX_PLANS) {
            throw failure("PROJECT_PLAN_LIMIT_REACHED", "Project plan limit reached");
        }
        String requestHash = hash(command);
        Optional<ProjectPlanRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), "create", command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return projected(user, spaceId, read(replay.get().responseJson()));
        }
        Map<UUID, Long> versions = validateGraph(
            user, spaceId, command.startDate(), command.endDate(),
            command.phases(), command.milestones(), command.links()
        );
        ProjectPlan result = repository.create(
            user.workspaceId(), spaceId, user.id(), command.requestId(),
            requestHash, command.name().trim(), normalized(command.description()),
            command.startDate(), command.endDate(), command.phases(),
            command.milestones(), command.links(), versions
        );
        emit(user, spaceId, result.plan(), "create", command.requestId());
        return projected(user, spaceId, result);
    }

    @Transactional
    public ProjectPlan mutate(
        CurrentUser user,
        UUID spaceId,
        UUID planId,
        MutateCommand command
    ) {
        requireWritable(user, spaceId);
        validateMutation(command);
        String operation = command.operation().trim().toLowerCase();
        String requestHash = hash(List.of(planId, command));
        Optional<ProjectPlanRepository.CommandRecord> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), operation, command.requestId()
        );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return projected(user, spaceId, read(replay.get().responseJson()));
        }
        ProjectPlan current = repository.find(
            user.workspaceId(), spaceId, planId, MAX_CHANGES
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "Project plan is not available"
        ));
        Map<UUID, Long> versions = Map.of();
        String name = current.plan().name();
        String description = current.plan().description();
        LocalDate startDate = current.plan().startDate();
        LocalDate endDate = current.plan().endDate();
        List<PhaseInput> phases = List.of();
        List<MilestoneInput> milestones = List.of();
        List<LinkInput> links = List.of();
        if ("update".equals(operation)) {
            requireEditable(command);
            versions = validateGraph(
                user, spaceId, command.startDate(), command.endDate(),
                command.phases(), command.milestones(), command.links()
            );
            name = command.name().trim();
            description = normalized(command.description());
            startDate = command.startDate();
            endDate = command.endDate();
            phases = command.phases();
            milestones = command.milestones();
            links = command.links();
        } else if ("publish".equals(operation)
            && (current.phases().isEmpty() || current.milestones().isEmpty())) {
            throw failure(
                "PROJECT_PLAN_PUBLISH_INCOMPLETE",
                "A project plan needs at least one phase and milestone before publication"
            );
        }
        ProjectPlan result = repository.mutate(
            user.workspaceId(), spaceId, user.id(), planId, operation,
            command.requestId(), requestHash, command.expectedVersion(),
            normalized(command.reason()), name, description, startDate, endDate,
            phases, milestones, links, versions
        );
        emit(user, spaceId, result.plan(), operation, command.requestId());
        return projected(user, spaceId, result);
    }

    private Map<UUID, Long> validateGraph(
        CurrentUser user,
        UUID spaceId,
        LocalDate planStart,
        LocalDate planEnd,
        List<PhaseInput> phases,
        List<MilestoneInput> milestones,
        List<LinkInput> links
    ) {
        if (planStart == null || planEnd == null || planEnd.isBefore(planStart)
            || phases == null || phases.isEmpty() || phases.size() > MAX_PHASES
            || milestones == null || milestones.isEmpty()
            || milestones.size() > MAX_MILESTONES
            || links == null || links.size() > MAX_LINKS) {
            throw failure("PROJECT_PLAN_GRAPH_INVALID", "Project plan graph is invalid");
        }
        Map<UUID, PhaseInput> phaseById = new LinkedHashMap<>();
        Set<String> phaseKeys = new HashSet<>();
        Set<Integer> phasePositions = new HashSet<>();
        for (PhaseInput phase : phases) {
            if (phase == null || phase.id() == null || !key(phase.phaseKey())
                || !name(phase.name()) || !ITEM_STATUSES.contains(phase.status())
                || phase.position() < 0 || phase.position() >= MAX_PHASES
                || phase.startDate() == null || phase.endDate() == null
                || phase.endDate().isBefore(phase.startDate())
                || phase.startDate().isBefore(planStart)
                || phase.endDate().isAfter(planEnd)
                || phaseById.putIfAbsent(phase.id(), phase) != null
                || !phaseKeys.add(phase.phaseKey())
                || !phasePositions.add(phase.position())) {
                throw failure("PROJECT_PLAN_PHASE_INVALID", "Project plan phase is invalid");
            }
        }
        Set<UUID> milestoneIds = new HashSet<>();
        Set<String> milestoneKeys = new HashSet<>();
        Set<Integer> milestonePositions = new HashSet<>();
        for (MilestoneInput milestone : milestones) {
            PhaseInput phase = milestone == null ? null : phaseById.get(milestone.phaseId());
            if (milestone == null || milestone.id() == null || phase == null
                || !key(milestone.milestoneKey()) || !name(milestone.name())
                || !ITEM_STATUSES.contains(milestone.status())
                || milestone.position() < 0 || milestone.position() >= MAX_MILESTONES
                || milestone.targetDate() == null
                || milestone.targetDate().isBefore(phase.startDate())
                || milestone.targetDate().isAfter(phase.endDate())
                || !milestoneIds.add(milestone.id())
                || !milestoneKeys.add(milestone.milestoneKey())
                || !milestonePositions.add(milestone.position())) {
                throw failure(
                    "PROJECT_PLAN_MILESTONE_INVALID", "Project plan milestone is invalid"
                );
            }
            if (milestone.ownerUserId() != null) {
                ProjectSpaceMember member = members.findMemberByUser(
                    user.workspaceId(), spaceId, milestone.ownerUserId()
                ).orElseThrow(() -> failure(
                    "PROJECT_PLAN_OWNER_INVALID", "Milestone owner is not an active member"
                ));
                if (!member.effective()) {
                    throw failure(
                        "PROJECT_PLAN_OWNER_INVALID", "Milestone owner is not an active member"
                    );
                }
            }
        }
        Map<UUID, Long> versions = new HashMap<>();
        Set<String> pairs = new HashSet<>();
        Set<UUID> linkIds = new HashSet<>();
        for (LinkInput link : links) {
            if (link == null || link.id() == null || link.workItemId() == null
                || !milestoneIds.contains(link.milestoneId())
                || !linkIds.add(link.id())
                || !pairs.add(link.milestoneId() + ":" + link.workItemId())) {
                throw failure("PROJECT_PLAN_LINK_INVALID", "Project plan link is invalid");
            }
            versions.computeIfAbsent(
                link.workItemId(),
                id -> workItems.get(user, spaceId, id).item().version()
            );
        }
        return Map.copyOf(versions);
    }

    private ProjectPlan projected(CurrentUser user, UUID spaceId, ProjectPlan raw) {
        List<PlanLink> visibleLinks = new ArrayList<>();
        for (PlanLink link : raw.links()) {
            try {
                workItems.get(user, spaceId, link.workItemId());
                visibleLinks.add(link);
            } catch (WorkItemRuntimeException ignored) {
                // Hidden and revoked links are omitted without count or reason disclosure.
            }
        }
        List<PlanMilestone> safeMilestones = raw.milestones().stream()
            .map(milestone -> {
                UUID owner = milestone.ownerUserId();
                if (owner != null && members.findMemberByUser(
                    user.workspaceId(), spaceId, owner
                ).filter(ProjectSpaceMember::effective).isEmpty()) {
                    owner = null;
                }
                return new PlanMilestone(
                    milestone.id(), milestone.phaseId(), milestone.milestoneKey(),
                    milestone.name(), milestone.position(), milestone.targetDate(),
                    milestone.status(), owner
                );
            }).toList();
        int completed = (int) safeMilestones.stream()
            .filter(value -> "completed".equals(value.status())).count();
        int overdue = (int) safeMilestones.stream()
            .filter(value -> !"completed".equals(value.status())
                && value.targetDate().isBefore(LocalDate.now(clock))).count();
        int percent = safeMilestones.isEmpty()
            ? 0 : completed * 100 / safeMilestones.size();
        PlanProgress progress = new PlanProgress(
            safeMilestones.size(), completed, visibleLinks.size(), overdue,
            percent, raw.links().size() > visibleLinks.size()
        );
        return new ProjectPlan(
            raw.plan(), raw.phases(), safeMilestones, List.copyOf(visibleLinks),
            raw.changes(), progress
        );
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
            throw failure("FORBIDDEN", "Guest project space members have read-only plan access");
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
    }

    private void validateCreate(CreateCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || !name(command.name())
            || normalized(command.description()).length() > 1000) {
            throw failure("PROJECT_PLAN_COMMAND_INVALID", "Project plan create command is invalid");
        }
    }

    private void validateMutation(MutateCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 1
            || command.operation() == null
            || !OPERATIONS.contains(command.operation().trim().toLowerCase())
            || normalized(command.reason()).length() > 500) {
            throw failure("PROJECT_PLAN_COMMAND_INVALID", "Project plan command is invalid");
        }
    }

    private void requireEditable(MutateCommand command) {
        if (!name(command.name())
            || normalized(command.description()).length() > 1000) {
            throw failure("PROJECT_PLAN_COMMAND_INVALID", "Project plan update is invalid");
        }
    }

    private void emit(
        CurrentUser user,
        UUID spaceId,
        PlanSummary plan,
        String operation,
        String requestId
    ) {
        Map<String, Object> metadata = Map.of(
            "space_id", spaceId.toString(),
            "operation", operation,
            "version", plan.version(),
            "status", plan.status()
        );
        auditLog.log(
            user, "project_plan." + operation, "project_plan", plan.id(), metadata
        );
        outbox.append(
            user.workspaceId(),
            "project.plan.changed",
            "project_plan",
            plan.id(),
            user.id(),
            Map.of(
                "operation", operation,
                "status", plan.status(),
                "version", plan.version()
            ),
            "project-plan:" + operation + ":" + requestId
        );
    }

    private void requireHash(
        ProjectPlanRepository.CommandRecord command, String expected
    ) {
        if (!expected.equals(command.requestHash())) {
            throw failure(
                "PROJECT_PLAN_REQUEST_CONFLICT",
                "Request ID was reused with different input"
            );
        }
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private boolean key(String value) {
        return value != null && KEY.matcher(value).matches();
    }

    private boolean name(String value) {
        return value != null && !value.trim().isEmpty() && value.trim().length() <= 120;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
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
            throw new IllegalStateException("Could not serialize project plan value", exception);
        }
    }

    private ProjectPlan read(String value) {
        try {
            return objectMapper.readValue(value, ProjectPlan.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read project plan command receipt", exception);
        }
    }
}
