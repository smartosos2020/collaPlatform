package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectRegisterModels.MAX_ENTRIES;
import static com.colla.platform.modules.project.domain.ProjectRegisterModels.MAX_HISTORY;
import static com.colla.platform.modules.project.domain.ProjectRegisterModels.MAX_REFERENCES;
import static com.colla.platform.modules.project.domain.ProjectRegisterModels.MAX_RESPONSES;
import static com.colla.platform.modules.project.domain.ProjectRegisterModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectPlanModels.ProjectPlan;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.CreateCommand;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.MutateCommand;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.ReferenceInput;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterEntry;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterReference;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.RegisterSummary;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.ResponseInput;
import com.colla.platform.modules.project.domain.ProjectRegisterModels.ResponsePlan;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectRegisterRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectRegisterService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Pattern RESPONSE_TYPE = Pattern.compile("^[a-z][a-z0-9_-]{0,23}$");
    private static final Set<String> TYPES = Set.of("risk", "issue", "decision", "change");
    private static final Set<String> REFERENCE_TYPES = Set.of("work_item", "plan");
    private static final Set<String> RESPONSE_STATUSES =
        Set.of("planned", "active", "completed", "cancelled");
    private static final Map<String, Set<String>> OPERATIONS = Map.of(
        "risk", Set.of("update", "assess", "monitor", "close", "reopen"),
        "issue", Set.of("update", "escalate", "resolve", "verify", "reopen"),
        "decision", Set.of("update", "adopt", "supersede", "revoke"),
        "change", Set.of("update", "analyze", "approve", "reject", "apply", "reopen")
    );

    private final ProjectRegisterRepository repository;
    private final ProjectSpaceRepository spaces;
    private final ProjectSpaceMembershipRepository members;
    private final WorkItemService workItems;
    private final ProjectPlanService plans;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public ProjectRegisterService(
        ProjectRegisterRepository repository,
        ProjectSpaceRepository spaces,
        ProjectSpaceMembershipRepository members,
        WorkItemService workItems,
        ProjectPlanService plans,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.spaces = spaces;
        this.members = members;
        this.workItems = workItems;
        this.plans = plans;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public List<RegisterSummary> list(
        CurrentUser user, UUID spaceId, String entryType
    ) {
        requireVisible(user, spaceId);
        String type = normalized(entryType).toLowerCase();
        if (!type.isEmpty() && !TYPES.contains(type)) {
            throw failure("PROJECT_REGISTER_TYPE_INVALID", "Register type is invalid");
        }
        return repository.list(
            user.workspaceId(), spaceId, type.isEmpty() ? null : type, MAX_ENTRIES
        ).stream().map(value -> safeSummary(user, spaceId, value)).toList();
    }

    public RegisterEntry get(CurrentUser user, UUID spaceId, UUID entryId) {
        requireVisible(user, spaceId);
        return projected(
            user, spaceId, repository.find(
                user.workspaceId(), spaceId, entryId, MAX_HISTORY
            ).orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Project register entry is not available"
            ))
        );
    }

    @Transactional
    public RegisterEntry create(
        CurrentUser user, UUID spaceId, CreateCommand command
    ) {
        requireWritable(user, spaceId);
        validateCreate(command);
        if (repository.list(
            user.workspaceId(), spaceId, null, MAX_ENTRIES
        ).size() >= MAX_ENTRIES) {
            throw failure(
                "PROJECT_REGISTER_LIMIT_REACHED", "Project register limit reached"
            );
        }
        String requestHash = hash(command);
        Optional<ProjectRegisterRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(), "create", command.requestId()
            );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return projected(user, spaceId, read(replay.get().responseJson()));
        }
        validateTypeDetails(
            command.entryType(), command.probability(), command.impact(),
            command.decisionBasis(), command.changeImpact()
        );
        requireMember(user, spaceId, command.ownerUserId());
        Map<UUID, Long> versions = validateChildren(
            user, spaceId, command.references(), command.responses()
        );
        RegisterEntry result = repository.create(
            user.workspaceId(), spaceId, user.id(), command.requestId(), requestHash,
            command.entryType(), command.title().trim(), normalized(command.summary()),
            command.ownerUserId(), command.dueDate(), command.probability(),
            command.impact(), normalized(command.decisionBasis()),
            normalized(command.changeImpact()), command.references(),
            command.responses(), versions
        );
        emit(user, spaceId, result.entry(), "create", command.requestId());
        return projected(user, spaceId, result);
    }

    @Transactional
    public RegisterEntry mutate(
        CurrentUser user, UUID spaceId, UUID entryId, MutateCommand command
    ) {
        requireWritable(user, spaceId);
        validateMutation(command);
        RegisterEntry current = repository.find(
            user.workspaceId(), spaceId, entryId, MAX_HISTORY
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "Project register entry is not available"
        ));
        String operation = normalized(command.operation()).toLowerCase();
        if (!OPERATIONS.get(current.entry().entryType()).contains(operation)) {
            throw failure(
                "PROJECT_REGISTER_TRANSITION_INVALID",
                "Project register transition is invalid"
            );
        }
        String requestHash = hash(List.of(entryId, command));
        Optional<ProjectRegisterRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(), operation, command.requestId()
            );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return projected(user, spaceId, read(replay.get().responseJson()));
        }
        validateTypeDetails(
            current.entry().entryType(), command.probability(), command.impact(),
            command.decisionBasis(), command.changeImpact()
        );
        requireText(command.title(), 160, "PROJECT_REGISTER_COMMAND_INVALID");
        if (normalized(command.summary()).length() > 2000
            || normalized(command.verification()).length() > 1000
            || normalized(command.reason()).length() > 500) {
            throw failure(
                "PROJECT_REGISTER_COMMAND_INVALID", "Register command is invalid"
            );
        }
        requireMember(user, spaceId, command.ownerUserId());
        validateTransition(user, spaceId, entryId, current, operation, command);
        Map<UUID, Long> versions = validateChildren(
            user, spaceId, command.references(), command.responses()
        );
        if ("approve".equals(operation)) {
            applyPlanAction(user, spaceId, command);
        }
        RegisterEntry result = repository.mutate(
            user.workspaceId(), spaceId, user.id(), entryId, operation,
            command.requestId(), requestHash, command.expectedVersion(),
            normalized(command.reason()), command.title().trim(),
            normalized(command.summary()), command.ownerUserId(), command.dueDate(),
            command.probability(), command.impact(),
            normalized(command.decisionBasis()), normalized(command.changeImpact()),
            command.supersedesEntryId(), normalized(command.verification()),
            command.references(), command.responses(), versions
        );
        emit(user, spaceId, result.entry(), operation, command.requestId());
        return projected(user, spaceId, result);
    }

    private void validateTransition(
        CurrentUser user,
        UUID spaceId,
        UUID entryId,
        RegisterEntry current,
        String operation,
        MutateCommand command
    ) {
        if (Set.of("close", "resolve", "verify", "supersede", "revoke",
            "approve", "reject", "apply").contains(operation)
            && normalized(command.reason()).length() < 3) {
            throw failure(
                "PROJECT_REGISTER_REASON_REQUIRED", "A transition reason is required"
            );
        }
        if ("verify".equals(operation)
            && normalized(command.verification()).length() < 3) {
            throw failure(
                "PROJECT_REGISTER_VERIFICATION_REQUIRED",
                "Issue verification is required"
            );
        }
        if ("supersede".equals(operation)) {
            UUID target = command.supersedesEntryId();
            if (target == null || target.equals(entryId)) {
                throw failure(
                    "PROJECT_REGISTER_DECISION_CHAIN_INVALID",
                    "Decision supersession chain is invalid"
                );
            }
            Set<UUID> visited = new HashSet<>();
            visited.add(entryId);
            while (target != null && visited.add(target)) {
                RegisterSummary prior = repository.find(
                    user.workspaceId(), spaceId, target, 1
                ).map(RegisterEntry::entry).orElseThrow(() -> failure(
                    "NOT_FOUND_OR_HIDDEN", "Decision is not available"
                ));
                if (!"decision".equals(prior.entryType())) {
                    throw failure(
                        "PROJECT_REGISTER_DECISION_CHAIN_INVALID",
                        "Decision supersession chain is invalid"
                    );
                }
                target = prior.supersedesEntryId();
            }
            if (target != null) {
                throw failure(
                    "PROJECT_REGISTER_DECISION_CHAIN_INVALID",
                    "Decision supersession chain is cyclic"
                );
            }
        }
    }

    private void applyPlanAction(
        CurrentUser user, UUID spaceId, MutateCommand command
    ) {
        if (command.planAction() == null
            || !Set.of("publish", "archive", "restore")
                .contains(normalized(command.planAction().operation()).toLowerCase())
            || !requestId(command.planAction().requestId())) {
            throw failure(
                "PROJECT_REGISTER_PLAN_ACTION_REQUIRED",
                "Approved change requires a canonical plan action"
            );
        }
        ProjectPlan plan = plans.get(user, spaceId, command.planAction().planId());
        plans.mutate(
            user,
            spaceId,
            plan.plan().id(),
            new com.colla.platform.modules.project.domain.ProjectPlanModels.MutateCommand(
                1,
                command.planAction().requestId(),
                command.planAction().expectedPlanVersion(),
                command.planAction().operation(),
                "approved_change:" + command.requestId(),
                plan.plan().name(),
                plan.plan().description(),
                plan.plan().startDate(),
                plan.plan().endDate(),
                List.of(),
                List.of(),
                List.of()
            )
        );
    }

    private Map<UUID, Long> validateChildren(
        CurrentUser user,
        UUID spaceId,
        List<ReferenceInput> references,
        List<ResponseInput> responses
    ) {
        if (references == null || references.size() > MAX_REFERENCES
            || responses == null || responses.size() > MAX_RESPONSES) {
            throw failure(
                "PROJECT_REGISTER_GRAPH_INVALID", "Register graph exceeds its limit"
            );
        }
        Map<UUID, Long> versions = new HashMap<>();
        Set<UUID> ids = new HashSet<>();
        Set<String> sources = new HashSet<>();
        for (ReferenceInput reference : references) {
            if (reference == null || reference.id() == null
                || reference.sourceId() == null
                || !REFERENCE_TYPES.contains(reference.sourceType())
                || !ids.add(reference.id())
                || !sources.add(reference.sourceType() + ":" + reference.sourceId())) {
                throw failure(
                    "PROJECT_REGISTER_REFERENCE_INVALID",
                    "Register reference is invalid"
                );
            }
            long version = "work_item".equals(reference.sourceType())
                ? workItems.get(user, spaceId, reference.sourceId()).item().version()
                : plans.get(user, spaceId, reference.sourceId()).plan().version();
            versions.put(reference.sourceId(), version);
        }
        ids.clear();
        for (ResponseInput response : responses) {
            if (response == null || response.id() == null || !ids.add(response.id())
                || response.responseType() == null
                || !RESPONSE_TYPE.matcher(response.responseType()).matches()
                || normalized(response.description()).isEmpty()
                || normalized(response.description()).length() > 1000
                || !RESPONSE_STATUSES.contains(response.status())) {
                throw failure(
                    "PROJECT_REGISTER_RESPONSE_INVALID",
                    "Register response plan is invalid"
                );
            }
            requireMember(user, spaceId, response.ownerUserId());
        }
        return Map.copyOf(versions);
    }

    private RegisterEntry projected(
        CurrentUser user, UUID spaceId, RegisterEntry raw
    ) {
        List<RegisterReference> visible = new ArrayList<>();
        for (RegisterReference reference : raw.references()) {
            try {
                if ("work_item".equals(reference.sourceType())) {
                    workItems.get(user, spaceId, reference.sourceId());
                } else {
                    plans.get(user, spaceId, reference.sourceId());
                }
                visible.add(reference);
            } catch (WorkItemRuntimeException ignored) {
                // Current authorization hides the complete reference shape.
            }
        }
        List<ResponsePlan> responses = raw.responses().stream()
            .map(value -> new ResponsePlan(
                value.id(), value.responseType(), value.description(),
                safeOwner(user, spaceId, value.ownerUserId()), value.dueDate(),
                value.status()
            )).toList();
        return new RegisterEntry(
            safeSummary(user, spaceId, raw.entry()), List.copyOf(visible),
            responses, raw.history(), visible.size() != raw.references().size()
        );
    }

    private RegisterSummary safeSummary(
        CurrentUser user, UUID spaceId, RegisterSummary value
    ) {
        return new RegisterSummary(
            value.id(), value.entryType(), value.title(), value.summary(),
            value.status(), safeOwner(user, spaceId, value.ownerUserId()),
            value.dueDate(), value.probability(), value.impact(), value.score(),
            value.decisionBasis(), value.changeImpact(), value.supersedesEntryId(),
            value.verification(), value.version(), value.createdBy(),
            value.createdAt(), value.updatedBy(), value.updatedAt()
        );
    }

    private UUID safeOwner(CurrentUser user, UUID spaceId, UUID owner) {
        if (owner == null) {
            return null;
        }
        return members.findMemberByUser(user.workspaceId(), spaceId, owner)
            .filter(ProjectSpaceMember::effective).map(ProjectSpaceMember::userId)
            .orElse(null);
    }

    private void validateCreate(CreateCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || !TYPES.contains(command.entryType())) {
            throw failure(
                "PROJECT_REGISTER_COMMAND_INVALID", "Register create command is invalid"
            );
        }
        requireText(command.title(), 160, "PROJECT_REGISTER_COMMAND_INVALID");
        if (normalized(command.summary()).length() > 2000) {
            throw failure(
                "PROJECT_REGISTER_COMMAND_INVALID", "Register create command is invalid"
            );
        }
    }

    private void validateMutation(MutateCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 1
            || normalized(command.operation()).isEmpty()) {
            throw failure(
                "PROJECT_REGISTER_COMMAND_INVALID", "Register command is invalid"
            );
        }
    }

    private void validateTypeDetails(
        String type, Integer probability, Integer impact,
        String decisionBasis, String changeImpact
    ) {
        if ("risk".equals(type)) {
            if (probability == null || impact == null
                || probability < 1 || probability > 5 || impact < 1 || impact > 5) {
                throw failure(
                    "PROJECT_REGISTER_RISK_ASSESSMENT_INVALID",
                    "Risk probability and impact must be between 1 and 5"
                );
            }
        } else if (probability != null || impact != null) {
            throw failure(
                "PROJECT_REGISTER_TYPE_DETAIL_INVALID", "Type detail is invalid"
            );
        }
        if ("decision".equals(type) && normalized(decisionBasis).length() < 3) {
            throw failure(
                "PROJECT_REGISTER_DECISION_BASIS_REQUIRED",
                "Decision basis is required"
            );
        }
        if ("change".equals(type) && normalized(changeImpact).length() < 3) {
            throw failure(
                "PROJECT_REGISTER_CHANGE_IMPACT_REQUIRED",
                "Change impact is required"
            );
        }
        if (normalized(decisionBasis).length() > 2000
            || normalized(changeImpact).length() > 2000) {
            throw failure(
                "PROJECT_REGISTER_TYPE_DETAIL_INVALID", "Type detail is invalid"
            );
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
            throw failure(
                "FORBIDDEN", "Guest project space members have read-only register access"
            );
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
    }

    private void requireMember(CurrentUser user, UUID spaceId, UUID owner) {
        if (owner != null && members.findMemberByUser(
            user.workspaceId(), spaceId, owner
        ).filter(ProjectSpaceMember::effective).isEmpty()) {
            throw failure(
                "PROJECT_REGISTER_OWNER_INVALID",
                "Register owner is not an active member"
            );
        }
    }

    private void emit(
        CurrentUser user, UUID spaceId, RegisterSummary entry,
        String operation, String requestId
    ) {
        Map<String, Object> metadata = Map.of(
            "space_id", spaceId.toString(),
            "entry_type", entry.entryType(),
            "operation", operation,
            "status", entry.status(),
            "version", entry.version()
        );
        auditLog.log(
            user, "project_register." + operation,
            "project_register_entry", entry.id(), metadata
        );
        outbox.append(
            user.workspaceId(), "project.register.changed",
            "project_register_entry", entry.id(), user.id(),
            Map.of(
                "entryType", entry.entryType(),
                "operation", operation,
                "status", entry.status(),
                "version", entry.version()
            ),
            "project-register:" + operation + ":" + requestId
        );
    }

    private void requireHash(
        ProjectRegisterRepository.CommandRecord record, String expected
    ) {
        if (!expected.equals(record.requestHash())) {
            throw failure(
                "PROJECT_REGISTER_REQUEST_CONFLICT",
                "Request ID was reused with different input"
            );
        }
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private void requireText(String value, int max, String code) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) {
            throw failure(code, "Required register text is invalid");
        }
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
            throw new IllegalStateException("Could not serialize register value", exception);
        }
    }

    private RegisterEntry read(String value) {
        try {
            return objectMapper.readValue(value, RegisterEntry.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read register command receipt", exception);
        }
    }
}
