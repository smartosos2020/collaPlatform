package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectLegacyMappingModels {
    private ProjectLegacyMappingModels() {
    }

    public enum SpaceMappingDecision {
        CREATE_NEW,
        REUSE_EXISTING_MAP,
        KEY_CONFLICT_SUFFIXED
    }

    public enum MemberFailureReason {
        UNKNOWN_ROLE,
        ORPHAN_USER
    }

    public enum ProjectFailureReason {
        WORKSPACE_MISSING
    }

    public record LegacyProjectRow(
        UUID projectId,
        String projectKey,
        String projectName,
        boolean workspaceExists
    ) {
    }

    public record LegacyMemberRow(
        UUID projectId,
        UUID userId,
        String projectRole,
        boolean userHealthy
    ) {
    }

    public record ActiveSpaceMap(
        UUID legacyProjectId,
        UUID spaceId
    ) {
    }

    public record SourceFingerprintRow(
        UUID projectId,
        Instant updatedAt,
        long activeMembers,
        String memberDigest
    ) {
    }

    public record MemberMapping(
        UUID userId,
        String legacyRole,
        String targetRole,
        String explanation
    ) {
    }

    public record MemberFailure(
        UUID userId,
        String legacyRole,
        MemberFailureReason reason,
        String detail
    ) {
    }

    public record ProjectMappingPlan(
        UUID projectId,
        String projectKey,
        String projectName,
        SpaceMappingDecision decision,
        UUID deterministicSpaceId,
        String resolvedSpaceKey,
        UUID resolvedSpaceId,
        List<MemberMapping> memberMappings,
        List<MemberFailure> memberFailures,
        ProjectFailureReason projectFailure
    ) {
    }

    public record LegacySpaceMappingPlan(
        UUID workspaceId,
        Instant generatedAt,
        List<ProjectMappingPlan> plans,
        long totalProjects,
        long migratableProjects,
        long reuseCount,
        long keyConflictCount,
        long memberFailureCount,
        long projectFailureCount
    ) {
    }
}
