package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.BATCH_STATUS_COMPLETED;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.BATCH_STATUS_FAILED;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.BATCH_STATUS_ROLLED_BACK;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.BATCH_STATUS_RUNNING;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.FAILURE_SCOPE_MEMBER;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.FAILURE_SCOPE_PROJECT;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MAP_STATUS_ACTIVE;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MISMATCH_MAP_MISSING;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MISMATCH_MAP_SUPERSEDED;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MISMATCH_MAP_UNEXPECTED;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MISMATCH_MEMBER_SET;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MISMATCH_PLAN_MISSING;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MISMATCH_SPACE_ID;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MISMATCH_SPACE_MISSING;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MISMATCH_SPACE_NOT_ACTIVE;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.PROJECT_FAILURE_NO_VALID_OWNER;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.PROJECT_FAILURE_ROLLBACK_FAILED;
import static com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.PROJECT_FAILURE_UNIT_FAILED;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.permission.contract.ProjectAuthorization;
import com.colla.platform.modules.platform.contract.PlatformObjectCommands;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.ActiveSpaceMap;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.LegacySpaceMappingPlan;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.MemberFailure;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.MemberMapping;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.ProjectMappingPlan;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.SourceFingerprintRow;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.LegacySpaceMapRecord;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationBatchRecord;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.MigrationVerificationReport;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.VerificationMismatch;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.VerifiedSpaceMatch;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceMember;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.ProjectLegacyMappingRepository;
import com.colla.platform.modules.project.infrastructure.ProjectLegacySpaceMapRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMembershipRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceMigrationBatchRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Batch state machine for the legacy project to project space migration. Public methods require
 * the project governance permission and are scoped to the caller's workspace.
 *
 * <p>Transaction design: the batch row is committed in its own transaction before any unit work so
 * that per-project REQUIRES_NEW units can reference it. An outer REPEATABLE_READ transaction then
 * holds a FOR NO KEY UPDATE lock on the workspace row to serialize concurrent batches of the same
 * workspace (FOR NO KEY UPDATE because unit transactions insert rows that foreign-key back to
 * workspaces; FOR UPDATE would conflict with their FOR KEY SHARE checks and self-deadlock). The
 * mapping plan and the source fingerprint are computed inside that transaction, so both are read
 * from one consistent database snapshot. Each project is applied in an isolated REQUIRES_NEW unit:
 * a unit failure rolls back only that project and is recorded as a batch failure item while the
 * remaining projects continue; every unit re-validates its project fingerprint before writing
 * because legacy writes are not serialized by the workspace lock.
 */
@Service
public class ProjectSpaceMigrationService {
    private static final String OUTCOME_CREATED = "CREATED";
    private static final String OUTCOME_REUSED = "REUSED";
    private static final String OUTCOME_SKIPPED = "SKIPPED";
    private static final String OUTCOME_FAILED = "FAILED";
    private static final Set<String> RESUMABLE_STATUSES = Set.of(BATCH_STATUS_FAILED, BATCH_STATUS_RUNNING);
    private static final Set<String> ROLLBACKABLE_STATUSES = Set.of(BATCH_STATUS_COMPLETED, BATCH_STATUS_FAILED);
    private static final int MAX_FAILURE_DETAIL_LENGTH = 400;

    private final ProjectSpaceMigrationBatchRepository batchRepository;
    private final ProjectLegacySpaceMapRepository spaceMapRepository;
    private final ProjectLegacyMappingRepository legacyMappingRepository;
    private final ProjectLegacyMappingService legacyMappingService;
    private final ProjectSpaceRepository spaceRepository;
    private final ProjectSpaceMembershipRepository membershipRepository;
    private final ProjectSpaceMembershipService membershipService;
    private final PlatformObjectCommands platformObjectCommands;
    private final ProjectAuthorization permissionService;
    private final AuditLog auditService;
    private final WorkItemTypePresetReconciliationService presetReconciliationService;
    private final TransactionTemplate lockTemplate;
    private final TransactionTemplate readSnapshotTemplate;
    private final TransactionTemplate unitTemplate;

