package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDraft;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationSnapshot;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DraftCommandReceipt;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ValidationResult;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository;
import com.colla.platform.modules.project.infrastructure.ConfigurationPublicationRepository;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.DraftCommandResponse;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.DraftCommandStart;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.NewDraft;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.UpdateDraft;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemConfigurationDraftService {
    private static final Set<String> MANAGER_ROLES = Set.of("owner", "admin");

    private final ConfigurationDraftRepository draftRepository;
    private final ConfigurationPublicationRepository publicationRepository;
    private final WorkItemConfigurationSnapshotAssembler assembler;
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer;
    private final WorkItemConfigurationValidator validator;
    private final ProjectSpaceRepository spaceRepository;
    private final WorkItemTypeRepository typeRepository;
    private final WorkItemTypeConfigCanonicalizer requestCanonicalizer;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public WorkItemConfigurationDraftService(
        ConfigurationDraftRepository draftRepository,
        ConfigurationPublicationRepository publicationRepository,
        WorkItemConfigurationSnapshotAssembler assembler,
        WorkItemConfigurationSnapshotCanonicalizer canonicalizer,
        WorkItemConfigurationValidator validator,
        ProjectSpaceRepository spaceRepository,
        WorkItemTypeRepository typeRepository,
        WorkItemTypeConfigCanonicalizer requestCanonicalizer,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.draftRepository = draftRepository;
        this.publicationRepository = publicationRepository;
        this.assembler = assembler;
        this.canonicalizer = canonicalizer;
        this.validator = validator;
        this.spaceRepository = spaceRepository;
        this.typeRepository = typeRepository;
        this.requestCanonicalizer = requestCanonicalizer;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DraftDetail detail(CurrentUser user, UUID spaceId, UUID typeId) {
        requireManager(user, spaceId, typeId, false);
        return view(ensureFromLive(user.workspaceId(), spaceId, typeId, user.id()));
    }

    @Transactional
    public DraftDetail save(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        JsonNode requestedSnapshot,
        long expectedAggregateVersion,
        String requestId
    ) {
        requireManager(user, spaceId, typeId, true);
        ConfigurationSnapshot canonical = canonicalizer.canonicalize(requestedSnapshot);
        ValidationResult validation = validator.validate(canonical.payload());
        Command command = begin(
            user,
            spaceId,
            typeId,
            "save",
            Map.of(
                "expectedAggregateVersion", expectedAggregateVersion,
                "configHash", canonical.configHash()
            ),
            requestId
        );
        if (command.replay()) {
            return replay(command.receipt());
        }
        ConfigurationDraft active = ensureFromLive(user.workspaceId(), spaceId, typeId, user.id());
        requireVersion(active, expectedAggregateVersion);
        ConfigurationDraft saved = update(
            active,
            canonical,
            validation.diagnostics(),
            "editing",
            user.id()
        );
        DraftDetail response = view(saved);
        complete(command, response);
        record(user, saved, "saved", command.requestId());
        return response;
    }

    @Transactional
    public DraftDetail validate(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        long expectedAggregateVersion,
        String requestId
    ) {
        requireManager(user, spaceId, typeId, true);
        Command command = begin(
            user,
            spaceId,
            typeId,
            "validate",
            Map.of("expectedAggregateVersion", expectedAggregateVersion),
            requestId
        );
        if (command.replay()) {
            return replay(command.receipt());
        }
        ConfigurationDraft active = draftRepository.lockActive(user.workspaceId(), spaceId, typeId)
            .orElseGet(() -> ensureFromLive(user.workspaceId(), spaceId, typeId, user.id()));
        requireVersion(active, expectedAggregateVersion);
        ValidationResult validation = validator.validate(active.snapshot());
        ConfigurationSnapshot canonical = canonicalizer.canonicalize(active.snapshot());
        ConfigurationDraft saved = update(
            active,
            canonical,
            validation.diagnostics(),
            validation.valid() ? "valid" : "invalid",
            user.id()
        );
        DraftDetail response = view(saved);
        complete(command, response);
        record(user, saved, validation.valid() ? "validated" : "validation_failed", command.requestId());
        return response;
    }

    @Transactional
    public DraftDetail abandon(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        long expectedAggregateVersion,
        String requestId
    ) {
        requireManager(user, spaceId, typeId, true);
        Command command = begin(
            user,
            spaceId,
            typeId,
            "abandon",
            Map.of("expectedAggregateVersion", expectedAggregateVersion),
            requestId
        );
        if (command.replay()) {
            return replay(command.receipt());
        }
        ConfigurationDraft active = draftRepository.lockActive(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        requireVersion(active, expectedAggregateVersion);
        if (draftRepository.abandon(
            user.workspaceId(),
            spaceId,
            typeId,
            active.id(),
            user.id(),
            expectedAggregateVersion
        ) != 1) {
            throw failure("DRAFT_VERSION_CONFLICT", "Configuration draft version changed");
        }
        ConfigurationDraft abandoned = draftRepository.findById(
            user.workspaceId(), spaceId, typeId, active.id()
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        DraftDetail response = view(abandoned);
        complete(command, response);
        record(user, abandoned, "abandoned", command.requestId());
        return response;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ConfigurationDraft refreshAfterMutation(
        CurrentUser user,
        UUID spaceId,
        UUID typeId
    ) {
        return refreshAfterMutation(user.workspaceId(), spaceId, typeId, user.id());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ConfigurationDraft refreshAfterMutation(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID actorId
    ) {
        ConfigurationDraft active = draftRepository.lockActive(workspaceId, spaceId, typeId).orElse(null);
        ConfigurationSnapshot snapshot = preserveDraftOnlyConfiguration(
            preservePublishedStateFlow(
                assembler.assemble(workspaceId, spaceId, typeId),
                workspaceId,
                spaceId,
                typeId
            ),
            active
        );
        ValidationResult validation = validator.validate(snapshot.payload());
        if (active == null) {
            return create(workspaceId, spaceId, typeId, actorId, snapshot, validation.diagnostics());
        }
        if (active.configHash().equals(snapshot.configHash())
            && "editing".equals(active.status())
            && active.diagnostics().equals(validation.diagnostics())) {
            return active;
        }
        return update(active, snapshot, validation.diagnostics(), "editing", actorId);
    }

    private ConfigurationSnapshot preserveDraftOnlyConfiguration(
        ConfigurationSnapshot assembled,
        ConfigurationDraft active
    ) {
        if (active == null || !active.snapshot().path("stateFlow").isObject()) {
            return assembled;
        }
        ObjectNode merged = assembled.payload().deepCopy();
        merged.set("stateFlow", active.snapshot().path("stateFlow").deepCopy());
        return canonicalizer.canonicalize(merged);
    }

    private ConfigurationSnapshot preservePublishedStateFlow(
        ConfigurationSnapshot assembled,
        UUID workspaceId,
        UUID spaceId,
        UUID typeId
    ) {
        var type = typeRepository.findById(workspaceId, spaceId, typeId).orElse(null);
        if (type == null || type.currentVersionId() == null) {
            return assembled;
        }
        var current = publicationRepository.findVersion(
            workspaceId, spaceId, typeId, type.currentVersionId()
        ).orElse(null);
        if (current == null
            || !current.completeSnapshot()
            || !current.snapshot().path("stateFlow").isObject()) {
            return assembled;
        }
        ObjectNode merged = assembled.payload().deepCopy();
        merged.set("stateFlow", current.snapshot().path("stateFlow").deepCopy());
        return canonicalizer.canonicalize(merged);
    }

    private ConfigurationDraft ensureFromLive(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID actorId
    ) {
        ConfigurationDraft active = draftRepository.lockActive(workspaceId, spaceId, typeId).orElse(null);
        if (active != null) {
            return active;
        }
        ConfigurationSnapshot snapshot = preservePublishedStateFlow(
            assembler.assemble(workspaceId, spaceId, typeId),
            workspaceId,
            spaceId,
            typeId
        );
        return create(
            workspaceId,
            spaceId,
            typeId,
            actorId,
            snapshot,
            validator.validate(snapshot.payload()).diagnostics()
        );
    }

    private ConfigurationDraft create(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID actorId,
        ConfigurationSnapshot snapshot,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        UUID draftId = UUID.randomUUID();
        boolean inserted = draftRepository.tryInsert(new NewDraft(
            draftId,
            workspaceId,
            spaceId,
            typeId,
            "editing",
            snapshot.schemaVersion(),
            snapshot.configHash(),
            snapshot.payload(),
            objectMapper.valueToTree(diagnostics),
            null,
            "live_edit",
            actorId
        ));
        if (!inserted) {
            return draftRepository.lockActive(workspaceId, spaceId, typeId)
                .orElseThrow(() -> failure("ACTIVE_DRAFT_CONFLICT", "An active configuration draft already exists"));
        }
        return draftRepository.findById(workspaceId, spaceId, typeId, draftId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
    }

    private ConfigurationDraft update(
        ConfigurationDraft active,
        ConfigurationSnapshot snapshot,
        List<ConfigurationDiagnostic> diagnostics,
        String status,
        UUID actorId
    ) {
        if (draftRepository.update(new UpdateDraft(
            active.workspaceId(),
            active.spaceId(),
            active.typeDefinitionId(),
            active.id(),
            status,
            snapshot.schemaVersion(),
            snapshot.configHash(),
            snapshot.payload(),
            objectMapper.valueToTree(diagnostics),
            actorId,
            active.aggregateVersion()
        )) != 1) {
            throw failure("DRAFT_VERSION_CONFLICT", "Configuration draft version changed");
        }
        return draftRepository.findById(
            active.workspaceId(), active.spaceId(), active.typeDefinitionId(), active.id()
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
    }

    private void requireManager(CurrentUser user, UUID spaceId, UUID typeId, boolean writable) {
        ProjectSpaceSummary space = spaceRepository.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        if (!space.isMember()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available");
        }
        if (!MANAGER_ROLES.contains(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Project space owner or admin role required");
        }
        if (writable && !"active".equals(space.status())) {
            throw failure("SPACE_UNAVAILABLE", "Project space must be active for configuration changes");
        }
        if (typeRepository.findById(user.workspaceId(), spaceId, typeId).isEmpty()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available");
        }
    }

    private void requireVersion(ConfigurationDraft draft, long expectedAggregateVersion) {
        if (expectedAggregateVersion < 0 || draft.aggregateVersion() != expectedAggregateVersion) {
            throw failure("DRAFT_VERSION_CONFLICT", "Configuration draft version changed");
        }
    }

    private Command begin(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String operation,
        Map<String, Object> payload,
        String requestId
    ) {
        String normalizedRequestId = normalizeRequestId(requestId);
        String requestHash = requestCanonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", user.id().toString(),
            "operation", operation,
            "payload", payload
        )));
        UUID commandId = UUID.randomUUID();
        boolean started = draftRepository.tryStartCommand(new DraftCommandStart(
            commandId,
            user.workspaceId(),
            spaceId,
            typeId,
            normalizedRequestId,
            operation,
            requestHash,
            user.id()
        ));
        DraftCommandReceipt receipt = draftRepository.findCommand(
            user.workspaceId(), spaceId, typeId, operation, normalizedRequestId
        ).orElseThrow(() -> failure("IDEMPOTENCY_CONFLICT", "Configuration draft receipt is unavailable"));
        if (!receipt.requestHash().equals(requestHash) || !receipt.createdBy().equals(user.id())) {
            throw failure("IDEMPOTENCY_KEY_REUSED", "Request id was already used with a different command");
        }
        if (!started && !"completed".equals(receipt.status())) {
            throw failure("IDEMPOTENCY_IN_PROGRESS", "The original command is still in progress");
        }
        return new Command(!started, normalizedRequestId, receipt);
    }

    private DraftDetail replay(DraftCommandReceipt receipt) {
        if (!"completed".equals(receipt.status())
            || receipt.responseSchemaVersion() != 1
            || receipt.responsePayload() == null) {
            throw failure("IDEMPOTENCY_CONFLICT", "Configuration draft receipt is not replayable");
        }
        try {
            DraftDetail response = objectMapper.treeToValue(receipt.responsePayload(), DraftDetail.class);
            if (!response.id().equals(receipt.responseDraftId())
                || response.aggregateVersion() != receipt.responseAggregateVersion()
                || !response.configHash().equals(receipt.responseConfigHash())) {
                throw failure("IDEMPOTENCY_CONFLICT", "Configuration draft receipt failed integrity validation");
            }
            return response;
        } catch (JsonProcessingException exception) {
            throw failure("IDEMPOTENCY_CONFLICT", "Configuration draft receipt cannot be decoded", exception);
        }
    }

    private void complete(Command command, DraftDetail response) {
        draftRepository.completeCommand(
            command.receipt().id(),
            new DraftCommandResponse(
                response.id(),
                response.aggregateVersion(),
                response.configHash(),
                objectMapper.valueToTree(response)
            )
        );
    }

    private void record(
        CurrentUser user,
        ConfigurationDraft draft,
        String action,
        String requestId
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", requestId);
        metadata.put("spaceId", draft.spaceId().toString());
        metadata.put("typeDefinitionId", draft.typeDefinitionId().toString());
        metadata.put("status", draft.status());
        metadata.put("snapshotSchemaVersion", draft.snapshotSchemaVersion());
        metadata.put("configHash", draft.configHash());
        metadata.put("aggregateVersion", draft.aggregateVersion());
        metadata.put("diagnosticCount", draft.diagnostics().size());
        auditLog.log(
            user,
            "work_item_configuration_draft." + action,
            "work_item_configuration_draft",
            draft.id(),
            metadata
        );
        outbox.append(
            user.workspaceId(),
            "work_item_configuration_draft." + action,
            "work_item_configuration_draft",
            draft.id(),
            user.id(),
            metadata,
            "wicd:" + draft.id() + ":" + draft.aggregateVersion()
        );
    }

    private DraftDetail view(ConfigurationDraft draft) {
        List<String> actions = draft.active()
            ? List.of("save", "validate", "abandon")
            : List.of();
        return new DraftDetail(
            draft.id(),
            draft.spaceId(),
            draft.typeDefinitionId(),
            draft.status(),
            draft.snapshotSchemaVersion(),
            draft.configHash(),
            draft.snapshot(),
            draft.diagnostics(),
            draft.aggregateVersion(),
            draft.sourceLegacyVersionId(),
            draft.sourceVersionId(),
            draft.lineageKind(),
            draft.updatedBy(),
            draft.updatedAt(),
            actions
        );
    }

    private String normalizeRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 8 || normalized.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Request id must contain 8 to 120 characters");
        }
        return normalized;
    }

    public record DraftDetail(
        UUID id,
        UUID spaceId,
        UUID typeDefinitionId,
        String status,
        int snapshotSchemaVersion,
        String configHash,
        JsonNode snapshot,
        List<ConfigurationDiagnostic> diagnostics,
        long aggregateVersion,
        UUID sourceLegacyVersionId,
        UUID sourceVersionId,
        String lineageKind,
        UUID updatedBy,
        java.time.Instant updatedAt,
        List<String> availableActions
    ) {
    }

    private record Command(boolean replay, String requestId, DraftCommandReceipt receipt) {
    }
}
