package com.colla.platform.modules.project.application;

import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.ActiveSpaceMap;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.LegacyMemberRow;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.LegacyProjectRow;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.LegacySpaceMappingPlan;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.MemberFailure;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.MemberFailureReason;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.MemberMapping;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.ProjectFailureReason;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.ProjectMappingPlan;
import com.colla.platform.modules.project.domain.ProjectLegacyMappingModels.SpaceMappingDecision;
import com.colla.platform.modules.project.infrastructure.ProjectLegacyMappingRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ProjectLegacyMappingService {
    static final int MAX_SPACE_KEY_LENGTH = 64;
    static final String SPACE_ID_SEED_PREFIX = "colla:project-legacy-space:";

    private final ProjectLegacyMappingRepository projectLegacyMappingRepository;
    private final PermissionService permissionService;

    public ProjectLegacyMappingService(
        ProjectLegacyMappingRepository projectLegacyMappingRepository,
        PermissionService permissionService
    ) {
        this.projectLegacyMappingRepository = projectLegacyMappingRepository;
        this.permissionService = permissionService;
    }

    public LegacySpaceMappingPlan planWorkspaceMigration(CurrentUser currentUser) {
        permissionService.requireManageProjects(currentUser);
        UUID workspaceId = currentUser.workspaceId();
        List<LegacyProjectRow> projects = projectLegacyMappingRepository.findActiveProjects(workspaceId);
        Map<UUID, List<LegacyMemberRow>> membersByProject = projectLegacyMappingRepository
            .findActiveMembers(workspaceId)
            .stream()
            .collect(Collectors.groupingBy(
                LegacyMemberRow::projectId, LinkedHashMap::new, Collectors.toList()
            ));
        Map<UUID, UUID> activeSpaceByProject = projectLegacyMappingRepository
            .findActiveSpaceMaps(workspaceId)
            .stream()
            .collect(Collectors.toMap(ActiveSpaceMap::legacyProjectId, ActiveSpaceMap::spaceId));
        Set<String> occupiedKeys = new HashSet<>(projectLegacyMappingRepository.findOccupiedSpaceKeys(workspaceId));

        List<ProjectMappingPlan> plans = new ArrayList<>(projects.size());
        for (LegacyProjectRow project : projects) {
            plans.add(planProject(
                project,
                membersByProject.getOrDefault(project.projectId(), List.of()),
                activeSpaceByProject,
                occupiedKeys
            ));
        }
        return new LegacySpaceMappingPlan(
            workspaceId,
            Instant.now(),
            List.copyOf(plans),
            plans.size(),
            plans.stream().filter(plan -> plan.projectFailure() == null).count(),
            plans.stream().filter(plan -> plan.decision() == SpaceMappingDecision.REUSE_EXISTING_MAP).count(),
            plans.stream().filter(plan -> plan.decision() == SpaceMappingDecision.KEY_CONFLICT_SUFFIXED).count(),
            plans.stream().mapToLong(plan -> plan.memberFailures().size()).sum(),
            plans.stream().filter(plan -> plan.projectFailure() != null).count()
        );
    }

    private ProjectMappingPlan planProject(
        LegacyProjectRow project,
        List<LegacyMemberRow> members,
        Map<UUID, UUID> activeSpaceByProject,
        Set<String> occupiedKeys
    ) {
        UUID deterministicSpaceId = deterministicSpaceId(project.projectId());
        SpaceMappingDecision decision;
        String resolvedSpaceKey = null;
        UUID resolvedSpaceId = null;
        UUID mappedSpaceId = activeSpaceByProject.get(project.projectId());
        if (mappedSpaceId != null) {
            decision = SpaceMappingDecision.REUSE_EXISTING_MAP;
            resolvedSpaceId = mappedSpaceId;
        } else {
            String baseKey = normalizeBaseKey(project.projectKey(), project.projectId());
            if (occupiedKeys.contains(baseKey)) {
                decision = SpaceMappingDecision.KEY_CONFLICT_SUFFIXED;
                resolvedSpaceKey = conflictSuffixedKey(baseKey, project.projectId(), occupiedKeys);
            } else {
                decision = SpaceMappingDecision.CREATE_NEW;
                resolvedSpaceKey = baseKey;
            }
            occupiedKeys.add(resolvedSpaceKey);
        }

        List<MemberMapping> memberMappings = new ArrayList<>();
        List<MemberFailure> memberFailures = new ArrayList<>();
        for (LegacyMemberRow member : members) {
            if (!member.userHealthy()) {
                memberFailures.add(new MemberFailure(
                    member.userId(),
                    member.projectRole(),
                    MemberFailureReason.ORPHAN_USER,
                    "legacy member user is missing, deleted, disabled or belongs to another workspace"
                ));
                continue;
            }
            switch (member.projectRole()) {
                case "owner" -> memberMappings.add(new MemberMapping(
                    member.userId(), "owner", "owner", "legacy owner maps to space owner"
                ));
                case "member" -> memberMappings.add(new MemberMapping(
                    member.userId(), "member", "member", "legacy member maps to space member"
                ));
                case "viewer" -> memberMappings.add(new MemberMapping(
                    member.userId(), "viewer", "guest", "legacy viewer maps to space guest"
                ));
                default -> memberFailures.add(new MemberFailure(
                    member.userId(),
                    member.projectRole(),
                    MemberFailureReason.UNKNOWN_ROLE,
                    "legacy role '" + member.projectRole() + "' has no space role mapping"
                ));
            }
        }

        return new ProjectMappingPlan(
            project.projectId(),
            project.projectKey(),
            project.projectName(),
            decision,
            deterministicSpaceId,
            resolvedSpaceKey,
            resolvedSpaceId,
            List.copyOf(memberMappings),
            List.copyOf(memberFailures),
            project.workspaceExists() ? null : ProjectFailureReason.WORKSPACE_MISSING
        );
    }

    private UUID deterministicSpaceId(UUID projectId) {
        return UUID.nameUUIDFromBytes((SPACE_ID_SEED_PREFIX + projectId).getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeBaseKey(String projectKey, UUID projectId) {
        String normalized = (projectKey == null ? "" : projectKey)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        if (normalized.isEmpty()) {
            normalized = "project-" + shortHex(projectId);
        }
        if (normalized.length() > MAX_SPACE_KEY_LENGTH) {
            normalized = normalized.substring(0, MAX_SPACE_KEY_LENGTH).replaceAll("-+$", "");
        }
        return normalized;
    }

    private String conflictSuffixedKey(String baseKey, UUID projectId, Set<String> occupiedKeys) {
        String hex = projectId.toString().replace("-", "");
        for (int suffixLength = 8; suffixLength <= hex.length(); suffixLength += 4) {
            String suffix = hex.substring(0, suffixLength);
            String head = baseKey
                .substring(0, Math.min(baseKey.length(), MAX_SPACE_KEY_LENGTH - suffix.length() - 1))
                .replaceAll("-+$", "");
            if (head.isEmpty()) {
                head = "project";
            }
            String candidate = head + "-" + suffix;
            if (!occupiedKeys.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to resolve space key conflict for project " + projectId);
    }

    private String shortHex(UUID projectId) {
        return projectId.toString().replace("-", "").substring(0, 8);
    }
}