    public ProjectSpaceMigrationService(
        ProjectSpaceMigrationBatchRepository batchRepository,
        ProjectLegacySpaceMapRepository spaceMapRepository,
        ProjectLegacyMappingRepository legacyMappingRepository,
        ProjectLegacyMappingService legacyMappingService,
        ProjectSpaceRepository spaceRepository,
        ProjectSpaceMembershipRepository membershipRepository,
        ProjectSpaceMembershipService membershipService,
        PlatformObjectCommands platformObjectCommands,
        ProjectAuthorization permissionService,
        AuditLog auditService,
        WorkItemTypePresetReconciliationService presetReconciliationService,
        PlatformTransactionManager transactionManager
    ) {
        this.batchRepository = batchRepository;
        this.spaceMapRepository = spaceMapRepository;
        this.legacyMappingRepository = legacyMappingRepository;
        this.legacyMappingService = legacyMappingService;
        this.spaceRepository = spaceRepository;
        this.membershipRepository = membershipRepository;
        this.membershipService = membershipService;
        this.platformObjectCommands = platformObjectCommands;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.presetReconciliationService = presetReconciliationService;
        this.lockTemplate = new TransactionTemplate(transactionManager);
        // REPEATABLE_READ so the mapping plan (multiple SELECTs) and the source fingerprint are
        // read from one consistent database snapshot instead of several READ_COMMITTED snapshots.
        this.lockTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.readSnapshotTemplate = new TransactionTemplate(transactionManager);
        this.readSnapshotTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.readSnapshotTemplate.setReadOnly(true);
        this.unitTemplate = new TransactionTemplate(transactionManager);
        this.unitTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<MigrationBatchRecord> listBatches(CurrentUser currentUser) {
        permissionService.requireManageProjects(currentUser);
        return batchRepository.listByWorkspace(currentUser.workspaceId());
    }

    public MigrationBatchRecord getBatch(CurrentUser currentUser, UUID batchId) {
        permissionService.requireManageProjects(currentUser);
        return requireBatch(currentUser.workspaceId(), batchId);
    }

    public MigrationBatchRecord dryRun(CurrentUser currentUser) {
        permissionService.requireManageProjects(currentUser);
        UUID workspaceId = currentUser.workspaceId();
        // The plan and the fingerprint are read inside one REPEATABLE_READ snapshot so the dry-run
        // batch records a self-consistent input picture even while legacy writes continue.
        record PlanAndFingerprint(LegacySpaceMappingPlan plan, SourceFingerprint fingerprint) {
        }
        PlanAndFingerprint input = readSnapshotTemplate.execute(transaction -> {
            LegacySpaceMappingPlan planned = legacyMappingService.planWorkspaceMigration(currentUser);
            SourceFingerprint fingerprint = fingerprint(
                workspaceId, legacyMappingRepository.findSourceFingerprintRows(workspaceId)
            );
            return new PlanAndFingerprint(planned, fingerprint);
        });
        LegacySpaceMappingPlan plan = input.plan();
        SourceFingerprint fingerprint = input.fingerprint();
        UUID batchId = batchRepository.insertBatch(
            workspaceId, true, fingerprint.watermark(), fingerprint.checksum(), currentUser.id()
        );
        batchRepository.finalizeBatch(
            workspaceId, batchId, BATCH_STATUS_COMPLETED, null, dryRunSummary(plan), planFailureItems(plan)
        );
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("batchId", batchId.toString());
        metadata.put("dryRun", true);
        metadata.put("totalProjects", plan.totalProjects());
        metadata.put("migratableProjects", plan.migratableProjects());
        metadata.put("reuseCount", plan.reuseCount());
        metadata.put("keyConflictCount", plan.keyConflictCount());
        metadata.put("memberFailureCount", plan.memberFailureCount());
        metadata.put("projectFailureCount", plan.projectFailureCount());
        metadata.put("sourceChecksum", fingerprint.checksum());
        auditService.log(currentUser, "project_migration.dry_run", "project_migration", batchId, metadata);
        return requireBatch(workspaceId, batchId);
    }

    public MigrationBatchRecord execute(CurrentUser currentUser) {
        permissionService.requireManageProjects(currentUser);
        UUID workspaceId = currentUser.workspaceId();
        UUID batchId = batchRepository.insertBatch(workspaceId, false, null, null, currentUser.id());
        ApplyOutcome outcome = applyPlan(workspaceId, batchId, currentUser, false, 1);
        return finalizeApply(currentUser, batchId, outcome, "project_migration.executed");
    }

    public MigrationBatchRecord resume(CurrentUser currentUser, UUID batchId) {
        permissionService.requireManageProjects(currentUser);
        UUID workspaceId = currentUser.workspaceId();
        MigrationBatchRecord batch = requireBatch(workspaceId, batchId);
        if (batch.dryRun()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dry-run batches cannot be resumed");
        }
        if (!RESUMABLE_STATUSES.contains(batch.status())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only failed or stuck running batches can be resumed"
            );
        }
        ApplyOutcome outcome = applyPlan(
            workspaceId, batchId, currentUser, true, previousAttempt(batch.summary()) + 1
        );
        return finalizeApply(currentUser, batchId, outcome, "project_migration.resumed");
    }

    public MigrationVerificationReport verify(CurrentUser currentUser, UUID batchId) {
        permissionService.requireManageProjects(currentUser);
        UUID workspaceId = currentUser.workspaceId();
        MigrationBatchRecord batch = requireBatch(workspaceId, batchId);
        if (batch.dryRun()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Dry-run batches hold no migrated data to verify"
            );
        }
        // Batch verification is anchored to the batch's lifecycle ownership manifest. New legacy
        // projects cannot widen a historical batch, while a later batch that re-owns an artifact
        // explicitly supersedes its historical owner. Use verifyWorkspaceConvergence for the
        // workspace-level convergence view instead.
        LegacySpaceMappingPlan plan = legacyMappingService.planWorkspaceMigration(currentUser);
        Map<UUID, ProjectMappingPlan> plansByProject = plan.plans().stream()
            .collect(Collectors.toMap(ProjectMappingPlan::projectId, Function.identity()));

        List<VerifiedSpaceMatch> matches = new ArrayList<>();
        List<VerificationMismatch> mismatches = new ArrayList<>();
        List<LegacySpaceMapRecord> activeMaps = spaceMapRepository.findActiveByWorkspace(workspaceId);
        Map<UUID, LegacySpaceMapRecord> mapsByProject = new HashMap<>();
        for (LegacySpaceMapRecord map : activeMaps) {
            mapsByProject.put(map.legacyProjectId(), map);
        }

