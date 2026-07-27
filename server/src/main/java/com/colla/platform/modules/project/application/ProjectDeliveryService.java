package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectDeliveryModels.MAX_DELIVERABLES;
import static com.colla.platform.modules.project.domain.ProjectDeliveryModels.MAX_MATERIALS;
import static com.colla.platform.modules.project.domain.ProjectDeliveryModels.MAX_REVIEW_ITEMS;
import static com.colla.platform.modules.project.domain.ProjectDeliveryModels.MAX_SIGNERS;
import static com.colla.platform.modules.project.domain.ProjectDeliveryModels.MAX_VERSIONS;
import static com.colla.platform.modules.project.domain.ProjectDeliveryModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectRegistry;
import com.colla.platform.modules.platform.contract.PlatformObjectSummary;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.Acceptance;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.CreateCommand;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.Deliverable;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableSummary;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.DeliverableVersion;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.MaterialInput;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.MaterialReference;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.MutateCommand;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.ReviewRound;
import com.colla.platform.modules.project.domain.ProjectDeliveryModels.Signoff;
import com.colla.platform.modules.project.domain.ProjectPlanModels.ProjectPlan;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.infrastructure.ProjectDeliveryRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
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
public class ProjectDeliveryService {
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,120}$");
    private static final Set<String> OPERATIONS = Set.of(
        "update", "submit_version", "withdraw_version", "archive", "restore",
        "open_review", "reopen_review", "sign", "revoke_signoff",
        "close_review", "accept", "reject"
    );
    private static final Set<String> PLATFORM_MATERIALS =
        Set.of("file", "knowledge_content", "work_item");
    private static final Set<String> MATERIALS =
        Set.of("file", "knowledge_content", "work_item", "plan", "milestone", "external");

    private final ProjectDeliveryRepository repository;
    private final ProjectSpaceRepository spaces;
    private final ProjectSpaceMembershipRepository members;
    private final ProjectPlanService plans;
    private final ProjectRegisterService register;
    private final PlatformObjectRegistry objects;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public ProjectDeliveryService(
        ProjectDeliveryRepository repository,
        ProjectSpaceRepository spaces,
        ProjectSpaceMembershipRepository members,
        ProjectPlanService plans,
        ProjectRegisterService register,
        PlatformObjectRegistry objects,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.spaces = spaces;
        this.members = members;
        this.plans = plans;
        this.register = register;
        this.objects = objects;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public List<DeliverableSummary> list(CurrentUser user, UUID spaceId) {
        requireVisible(user, spaceId);
        return repository.list(
            user.workspaceId(), spaceId, MAX_DELIVERABLES
        ).stream().map(value -> safeSummary(user, spaceId, value)).toList();
    }

    public Deliverable get(CurrentUser user, UUID spaceId, UUID deliverableId) {
        requireVisible(user, spaceId);
        return projected(
            user, spaceId, repository.find(
                user.workspaceId(), spaceId, deliverableId
            ).orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Project deliverable is not available"
            ))
        );
    }

    @Transactional
    public Deliverable create(
        CurrentUser user, UUID spaceId, CreateCommand command
    ) {
        requireWritable(user, spaceId);
        validateCreate(command);
        if (repository.list(
            user.workspaceId(), spaceId, MAX_DELIVERABLES
        ).size() >= MAX_DELIVERABLES) {
            throw failure(
                "PROJECT_DELIVERABLE_LIMIT_REACHED", "Deliverable limit reached"
            );
        }
        String requestHash = hash(command);
        Optional<ProjectDeliveryRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(), "create", command.requestId()
            );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return projected(user, spaceId, read(replay.get().responseJson()));
        }
        requireMember(user, spaceId, command.ownerUserId());
        validateTraceability(
            user, spaceId, command.planId(), command.milestoneId(),
            command.registerEntryIds()
        );
        Deliverable result = repository.create(
            user.workspaceId(), spaceId, user.id(), command.requestId(),
            requestHash, command.title().trim(), normalized(command.summary()),
            command.ownerUserId(), command.dueDate(), command.planId(),
            command.milestoneId(), command.registerEntryIds()
        );
        emit(user, spaceId, result.deliverable(), "create", command.requestId());
        return projected(user, spaceId, result);
    }

    @Transactional
    public Deliverable mutate(
        CurrentUser user,
        UUID spaceId,
        UUID deliverableId,
        MutateCommand command
    ) {
        requireWritable(user, spaceId);
        validateMutation(command);
        Deliverable current = repository.find(
            user.workspaceId(), spaceId, deliverableId
        ).orElseThrow(() -> failure(
            "NOT_FOUND_OR_HIDDEN", "Project deliverable is not available"
        ));
        String operation = normalized(command.operation()).toLowerCase();
        String requestHash = hash(List.of(deliverableId, command));
        Optional<ProjectDeliveryRepository.CommandRecord> replay =
            repository.findCommand(
                user.workspaceId(), spaceId, user.id(), operation, command.requestId()
            );
        if (replay.isPresent()) {
            requireHash(replay.get(), requestHash);
            return projected(user, spaceId, read(replay.get().responseJson()));
        }
        validateTransition(user, spaceId, current, operation, command);
        requireText(command.title(), 160);
        if (normalized(command.summary()).length() > 2000) {
            throw failure(
                "PROJECT_DELIVERABLE_COMMAND_INVALID", "Deliverable summary is invalid"
            );
        }
        requireMember(user, spaceId, command.ownerUserId());
        Map<UUID, Long> materialVersions = validateMaterials(
            user, spaceId, command.materials()
        );
        validateReview(user, spaceId, command.reviewItems(),
            command.requiredSignerIds(), command.quorum());
        Deliverable result = repository.mutate(
            user.workspaceId(), spaceId, user.id(), deliverableId,
            operation, command.requestId(), requestHash, command.expectedVersion(),
            normalized(command.reason()), command.title().trim(),
            normalized(command.summary()), command.ownerUserId(), command.dueDate(),
            normalized(command.versionLabel()), normalized(command.versionNote()),
            command.materials(), materialVersions, command.reviewItems(),
            command.requiredSignerIds(), command.quorum() == null ? 0 : command.quorum(),
            normalized(command.conclusion()).toLowerCase(), normalized(command.comment())
        );
        emit(user, spaceId, result.deliverable(), operation, command.requestId());
        return projected(user, spaceId, result);
    }

    private void validateTransition(
        CurrentUser user,
        UUID spaceId,
        Deliverable current,
        String operation,
        MutateCommand command
    ) {
        String status = current.deliverable().status();
        if ("submit_version".equals(operation)) {
            if (current.versions().size() >= MAX_VERSIONS
                || normalized(command.versionLabel()).isEmpty()
                || normalized(command.versionLabel()).length() > 80
                || normalized(command.versionNote()).length() > 1000) {
                throw failure(
                    "PROJECT_DELIVERABLE_VERSION_INVALID",
                    "Deliverable version is invalid"
                );
            }
        }
        if ("withdraw_version".equals(operation) && !"submitted".equals(status)) {
            invalidTransition();
        }
        if ("open_review".equals(operation)
            && !Set.of("submitted", "withdrawn").contains(status)) {
            invalidTransition();
        }
        if ("reopen_review".equals(operation) && !"reviewed".equals(status)) {
            invalidTransition();
        }
        if (Set.of("sign", "revoke_signoff", "close_review").contains(operation)
            && !"reviewing".equals(status)) {
            invalidTransition();
        }
        if (Set.of("accept", "reject").contains(operation)
            && !"reviewed".equals(status)) {
            invalidTransition();
        }
        if ("restore".equals(operation) && !"archived".equals(status)) {
            invalidTransition();
        }
        if ("archive".equals(operation) && "archived".equals(status)) {
            invalidTransition();
        }
        if (Set.of("withdraw_version", "close_review", "accept", "reject", "archive")
            .contains(operation) && normalized(command.reason()).length() < 3) {
            throw failure(
                "PROJECT_DELIVERABLE_REASON_REQUIRED",
                "A transition reason is required"
            );
        }
        if (Set.of("accept", "reject").contains(operation)
            && normalized(command.comment()).length() < 3) {
            throw failure(
                "PROJECT_DELIVERABLE_ACCEPTANCE_INVALID",
                "An acceptance conclusion is required"
            );
        }
        if ("sign".equals(operation) || "revoke_signoff".equals(operation)) {
            ReviewRound review = current.reviews().stream()
                .filter(value -> "open".equals(value.status())).findFirst()
                .orElseThrow(() -> failure(
                    "PROJECT_DELIVERABLE_REVIEW_NOT_OPEN", "No review is open"
                ));
            if (!review.requiredSignerIds().contains(user.id())) {
                throw failure(
                    "PROJECT_DELIVERABLE_SIGNER_FORBIDDEN",
                    "Current user is not a required signer"
                );
            }
            Signoff latest = review.signoffs().stream()
                .filter(value -> value.signerId().equals(user.id())).findFirst()
                .orElse(null);
            if ("sign".equals(operation)) {
                if (!Set.of("approve", "reject")
                    .contains(normalized(command.conclusion()).toLowerCase())
                    || (latest != null && !latest.revoked())) {
                    throw failure(
                        "PROJECT_DELIVERABLE_SIGNOFF_CONFLICT",
                        "Signer already has an active conclusion"
                    );
                }
            } else if (latest == null || latest.revoked()) {
                throw failure(
                    "PROJECT_DELIVERABLE_SIGNOFF_CONFLICT",
                    "No active signoff can be revoked"
                );
            }
        }
    }

    private Map<UUID, Long> validateMaterials(
        CurrentUser user, UUID spaceId, List<MaterialInput> materials
    ) {
        if (materials == null || materials.size() > MAX_MATERIALS) {
            throw failure(
                "PROJECT_DELIVERABLE_MATERIAL_INVALID", "Material limit exceeded"
            );
        }
        Map<UUID, Long> versions = new HashMap<>();
        Set<UUID> ids = new HashSet<>();
        Set<String> sources = new HashSet<>();
        for (MaterialInput material : materials) {
            if (material == null || material.id() == null
                || !ids.add(material.id()) || !MATERIALS.contains(material.sourceType())) {
                invalidMaterial();
            }
            if ("external".equals(material.sourceType())) {
                if (material.sourceId() != null || !externalUri(material.externalUri())
                    || !sources.add("external:" + material.externalUri())) {
                    invalidMaterial();
                }
                continue;
            }
            if (material.sourceId() == null || material.externalUri() != null
                || !sources.add(material.sourceType() + ":" + material.sourceId())) {
                invalidMaterial();
            }
            long version;
            if (PLATFORM_MATERIALS.contains(material.sourceType())) {
                PlatformObjectSummary value = objects.resolve(
                    user.workspaceId(), user.id(), material.sourceType(), material.sourceId()
                ).filter(summary -> summary.accessState() == ObjectAccessState.available)
                    .orElseThrow(() -> failure(
                        "NOT_FOUND_OR_HIDDEN", "Material is not available"
                    ));
                Object raw = value.metadata().get("version");
                version = raw instanceof Number number ? number.longValue() : 1;
            } else if ("plan".equals(material.sourceType())) {
                version = plans.get(user, spaceId, material.sourceId()).plan().version();
            } else {
                version = milestoneVersion(user, spaceId, material.sourceId());
            }
            versions.put(material.sourceId(), Math.max(1, version));
        }
        return Map.copyOf(versions);
    }

    private long milestoneVersion(CurrentUser user, UUID spaceId, UUID milestoneId) {
        for (var summary : plans.list(user, spaceId)) {
            ProjectPlan plan = plans.get(user, spaceId, summary.id());
            if (plan.milestones().stream().anyMatch(value -> value.id().equals(milestoneId))) {
                return plan.plan().version();
            }
        }
        throw failure("NOT_FOUND_OR_HIDDEN", "Milestone is not available");
    }

    private void validateReview(
        CurrentUser user,
        UUID spaceId,
        List<String> reviewItems,
        List<UUID> signerIds,
        Integer quorum
    ) {
        if (reviewItems == null || reviewItems.size() > MAX_REVIEW_ITEMS
            || signerIds == null || signerIds.size() > MAX_SIGNERS) {
            throw failure(
                "PROJECT_DELIVERABLE_REVIEW_INVALID", "Review contract is invalid"
            );
        }
        if (reviewItems.stream().anyMatch(value ->
            normalized(value).isEmpty() || normalized(value).length() > 240
        ) || new HashSet<>(reviewItems).size() != reviewItems.size()) {
            throw failure(
                "PROJECT_DELIVERABLE_REVIEW_INVALID", "Review items are invalid"
            );
        }
        if (!signerIds.isEmpty()) {
            if (new HashSet<>(signerIds).size() != signerIds.size()
                || quorum == null || quorum < 1 || quorum > signerIds.size()) {
                throw failure(
                    "PROJECT_DELIVERABLE_REVIEW_INVALID", "Review quorum is invalid"
                );
            }
            signerIds.forEach(id -> requireMember(user, spaceId, id));
        } else if (quorum != null && quorum != 0) {
            throw failure(
                "PROJECT_DELIVERABLE_REVIEW_INVALID", "Review quorum is invalid"
            );
        }
    }

    private void validateTraceability(
        CurrentUser user,
        UUID spaceId,
        UUID planId,
        UUID milestoneId,
        List<UUID> registerEntryIds
    ) {
        if (registerEntryIds == null || registerEntryIds.size() > 20
            || new HashSet<>(registerEntryIds).size() != registerEntryIds.size()) {
            throw failure(
                "PROJECT_DELIVERABLE_TRACE_INVALID", "Traceability input is invalid"
            );
        }
        if (planId == null && milestoneId != null) {
            throw failure(
                "PROJECT_DELIVERABLE_TRACE_INVALID", "Milestone requires a plan"
            );
        }
        if (planId != null) {
            ProjectPlan plan = plans.get(user, spaceId, planId);
            if (milestoneId != null && plan.milestones().stream()
                .noneMatch(value -> value.id().equals(milestoneId))) {
                throw failure("NOT_FOUND_OR_HIDDEN", "Milestone is not available");
            }
        }
        registerEntryIds.forEach(id -> register.get(user, spaceId, id));
    }

    private Deliverable projected(
        CurrentUser user, UUID spaceId, Deliverable raw
    ) {
        List<DeliverableVersion> versions = new ArrayList<>();
        boolean truncated = false;
        for (DeliverableVersion version : raw.versions()) {
            List<MaterialReference> visible = version.materials().stream()
                .filter(material -> visibleMaterial(user, spaceId, material))
                .toList();
            truncated |= visible.size() != version.materials().size();
            versions.add(new DeliverableVersion(
                version.id(), version.sequence(), version.label(), version.note(),
                safeOwner(user, spaceId, version.submittedBy()), version.submittedAt(),
                visible
            ));
        }
        List<ReviewRound> reviews = raw.reviews().stream()
            .map(review -> {
                List<UUID> signers = review.requiredSignerIds().stream()
                    .filter(id -> safeOwner(user, spaceId, id) != null).toList();
                List<Signoff> signoffs = review.signoffs().stream()
                    .filter(value -> safeOwner(user, spaceId, value.signerId()) != null)
                    .toList();
                return new ReviewRound(
                    review.id(), review.round(), review.deliverableVersionId(),
                    review.reviewItems(), signers, Math.min(review.quorum(), signers.size()),
                    review.status(), review.conclusion(), signoffs,
                    review.openedAt(), review.closedAt()
                );
            }).toList();
        List<Acceptance> acceptances = raw.acceptances().stream()
            .filter(value -> safeOwner(user, spaceId, value.actorId()) != null)
            .toList();
        return new Deliverable(
            safeSummary(user, spaceId, raw.deliverable()),
            List.copyOf(versions), reviews, acceptances, truncated
        );
    }

    private boolean visibleMaterial(
        CurrentUser user, UUID spaceId, MaterialReference material
    ) {
        try {
            if ("external".equals(material.sourceType())) {
                return true;
            }
            if (PLATFORM_MATERIALS.contains(material.sourceType())) {
                return objects.accessState(
                    user.workspaceId(), user.id(), material.sourceType(), material.sourceId()
                ) == ObjectAccessState.available;
            }
            if ("plan".equals(material.sourceType())) {
                plans.get(user, spaceId, material.sourceId());
                return true;
            }
            milestoneVersion(user, spaceId, material.sourceId());
            return true;
        } catch (WorkItemRuntimeException exception) {
            return false;
        }
    }

    private DeliverableSummary safeSummary(
        CurrentUser user, UUID spaceId, DeliverableSummary value
    ) {
        List<UUID> registerIds = value.registerEntryIds().stream()
            .filter(id -> {
                try {
                    register.get(user, spaceId, id);
                    return true;
                } catch (WorkItemRuntimeException exception) {
                    return false;
                }
            }).toList();
        return new DeliverableSummary(
            value.id(), value.title(), value.summary(), value.status(),
            safeOwner(user, spaceId, value.ownerUserId()), value.dueDate(),
            value.planId(), value.milestoneId(), registerIds, value.currentVersionId(),
            value.version(), value.createdBy(), value.createdAt(),
            value.updatedBy(), value.updatedAt()
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
            || !requestId(command.requestId())) {
            throw failure(
                "PROJECT_DELIVERABLE_COMMAND_INVALID",
                "Deliverable create command is invalid"
            );
        }
        requireText(command.title(), 160);
        if (normalized(command.summary()).length() > 2000) {
            throw failure(
                "PROJECT_DELIVERABLE_COMMAND_INVALID",
                "Deliverable create command is invalid"
            );
        }
    }

    private void validateMutation(MutateCommand command) {
        if (command == null || command.schemaVersion() != SCHEMA_VERSION
            || !requestId(command.requestId()) || command.expectedVersion() < 1
            || !OPERATIONS.contains(normalized(command.operation()).toLowerCase())
            || normalized(command.reason()).length() > 500
            || normalized(command.comment()).length() > 1000) {
            throw failure(
                "PROJECT_DELIVERABLE_COMMAND_INVALID",
                "Deliverable command is invalid"
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
                "FORBIDDEN", "Guest project space members have read-only delivery access"
            );
        }
        if (!"active".equals(space.status())) {
            throw failure("RUNTIME_NOT_WRITABLE", "Project space is not active");
        }
    }

    private void requireMember(CurrentUser user, UUID spaceId, UUID actor) {
        if (actor != null && members.findMemberByUser(
            user.workspaceId(), spaceId, actor
        ).filter(ProjectSpaceMember::effective).isEmpty()) {
            throw failure(
                "PROJECT_DELIVERABLE_PARTICIPANT_INVALID",
                "Deliverable participant is not an active member"
            );
        }
    }

    private void emit(
        CurrentUser user, UUID spaceId, DeliverableSummary value,
        String operation, String requestId
    ) {
        Map<String, Object> metadata = Map.of(
            "space_id", spaceId.toString(),
            "operation", operation,
            "status", value.status(),
            "version", value.version()
        );
        auditLog.log(
            user, "project_deliverable." + operation,
            "project_deliverable", value.id(), metadata
        );
        outbox.append(
            user.workspaceId(), "project.deliverable.changed",
            "project_deliverable", value.id(), user.id(),
            Map.of(
                "operation", operation,
                "status", value.status(),
                "version", value.version()
            ),
            "project-deliverable:" + operation + ":" + requestId
        );
    }

    private void requireHash(
        ProjectDeliveryRepository.CommandRecord record, String expected
    ) {
        if (!expected.equals(record.requestHash())) {
            throw failure(
                "PROJECT_DELIVERABLE_REQUEST_CONFLICT",
                "Request ID was reused with different input"
            );
        }
    }

    private void invalidTransition() {
        throw failure(
            "PROJECT_DELIVERABLE_TRANSITION_INVALID",
            "Deliverable transition is invalid"
        );
    }

    private void invalidMaterial() {
        throw failure(
            "PROJECT_DELIVERABLE_MATERIAL_INVALID",
            "Deliverable material is invalid"
        );
    }

    private boolean externalUri(String value) {
        try {
            URI uri = URI.create(normalized(value));
            return normalized(value).length() <= 1000
                && Set.of("https", "http").contains(uri.getScheme())
                && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean requestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    private void requireText(String value, int max) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) {
            throw failure(
                "PROJECT_DELIVERABLE_COMMAND_INVALID", "Required text is invalid"
            );
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
            throw new IllegalStateException("Could not serialize deliverable value", exception);
        }
    }

    private Deliverable read(String value) {
        try {
            return objectMapper.readValue(value, Deliverable.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read deliverable command receipt", exception);
        }
    }
}
