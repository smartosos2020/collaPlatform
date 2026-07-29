package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.permission.contract.ProjectAuthorization;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditFinding;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditObservation;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacyAuditSnapshot;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.LegacySurface;
import com.colla.platform.modules.project.domain.LegacyExitAuditModels.RemovalDecision;
import com.colla.platform.modules.project.infrastructure.LegacyExitAuditRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegacyExitAuditService {
    public static final String INVENTORY_VERSION = "s21-m1-v1";
    private static final Set<String> DECISIONS = Set.of("remove", "retain_history", "blocked");
    private static final List<LegacySurface> SURFACES = List.of(
        surface("api.issues", "api", "project", "read_write", true,
            "M2", "ProjectController issue endpoints and fixed Issue DTOs"),
        surface("web.issues", "web", "project", "read_write", true,
            "M2", "/issues route, ProjectsPage issue board and API client"),
        surface("workspace.issue-summary", "application", "workspace", "read", true,
            "M2", "workspace dashboard IssueSummary projection"),
        surface("search.issue-index", "infrastructure", "search", "read", true,
            "M2", "legacy issue search indexing and result object type"),
        surface("realtime.issue", "event", "event", "read", true,
            "M2", "issue.changed/invalidated reconciliation"),
        surface("platform-object.issue", "contract", "platform-object", "read", true,
            "M2", "legacy issue object resolver and deep link"),
        surface("messenger.convert-issue", "application", "messenger", "write", true,
            "M2", "message-to-issue command and fixed issue type"),
        surface("database.projects-issues", "database", "project", "read_only", false,
            "M2", "legacy source tables pending an explicit retention decision"),
        surface("migration.maps-provenance", "database", "project", "history", false,
            "retain", "immutable maps, manifests, provenance and verification evidence"),
        surface("migration.compat-resolver", "application", "project", "read", true,
            "M2", "temporary map-backed location and compatibility resolver")
    );

    private final LegacyExitAuditRepository repository;
    private final ProjectAuthorization authorization;
    private final AuditLog auditLog;

    public LegacyExitAuditService(
        LegacyExitAuditRepository repository,
        ProjectAuthorization authorization,
        AuditLog auditLog
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.auditLog = auditLog;
    }

    @Transactional
    public LegacyAuditSnapshot createSnapshot(CurrentUser user) {
        authorization.requireManageProjects(user);
        LegacyAuditObservation observation = repository.observe(user.workspaceId());
        LegacyAuditSnapshot snapshot = repository.insertSnapshot(
            user.workspaceId(), INVENTORY_VERSION, observation, SURFACES,
            findings(observation), user.id()
        );
        auditLog.log(user, "project_legacy_audit.snapshot_created", "project_legacy_audit",
            snapshot.id(), Map.of(
                "inventoryVersion", INVENTORY_VERSION,
                "status", snapshot.status(),
                "findingCount", snapshot.findings().size(),
                "surfaceCount", snapshot.surfaces().size(),
                "sourceFingerprint", snapshot.sourceFingerprint()
            ));
        return snapshot;
    }

    public List<LegacyAuditSnapshot> list(CurrentUser user) {
        authorization.requireManageProjects(user);
        return repository.listSnapshots(user.workspaceId(), 20);
    }

    public LegacyAuditSnapshot get(CurrentUser user, UUID snapshotId) {
        authorization.requireManageProjects(user);
        return repository.findSnapshot(user.workspaceId(), snapshotId)
            .orElseThrow(() -> failure("LEGACY_AUDIT_NOT_FOUND", "Legacy audit snapshot is not available"));
    }

    @Transactional
    public RemovalDecision decide(
        CurrentUser user,
        UUID snapshotId,
        String surfaceKey,
        String decision,
        String reason,
        String requestId
    ) {
        authorization.requireManageProjects(user);
        LegacyAuditSnapshot snapshot = get(user, snapshotId);
        if (SURFACES.stream().noneMatch(value -> value.key().equals(surfaceKey))) {
            throw failure("INVALID_LEGACY_SURFACE", "Legacy surface is not registered");
        }
        if (!DECISIONS.contains(decision)) {
            throw failure("INVALID_REMOVAL_DECISION", "Removal decision is not registered");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() < 10 || normalizedReason.length() > 1000) {
            throw failure("INVALID_REMOVAL_REASON", "Removal decision reason must contain 10 to 1000 characters");
        }
        String normalizedRequestId = requestId == null ? "" : requestId.trim();
        if (normalizedRequestId.length() < 8 || normalizedRequestId.length() > 160) {
            throw failure("INVALID_REQUEST_ID", "Removal decision request id must contain 8 to 160 characters");
        }
        String requestHash = hash(snapshot.id() + "|" + surfaceKey + "|" + decision + "|" + normalizedReason);
        RemovalDecision existing = repository.findDecisionByRequest(user.workspaceId(), normalizedRequestId)
            .orElse(null);
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw failure("REQUEST_REPLAY_CONFLICT", "Request id was already used with another decision");
            }
            return existing.replayedCopy();
        }
        RemovalDecision result = repository.insertDecision(
            user.workspaceId(), snapshot.id(), surfaceKey, decision, normalizedReason,
            normalizedRequestId, requestHash, user.id()
        );
        auditLog.log(user, "project_legacy_audit.removal_decided", "project_legacy_audit",
            snapshot.id(), Map.of(
                "surfaceKey", surfaceKey,
                "decision", decision,
                "requestId", normalizedRequestId
            ));
        return result;
    }

    public List<LegacySurface> surfaces() {
        return SURFACES;
    }

    private List<LegacyAuditFinding> findings(LegacyAuditObservation observation) {
        List<LegacyAuditFinding> result = new ArrayList<>();
        add(result, observation, "unmappedProjects", "migration_coverage", "blocking");
        add(result, observation, "unmappedIssues", "migration_coverage", "blocking");
        add(result, observation, "danglingMaps", "referential_integrity", "blocking");
        add(result, observation, "mismatchedVerifications", "consistency", "blocking");
        add(result, observation, "shadowDrifts", "read_parity", "warning");
        add(result, observation, "migrationFailures", "migration_history", "warning");
        add(result, observation, "legacyWriteScopes", "legacy_usage", "warning");
        add(result, observation, "legacyReadScopes", "legacy_usage", "info");
        if (result.isEmpty()) {
            result.add(new LegacyAuditFinding(
                UUID.randomUUID(), "audit_complete", "consistency", "info", "resolved", 0,
                Map.of("message", "No migration consistency finding was observed"), Instant.now()
            ));
        }
        return List.copyOf(result);
    }

    private void add(
        List<LegacyAuditFinding> findings,
        LegacyAuditObservation observation,
        String totalKey,
        String category,
        String severity
    ) {
        long count = observation.totals().getOrDefault(totalKey, 0L);
        if (count == 0) {
            return;
        }
        findings.add(new LegacyAuditFinding(
            UUID.randomUUID(), totalKey, category, severity, "observed", count,
            Map.of("metric", totalKey, "count", count), Instant.now()
        ));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static LegacySurface surface(
        String key,
        String layer,
        String owner,
        String accessMode,
        boolean userVisible,
        String removalStage,
        String evidence
    ) {
        return new LegacySurface(key, layer, owner, accessMode, userVisible, removalStage, evidence);
    }
}