        boolean rolledBack = BATCH_STATUS_ROLLED_BACK.equals(batch.status());
        Set<UUID> manifestProjectIds = new HashSet<>();
        for (Map<String, Object> manifestEntry : manifestProjects(batch)) {
            UUID projectId = UUID.fromString(String.valueOf(manifestEntry.get("projectId")));
            String outcome = String.valueOf(manifestEntry.get("outcome"));
            boolean ownedByBatch = manifestOwnedByBatch(manifestEntry);
            UUID spaceId = manifestEntry.get("spaceId") == null
                ? null
                : UUID.fromString(String.valueOf(manifestEntry.get("spaceId")));
            manifestProjectIds.add(projectId);
            LegacySpaceMapRecord map = mapsByProject.get(projectId);
            if (ownedByBatch || OUTCOME_REUSED.equals(outcome)) {
                if (rolledBack) {
                    if (ownedByBatch && map != null) {
                        mismatches.add(new VerificationMismatch(
                            projectId, spaceId, MISMATCH_MAP_SUPERSEDED,
                            "no active map; the batch was rolled back",
                            "active map present (owned by batch " + map.batchId() + ")"
                        ));
                    } else if (ownedByBatch) {
                        matches.add(new VerifiedSpaceMatch(projectId, spaceId));
                    } else if (map == null) {
                        mismatches.add(new VerificationMismatch(
                            projectId, spaceId, MISMATCH_MAP_MISSING,
                            "reused map recorded by this batch", "no active map"
                        ));
                    } else {
                        verifyMap(workspaceId, currentUser.id(), map, plansByProject, matches, mismatches);
                    }
                    continue;
                }
                if (map == null) {
                    mismatches.add(new VerificationMismatch(
                        projectId, spaceId, MISMATCH_MAP_MISSING,
                        "active map recorded by this batch", "no active map"
                    ));
                    continue;
                }
                if (ownedByBatch && !batchId.equals(map.batchId())) {
                    mismatches.add(new VerificationMismatch(
                        projectId, map.spaceId(), MISMATCH_MAP_SUPERSEDED,
                        "map owned by batch " + batchId, "map owned by batch " + map.batchId()
                    ));
                    continue;
                }
                verifyMap(workspaceId, currentUser.id(), map, plansByProject, matches, mismatches);
            } else if (map != null && batchId.equals(map.batchId())) {
                mismatches.add(new VerificationMismatch(
                    projectId, map.spaceId(), MISMATCH_MAP_UNEXPECTED,
                    "no map owned by this batch; the project outcome is " + outcome,
                    "active map owned by this batch"
                ));
            }
        }
        for (LegacySpaceMapRecord map : activeMaps) {
            if (batchId.equals(map.batchId()) && !manifestProjectIds.contains(map.legacyProjectId())) {
                mismatches.add(new VerificationMismatch(
                    map.legacyProjectId(), map.spaceId(), MISMATCH_MAP_UNEXPECTED,
                    "no manifest entry for this map in the batch", "active map owned by this batch"
                ));
            }
        }
        MigrationVerificationReport report = new MigrationVerificationReport(
            batchId, workspaceId, Instant.now(), mismatches.isEmpty(),
            List.copyOf(matches), List.copyOf(mismatches)
        );

