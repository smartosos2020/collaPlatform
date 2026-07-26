package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.FIRST_COMPLETE_SNAPSHOT_SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.SNAPSHOT_SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiff;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDraft;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationSnapshot;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublicationCommandReceipt;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublishedConfigurationVersion;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ValidationResult;
import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityReport;
import com.colla.platform.modules.project.domain.WorkItemConfigurationCompatibilityModels.CompatibilityImpact;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.NewDraft;
import com.colla.platform.modules.project.infrastructure.ConfigurationPublicationRepository;
import com.colla.platform.modules.project.infrastructure.ConfigurationPublicationRepository.LockedType;
import com.colla.platform.modules.project.infrastructure.ConfigurationPublicationRepository.NewPublishedVersion;
import com.colla.platform.modules.project.infrastructure.ConfigurationPublicationRepository.PublicationCommandResponse;
import com.colla.platform.modules.project.infrastructure.ConfigurationPublicationRepository.PublicationCommandStart;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemConfigurationPublicationService {
    private static final Set<String> MANAGER_ROLES = Set.of("owner", "admin");

    private final ConfigurationPublicationRepository publicationRepository;
    private final ConfigurationDraftRepository draftRepository;
    private final ProjectSpaceRepository spaceRepository;
    private final WorkItemTypeRepository typeRepository;
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer;
    private final WorkItemConfigurationValidator validator;
    private final WorkItemConfigurationDiffEngine diffEngine;
    private final WorkItemConfigurationCompatibilityAnalyzer compatibilityAnalyzer;
    private final WorkItemTypeConfigCanonicalizer requestCanonicalizer;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public WorkItemConfigurationPublicationService(
        ConfigurationPublicationRepository publicationRepository,
        ConfigurationDraftRepository draftRepository,
        ProjectSpaceRepository spaceRepository,
        WorkItemTypeRepository typeRepository,
        WorkItemConfigurationSnapshotCanonicalizer canonicalizer,
        WorkItemConfigurationValidator validator,
        WorkItemConfigurationDiffEngine diffEngine,
        WorkItemConfigurationCompatibilityAnalyzer compatibilityAnalyzer,
        WorkItemTypeConfigCanonicalizer requestCanonicalizer,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.publicationRepository = publicationRepository;
        this.draftRepository = draftRepository;
        this.spaceRepository = spaceRepository;
        this.typeRepository = typeRepository;
        this.canonicalizer = canonicalizer;
        this.validator = validator;
        this.diffEngine = diffEngine;
        this.compatibilityAnalyzer = compatibilityAnalyzer;
        this.requestCanonicalizer = requestCanonicalizer;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<VersionDetail> versions(CurrentUser user, UUID spaceId, UUID typeId) {
        requireManager(user, spaceId, typeId, false);
        return publicationRepository.listVersions(user.workspaceId(), spaceId, typeId).stream()
            .map(this::view)
            .toList();
    }

    @Transactional(readOnly = true)
    public VersionDetail version(CurrentUser user, UUID spaceId, UUID typeId, UUID versionId) {
        requireManager(user, spaceId, typeId, false);
        return view(requireVersion(user.workspaceId(), spaceId, typeId, versionId));
    }

    @Transactional
    public PublicationResult publish(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        long expectedDraftVersion,
        boolean breakingConfirmed,
        String requestId
    ) {
        return publish(user, spaceId, typeId, expectedDraftVersion, breakingConfirmed, requestId, null);
    }

    @Transactional
    PublicationResult publish(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        long expectedDraftVersion,
        boolean breakingConfirmed,
        String requestId,
        FailurePoint failurePoint
    ) {
        requireManager(user, spaceId, typeId, true);
        LockedType locked = publicationRepository.lockType(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration type is not available"));
        Command command = begin(
            user,
            spaceId,
            typeId,
            "publish",
            Map.of(
                "expectedDraftVersion", expectedDraftVersion,
                "breakingConfirmed", breakingConfirmed
            ),
            requestId
        );
        if (command.replay()) {
            return replay(command.receipt(), PublicationResult.class);
        }
        fail(failurePoint, FailurePoint.after_type_lock);
        ConfigurationDraft draft = draftRepository.lockActive(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        if (draft.aggregateVersion() != expectedDraftVersion) {
            throw failure("DRAFT_VERSION_CONFLICT", "Configuration draft version changed");
        }
        if (draft.snapshotSchemaVersion() < FIRST_COMPLETE_SNAPSHOT_SCHEMA_VERSION) {
            throw failure("LEGACY_PARTIAL_SNAPSHOT", "Legacy partial snapshots cannot be published");
        }
        if (draft.snapshotSchemaVersion() > SNAPSHOT_SCHEMA_VERSION) {
            throw failure("UNSUPPORTED_SNAPSHOT_SCHEMA", "Configuration snapshot schema is not supported");
        }
        ConfigurationSnapshot canonical = canonicalizer.canonicalize(draft.snapshot());
        ValidationResult validation = validator.validate(canonical.payload());
        if (!validation.valid() || !"valid".equals(draft.status())) {
            throw failure("DRAFT_NOT_VALID", "Configuration draft must be validated before publication");
        }
        if (!canonical.configHash().equals(draft.configHash())) {
            throw failure("DRAFT_HASH_MISMATCH", "Configuration draft hash is not canonical");
        }
        PublishedConfigurationVersion current = requireVersion(
            user.workspaceId(), spaceId, typeId, locked.currentVersionId()
        );
        ConfigurationDiff diff = current.completeSnapshot()
            ? diffEngine.diff(current.configHash(), current.snapshot(), draft.configHash(), draft.snapshot())
            : new ConfigurationDiff(current.configHash(), draft.configHash(), List.of(), Map.of(), false);
        CompatibilityReport compatibility = current.completeSnapshot()
            ? compatibilityAnalyzer.analyze(
                current.configHash(), current.snapshot(), draft.configHash(), draft.snapshot()
            )
            : null;
        if (compatibility != null && compatibility.overallImpact() == CompatibilityImpact.blocked) {
            throw failure(
                "CONFIGURATION_CHANGE_BLOCKED",
                "Blocked configuration changes require an explicit instance recovery plan"
            );
        }
        if (compatibility != null
            && compatibility.overallImpact() == CompatibilityImpact.migration_required
            && !breakingConfirmed) {
            throw failure(
                "MIGRATION_CONFIRMATION_REQUIRED",
                "Configuration changes that require instance migration need explicit confirmation"
            );
        }
        if (diff.breaking() && !breakingConfirmed) {
            throw failure("BREAKING_CONFIRMATION_REQUIRED", "Breaking configuration changes require confirmation");
        }
        fail(failurePoint, FailurePoint.after_validation);
        UUID versionId = UUID.randomUUID();
        publicationRepository.insertPublished(new NewPublishedVersion(
            versionId,
            user.workspaceId(),
            spaceId,
            typeId,
            locked.nextVersionNumber(),
            draft.snapshotSchemaVersion(),
            draft.configHash(),
            draft.snapshot(),
            draft.id(),
            "rollback".equals(draft.lineageKind()) ? draft.sourceVersionId() : null,
            user.id()
        ));
        fail(failurePoint, FailurePoint.after_version_insert);
        if (publicationRepository.supersede(
            user.workspaceId(), spaceId, typeId, locked.currentVersionId()
        ) != 1) {
            throw failure("PUBLICATION_CONFLICT", "Current configuration version changed");
        }
        if (publicationRepository.switchCurrent(
            user.workspaceId(),
            spaceId,
            typeId,
            locked.currentVersionId(),
            versionId,
            user.id()
        ) != 1) {
            throw failure("PUBLICATION_CONFLICT", "Current configuration pointer changed");
        }
        fail(failurePoint, FailurePoint.after_pointer_switch);
        if (draftRepository.abandon(
            user.workspaceId(), spaceId, typeId, draft.id(), user.id(), draft.aggregateVersion()
        ) != 1) {
            throw failure("DRAFT_VERSION_CONFLICT", "Configuration draft changed during publication");
        }
        PublishedConfigurationVersion published = requireVersion(
            user.workspaceId(), spaceId, typeId, versionId
        );
        PublicationResult result = new PublicationResult(view(published), diff, false);
        recordPublication(user, published, draft, command.requestId(), diff);
        fail(failurePoint, FailurePoint.after_side_effects);
        complete(command, published, result);
        return result;
    }

    @Transactional(readOnly = true)
    public ConfigurationDiff diffVersions(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID fromVersionId,
        UUID toVersionId
    ) {
        requireManager(user, spaceId, typeId, false);
        PublishedConfigurationVersion from = requireComplete(
            requireVersion(user.workspaceId(), spaceId, typeId, fromVersionId)
        );
        PublishedConfigurationVersion to = requireComplete(
            requireVersion(user.workspaceId(), spaceId, typeId, toVersionId)
        );
        return diffEngine.diff(from.configHash(), from.snapshot(), to.configHash(), to.snapshot());
    }

    @Transactional(readOnly = true)
    public ConfigurationDiff diffDraft(CurrentUser user, UUID spaceId, UUID typeId) {
        requireManager(user, spaceId, typeId, false);
        ConfigurationDraft draft = draftRepository.findActive(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        PublishedConfigurationVersion current = requireComplete(requireVersion(
            user.workspaceId(),
            spaceId,
            typeId,
            typeRepository.findById(user.workspaceId(), spaceId, typeId)
                .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration type is not available"))
                .currentVersionId()
        ));
        return diffEngine.diff(current.configHash(), current.snapshot(), draft.configHash(), draft.snapshot());
    }

    @Transactional(readOnly = true)
    public CompatibilityReport compatibilityVersions(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID fromVersionId,
        UUID toVersionId
    ) {
        requireManager(user, spaceId, typeId, false);
        PublishedConfigurationVersion from = requireComplete(
            requireVersion(user.workspaceId(), spaceId, typeId, fromVersionId)
        );
        PublishedConfigurationVersion to = requireComplete(
            requireVersion(user.workspaceId(), spaceId, typeId, toVersionId)
        );
        return compatibilityAnalyzer.analyze(
            from.configHash(), from.snapshot(), to.configHash(), to.snapshot()
        );
    }

    @Transactional(readOnly = true)
    public CompatibilityReport compatibilityDraft(CurrentUser user, UUID spaceId, UUID typeId) {
        requireManager(user, spaceId, typeId, false);
        ConfigurationDraft draft = draftRepository.findActive(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        PublishedConfigurationVersion current = requireComplete(requireVersion(
            user.workspaceId(),
            spaceId,
            typeId,
            typeRepository.findById(user.workspaceId(), spaceId, typeId)
                .orElseThrow(() -> failure(
                    "NOT_FOUND_OR_HIDDEN",
                    "Configuration type is not available"
                ))
                .currentVersionId()
        ));
        return compatibilityAnalyzer.analyze(
            current.configHash(), current.snapshot(), draft.configHash(), draft.snapshot()
        );
    }

    @Transactional
    public RollbackPreparation prepareRollback(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID sourceVersionId,
        long expectedDraftVersion,
        String requestId
    ) {
        requireManager(user, spaceId, typeId, true);
        PublishedConfigurationVersion source = requireComplete(
            requireVersion(user.workspaceId(), spaceId, typeId, sourceVersionId)
        );
        publicationRepository.lockType(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration type is not available"));
        Command command = begin(
            user,
            spaceId,
            typeId,
            "prepare_rollback",
            Map.of(
                "sourceVersionId", sourceVersionId.toString(),
                "expectedDraftVersion", expectedDraftVersion
            ),
            requestId
        );
        if (command.replay()) {
            return replay(command.receipt(), RollbackPreparation.class);
        }
        ConfigurationDraft active = draftRepository.lockActive(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        if (active.aggregateVersion() != expectedDraftVersion) {
            throw failure("DRAFT_VERSION_CONFLICT", "Configuration draft version changed");
        }
        if (draftRepository.abandon(
            user.workspaceId(), spaceId, typeId, active.id(), user.id(), active.aggregateVersion()
        ) != 1) {
            throw failure("DRAFT_VERSION_CONFLICT", "Configuration draft changed");
        }
        UUID draftId = UUID.randomUUID();
        ValidationResult validation = validator.validate(source.snapshot());
        if (!draftRepository.tryInsert(new NewDraft(
            draftId,
            user.workspaceId(),
            spaceId,
            typeId,
            validation.valid() ? "valid" : "invalid",
            source.snapshotSchemaVersion(),
            source.configHash(),
            source.snapshot(),
            objectMapper.valueToTree(validation.diagnostics()),
            source.id(),
            "rollback",
            user.id()
        ))) {
            throw failure("ACTIVE_DRAFT_CONFLICT", "An active configuration draft already exists");
        }
        ConfigurationDraft rollbackDraft = draftRepository.findById(
            user.workspaceId(), spaceId, typeId, draftId
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Rollback draft is not available"));
        RollbackPreparation response = new RollbackPreparation(
            rollbackDraft.id(),
            rollbackDraft.aggregateVersion(),
            rollbackDraft.status(),
            source.id(),
            source.versionNumber(),
            source.configHash()
        );
        complete(command, source, response);
        recordRollback(user, rollbackDraft, source, command.requestId());
        return response;
    }

    private void requireManager(CurrentUser user, UUID spaceId, UUID typeId, boolean writable) {
        ProjectSpaceSummary space = spaceRepository.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration history is not available"));
        if (!space.isMember()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Configuration history is not available");
        }
        if (!MANAGER_ROLES.contains(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Project space owner or admin role required");
        }
        if (writable && !"active".equals(space.status())) {
            throw failure("SPACE_UNAVAILABLE", "Project space must be active for configuration publication");
        }
        if (typeRepository.findById(user.workspaceId(), spaceId, typeId).isEmpty()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Configuration history is not available");
        }
    }

    private PublishedConfigurationVersion requireVersion(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId
    ) {
        return publicationRepository.findVersion(workspaceId, spaceId, typeId, versionId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration version is not available"));
    }

    private PublishedConfigurationVersion requireComplete(PublishedConfigurationVersion version) {
        if (!version.completeSnapshot()) {
            throw failure("LEGACY_PARTIAL_SNAPSHOT", "Legacy partial snapshots cannot be compared or restored");
        }
        if (!version.supportedSnapshot()) {
            throw failure("UNSUPPORTED_SNAPSHOT_SCHEMA", "Configuration snapshot schema is not supported");
        }
        return version;
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
        boolean started = publicationRepository.tryStartCommand(new PublicationCommandStart(
            commandId,
            user.workspaceId(),
            spaceId,
            typeId,
            normalizedRequestId,
            operation,
            requestHash,
            user.id()
        ));
        PublicationCommandReceipt receipt = publicationRepository.findCommand(
            user.workspaceId(), spaceId, typeId, operation, normalizedRequestId
        ).orElseThrow(() -> failure("IDEMPOTENCY_CONFLICT", "Publication receipt is unavailable"));
        if (!receipt.requestHash().equals(requestHash) || !receipt.createdBy().equals(user.id())) {
            throw failure("IDEMPOTENCY_KEY_REUSED", "Request id was already used with a different command");
        }
        if (!started && !"completed".equals(receipt.status())) {
            throw failure("IDEMPOTENCY_IN_PROGRESS", "The original publication command is in progress");
        }
        return new Command(!started, normalizedRequestId, receipt);
    }

    private <T> T replay(PublicationCommandReceipt receipt, Class<T> type) {
        if (!"completed".equals(receipt.status()) || receipt.responsePayload() == null) {
            throw failure("IDEMPOTENCY_CONFLICT", "Publication receipt is not replayable");
        }
        try {
            return objectMapper.treeToValue(receipt.responsePayload(), type);
        } catch (JsonProcessingException exception) {
            throw failure("IDEMPOTENCY_CONFLICT", "Publication receipt cannot be decoded", exception);
        }
    }

    private void complete(Command command, PublishedConfigurationVersion version, Object response) {
        publicationRepository.completeCommand(
            command.receipt().id(),
            new PublicationCommandResponse(
                version.id(),
                version.versionNumber(),
                version.configHash(),
                objectMapper.valueToTree(response)
            )
        );
    }

    private void recordPublication(
        CurrentUser user,
        PublishedConfigurationVersion version,
        ConfigurationDraft draft,
        String requestId,
        ConfigurationDiff diff
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", requestId);
        metadata.put("spaceId", version.spaceId().toString());
        metadata.put("typeDefinitionId", version.typeDefinitionId().toString());
        metadata.put("versionNumber", version.versionNumber());
        metadata.put("snapshotSchemaVersion", version.snapshotSchemaVersion());
        metadata.put("configHash", version.configHash());
        metadata.put("sourceDraftId", draft.id().toString());
        metadata.put("breaking", diff.breaking());
        auditLog.log(user, "work_item_configuration.published", "work_item_configuration_version", version.id(), metadata);
        outbox.append(
            user.workspaceId(),
            "work_item_configuration.published",
            "work_item_configuration_version",
            version.id(),
            user.id(),
            metadata,
            "wicv:" + version.id()
        );
    }

    private void recordRollback(
        CurrentUser user,
        ConfigurationDraft draft,
        PublishedConfigurationVersion source,
        String requestId
    ) {
        Map<String, Object> metadata = Map.of(
            "requestId", requestId,
            "spaceId", draft.spaceId().toString(),
            "typeDefinitionId", draft.typeDefinitionId().toString(),
            "sourceVersionId", source.id().toString(),
            "sourceVersionNumber", source.versionNumber()
        );
        auditLog.log(user, "work_item_configuration.rollback_prepared", "work_item_configuration_draft", draft.id(), metadata);
        outbox.append(
            user.workspaceId(),
            "work_item_configuration.rollback_prepared",
            "work_item_configuration_draft",
            draft.id(),
            user.id(),
            metadata,
            "wicr:" + draft.id()
        );
    }

    private VersionDetail view(PublishedConfigurationVersion version) {
        return new VersionDetail(
            version.id(),
            version.versionNumber(),
            version.status(),
            version.snapshotSchemaVersion(),
            version.completeSnapshot(),
            version.configHash(),
            version.snapshot(),
            version.sourceDraftId(),
            version.rollbackSourceVersionId(),
            version.publishedBy(),
            version.publishedAt()
        );
    }

    private String normalizeRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 8 || normalized.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Request id must contain 8 to 120 characters");
        }
        return normalized;
    }

    private void fail(FailurePoint actual, FailurePoint expected) {
        if (actual == expected) {
            throw new IllegalStateException("Injected publication failure at " + expected);
        }
    }

    enum FailurePoint {
        after_type_lock,
        after_validation,
        after_version_insert,
        after_pointer_switch,
        after_side_effects
    }

    public record VersionDetail(
        UUID id,
        int versionNumber,
        String status,
        int snapshotSchemaVersion,
        boolean completeSnapshot,
        String configHash,
        JsonNode snapshot,
        UUID sourceDraftId,
        UUID rollbackSourceVersionId,
        UUID publishedBy,
        java.time.Instant publishedAt
    ) {
    }

    public record PublicationResult(VersionDetail version, ConfigurationDiff diff, boolean replayed) {
    }

    public record RollbackPreparation(
        UUID draftId,
        long draftAggregateVersion,
        String draftStatus,
        UUID sourceVersionId,
        int sourceVersionNumber,
        String sourceConfigHash
    ) {
    }

    private record Command(boolean replay, String requestId, PublicationCommandReceipt receipt) {
    }
}
