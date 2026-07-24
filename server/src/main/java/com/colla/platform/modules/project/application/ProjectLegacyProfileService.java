package com.colla.platform.modules.project.application;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.permission.contract.ProjectAuthorization;
import com.colla.platform.modules.project.domain.ProjectLegacyProfileModels.ProjectLegacyProfile;
import com.colla.platform.modules.project.infrastructure.ProjectLegacyProfileRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectLegacyProfileService {
    static final int MAX_FINDINGS_PER_CATEGORY = 200;

    private final ProjectLegacyProfileRepository projectLegacyProfileRepository;
    private final ProjectAuthorization permissionService;
    private final AuditLog auditService;

    public ProjectLegacyProfileService(
        ProjectLegacyProfileRepository projectLegacyProfileRepository,
        ProjectAuthorization permissionService,
        AuditLog auditService
    ) {
        this.projectLegacyProfileRepository = projectLegacyProfileRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public ProjectLegacyProfile generateProfile(CurrentUser currentUser) {
        permissionService.requireManageProjects(currentUser);
        UUID workspaceId = currentUser.workspaceId();
        ProjectLegacyProfile profile = new ProjectLegacyProfile(
            workspaceId,
            Instant.now(),
            projectLegacyProfileRepository.summarizeTotals(workspaceId),
            projectLegacyProfileRepository.summarizeRoleDistribution(workspaceId),
            projectLegacyProfileRepository.findOrphanMembers(workspaceId, MAX_FINDINGS_PER_CATEGORY),
            projectLegacyProfileRepository.findIllegalRoles(workspaceId, MAX_FINDINGS_PER_CATEGORY),
            projectLegacyProfileRepository.findDuplicateOwners(workspaceId, MAX_FINDINGS_PER_CATEGORY),
            projectLegacyProfileRepository.findSharedConversations(workspaceId, MAX_FINDINGS_PER_CATEGORY),
            projectLegacyProfileRepository.findProjectsWithoutOwner(workspaceId, MAX_FINDINGS_PER_CATEGORY),
            projectLegacyProfileRepository.findImDrifts(workspaceId, MAX_FINDINGS_PER_CATEGORY),
            projectLegacyProfileRepository.findMissingConversations(workspaceId, MAX_FINDINGS_PER_CATEGORY)
        );
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("activeProjects", profile.totals().activeProjects());
        metadata.put("archivedProjects", profile.totals().archivedProjects());
        metadata.put("activeMembers", profile.totals().activeMembers());
        metadata.put("archivedMembers", profile.totals().archivedMembers());
        metadata.put("orphanMembers", profile.orphanMembers().totalCount());
        metadata.put("illegalRoles", profile.illegalRoles().totalCount());
        metadata.put("duplicateOwners", profile.duplicateOwners().totalCount());
        metadata.put("sharedConversations", profile.sharedConversations().totalCount());
        metadata.put("projectsWithoutOwner", profile.projectsWithoutOwner().totalCount());
        metadata.put("imDrifts", profile.imDrifts().totalCount());
        metadata.put("missingConversations", profile.missingConversations().totalCount());
        auditService.log(currentUser, "project_migration.profiled", "project_migration", workspaceId, metadata);
        return profile;
    }
}