        Map<String, Object> summary = new LinkedHashMap<>(batch.summary());
        Map<String, Object> verification = new LinkedHashMap<>();
        verification.put("verifiedAt", report.verifiedAt().toString());
        verification.put("matched", matches.size());
        verification.put("mismatched", mismatches.size());
        verification.put("allMatched", report.allMatched());
        summary.put("verification", verification);
        batchRepository.updateSummary(workspaceId, batchId, summary);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("batchId", batchId.toString());
        metadata.put("matched", matches.size());
        metadata.put("mismatched", mismatches.size());
        metadata.put("allMatched", report.allMatched());
        auditService.log(currentUser, "project_migration.verified", "project_migration", batchId, metadata);
        return report;
    }

    public MigrationVerificationReport verifyWorkspaceConvergence(CurrentUser currentUser) {
        permissionService.requireManageProjects(currentUser);
        UUID workspaceId = currentUser.workspaceId();
        // Workspace convergence compares the CURRENT plan against every active map in the
        // workspace. It is independent of any batch and never writes into a batch summary.
        LegacySpaceMappingPlan plan = legacyMappingService.planWorkspaceMigration(currentUser);
        Map<UUID, ProjectMappingPlan> plansByProject = plan.plans().stream()
            .collect(Collectors.toMap(ProjectMappingPlan::projectId, Function.identity()));
        Set<UUID> migratableProjects = plan.plans().stream()
            .filter(projectPlan -> projectPlan.projectFailure() == null && hasOwnerMapping(projectPlan))
            .map(ProjectMappingPlan::projectId)
            .collect(Collectors.toSet());

        List<VerifiedSpaceMatch> matches = new ArrayList<>();
        List<VerificationMismatch> mismatches = new ArrayList<>();
        List<LegacySpaceMapRecord> activeMaps = spaceMapRepository.findActiveByWorkspace(workspaceId);
        Map<UUID, LegacySpaceMapRecord> mapsByProject = new HashMap<>();
        for (LegacySpaceMapRecord map : activeMaps) {
            mapsByProject.put(map.legacyProjectId(), map);
            ProjectMappingPlan projectPlan = plansByProject.get(map.legacyProjectId());
            if (projectPlan != null && !migratableProjects.contains(map.legacyProjectId())) {
                mismatches.add(new VerificationMismatch(
                    map.legacyProjectId(), map.spaceId(), MISMATCH_MAP_UNEXPECTED,
                    "no active map; the project is not migratable in the current plan",
                    "active map present"
                ));
                continue;
            }
            verifyMap(workspaceId, currentUser.id(), map, plansByProject, matches, mismatches);
        }
        for (UUID projectId : migratableProjects) {
            if (!mapsByProject.containsKey(projectId)) {
                mismatches.add(new VerificationMismatch(
                    projectId, plansByProject.get(projectId).deterministicSpaceId(), MISMATCH_MAP_MISSING,
                    "active map for a migratable legacy project", "no active map"
                ));
            }
        }
        MigrationVerificationReport report = new MigrationVerificationReport(
            null, workspaceId, Instant.now(), mismatches.isEmpty(),
            List.copyOf(matches), List.copyOf(mismatches)
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workspaceId", workspaceId.toString());
        metadata.put("matched", matches.size());
        metadata.put("mismatched", mismatches.size());
        metadata.put("allMatched", report.allMatched());
        auditService.log(
            currentUser, "project_migration.convergence_verified", "workspace", workspaceId, metadata
        );
        return report;
    }

    public MigrationBatchRecord rollback(CurrentUser currentUser, UUID batchId) {
        permissionService.requireManageProjects(currentUser);
        UUID workspaceId = currentUser.workspaceId();
        MigrationBatchRecord batch = requireBatch(workspaceId, batchId);
        requireRollbackable(batch);

        RollbackTally tally = new RollbackTally();
        lockTemplate.executeWithoutResult(transaction -> {
            batchRepository.lockWorkspaceForMigration(workspaceId);
            requireRollbackable(requireBatch(workspaceId, batchId));
            List<LegacySpaceMapRecord> activeMaps = spaceMapRepository.findByBatch(workspaceId, batchId).stream()
                .filter(map -> MAP_STATUS_ACTIVE.equals(map.mappingStatus()))
                .toList();
            for (LegacySpaceMapRecord map : activeMaps) {
                try {
                    unitTemplate.executeWithoutResult(unit -> rollbackMapUnit(workspaceId, map, currentUser.id()));
                    tally.rolledBack.add(map.legacyProjectId());
                } catch (RuntimeException exception) {
                    tally.failures.add(projectFailureItem(
                        map.legacyProjectId(), null, PROJECT_FAILURE_ROLLBACK_FAILED, failureDetail(exception)
                    ));
                }
            }
        });

        String status = tally.failures.isEmpty() ? BATCH_STATUS_ROLLED_BACK : BATCH_STATUS_FAILED;
        Map<String, Object> summary = new LinkedHashMap<>(batch.summary());
        Map<String, Object> rollback = new LinkedHashMap<>();
        rollback.put("rolledBackAt", Instant.now().toString());
        rollback.put("spacesRemoved", tally.rolledBack.size());
        rollback.put("rollbackFailures", tally.failures.size());
        summary.put("rollback", rollback);
        // Rollback failures are appended to the original migration failure list so the audit
        // chain keeps orphan/unknown-role/unit failures even after a successful rollback.
        List<Map<String, Object>> mergedFailures = new ArrayList<>(batch.failures());
        mergedFailures.addAll(tally.failures);
        batchRepository.markBatchRolledBack(workspaceId, batchId, status, currentUser.id(), summary, mergedFailures);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("batchId", batchId.toString());
        metadata.put("spacesRemoved", tally.rolledBack.size());
        metadata.put("rollbackFailures", tally.failures.size());
        metadata.put("status", status);
        auditService.log(currentUser, "project_migration.rolled_back", "project_migration", batchId, metadata);
        return requireBatch(workspaceId, batchId);
    }

    private ApplyOutcome applyPlan(
        UUID workspaceId,
        UUID batchId,
        CurrentUser currentUser,
        boolean revalidateResumable,
        int attempt
    ) {
        List<ProjectOutcome> outcomes = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        lockTemplate.executeWithoutResult(transaction -> {
            batchRepository.lockWorkspaceForMigration(workspaceId);
            // Plan and source fingerprint are computed inside the workspace lock so the migration
            // snapshot is serialized against other batches of the same workspace.
            LegacySpaceMappingPlan plan = legacyMappingService.planWorkspaceMigration(currentUser);
            List<SourceFingerprintRow> fingerprintRows = legacyMappingRepository.findSourceFingerprintRows(workspaceId);
            SourceFingerprint fingerprint = fingerprint(workspaceId, fingerprintRows);
            batchRepository.updateSourceFingerprint(
                workspaceId, batchId, fingerprint.watermark(), fingerprint.checksum()
            );
            Map<UUID, SourceFingerprintRow> fingerprintsByProject = fingerprintRows.stream()
                .collect(Collectors.toMap(SourceFingerprintRow::projectId, Function.identity()));
            failures.addAll(memberFailureItems(plan));
            if (revalidateResumable) {
                MigrationBatchRecord fresh = requireBatch(workspaceId, batchId);
                if (!RESUMABLE_STATUSES.contains(fresh.status())) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Only failed or stuck running batches can be resumed"
                    );
                }
            }
            for (ProjectMappingPlan projectPlan : plan.plans()) {
                if (projectPlan.projectFailure() != null) {
                    outcomes.add(ProjectOutcome.skipped(projectPlan));
                    failures.add(projectFailureItem(
                        projectPlan.projectId(), projectPlan.projectKey(),
                        projectPlan.projectFailure().name(),
                        "legacy project workspace is missing"
                    ));
                    continue;
                }
                if (!hasOwnerMapping(projectPlan)) {
                    outcomes.add(ProjectOutcome.skipped(projectPlan));
                    failures.add(projectFailureItem(
                        projectPlan.projectId(), projectPlan.projectKey(), PROJECT_FAILURE_NO_VALID_OWNER,
                        "no valid owner mapping remains; a space requires at least one owner"
                    ));
                    continue;
                }
                try {
                    ProjectOutcome outcome = unitTemplate.execute(
                        unit -> applyProjectUnit(
                            workspaceId, batchId, projectPlan,
                            fingerprintsByProject.get(projectPlan.projectId()), currentUser.id()
                        )
                    );
                    outcomes.add(outcome);
                } catch (RuntimeException exception) {
                    outcomes.add(ProjectOutcome.failed(projectPlan, failureDetail(exception)));
                    failures.add(projectFailureItem(
                        projectPlan.projectId(), projectPlan.projectKey(),
                        PROJECT_FAILURE_UNIT_FAILED, failureDetail(exception)
                    ));
                }
            }
        });
        return new ApplyOutcome(List.copyOf(outcomes), List.copyOf(failures), attempt);
    }

    private ProjectOutcome applyProjectUnit(
        UUID workspaceId,
        UUID batchId,
        ProjectMappingPlan projectPlan,
        SourceFingerprintRow expectedFingerprint,
        UUID actorId
    ) {
        Optional<LegacySpaceMapRecord> activeMap = spaceMapRepository.findActiveByProject(
            workspaceId, projectPlan.projectId()
        );
        if (activeMap.isPresent()) {
            return ProjectOutcome.reused(projectPlan, activeMap.get(), batchId);
        }
        // Legacy writes are not serialized by the workspace migration lock, so each unit
        // re-validates that the project snapshot it is about to apply has not drifted since
        // the plan was computed under the lock.
        Optional<SourceFingerprintRow> currentFingerprint = legacyMappingRepository.findSourceFingerprintRow(
            workspaceId, projectPlan.projectId()
        );
        if (expectedFingerprint == null
            || currentFingerprint.isEmpty()
            || !sameFingerprint(expectedFingerprint, currentFingerprint.get())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Legacy source changed during migration; rerun the batch"
            );
        }
        if (projectPlan.resolvedSpaceKey() == null) {
            throw new IllegalStateException(
                "Mapping plan is stale for project " + projectPlan.projectId() + "; rerun the batch"
            );
        }
        UUID spaceId = projectPlan.deterministicSpaceId();
        spaceRepository.createMigratedSpace(
            workspaceId, spaceId, projectPlan.resolvedSpaceKey(), projectPlan.projectName(),
            null, "private", actorId
        );
        presetReconciliationService.reconcile(workspaceId, spaceId, actorId);
        platformObjectCommands.upsertLink(
            workspaceId, "project_space", spaceId,
            "/project-spaces/" + spaceId, "colla://project-space/" + spaceId, projectPlan.projectName(), actorId
        );
        for (MemberMapping memberMapping : projectPlan.memberMappings()) {
            membershipService.addMigratedMember(
                workspaceId, spaceId, memberMapping.userId(), memberMapping.targetRole(), actorId
            );
        }
        spaceMapRepository.insertMap(
            workspaceId, projectPlan.projectId(), spaceId, 1, batchId,
            memberSetChecksum(projectPlan.memberMappings()), actorId
        );
        return ProjectOutcome.created(projectPlan, spaceId);
    }

    private void rollbackMapUnit(UUID workspaceId, LegacySpaceMapRecord map, UUID actorId) {
        UUID spaceId = map.spaceId();
        membershipRepository.deleteAllForSpace(workspaceId, spaceId);
        spaceMapRepository.markRolledBack(workspaceId, map.id(), actorId);
        spaceRepository.deleteSpace(workspaceId, spaceId);
        platformObjectCommands.removeLink(workspaceId, "project_space", spaceId, actorId);
    }

    private MigrationBatchRecord finalizeApply(
        CurrentUser currentUser,
        UUID batchId,
        ApplyOutcome outcome,
        String auditAction
    ) {
        UUID workspaceId = currentUser.workspaceId();
        String resultChecksum = resultChecksum(workspaceId);
        String status = outcome.projectScopeFailureCount() > 0 ? BATCH_STATUS_FAILED : BATCH_STATUS_COMPLETED;
        MigrationBatchRecord existing = requireBatch(workspaceId, batchId);
        Map<String, Object> summary = applySummary(outcome, existing);
        batchRepository.finalizeBatch(workspaceId, batchId, status, resultChecksum, summary, outcome.failures());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("batchId", batchId.toString());
        metadata.put("status", status);
        metadata.put("attempt", outcome.attempt());
        metadata.put("created", outcome.countOf(OUTCOME_CREATED));
        metadata.put("reused", outcome.countOf(OUTCOME_REUSED));
        metadata.put("skipped", outcome.countOf(OUTCOME_SKIPPED));
        metadata.put("failed", outcome.countOf(OUTCOME_FAILED));
        metadata.put("memberFailures", outcome.memberFailureCount());
        metadata.put("resultChecksum", resultChecksum);
        auditService.log(currentUser, auditAction, "project_migration", batchId, metadata);
        return requireBatch(workspaceId, batchId);
    }

    private void verifyMap(
        UUID workspaceId,
        UUID viewerId,
        LegacySpaceMapRecord map,
        Map<UUID, ProjectMappingPlan> plansByProject,
        List<VerifiedSpaceMatch> matches,
        List<VerificationMismatch> mismatches
    ) {
        UUID projectId = map.legacyProjectId();
        ProjectMappingPlan projectPlan = plansByProject.get(projectId);
        if (projectPlan == null) {
            mismatches.add(new VerificationMismatch(
                projectId, map.spaceId(), MISMATCH_PLAN_MISSING,
                "active legacy project in current mapping plan", "project missing from plan"
            ));
            return;
        }
        Optional<ProjectSpaceSummary> space = spaceRepository.findById(workspaceId, map.spaceId(), viewerId);
        if (space.isEmpty()) {
            mismatches.add(new VerificationMismatch(
                projectId, map.spaceId(), MISMATCH_SPACE_MISSING,
                "existing active space " + map.spaceId() + " in workspace " + workspaceId,
                "space row missing in this workspace"
            ));
            return;
        }
        boolean matched = true;
        if (!"active".equals(space.get().status())) {
            mismatches.add(new VerificationMismatch(
                projectId, map.spaceId(), MISMATCH_SPACE_NOT_ACTIVE, "active", space.get().status()
            ));
            matched = false;
        }
        if (!projectPlan.deterministicSpaceId().equals(map.spaceId())) {
            mismatches.add(new VerificationMismatch(
                projectId, map.spaceId(), MISMATCH_SPACE_ID,
                projectPlan.deterministicSpaceId().toString(), map.spaceId().toString()
            ));
            matched = false;
        }
        Map<UUID, String> expectedMembers = new HashMap<>();
        for (MemberMapping memberMapping : projectPlan.memberMappings()) {
            expectedMembers.put(memberMapping.userId(), memberMapping.targetRole());
        }
        Map<UUID, String> actualMembers = new HashMap<>();
        for (ProjectSpaceMember member : membershipRepository.listMembers(workspaceId, map.spaceId())) {
            if ("active".equals(member.memberStatus())) {
                actualMembers.put(member.userId(), member.roleKey());
            }
        }
        if (!expectedMembers.equals(actualMembers)) {
            mismatches.add(new VerificationMismatch(
                projectId, map.spaceId(), MISMATCH_MEMBER_SET,
                renderMemberSet(expectedMembers), renderMemberSet(actualMembers)
            ));
            matched = false;
        }
        if (matched) {
            matches.add(new VerifiedSpaceMatch(projectId, map.spaceId()));
        }
    }

    private MigrationBatchRecord requireBatch(UUID workspaceId, UUID batchId) {
        return batchRepository.findBatch(workspaceId, batchId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Project space migration batch not found"
            ));
    }

    private void requireRollbackable(MigrationBatchRecord batch) {
        if (batch.dryRun()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dry-run batches hold no migrated data to roll back");
        }
        if (!ROLLBACKABLE_STATUSES.contains(batch.status())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only completed or failed batches can be rolled back"
            );
        }
    }

    private SourceFingerprint fingerprint(UUID workspaceId, List<SourceFingerprintRow> rows) {
        StringBuilder content = new StringBuilder();
        for (SourceFingerprintRow row : rows) {
            if (content.length() > 0) {
                content.append('\n');
            }
            content.append(row.projectId()).append('|')
                .append(row.updatedAt()).append('|')
                .append(row.activeMembers()).append('|')
                .append(row.memberDigest());
        }
        return new SourceFingerprint(
            legacyMappingRepository.findSourceWatermark(workspaceId),
            sha256(content.toString())
        );
    }

    private boolean sameFingerprint(SourceFingerprintRow expected, SourceFingerprintRow current) {
        return expected.updatedAt().equals(current.updatedAt())
            && expected.activeMembers() == current.activeMembers()
            && expected.memberDigest().equals(current.memberDigest());
    }

    private String resultChecksum(UUID workspaceId) {
        StringBuilder content = new StringBuilder();
        for (ActiveSpaceMap map : legacyMappingRepository.findActiveSpaceMaps(workspaceId)) {
            if (content.length() > 0) {
                content.append('\n');
            }
            content.append(map.legacyProjectId()).append("->").append(map.spaceId());
        }
        return sha256(content.toString());
    }

    private String memberSetChecksum(List<MemberMapping> memberMappings) {
        String content = memberMappings.stream()
            .map(mapping -> mapping.userId() + "|" + mapping.legacyRole() + "|" + mapping.targetRole())
            .sorted()
            .collect(Collectors.joining("\n"));
        return sha256(content);
    }

    private String renderMemberSet(Map<UUID, String> members) {
        return members.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .collect(Collectors.joining(","));
    }

    private boolean hasOwnerMapping(ProjectMappingPlan projectPlan) {
        return projectPlan.memberMappings().stream()
            .anyMatch(mapping -> "owner".equals(mapping.targetRole()));
    }

    private int previousAttempt(Map<String, Object> summary) {
        Object attempt = summary.get("attempt");
        return attempt instanceof Number number ? number.intValue() : 1;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> manifestProjects(MigrationBatchRecord batch) {
        Object projects = batch.summary().get("manifestProjects");
        if (!(projects instanceof List<?>)) {
            projects = batch.summary().get("projects");
        }
        return projects instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private boolean manifestOwnedByBatch(Map<String, Object> manifestEntry) {
        Object ownedByBatch = manifestEntry.get("ownedByBatch");
        if (ownedByBatch instanceof Boolean value) {
            return value;
        }
        return OUTCOME_CREATED.equals(String.valueOf(manifestEntry.get("outcome")));
    }

    private Map<String, Object> dryRunSummary(LegacySpaceMappingPlan plan) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", "dry_run");
        summary.put("generatedAt", Instant.now().toString());
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("total", plan.totalProjects());
        counts.put("migratable", plan.migratableProjects());
        counts.put("reuse", plan.reuseCount());
        counts.put("keyConflicts", plan.keyConflictCount());
        counts.put("memberFailures", plan.memberFailureCount());
        counts.put("projectFailures", plan.projectFailureCount());
        summary.put("counts", counts);
        List<Map<String, Object>> projects = new ArrayList<>();
        for (ProjectMappingPlan projectPlan : plan.plans()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", projectPlan.projectId());
            item.put("projectKey", projectPlan.projectKey());
            item.put("projectName", projectPlan.projectName());
            item.put("decision", projectPlan.decision().name());
            item.put("deterministicSpaceId", projectPlan.deterministicSpaceId());
            item.put("resolvedSpaceKey", projectPlan.resolvedSpaceKey());
            item.put("resolvedSpaceId", projectPlan.resolvedSpaceId());
            item.put("memberMappings", memberMappingItems(projectPlan.memberMappings()));
            item.put("memberFailures", projectPlan.memberFailures().stream()
                .map(failure -> memberFailureItem(projectPlan.projectId(), projectPlan.projectKey(), failure))
                .toList());
            item.put("projectFailure", projectPlan.projectFailure() == null ? null : projectPlan.projectFailure().name());
            projects.add(item);
        }
        summary.put("projects", projects);
        return summary;
    }

    private Map<String, Object> applySummary(ApplyOutcome outcome, MigrationBatchRecord existingBatch) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", "apply");
        summary.put("resumed", outcome.attempt() > 1);
        summary.put("attempt", outcome.attempt());
        summary.put("appliedAt", Instant.now().toString());
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("total", outcome.outcomes().size());
        counts.put("created", outcome.countOf(OUTCOME_CREATED));
        counts.put("reused", outcome.countOf(OUTCOME_REUSED));
        counts.put("skipped", outcome.countOf(OUTCOME_SKIPPED));
        counts.put("failed", outcome.countOf(OUTCOME_FAILED));
        counts.put("memberFailures", outcome.memberFailureCount());
        summary.put("counts", counts);
        List<Map<String, Object>> projects = new ArrayList<>();
        for (ProjectOutcome projectOutcome : outcome.outcomes()) {
            projects.add(projectOutcomeItem(projectOutcome));
        }
        summary.put("projects", projects);
        summary.put("manifestProjects", mergeManifest(existingBatch, outcome.outcomes()));
        return summary;
    }

    private List<Map<String, Object>> mergeManifest(
        MigrationBatchRecord existingBatch,
        List<ProjectOutcome> currentOutcomes
    ) {
        Map<UUID, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> existing : manifestProjects(existingBatch)) {
            UUID projectId = UUID.fromString(String.valueOf(existing.get("projectId")));
            merged.put(projectId, new LinkedHashMap<>(existing));
        }
        for (ProjectOutcome current : currentOutcomes) {
            Map<String, Object> previous = merged.get(current.projectId());
            Map<String, Object> item = projectOutcomeItem(current);
            boolean ownedByBatch = current.ownedByBatch()
                || (previous != null && manifestOwnedByBatch(previous));
            if (ownedByBatch) {
                item.put("outcome", OUTCOME_CREATED);
                item.put("ownedByBatch", true);
                preserveManifestValue(item, previous, "spaceId");
                preserveManifestValue(item, previous, "spaceKey");
                preserveManifestValue(item, previous, "members");
                item.put("reason", null);
            }
            merged.put(current.projectId(), item);
        }
        return List.copyOf(merged.values());
    }

    private Map<String, Object> projectOutcomeItem(ProjectOutcome projectOutcome) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("projectId", projectOutcome.projectId());
        item.put("projectKey", projectOutcome.projectKey());
        item.put("outcome", projectOutcome.outcome());
        item.put("ownedByBatch", projectOutcome.ownedByBatch());
        item.put("spaceId", projectOutcome.spaceId());
        item.put("spaceKey", projectOutcome.spaceKey());
        item.put("members", projectOutcome.members());
        item.put("reason", projectOutcome.reason());
        return item;
    }

    private void preserveManifestValue(
        Map<String, Object> target,
        Map<String, Object> previous,
        String key
    ) {
        if (target.get(key) == null && previous != null && previous.get(key) != null) {
            target.put(key, previous.get(key));
        }
    }

    private List<Map<String, Object>> planFailureItems(LegacySpaceMappingPlan plan) {
        List<Map<String, Object>> failures = new ArrayList<>(memberFailureItems(plan));
        for (ProjectMappingPlan projectPlan : plan.plans()) {
            if (projectPlan.projectFailure() != null) {
                failures.add(projectFailureItem(
                    projectPlan.projectId(), projectPlan.projectKey(),
                    projectPlan.projectFailure().name(), "legacy project workspace is missing"
                ));
            }
        }
        return failures;
    }

    private List<Map<String, Object>> memberFailureItems(LegacySpaceMappingPlan plan) {
        List<Map<String, Object>> failures = new ArrayList<>();
        for (ProjectMappingPlan projectPlan : plan.plans()) {
            for (MemberFailure failure : projectPlan.memberFailures()) {
                failures.add(memberFailureItem(projectPlan.projectId(), projectPlan.projectKey(), failure));
            }
        }
        return failures;
    }

    private List<Map<String, Object>> memberMappingItems(List<MemberMapping> memberMappings) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (MemberMapping mapping : memberMappings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", mapping.userId());
            item.put("legacyRole", mapping.legacyRole());
            item.put("targetRole", mapping.targetRole());
            item.put("explanation", mapping.explanation());
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> memberFailureItem(UUID projectId, String projectKey, MemberFailure failure) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("scope", FAILURE_SCOPE_MEMBER);
        item.put("projectId", projectId);
        item.put("projectKey", projectKey);
        item.put("userId", failure.userId());
        item.put("legacyRole", failure.legacyRole());
        item.put("reason", failure.reason().name());
        item.put("detail", failure.detail());
        return item;
    }

    private Map<String, Object> projectFailureItem(UUID projectId, String projectKey, String reason, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("scope", FAILURE_SCOPE_PROJECT);
        item.put("projectId", projectId);
        item.put("projectKey", projectKey);
        item.put("reason", reason);
        item.put("detail", detail);
        return item;
    }

    private String failureDetail(RuntimeException exception) {
        String message = exception.getMessage();
        String firstLine = message == null ? "" : message.split("\n")[0];
        String detail = exception.getClass().getSimpleName() + (firstLine.isEmpty() ? "" : ": " + firstLine);
        return detail.length() > MAX_FAILURE_DETAIL_LENGTH
            ? detail.substring(0, MAX_FAILURE_DETAIL_LENGTH)
            : detail;
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record SourceFingerprint(Instant watermark, String checksum) {
    }

    private record ProjectOutcome(
        UUID projectId,
        String projectKey,
        String outcome,
        UUID spaceId,
        String spaceKey,
        Integer members,
        String reason,
        boolean ownedByBatch
    ) {
        static ProjectOutcome created(ProjectMappingPlan plan, UUID spaceId) {
            return new ProjectOutcome(
                plan.projectId(), plan.projectKey(), OUTCOME_CREATED, spaceId,
                plan.resolvedSpaceKey(), plan.memberMappings().size(), null, true
            );
        }

        static ProjectOutcome reused(ProjectMappingPlan plan, LegacySpaceMapRecord map, UUID batchId) {
            return new ProjectOutcome(
                plan.projectId(), plan.projectKey(), OUTCOME_REUSED, map.spaceId(), null, null, null,
                batchId.equals(map.batchId())
            );
        }

        static ProjectOutcome skipped(ProjectMappingPlan plan) {
            String reason = plan.projectFailure() != null ? plan.projectFailure().name() : PROJECT_FAILURE_NO_VALID_OWNER;
            return new ProjectOutcome(
                plan.projectId(), plan.projectKey(), OUTCOME_SKIPPED, null, null, null, reason, false
            );
        }

        static ProjectOutcome failed(ProjectMappingPlan plan, String detail) {
            return new ProjectOutcome(
                plan.projectId(), plan.projectKey(), OUTCOME_FAILED, null, null, null, detail, false
            );
        }
    }

    private record ApplyOutcome(
        List<ProjectOutcome> outcomes,
        List<Map<String, Object>> failures,
        int attempt
    ) {
        long countOf(String outcome) {
            return outcomes.stream().filter(item -> item.outcome().equals(outcome)).count();
        }

        long memberFailureCount() {
            return failures.stream().filter(item -> FAILURE_SCOPE_MEMBER.equals(item.get("scope"))).count();
        }

        long projectScopeFailureCount() {
            return failures.stream().filter(item -> FAILURE_SCOPE_PROJECT.equals(item.get("scope"))).count();
        }
    }

    private static final class RollbackTally {
        private final List<UUID> rolledBack = new ArrayList<>();
        private final List<Map<String, Object>> failures = new ArrayList<>();
    }
}
