package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationView;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationMigrationRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationMigrationRepository.LegacyRelationUnit;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationMigrationRepository.MigrationBatch;
import com.colla.platform.modules.project.infrastructure.WorkItemRelationMigrationRepository.MigrationUnit;
import com.colla.platform.modules.project.infrastructure.WorkItemRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkItemRelationMigrationService {
    private static final int MAX_UNITS = 5_000;

    private final WorkItemRelationMigrationRepository repository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemRelationService relationService;
    private final WorkItemRelationAccessDecisionService accessDecision;
    private final AuditLog auditLog;

    public WorkItemRelationMigrationService(
        WorkItemRelationMigrationRepository repository,
        WorkItemRepository workItemRepository,
        WorkItemRelationService relationService,
        WorkItemRelationAccessDecisionService accessDecision,
        AuditLog auditLog
    ) {
        this.repository = repository;
        this.workItemRepository = workItemRepository;
        this.relationService = relationService;
        this.accessDecision = accessDecision;
        this.auditLog = auditLog;
    }

    public MigrationState plan(
        CurrentUser user,
        UUID spaceId,
        String relationKey,
        boolean dryRun,
        String reason,
        String requestId
    ) {
        accessDecision.requireManager(user, spaceId);
        String key = key(relationKey);
        String normalizedReason = reason(reason);
        String normalizedRequest = requestId(requestId);
        var existing = repository.findBatchByRequest(
            user.workspaceId(), spaceId, normalizedRequest
        );
        if (existing.isPresent()) {
            MigrationBatch batch = existing.get();
            if (!batch.relationKey().equals(key) || batch.dryRun() != dryRun) {
                throw failure(
                    "RELATION_MIGRATION_REQUEST_CONFLICT",
                    "Migration request id was already used with different input"
                );
            }
            return state(user, batch);
        }
        List<LegacyRelationUnit> units = repository.inspectLegacy(
            user.workspaceId(), spaceId
        );
        if (units.size() > MAX_UNITS) {
            throw failure(
                "RELATION_MIGRATION_UNIT_LIMIT",
                "Legacy relation manifest exceeds the per-batch unit limit"
            );
        }
        int canonical = (int) units.stream()
            .filter(unit -> "canonical_work_item".equals(unit.classification()))
            .count();
        UUID batchId = UUID.randomUUID();
        MigrationBatch batch = new MigrationBatch(
            batchId,
            user.workspaceId(),
            spaceId,
            key,
            normalizedRequest,
            manifestHash(units),
            dryRun,
            "planned",
            0,
            units.size(),
            canonical,
            units.size() - canonical,
            0,
            0,
            hash(normalizedReason),
            user.id(),
            null,
            null,
            null
        );
        repository.createBatch(batch);
        repository.insertUnits(batchId, units);
        auditLog.log(
            user,
            "work_item_relation_migration.planned",
            "project_space",
            spaceId,
            Map.of(
                "batchId", batchId.toString(),
                "relationKey", key,
                "dryRun", dryRun,
                "manifestHash", batch.manifestHash(),
                "totalCount", units.size(),
                "canonicalCount", canonical
            )
        );
        return state(user, requireBatch(user, spaceId, batchId));
    }

    public MigrationState get(CurrentUser user, UUID spaceId, UUID batchId) {
        accessDecision.requireManager(user, spaceId);
        return state(user, requireBatch(user, spaceId, batchId));
    }

    public MigrationState execute(
        CurrentUser user,
        UUID spaceId,
        UUID batchId,
        long expectedVersion,
        String reason,
        String confirmation
    ) {
        accessDecision.requireManager(user, spaceId);
        requireConfirmation(confirmation, "MIGRATE_RELATIONS");
        reason(reason);
        MigrationBatch batch = requireBatch(user, spaceId, batchId);
        if (batch.dryRun()) {
            if (repository.transitionBatch(
                user.workspaceId(), spaceId, batchId, expectedVersion,
                List.of("planned"), "completed"
            ) != 1) {
                throw versionConflict();
            }
            return state(user, requireBatch(user, spaceId, batchId));
        }
        if (repository.transitionBatch(
            user.workspaceId(), spaceId, batchId, expectedVersion,
            List.of("planned", "failed"), "running"
        ) != 1) {
            throw versionConflict();
        }
        runUnits(user, spaceId, requireBatch(user, spaceId, batchId));
        return state(user, requireBatch(user, spaceId, batchId));
    }

    public MigrationState resume(
        CurrentUser user,
        UUID spaceId,
        UUID batchId,
        long expectedVersion,
        String reason,
        String confirmation
    ) {
        return execute(
            user, spaceId, batchId, expectedVersion, reason, confirmation
        );
    }

    public MigrationState verify(
        CurrentUser user,
        UUID spaceId,
        UUID batchId,
        long expectedVersion
    ) {
        accessDecision.requireManager(user, spaceId);
        MigrationBatch batch = requireBatch(user, spaceId, batchId);
        if (batch.version() != expectedVersion
            || !List.of("completed", "failed").contains(batch.status())) {
            throw versionConflict();
        }
        List<MigrationUnit> canonical = repository.listUnits(
            user.workspaceId(), spaceId, batchId,
            List.of("completed", "failed", "verified")
        );
        List<UUID> failures = repository.verificationFailures(
            user.workspaceId(), spaceId, batchId
        );
        repository.appendVerification(
            user.workspaceId(), spaceId, batchId, failures.isEmpty(),
            canonical.size(), failures, user.id()
        );
        if (failures.isEmpty()) {
            canonical.stream()
                .filter(unit -> "completed".equals(unit.status()))
                .forEach(unit -> repository.markUnitVerified(unit.id()));
            repository.refreshCounts(
                user.workspaceId(), spaceId, batchId, "verified"
            );
        } else {
            repository.refreshCounts(
                user.workspaceId(), spaceId, batchId, "failed"
            );
        }
        auditLog.log(
            user,
            "work_item_relation_migration.verified",
            "project_space",
            spaceId,
            Map.of(
                "batchId", batchId.toString(),
                "outcome", failures.isEmpty() ? "passed" : "failed",
                "failureCount", failures.size()
            )
        );
        return state(user, requireBatch(user, spaceId, batchId));
    }

    public MigrationState rollback(
        CurrentUser user,
        UUID spaceId,
        UUID batchId,
        long expectedVersion,
        String reason,
        String confirmation
    ) {
        accessDecision.requireManager(user, spaceId);
        requireConfirmation(confirmation, "ROLLBACK_RELATIONS");
        String normalizedReason = reason(reason);
        MigrationBatch batch = requireBatch(user, spaceId, batchId);
        if (batch.version() != expectedVersion
            || !List.of("completed", "verified", "failed").contains(batch.status())) {
            throw versionConflict();
        }
        List<MigrationUnit> units = repository.listUnits(
            user.workspaceId(), spaceId, batchId, List.of("completed", "verified")
        );
        for (MigrationUnit unit : units) {
            if (unit.relationId() == null) {
                continue;
            }
            RelationView relation = relationService.get(
                user, spaceId, unit.relationId(), unit.sourceWorkItemId()
            );
            if (!"active".equals(relation.status())) {
                throw failure(
                    "RELATION_MIGRATION_ROLLBACK_CONFLICT",
                    "A migrated relation changed after backfill"
                );
            }
            relationService.withdraw(
                user,
                spaceId,
                relation.id(),
                relation.version(),
                relation.source().version(),
                relation.target().version(),
                normalizedReason,
                "relation-migration-rollback-" + batchId + "-" + unit.id()
            );
            repository.markUnitRolledBack(unit.id());
        }
        repository.refreshCounts(
            user.workspaceId(), spaceId, batchId, "rolled_back"
        );
        auditLog.log(
            user,
            "work_item_relation_migration.rolled_back",
            "project_space",
            spaceId,
            Map.of("batchId", batchId.toString(), "rolledBackCount", units.size())
        );
        return state(user, requireBatch(user, spaceId, batchId));
    }

    private void runUnits(CurrentUser user, UUID spaceId, MigrationBatch batch) {
        List<MigrationUnit> units = repository.listUnits(
            user.workspaceId(), spaceId, batch.id(), List.of("planned", "failed")
        );
        for (MigrationUnit unit : units) {
            try {
                WorkItem source = requireItem(user, spaceId, unit.sourceWorkItemId());
                WorkItem target = requireItem(user, spaceId, unit.targetWorkItemId());
                RelationView relation = relationService.create(
                    user,
                    spaceId,
                    batch.relationKey(),
                    source.id(),
                    target.id(),
                    source.version(),
                    target.version(),
                    "relation-migration-" + batch.id() + "-" + unit.id()
                );
                repository.markUnitCompleted(unit.id(), relation.id());
            } catch (RuntimeException exception) {
                repository.markUnitFailed(unit.id(), errorCode(exception));
            }
        }
        boolean failed = !repository.listUnits(
            user.workspaceId(), spaceId, batch.id(), List.of("failed")
        ).isEmpty();
        repository.refreshCounts(
            user.workspaceId(), spaceId, batch.id(), failed ? "failed" : "completed"
        );
        auditLog.log(
            user,
            "work_item_relation_migration.executed",
            "project_space",
            spaceId,
            Map.of(
                "batchId", batch.id().toString(),
                "outcome", failed ? "failed" : "completed"
            )
        );
    }

    private MigrationState state(CurrentUser user, MigrationBatch batch) {
        List<MigrationUnit> units = repository.listUnits(
            user.workspaceId(), batch.spaceId(), batch.id(), List.of()
        );
        return new MigrationState(batch, units);
    }

    private MigrationBatch requireBatch(CurrentUser user, UUID spaceId, UUID batchId) {
        return repository.findBatch(user.workspaceId(), spaceId, batchId, false)
            .orElseThrow(() -> failure(
                "NOT_FOUND_OR_HIDDEN", "Relation migration batch is not available"
            ));
    }

    private WorkItem requireItem(CurrentUser user, UUID spaceId, UUID workItemId) {
        return workItemRepository.find(user.workspaceId(), spaceId, workItemId)
            .orElseThrow(() -> failure(
                "RELATION_MIGRATION_ENDPOINT_MISSING",
                "A mapped work item endpoint is unavailable"
            ));
    }

    private String errorCode(RuntimeException exception) {
        return exception instanceof WorkItemRuntimeException workItem
            ? workItem.code()
            : "RELATION_MIGRATION_UNIT_FAILED";
    }

    private WorkItemRuntimeException versionConflict() {
        return failure(
            "RELATION_MIGRATION_VERSION_CONFLICT",
            "Relation migration batch changed concurrently"
        );
    }

    private String key(String value) {
        String result = value == null ? "" : value.trim().toLowerCase();
        if (!result.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw failure("INVALID_RELATION_KEY", "Relation key is invalid");
        }
        return result;
    }

    private String reason(String value) {
        String result = value == null ? "" : value.trim();
        if (result.length() < 3 || result.length() > 500) {
            throw failure(
                "RELATION_MIGRATION_REASON_REQUIRED",
                "Migration reason must contain between 3 and 500 characters"
            );
        }
        return result;
    }

    private String requestId(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank() || result.length() > 120) {
            throw failure(
                "INVALID_REQUEST_ID", "Migration request id is invalid"
            );
        }
        return result;
    }

    private void requireConfirmation(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw failure(
                "RELATION_MIGRATION_CONFIRMATION_REQUIRED",
                "Exact dangerous-operation confirmation is required"
            );
        }
    }

    private String manifestHash(List<LegacyRelationUnit> units) {
        StringBuilder value = new StringBuilder();
        units.forEach(unit -> value
            .append(unit.sourceRelationId()).append(':')
            .append(unit.sourceFingerprint()).append(':')
            .append(unit.classification()).append('\n'));
        return hash(value.toString());
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record MigrationState(MigrationBatch batch, List<MigrationUnit> units) {
    }
}
