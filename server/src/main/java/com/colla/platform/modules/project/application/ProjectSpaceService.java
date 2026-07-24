package com.colla.platform.modules.project.application;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.permission.contract.ProjectAuthorization;
import com.colla.platform.modules.platform.contract.PlatformObjectCommands;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.LegacySpaceMapRecord;
import com.colla.platform.modules.project.domain.ProjectSpaceMigrationModels.LegacySpaceResolution;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceStatus;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceVisibility;
import com.colla.platform.modules.project.infrastructure.ProjectLegacySpaceMapRepository;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectSpaceService {
    private static final int MAX_GOVERNANCE_PAGE_SIZE = 200;

    private final ProjectSpaceRepository projectSpaceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectLegacySpaceMapRepository legacySpaceMapRepository;
    private final PlatformObjectCommands platformObjectCommands;
    private final ProjectAuthorization permissionService;
    private final AuditLog auditService;
    private final WorkItemTypePresetReconciliationService presetReconciliationService;

    public ProjectSpaceService(
        ProjectSpaceRepository projectSpaceRepository,
        ProjectRepository projectRepository,
        ProjectLegacySpaceMapRepository legacySpaceMapRepository,
        PlatformObjectCommands platformObjectCommands,
        ProjectAuthorization permissionService,
        AuditLog auditService,
        WorkItemTypePresetReconciliationService presetReconciliationService
    ) {
        this.projectSpaceRepository = projectSpaceRepository;
        this.projectRepository = projectRepository;
        this.legacySpaceMapRepository = legacySpaceMapRepository;
        this.platformObjectCommands = platformObjectCommands;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.presetReconciliationService = presetReconciliationService;
    }

    public List<ProjectSpaceSummary> listVisible(CurrentUser currentUser) {
        return projectSpaceRepository.listVisible(currentUser.workspaceId(), currentUser.id());
    }

    public ProjectSpaceSummary getVisible(CurrentUser currentUser, UUID spaceId) {
        ProjectSpaceSummary space = requireSpace(currentUser, spaceId);
        if (!space.isMember() && ("private".equals(space.visibility()) || "archived".equals(space.status()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project space not found");
        }
        return space;
    }

    @Transactional
    public ProjectSpaceSummary create(
        CurrentUser currentUser,
        String spaceKey,
        String name,
        String description,
        String visibility
    ) {
        permissionService.requireCreateProjects(currentUser);
        String normalizedName = normalizeName(name);
        String normalizedKey = normalizeKey(spaceKey, normalizedName);
        String normalizedDescription = normalizeDescription(description);
        String normalizedVisibility = visibility(visibility);
        UUID spaceId;
        try {
            spaceId = projectSpaceRepository.createSpaceWithOwner(
                currentUser.workspaceId(),
                normalizedKey,
                normalizedName,
                normalizedDescription,
                normalizedVisibility,
                currentUser.id()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project space key already exists", exception);
        }
        presetReconciliationService.reconcile(currentUser.workspaceId(), spaceId, currentUser.id());
        ProjectSpaceSummary created = requireSpace(currentUser, spaceId);
        registerObject(created, currentUser.id());
        auditService.log(currentUser, "project_space.created", "project_space", spaceId, Map.of(
            "spaceKey", created.spaceKey(),
            "status", created.status(),
            "visibility", created.visibility(),
            "ownerId", currentUser.id().toString()
        ));
        return created;
    }

    public ProjectSpaceSummary getSettings(CurrentUser currentUser, UUID spaceId) {
        ProjectSpaceSummary space = requireSpace(currentUser, spaceId);
        requireSpaceManager(space);
        return space;
    }

    @Transactional
    public ProjectSpaceSummary updateSettings(
        CurrentUser currentUser,
        UUID spaceId,
        String name,
        String description,
        String visibility
    ) {
        ProjectSpaceSummary before = getSettings(currentUser, spaceId);
        if (ProjectSpaceStatus.archived.name().equals(before.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived project spaces must be restored before editing");
        }
        String nextName = normalizeName(name);
        String nextDescription = normalizeDescription(description);
        String nextVisibility = visibility(visibility);
        try {
            projectSpaceRepository.updateSpace(
                currentUser.workspaceId(), spaceId, nextName, nextDescription, nextVisibility, currentUser.id()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project space update conflicts with current state", exception);
        }
        ProjectSpaceSummary after = requireSpace(currentUser, spaceId);
        registerObject(after, currentUser.id());
        auditService.log(currentUser, "project_space.updated", "project_space", spaceId, changedMetadata(before, after, "space_settings"));
        return after;
    }

    @Transactional
    public ProjectSpaceSummary transitionSettings(CurrentUser currentUser, UUID spaceId, String targetStatus) {
        ProjectSpaceSummary before = getSettings(currentUser, spaceId);
        return transition(currentUser, before, targetStatus, "space_settings");
    }

    public List<ProjectSpaceSummary> listGovernance(
        CurrentUser currentUser,
        String status,
        String visibility,
        boolean includeArchived,
        int limit,
        int offset
    ) {
        permissionService.requireManageProjects(currentUser);
        String normalizedStatus = optionalStatus(status);
        String normalizedVisibility = optionalVisibility(visibility);
        int safeLimit = Math.max(1, Math.min(limit, MAX_GOVERNANCE_PAGE_SIZE));
        int safeOffset = Math.max(0, offset);
        return projectSpaceRepository.listGovernance(
            currentUser.workspaceId(), currentUser.id(), normalizedStatus, normalizedVisibility,
            includeArchived, safeLimit, safeOffset
        );
    }

    public ProjectSpaceSummary getGovernance(CurrentUser currentUser, UUID spaceId) {
        permissionService.requireManageProjects(currentUser);
        return requireSpace(currentUser, spaceId);
    }

    @Transactional
    public ProjectSpaceSummary transitionGovernance(CurrentUser currentUser, UUID spaceId, String targetStatus) {
        ProjectSpaceSummary before = getGovernance(currentUser, spaceId);
        return transition(currentUser, before, targetStatus, "enterprise_governance");
    }

    public boolean hasContentAccess(ProjectSpaceSummary space) {
        return space.isMember();
    }

    /**
     * Resolves a legacy project deep link to its migrated project space without leaking space
     * metadata. Callers only learn the space id when the space is visible to them under the same
     * visibility rules used by {@link #getVisible}; anything else reports an opaque status.
     */
    public LegacySpaceResolution resolveLegacySpace(CurrentUser currentUser, UUID legacyProjectId) {
        UUID workspaceId = currentUser.workspaceId();
        if (projectRepository.findProjectById(workspaceId, legacyProjectId).isEmpty()) {
            return new LegacySpaceResolution("unmigrated", null);
        }
        Optional<LegacySpaceMapRecord> map = legacySpaceMapRepository.findByProject(workspaceId, legacyProjectId);
        if (map.isEmpty()) {
            return new LegacySpaceResolution("unmigrated", null);
        }
        if (!"active".equals(map.get().mappingStatus())) {
            return new LegacySpaceResolution("failed", null);
        }
        Optional<ProjectSpaceSummary> space = projectSpaceRepository.findById(
            workspaceId, map.get().spaceId(), currentUser.id()
        );
        if (space.isEmpty()) {
            return new LegacySpaceResolution("unavailable", null);
        }
        ProjectSpaceSummary summary = space.get();
        boolean visible = summary.isMember()
            || (!"private".equals(summary.visibility()) && !"archived".equals(summary.status()));
        if (!visible) {
            return new LegacySpaceResolution("unavailable", null);
        }
        return new LegacySpaceResolution("mapped", summary.id());
    }

    private ProjectSpaceSummary transition(CurrentUser currentUser, ProjectSpaceSummary before, String targetStatus, String source) {
        ProjectSpaceStatus current = ProjectSpaceStatus.parse(before.status());
        ProjectSpaceStatus target;
        try {
            target = ProjectSpaceStatus.parse(targetStatus);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        if (!current.canTransitionTo(target)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Project space cannot transition from " + current + " to " + target
            );
        }
        if (current == target) {
            return before;
        }
        projectSpaceRepository.transitionSpace(currentUser.workspaceId(), before.id(), target.name(), currentUser.id());
        ProjectSpaceSummary after = requireSpace(currentUser, before.id());
        registerObject(after, currentUser.id());
        auditService.log(
            currentUser,
            "project_space." + transitionAction(target),
            "project_space",
            before.id(),
            changedMetadata(before, after, source)
        );
        return after;
    }

    private String transitionAction(ProjectSpaceStatus target) {
        return switch (target) {
            case active -> "restored";
            case disabled -> "disabled";
            case archived -> "archived";
        };
    }

    private ProjectSpaceSummary requireSpace(CurrentUser currentUser, UUID spaceId) {
        return projectSpaceRepository.findById(currentUser.workspaceId(), spaceId, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project space not found"));
    }

    private void requireSpaceManager(ProjectSpaceSummary space) {
        if (!space.canManage()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Project space owner or admin role required");
        }
    }

    private void registerObject(ProjectSpaceSummary space, UUID actorId) {
        platformObjectCommands.upsertLink(
            space.workspaceId(),
            "project_space",
            space.id(),
            "/project-spaces/" + space.id(),
            "colla://project-space/" + space.id(),
            space.name(),
            actorId
        );
    }

    private Map<String, Object> changedMetadata(ProjectSpaceSummary before, ProjectSpaceSummary after, String source) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", source);
        metadata.put("previousStatus", before.status());
        metadata.put("currentStatus", after.status());
        metadata.put("previousVisibility", before.visibility());
        metadata.put("currentVisibility", after.visibility());
        metadata.put("previousName", before.name());
        metadata.put("currentName", after.name());
        metadata.put("previousVersion", before.version());
        metadata.put("currentVersion", after.version());
        return metadata;
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project space name is required");
        }
        if (normalized.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project space name is too long");
        }
        return normalized;
    }

    private String normalizeKey(String value, String name) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            normalized = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
            if (normalized.isEmpty()) {
                normalized = "space";
            }
            normalized = normalized.substring(0, Math.min(normalized.length(), 48))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (normalized.length() > 64 || !normalized.matches("[a-z0-9][a-z0-9-]*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project space key must use lowercase letters, numbers, and hyphens");
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project space description is too long");
        }
        return normalized;
    }

    private String visibility(String value) {
        try {
            return ProjectSpaceVisibility.parse(value == null || value.isBlank() ? "private" : value).value();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private String optionalVisibility(String value) {
        return value == null || value.isBlank() ? "" : visibility(value);
    }

    private String optionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return ProjectSpaceStatus.parse(value).name();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
