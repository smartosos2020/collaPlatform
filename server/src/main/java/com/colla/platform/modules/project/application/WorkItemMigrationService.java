package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.permission.contract.ProjectAuthorization;
import com.colla.platform.modules.platform.contract.PlatformObjectCommands;
import com.colla.platform.modules.project.application.WorkItemFieldValueCodec.CanonicalValues;
import com.colla.platform.modules.project.application.WorkItemLegacyIdentityResolver.IdentityResolution;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyProfile;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationBatch;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationExecution;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationFailure;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationPlan;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationPlanUnit;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationUnit;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.MigrationVerification;
import com.colla.platform.modules.project.domain.WorkItemMigrationModels.TypeBinding;
import com.colla.platform.modules.project.infrastructure.WorkItemCompatibilityRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemMigrationRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemMigrationRepository.Lease;
import com.colla.platform.modules.project.infrastructure.WorkItemMigrationRepository.VerificationObservation;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WorkItemMigrationService {
    private static final Set<String> ISSUE_TYPES = Set.of("requirement", "task", "bug");
    private static final Duration LEASE_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_SAFE_ERROR_LENGTH = 300;

    private final WorkItemMigrationRepository repository;
    private final WorkItemCompatibilityRepository compatibilityRepository;
    private final WorkItemLegacyIdentityResolver identityResolver;
    private final PublishedSnapshotAdapter snapshotAdapter;
    private final WorkItemFieldValueCodec valueCodec;
    private final WorkItemRepository workItemRepository;
    private final PlatformObjectCommands objectCommands;
    private final ProjectAuthorization authorization;
    private final AuditLog auditLog;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate snapshotTemplate;
    private final TransactionTemplate unitTemplate;

    public WorkItemMigrationService(
        WorkItemMigrationRepository repository,
        WorkItemCompatibilityRepository compatibilityRepository,
        WorkItemLegacyIdentityResolver identityResolver,
        PublishedSnapshotAdapter snapshotAdapter,
        WorkItemFieldValueCodec valueCodec,
        WorkItemRepository workItemRepository,
        PlatformObjectCommands objectCommands,
        ProjectAuthorization authorization,
        AuditLog auditLog,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.compatibilityRepository = compatibilityRepository;
        this.identityResolver = identityResolver;
        this.snapshotAdapter = snapshotAdapter;
        this.valueCodec = valueCodec;
        this.workItemRepository = workItemRepository;
        this.objectCommands = objectCommands;
        this.authorization = authorization;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
        this.snapshotTemplate = new TransactionTemplate(transactionManager);
        this.snapshotTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.unitTemplate = new TransactionTemplate(transactionManager);
        this.unitTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<MigrationBatch> list(CurrentUser user) {
        authorization.requireManageProjects(user);
        return repository.listBatches(user.workspaceId());
    }

    public MigrationBatch get(CurrentUser user, UUID batchId) {
        authorization.requireManageProjects(user);
        return requireBatch(user.workspaceId(), batchId);
    }

    public MigrationBatch plan(CurrentUser user, boolean dryRun, int throttleMillis) {
        return plan(user, dryRun, throttleMillis, Set.of());
    }

    public MigrationBatch plan(
        CurrentUser user,
        boolean dryRun,
        int throttleMillis,
        Set<UUID> projectIds
    ) {
        authorization.requireManageProjects(user);
        if (throttleMillis < 0 || throttleMillis > 60_000) {
            throw failure("INVALID_MIGRATION_THROTTLE", "Throttle must be between 0 and 60000 milliseconds");
        }
        Set<UUID> scope = projectIds == null ? Set.of() : Set.copyOf(projectIds);
        MigrationPlan plan = snapshotTemplate.execute(
            status -> buildPlan(user.workspaceId(), scope)
        );
        UUID batchId = repository.insertPlan(
            user.workspaceId(), plan, dryRun, throttleMillis, user.id()
        );
        auditLog.log(user, dryRun ? "work_item_migration.dry_run" : "work_item_migration.planned",
            "work_item_migration", batchId, Map.of(
                "unitCount", plan.units().size(),
                "preflightFailureCount", plan.failures().size(),
                "sourceFingerprint", plan.sourceFingerprint(),
                "planFingerprint", plan.planFingerprint()
            ));
        return requireBatch(user.workspaceId(), batchId);
    }

    public MigrationExecution execute(CurrentUser user, UUID batchId, String workerId) {
        authorization.requireManageProjects(user);
        String owner = normalizedWorker(workerId);
        MigrationBatch before = requireBatch(user.workspaceId(), batchId);
        if (before.failures().stream().anyMatch(this::blockingPreflightFailure)) {
            throw failure("MIGRATION_PREFLIGHT_BLOCKED", "Migration plan contains blocking preflight failures");
        }
        if (!Set.of("planned", "paused", "failed", "running").contains(before.status())) {
            throw failure("MIGRATION_NOT_EXECUTABLE", "Migration batch is not executable");
        }
        Lease lease;
        try {
            lease = repository.acquireLease(
                user.workspaceId(), batchId, owner, Instant.now().minus(LEASE_TIMEOUT)
            );
        } catch (IllegalStateException exception) {
            throw failure("MIGRATION_LEASE_UNAVAILABLE", "Another worker owns the migration batch", exception);
        }
        int completed = 0;
        int failed = 0;
        int migrated = 0;
        try {
            MigrationBatch leased = requireBatch(user.workspaceId(), batchId);
            if (!"running".equals(leased.status())) {
                repository.changeBatchStatus(
                    user.workspaceId(), batchId, leased.status(), "running", null,
                    lease.token(), lease.fenceVersion()
                );
            }
            while (true) {
                MigrationBatch state = requireBatch(user.workspaceId(), batchId);
                if ("paused".equals(state.status())) {
                    break;
                }
                if (!repository.heartbeat(
                    user.workspaceId(), batchId, lease.token(), lease.fenceVersion()
                )) {
                    throw failure("MIGRATION_FENCE_CONFLICT", "Migration worker lost its lease");
                }
                MigrationUnit unit = repository.claimNextUnit(
                    user.workspaceId(), batchId, lease.token(), lease.fenceVersion()
                ).orElse(null);
                if (unit == null) {
                    break;
                }
                try {
                    int unitObjects = unitTemplate.execute(
                        transaction -> migrateUnit(user, batchId, unit, lease.fenceVersion())
                    );
                    repository.completeUnit(
                        user.workspaceId(), unit.id(), lease.fenceVersion(), unitObjects
                    );
                    completed++;
                    migrated += unitObjects;
                } catch (RuntimeException exception) {
                    String code = errorCode(exception);
                    repository.failUnit(
                        user.workspaceId(), unit.id(), lease.fenceVersion(), code
                    );
                    repository.appendFailure(
                        user.workspaceId(), batchId, unit.id(), code, "project",
                        unit.legacyProjectId(), objectMapper.valueToTree(Map.of(
                            "message", safeMessage(exception),
                            "attempt", unit.attempt()
                        ))
                    );
                    failed++;
                }
                sleep(state.throttleMillis());
            }
            MigrationBatch state = requireBatch(user.workspaceId(), batchId);
            if ("running".equals(state.status())) {
                boolean anyFailed = state.units().stream()
                    .anyMatch(unit -> "failed".equals(unit.status()));
                boolean allComplete = state.units().stream()
                    .allMatch(unit -> "completed".equals(unit.status()));
                repository.changeBatchStatus(
                    user.workspaceId(), batchId, "running",
                    allComplete ? "completed" : anyFailed ? "failed" : "paused",
                    allComplete || anyFailed ? null : "No claimable unit remained",
                    lease.token(), lease.fenceVersion()
                );
            }
        } finally {
            repository.releaseLease(
                user.workspaceId(), batchId, lease.token(), lease.fenceVersion()
            );
        }
        MigrationBatch result = requireBatch(user.workspaceId(), batchId);
        auditLog.log(user, "work_item_migration.executed", "work_item_migration", batchId, Map.of(
            "workerId", owner,
            "completedUnits", completed,
            "failedUnits", failed,
            "migratedObjects", migrated,
            "status", result.status()
        ));
        return new MigrationExecution(result, completed, failed, migrated);
    }

    public MigrationBatch pause(CurrentUser user, UUID batchId, String reason) {
        authorization.requireManageProjects(user);
        String normalized = reason == null || reason.isBlank()
            ? "Paused by administrator"
            : reason.trim();
        if (normalized.length() > 500) {
            throw failure("INVALID_PAUSE_REASON", "Pause reason is too long");
        }
        try {
            repository.requestPause(user.workspaceId(), batchId, normalized);
        } catch (IllegalStateException exception) {
            throw failure("MIGRATION_NOT_PAUSABLE", "Migration batch cannot be paused", exception);
        }
        auditLog.log(user, "work_item_migration.paused", "work_item_migration", batchId,
            Map.of("reason", normalized));
        return requireBatch(user.workspaceId(), batchId);
    }

    public MigrationVerification verifyBatch(CurrentUser user, UUID batchId) {
        authorization.requireManageProjects(user);
        MigrationBatch batch = requireBatch(user.workspaceId(), batchId);
        if (!Set.of("completed", "failed", "rolled_back").contains(batch.status())) {
            throw failure("MIGRATION_NOT_VERIFIABLE", "Migration batch must be terminal before verification");
        }
        VerificationObservation observed = repository.observeBatch(user.workspaceId(), batchId);
        boolean matched = observed.mismatches() == 0;
        MigrationVerification result = repository.appendVerification(
            user.workspaceId(), batchId, "batch_manifest", matched ? "matched" : "mismatched",
            batch.manifestFingerprint(), observed, user.id()
        );
        auditLog.log(user, "work_item_migration.batch_verified", "work_item_migration", batchId,
            Map.of("matched", matched, "mismatches", observed.mismatches()));
        return result;
    }

    public MigrationVerification verifyConvergence(CurrentUser user) {
        authorization.requireManageProjects(user);
        VerificationObservation observed = repository.observeWorkspace(user.workspaceId());
        boolean matched = observed.mismatches() == 0;
        MigrationVerification result = repository.appendVerification(
            user.workspaceId(), null, "workspace_convergence", matched ? "matched" : "mismatched",
            null, observed, user.id()
        );
        auditLog.log(user, "work_item_migration.convergence_verified", "workspace",
            user.workspaceId(), Map.of("matched", matched, "mismatches", observed.mismatches()));
        return result;
    }

    public MigrationBatch rollback(CurrentUser user, UUID batchId, boolean confirm) {
        authorization.requireManageProjects(user);
        if (!confirm) {
            throw failure("MIGRATION_CONFIRMATION_REQUIRED", "Rollback requires explicit confirmation");
        }
        MigrationBatch batch = requireBatch(user.workspaceId(), batchId);
        if (!Set.of("completed", "failed", "paused").contains(batch.status())) {
            throw failure("MIGRATION_NOT_ROLLBACKABLE", "Migration batch is not rollbackable");
        }
        if (repository.hasCanonicalWrites(user.workspaceId(), batchId)) {
            repository.enableKillSwitch(user.workspaceId(), user.id());
            throw failure(
                "POST_CUTOVER_COMPENSATION_REQUIRED",
                "Canonical writes exist; kill switch was enabled and explicit compensation is required"
            );
        }
        List<UUID> targets = repository.listActiveTargets(user.workspaceId(), batchId);
        unitTemplate.executeWithoutResult(transaction -> {
            targets.forEach(target -> objectCommands.removeLink(
                user.workspaceId(), "work_item", target, user.id()
            ));
            repository.rollbackBatch(user.workspaceId(), batchId, user.id());
        });
        auditLog.log(user, "work_item_migration.rolled_back", "work_item_migration", batchId,
            Map.of("removedTargets", targets.size()));
        return requireBatch(user.workspaceId(), batchId);
    }

    private MigrationPlan buildPlan(UUID workspaceId, Set<UUID> projectIds) {
        LegacyProfile profile = compatibilityRepository.profile(workspaceId);
        List<MigrationPlanUnit> units = new ArrayList<>();
        List<MigrationFailure> failures = new ArrayList<>();
        Set<UUID> availableProjectIds = new LinkedHashSet<>(
            repository.listLegacyProjectIds(workspaceId)
        );
        List<UUID> selectedProjectIds = projectIds.isEmpty()
            ? List.copyOf(availableProjectIds)
            : projectIds.stream().sorted().toList();
        for (UUID projectId : selectedProjectIds) {
            if (!availableProjectIds.contains(projectId)) {
                failures.add(MigrationFailure.planned(
                    "LEGACY_PROJECT_NOT_FOUND", "project", projectId, Map.of()
                ));
                continue;
            }
            UUID spaceId = repository.findActiveSpace(workspaceId, projectId).orElse(null);
            if (spaceId == null) {
                failures.add(MigrationFailure.planned(
                    "ACTIVE_SPACE_MAP_MISSING", "project", projectId, Map.of()
                ));
                continue;
            }
            JsonNode source = repository.loadManifest(workspaceId, projectId);
            ObjectNode bindings = objectMapper.createObjectNode();
            TypeBinding projectBinding = binding(workspaceId, spaceId, "project", projectId, failures);
            if (projectBinding != null) {
                bindings.set("project", objectMapper.valueToTree(projectBinding));
            }
            boolean blocked = projectBinding == null;
            Set<String> issueTypes = new LinkedHashSet<>();
            for (JsonNode issue : source.path("issues")) {
                String issueType = issue.path("issueType").asText("").toLowerCase(Locale.ROOT);
                if (!ISSUE_TYPES.contains(issueType)) {
                    failures.add(MigrationFailure.planned(
                        "UNSUPPORTED_LEGACY_ISSUE_TYPE", "issue", uuid(issue, "id"),
                        Map.of("issueType", issueType)
                    ));
                    blocked = true;
                } else {
                    issueTypes.add(issueType);
                }
                if (issue.path("relations").size() > 0) {
                    failures.add(MigrationFailure.planned(
                        "RELATION_DEFERRED_TO_S10", "issue", uuid(issue, "id"),
                        Map.of("count", issue.path("relations").size())
                    ));
                }
            }
            for (String type : issueTypes) {
                TypeBinding binding = binding(workspaceId, spaceId, type, projectId, failures);
                if (binding == null) {
                    blocked = true;
                } else {
                    bindings.set(type, objectMapper.valueToTree(binding));
                }
            }
            if (blocked) {
                continue;
            }
            ObjectNode manifest = objectMapper.createObjectNode();
            manifest.set("source", source);
            manifest.set("targetBindings", bindings);
            String sourceFingerprint = hash(source);
            units.add(new MigrationPlanUnit(
                projectId, spaceId, sourceFingerprint, manifest, objectCount(source)
            ));
        }
        ArrayNode unitHashes = objectMapper.createArrayNode();
        units.stream().sorted((left, right) ->
            left.legacyProjectId().compareTo(right.legacyProjectId())
        ).forEach(unit -> unitHashes.add(objectMapper.valueToTree(Map.of(
            "projectId", unit.legacyProjectId().toString(),
            "spaceId", unit.spaceId().toString(),
            "sourceFingerprint", unit.sourceFingerprint(),
            "targetBindings", unit.manifest().path("targetBindings")
        ))));
        return new MigrationPlan(
            workspaceId,
            profile.sourceWatermark(),
            profile.sourceFingerprint(),
            hash(unitHashes),
            List.copyOf(units),
            List.copyOf(failures)
        );
    }

    private TypeBinding binding(
        UUID workspaceId,
        UUID spaceId,
        String typeKey,
        UUID sourceId,
        List<MigrationFailure> failures
    ) {
        TypeBinding binding = repository.findTypeBinding(workspaceId, spaceId, typeKey).orElse(null);
        if (binding == null) {
            failures.add(MigrationFailure.planned(
                "PUBLISHED_TYPE_BINDING_MISSING", "project", sourceId, Map.of("typeKey", typeKey)
            ));
            return null;
        }
        try {
            RuntimeConfiguration configuration = snapshotAdapter.requireComplete(
                workspaceId, spaceId, binding.typeId(), binding.versionId()
            );
            if (!configuration.configHash().equals(binding.configHash())) {
                throw failure("SNAPSHOT_INTEGRITY_FAILURE", "Migration binding hash is invalid");
            }
            return binding;
        } catch (RuntimeException exception) {
            failures.add(MigrationFailure.planned(
                "PUBLISHED_SNAPSHOT_INVALID", "project", sourceId,
                Map.of("typeKey", typeKey, "message", safeMessage(exception))
            ));
            return null;
        }
    }

    private int migrateUnit(
        CurrentUser user,
        UUID batchId,
        MigrationUnit unit,
        long fenceVersion
    ) {
        MigrationPlanUnit planned = repository.loadUnitManifest(
            user.workspaceId(), batchId, unit.id()
        ).orElseThrow(() -> failure("MIGRATION_MANIFEST_MISSING", "Migration manifest is unavailable"));
        JsonNode currentSource = repository.loadManifest(user.workspaceId(), unit.legacyProjectId());
        if (!planned.sourceFingerprint().equals(hash(currentSource))) {
            throw failure("LEGACY_SOURCE_CHANGED", "Legacy source changed after planning");
        }
        JsonNode source = planned.manifest().path("source");
        JsonNode bindings = planned.manifest().path("targetBindings");
        if (repository.findActiveMap(
            user.workspaceId(), "project", unit.legacyProjectId()
        ).filter(map -> map.batchId().equals(batchId)).isPresent()) {
            return planned.objectCount();
        }
        JsonNode project = source.path("project");
        UUID projectTarget = migrateItem(
            user, batchId, unit, "project", project, unit.legacyProjectId(),
            typeBinding(bindings.path("project")), project.path("projectKey").asText(),
            project.path("name").asText(), projectFields(project)
        );
        migrateProjectMembers(
            user, batchId, unit, projectTarget, source.path("members"), project
        );
        for (JsonNode issue : source.path("issues")) {
            String type = issue.path("issueType").asText().toLowerCase(Locale.ROOT);
            UUID issueTarget = migrateItem(
                user, batchId, unit, "issue", issue, unit.legacyProjectId(),
                typeBinding(bindings.path(type)), issue.path("issueKey").asText(),
                issue.path("title").asText(), issueFields(issue)
            );
            migrateIssueParticipants(user, batchId, unit, issueTarget, issue);
            migrateComments(user, batchId, unit, issueTarget, issue);
            migrateAttachments(user, batchId, unit, issueTarget, issue);
            migrateActivities(user, batchId, unit, issueTarget, issue);
        }
        return planned.objectCount();
    }

    private UUID migrateItem(
        CurrentUser user,
        UUID batchId,
        MigrationUnit unit,
        String sourceType,
        JsonNode source,
        UUID sourceProjectId,
        TypeBinding binding,
        String displayKey,
        String title,
        ObjectNode requestedFields
    ) {
        UUID sourceId = uuid(source, "id");
        if (repository.findActiveMap(user.workspaceId(), sourceType, sourceId).isPresent()) {
            throw failure("SOURCE_ALREADY_MAPPED", "Legacy source is already owned by another batch");
        }
        RuntimeConfiguration configuration = snapshotAdapter.requireComplete(
            user.workspaceId(), unit.spaceId(), binding.typeId(), binding.versionId()
        );
        if (!configuration.configHash().equals(binding.configHash())) {
            throw failure("SNAPSHOT_INTEGRITY_FAILURE", "Planned migration binding changed");
        }
        CanonicalValues values = migrationValues(configuration, requestedFields);
        IdentityResolution identity = identityResolver.resolve(
            user.workspaceId(), unit.spaceId(), sourceType, sourceId
        );
        long number = repository.nextNumber(user.workspaceId(), unit.spaceId(), binding.typeId());
        String lifecycle = archived(source) ? "archived" : "active";
        UUID createdBy = uuid(source, "createdBy");
        UUID updatedBy = nullableUuid(source, "updatedBy") == null
            ? createdBy
            : nullableUuid(source, "updatedBy");
        Instant createdAt = instant(source, "createdAt", Instant.now());
        Instant updatedAt = instant(source, "updatedAt", createdAt);
        Instant archivedAt = "archived".equals(lifecycle)
            ? instant(source, sourceType.equals("project") ? "archivedAt" : "deletedAt", updatedAt)
            : null;
        repository.insertMigratedWorkItem(
            identity.targetId(), user.workspaceId(), unit.spaceId(), binding, number,
            displayKey, title, values.values(), lifecycle, createdBy, createdAt,
            updatedBy, updatedAt, archivedAt
        );
        workItemRepository.replaceFieldProjections(
            user.workspaceId(), unit.spaceId(), identity.targetId(), values.projections()
        );
        repository.insertMap(
            user.workspaceId(), batchId, unit.id(), sourceType, sourceId, sourceProjectId,
            unit.spaceId(), identity.targetId(), identity.decision(), hash(source)
        );
        repository.insertProvenance(
            user.workspaceId(), batchId, unit.id(), sourceType, sourceId, sourceProjectId,
            hash(source), "work_item", identity.targetId(),
            objectMapper.valueToTree(Map.of("displayKey", displayKey, "typeKey", binding.typeKey()))
        );
        repository.insertActivity(
            deterministic("migration-created", sourceId), user.workspaceId(), unit.spaceId(),
            identity.targetId(), "legacy_migrated", createdBy,
            objectMapper.valueToTree(Map.of(
                "sourceType", sourceType,
                "sourceId", sourceId.toString(),
                "batchId", batchId.toString()
            )), createdAt
        );
        objectCommands.upsertLink(
            user.workspaceId(), "work_item", identity.targetId(),
            "/project-spaces/" + unit.spaceId() + "/work-items/" + identity.targetId(),
            "colla://work-item/" + identity.targetId(), title, user.id()
        );
        return identity.targetId();
    }

    private void migrateProjectMembers(
        CurrentUser user,
        UUID batchId,
        MigrationUnit unit,
        UUID targetId,
        JsonNode members,
        JsonNode project
    ) {
        for (JsonNode member : members) {
            UUID sourceId = uuid(member, "id");
            UUID participantId = deterministic("participant", sourceId);
            if (!member.path("archivedAt").isTextual()) {
                repository.upsertParticipant(
                    participantId, user.workspaceId(), unit.spaceId(), targetId,
                    uuid(member, "userId"), participantRole(member.path("role").asText()),
                    uuid(member, "createdBy"), instant(member, "joinedAt", Instant.now())
                );
            }
            repository.insertProvenance(
                user.workspaceId(), batchId, unit.id(), "member", sourceId,
                unit.legacyProjectId(), hash(member), "participant", participantId,
                objectMapper.valueToTree(Map.of("archived", !member.path("archivedAt").isNull()))
            );
        }
        if (members.isEmpty()) {
            UUID actor = uuid(project, "createdBy");
            UUID participantId = deterministic("project-owner", unit.legacyProjectId());
            repository.upsertParticipant(
                participantId, user.workspaceId(), unit.spaceId(), targetId, actor,
                "owner", actor, instant(project, "createdAt", Instant.now())
            );
        }
    }

    private void migrateIssueParticipants(
        CurrentUser user,
        UUID batchId,
        MigrationUnit unit,
        UUID targetId,
        JsonNode issue
    ) {
        UUID reporter = uuid(issue, "reporterId");
        repository.upsertParticipant(
            deterministic("issue-reporter", uuid(issue, "id")), user.workspaceId(), unit.spaceId(),
            targetId, reporter, "owner", reporter, instant(issue, "createdAt", Instant.now())
        );
        UUID assignee = nullableUuid(issue, "assigneeId");
        if (assignee != null && !assignee.equals(reporter)) {
            repository.upsertParticipant(
                deterministic("issue-assignee", uuid(issue, "id")), user.workspaceId(),
                unit.spaceId(), targetId, assignee, "assignee", reporter,
                instant(issue, "updatedAt", Instant.now())
            );
        }
    }

    private void migrateComments(
        CurrentUser user,
        UUID batchId,
        MigrationUnit unit,
        UUID targetId,
        JsonNode issue
    ) {
        for (JsonNode comment : issue.path("comments")) {
            UUID sourceId = uuid(comment, "id");
            UUID target = deterministic("comment", sourceId);
            repository.insertComment(
                target, user.workspaceId(), unit.spaceId(), targetId,
                uuid(comment, "authorId"), comment.path("content").asText(),
                instant(comment, "createdAt", Instant.now()),
                instant(comment, "updatedAt", null),
                instant(comment, "deletedAt", null)
            );
            repository.insertProvenance(
                user.workspaceId(), batchId, unit.id(), "comment", sourceId,
                unit.legacyProjectId(), hash(comment), "comment", target,
                objectMapper.createObjectNode()
            );
        }
    }

    private void migrateAttachments(
        CurrentUser user,
        UUID batchId,
        MigrationUnit unit,
        UUID targetId,
        JsonNode issue
    ) {
        for (JsonNode attachment : issue.path("attachments")) {
            UUID sourceId = uuid(attachment, "id");
            UUID target = deterministic("attachment", sourceId);
            repository.insertAttachment(
                target, user.workspaceId(), unit.spaceId(), targetId,
                uuid(attachment, "fileId"), uuid(attachment, "createdBy"),
                instant(attachment, "createdAt", Instant.now())
            );
            repository.insertProvenance(
                user.workspaceId(), batchId, unit.id(), "attachment", sourceId,
                unit.legacyProjectId(), hash(attachment), "attachment", target,
                objectMapper.valueToTree(Map.of("fileId", attachment.path("fileId").asText()))
            );
        }
    }

    private void migrateActivities(
        CurrentUser user,
        UUID batchId,
        MigrationUnit unit,
        UUID targetId,
        JsonNode issue
    ) {
        UUID fallbackActor = uuid(issue, "reporterId");
        for (JsonNode activity : issue.path("activities")) {
            UUID sourceId = uuid(activity, "id");
            UUID target = deterministic("activity", sourceId);
            UUID actor = nullableUuid(activity, "actorId");
            if (actor == null) {
                actor = fallbackActor;
            }
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("fromValue", activity.path("fromValue").asText(null));
            payload.put("toValue", activity.path("toValue").asText(null));
            payload.set("metadata", activity.path("metadata").deepCopy());
            repository.insertActivity(
                target, user.workspaceId(), unit.spaceId(), targetId,
                "legacy_" + safeActivity(activity.path("action").asText()), actor, payload,
                instant(activity, "createdAt", Instant.now())
            );
            repository.insertProvenance(
                user.workspaceId(), batchId, unit.id(), "activity", sourceId,
                unit.legacyProjectId(), hash(activity), "activity", target,
                objectMapper.createObjectNode()
            );
        }
    }

    private CanonicalValues migrationValues(
        RuntimeConfiguration configuration,
        ObjectNode sourceValues
    ) {
        ObjectNode values = objectMapper.createObjectNode();
        for (JsonNode field : configuration.snapshot().path("fields")) {
            if (!"active".equals(field.path("status").asText())) {
                continue;
            }
            String key = field.path("fieldKey").asText();
            JsonNode defaultValue = field.path("config").get("defaultValue");
            if (defaultValue != null && !defaultValue.isNull()) {
                values.set(key, defaultValue.deepCopy());
            }
            if (sourceValues.has(key) && !sourceValues.get(key).isNull()) {
                values.set(key, sourceValues.get(key).deepCopy());
            }
        }
        for (JsonNode field : configuration.snapshot().path("fields")) {
            String key = field.path("fieldKey").asText();
            if ("active".equals(field.path("status").asText())
                && field.path("config").path("required").asBoolean(false)
                && (!values.has(key) || values.path(key).isNull()
                    || values.path(key).isTextual() && values.path(key).asText().isBlank())) {
                throw failure("MIGRATION_REQUIRED_FIELD_MISSING",
                    "Legacy source cannot satisfy required field " + key);
            }
        }
        return valueCodec.canonicalize(configuration, values);
    }

    private ObjectNode projectFields(JsonNode project) {
        ObjectNode values = objectMapper.createObjectNode();
        copyText(values, "description", project.path("description"));
        copyText(values, "legacy_status", project.path("status"));
        copyText(values, "status", project.path("status"));
        return values;
    }

    private ObjectNode issueFields(JsonNode issue) {
        ObjectNode values = objectMapper.createObjectNode();
        copyText(values, "description", issue.path("description"));
        copyText(values, "priority", issue.path("priority"));
        copyText(values, "legacy_status", issue.path("status"));
        copyText(values, "status", issue.path("status"));
        copyText(values, "due_at", issue.path("dueAt"));
        copyText(values, "dueAt", issue.path("dueAt"));
        UUID assignee = nullableUuid(issue, "assigneeId");
        if (assignee != null) {
            ArrayNode refs = objectMapper.createArrayNode().add(assignee.toString());
            values.set("assignee", refs);
            values.set("assignee_id", refs.deepCopy());
            values.set("assigneeId", refs.deepCopy());
        }
        return values;
    }

    private void copyText(ObjectNode target, String key, JsonNode value) {
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            target.put(key, value.asText());
        }
    }

    private TypeBinding typeBinding(JsonNode value) {
        if (!value.isObject()) {
            throw failure("MIGRATION_BINDING_MISSING", "Migration type binding is missing");
        }
        return objectMapper.convertValue(value, TypeBinding.class);
    }

    private boolean archived(JsonNode source) {
        return source.path("archivedAt").isTextual()
            || source.path("deletedAt").isTextual()
            || "archived".equalsIgnoreCase(source.path("status").asText());
    }

    private int objectCount(JsonNode source) {
        int count = 1 + source.path("members").size() + source.path("issues").size();
        for (JsonNode issue : source.path("issues")) {
            count += issue.path("comments").size();
            count += issue.path("attachments").size();
            count += issue.path("activities").size();
        }
        return count;
    }

    private boolean blockingPreflightFailure(MigrationFailure failure) {
        return !"RELATION_DEFERRED_TO_S10".equals(failure.failureCode());
    }

    private MigrationBatch requireBatch(UUID workspaceId, UUID batchId) {
        return repository.findBatch(workspaceId, batchId)
            .orElseThrow(() -> failure("MIGRATION_BATCH_NOT_FOUND", "Migration batch is not available"));
    }

    private String participantRole(String legacy) {
        return switch (legacy == null ? "" : legacy.toLowerCase(Locale.ROOT)) {
            case "owner", "admin" -> "owner";
            default -> "collaborator";
        };
    }

    private UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(node.path(field).asText());
        } catch (IllegalArgumentException exception) {
            throw failure("MIGRATION_SOURCE_INVALID", "Legacy source contains an invalid " + field);
        }
    }

    private UUID nullableUuid(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return uuid(node, field);
    }

    private Instant instant(JsonNode node, String field, Instant fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value.asText());
        } catch (RuntimeException exception) {
            throw failure("MIGRATION_SOURCE_INVALID", "Legacy source contains an invalid " + field);
        }
    }

    private UUID deterministic(String kind, UUID sourceId) {
        return UUID.nameUUIDFromBytes(
            ("colla:work-item-migration:" + kind + ":" + sourceId)
                .getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hash(JsonNode node) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(node);
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(serialized)
            );
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw failure("MIGRATION_FINGERPRINT_FAILED", "Migration fingerprint failed", exception);
        }
    }

    private String normalizedWorker(String value) {
        String result = value == null || value.isBlank()
            ? "admin-api:" + UUID.randomUUID()
            : value.trim();
        if (result.length() > 160) {
            throw failure("INVALID_MIGRATION_WORKER", "Migration worker id is too long");
        }
        return result;
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException typed) {
            return typed.code();
        }
        return "MIGRATION_UNIT_FAILED";
    }

    private String safeMessage(Throwable exception) {
        String value = exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
        return value.length() <= MAX_SAFE_ERROR_LENGTH
            ? value
            : value.substring(0, MAX_SAFE_ERROR_LENGTH);
    }

    private String safeActivity(String value) {
        String result = value == null ? "changed" : value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]+", "_");
        return result.length() <= 40 ? result : result.substring(0, 40);
    }

    private void sleep(int millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("MIGRATION_INTERRUPTED", "Migration worker was interrupted", exception);
        }
    }
}
