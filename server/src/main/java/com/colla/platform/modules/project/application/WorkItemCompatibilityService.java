package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.permission.contract.ProjectAuthorization;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.CompatibilityWorkItem;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.CutoverState;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyProfile;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.LegacyWorkItemMap;
import com.colla.platform.modules.project.domain.WorkItemCompatibilityModels.ReadStage;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemCompatibilityRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemCompatibilityService {
    private final WorkItemCompatibilityRepository compatibilityRepository;
    private final ProjectRepository projectRepository;
    private final WorkItemService workItemService;
    private final ProjectAuthorization authorization;
    private final AuditLog auditLog;
    private final ObjectMapper objectMapper;

    public WorkItemCompatibilityService(
        WorkItemCompatibilityRepository compatibilityRepository,
        ProjectRepository projectRepository,
        WorkItemService workItemService,
        ProjectAuthorization authorization,
        AuditLog auditLog,
        ObjectMapper objectMapper
    ) {
        this.compatibilityRepository = compatibilityRepository;
        this.projectRepository = projectRepository;
        this.workItemService = workItemService;
        this.authorization = authorization;
        this.auditLog = auditLog;
        this.objectMapper = objectMapper;
    }

    public LegacyProfile profile(CurrentUser user) {
        authorization.requireManageProjects(user);
        return compatibilityRepository.profile(user.workspaceId());
    }

    @Transactional
    public CompatibilityWorkItem resolveLegacyIssue(CurrentUser user, UUID issueId) {
        long started = System.nanoTime();
        IssueSummary legacy = projectRepository.findIssue(user.workspaceId(), issueId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item is not available"));
        if (!projectRepository.isProjectMember(user.workspaceId(), legacy.projectId(), user.id())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Work item is not available");
        }
        LegacyWorkItemMap mapping = compatibilityRepository
            .findMap(user.workspaceId(), "issue", issueId)
            .orElse(null);
        UUID spaceId = mapping == null
            ? compatibilityRepository.findLegacyProjectSpace(user.workspaceId(), legacy.projectId()).orElse(null)
            : mapping.spaceId();
        CutoverState cutover = compatibilityRepository.findCutover(user.workspaceId(), spaceId)
            .orElseGet(() -> CutoverState.legacy(spaceId));
        CompatibilityWorkItem legacyView = legacy(legacy, spaceId, mapping);
        if (cutover.canonicalReadPreferred()) {
            if (mapping == null) {
                throw failure(
                    "CANONICAL_MAPPING_MISSING",
                    "Canonical mapping is required at the current cutover stage"
                );
            }
            CompatibilityWorkItem canonical = canonical(
                workItemService.get(user, mapping.spaceId(), mapping.workItemId()), mapping
            );
            recordSample(user, mapping, "canonical", legacyView, canonical, started);
            return canonical;
        }
        if (cutover.readStage() == ReadStage.SHADOW && mapping != null) {
            try {
                CompatibilityWorkItem canonical = canonical(
                    workItemService.get(user, mapping.spaceId(), mapping.workItemId()), mapping
                );
                recordSample(user, mapping, "legacy", legacyView, canonical, started);
            } catch (RuntimeException exception) {
                compatibilityRepository.recordShadowSample(
                    user.workspaceId(), spaceId, "issue", issueId, "legacy",
                    fingerprint(legacyView), null, "shadow_error", elapsed(started), null
                );
            }
        }
        return legacyView;
    }

    public String canonicalLocation(CurrentUser user, UUID issueId) {
        IssueSummary issue = projectRepository.findIssue(user.workspaceId(), issueId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item is not available"));
        if (!projectRepository.isProjectMember(user.workspaceId(), issue.projectId(), user.id())) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Work item is not available");
        }
        return compatibilityRepository.findMap(user.workspaceId(), "issue", issueId)
            .map(LegacyWorkItemMap::canonicalLocation)
            .orElse("/issues/" + issueId);
    }

    @Transactional
    public CutoverState changeCutover(
        CurrentUser user,
        UUID spaceId,
        ReadStage readStage,
        boolean legacyWriteEnabled,
        boolean killSwitchEnabled,
        long expectedVersion
    ) {
        authorization.requireManageProjects(user);
        if (readStage == null) {
            throw failure("INVALID_CUTOVER_STAGE", "Cutover stage is required");
        }
        if (readStage.ordinal() >= ReadStage.CANONICAL_WRITE.ordinal() && legacyWriteEnabled) {
            throw failure("INVALID_CUTOVER_STATE", "Legacy writes must be closed before canonical write");
        }
        CutoverState result;
        try {
            result = compatibilityRepository.changeCutover(
                user.workspaceId(),
                spaceId,
                readStage.name().toLowerCase(),
                legacyWriteEnabled,
                killSwitchEnabled,
                expectedVersion,
                user.id()
            );
        } catch (IllegalStateException exception) {
            throw failure("CUTOVER_VERSION_CONFLICT", "Cutover state changed concurrently", exception);
        }
        auditLog.log(user, "work_item.cutover.changed", "project_space",
            spaceId == null ? user.workspaceId() : spaceId, Map.of(
                "readStage", result.readStage().name().toLowerCase(),
                "legacyWriteEnabled", result.legacyWriteEnabled(),
                "killSwitchEnabled", result.killSwitchEnabled(),
                "version", result.version()
            ));
        return result;
    }

    public void requireLegacyWorkspaceWrite(CurrentUser user) {
        requireLegacyWrite(user, null, "/project-spaces");
    }

    public void requireLegacyProjectWrite(CurrentUser user, UUID projectId) {
        UUID spaceId = compatibilityRepository.findLegacyProjectSpace(user.workspaceId(), projectId).orElse(null);
        requireLegacyWrite(user, spaceId, spaceId == null ? "/projects/" + projectId : "/project-spaces/" + spaceId);
    }

    public void requireLegacyIssueWrite(CurrentUser user, UUID issueId) {
        UUID projectId = compatibilityRepository.findIssueProject(user.workspaceId(), issueId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Work item is not available"));
        LegacyWorkItemMap map = compatibilityRepository.findMap(user.workspaceId(), "issue", issueId).orElse(null);
        UUID spaceId = map == null
            ? compatibilityRepository.findLegacyProjectSpace(user.workspaceId(), projectId).orElse(null)
            : map.spaceId();
        requireLegacyWrite(
            user,
            spaceId,
            map == null ? "/issues/" + issueId : map.canonicalLocation()
        );
    }

    private void requireLegacyWrite(CurrentUser user, UUID spaceId, String canonicalLocation) {
        CutoverState state = compatibilityRepository.findCutover(user.workspaceId(), spaceId)
            .orElseGet(() -> CutoverState.legacy(spaceId));
        if (!state.legacyWriteEnabled()) {
            throw new LegacyWriteClosedException(canonicalLocation);
        }
    }

    private CompatibilityWorkItem legacy(
        IssueSummary issue,
        UUID spaceId,
        LegacyWorkItemMap mapping
    ) {
        ObjectNode fields = objectMapper.createObjectNode();
        fields.put("description", issue.description());
        fields.put("priority", issue.priority());
        if (issue.assigneeId() != null) {
            fields.put("assigneeId", issue.assigneeId().toString());
        }
        if (issue.dueAt() != null) {
            fields.put("dueAt", issue.dueAt().toString());
        }
        return new CompatibilityWorkItem(
            "legacy",
            issue.id(),
            mapping == null ? null : mapping.workItemId(),
            spaceId,
            issue.issueKey(),
            issue.issueType(),
            issue.title(),
            issue.status(),
            fields,
            mapping == null ? null : mapping.canonicalLocation(),
            issue.updatedAt()
        );
    }

    private CompatibilityWorkItem canonical(WorkItemView view, LegacyWorkItemMap mapping) {
        var item = view.item();
        return new CompatibilityWorkItem(
            "canonical",
            mapping.sourceId(),
            item.id(),
            item.spaceId(),
            item.displayKey(),
            item.typeKey(),
            item.title(),
            item.status(),
            view.fieldValues(),
            mapping.canonicalLocation(),
            item.updatedAt()
        );
    }

    private void recordSample(
        CurrentUser user,
        LegacyWorkItemMap mapping,
        String primary,
        CompatibilityWorkItem legacy,
        CompatibilityWorkItem canonical,
        long started
    ) {
        String legacyHash = fingerprint(legacy);
        String canonicalHash = fingerprint(canonical);
        compatibilityRepository.recordShadowSample(
            user.workspaceId(),
            mapping.spaceId(),
            mapping.sourceType(),
            mapping.sourceId(),
            primary,
            legacyHash,
            canonicalHash,
            legacyHash.equals(canonicalHash) ? "match" : "drift",
            elapsed(started),
            0
        );
    }

    private String fingerprint(CompatibilityWorkItem value) {
        try {
            JsonNode safe = objectMapper.valueToTree(Map.of(
                "displayKey", value.displayKey(),
                "typeKey", value.typeKey(),
                "title", value.title(),
                "status", value.status(),
                "fieldValues", value.fieldValues()
            ));
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(safe).getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to fingerprint compatibility view", exception);
        }
    }

    private int elapsed(long started) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000L);
    }

    public static final class LegacyWriteClosedException extends RuntimeException {
        private final String canonicalLocation;

        public LegacyWriteClosedException(String canonicalLocation) {
            super("Legacy write endpoint is closed");
            this.canonicalLocation = canonicalLocation;
        }

        public String canonicalLocation() {
            return canonicalLocation;
        }
    }
}
