package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectLegacyProfileModels {
    private ProjectLegacyProfileModels() {
    }

    public enum OrphanMemberReason {
        USER_MISSING,
        USER_DELETED,
        USER_DISABLED,
        WORKSPACE_MISMATCH
    }

    public enum ImDriftDirection {
        PROJECT_MEMBER_NOT_IN_GROUP,
        GROUP_MEMBER_NOT_IN_PROJECT
    }

    public enum MissingConversationReason {
        NO_CONVERSATION_ID,
        CONVERSATION_NOT_FOUND,
        CONVERSATION_ARCHIVED,
        CONVERSATION_TYPE_MISMATCH
    }

    public record ProjectTotals(
        long activeProjects,
        long archivedProjects,
        long activeMembers,
        long archivedMembers
    ) {
    }

    public record RoleDistribution(
        long ownerCount,
        long memberCount,
        long viewerCount,
        long otherCount
    ) {
    }

    public record Findings<T>(long totalCount, List<T> items) {
    }

    public record OrphanMemberItem(
        UUID projectId,
        String projectKey,
        UUID userId,
        OrphanMemberReason reason
    ) {
    }

    public record IllegalRoleItem(
        UUID projectId,
        String projectKey,
        UUID userId,
        String projectRole
    ) {
    }

    public record DuplicateOwnerItem(
        UUID projectId,
        String projectKey,
        List<UUID> userIds
    ) {
    }

    public record SharedConversationItem(
        UUID conversationId,
        List<UUID> projectIds,
        List<String> projectKeys
    ) {
    }

    public record MissingOwnerItem(
        UUID projectId,
        String projectKey
    ) {
    }

    public record ImDriftItem(
        UUID projectId,
        String projectKey,
        UUID conversationId,
        ImDriftDirection direction,
        UUID userId
    ) {
    }

    public record MissingConversationItem(
        UUID projectId,
        String projectKey,
        UUID conversationId,
        MissingConversationReason reason
    ) {
    }

    public record ProjectLegacyProfile(
        UUID workspaceId,
        Instant generatedAt,
        ProjectTotals totals,
        RoleDistribution roleDistribution,
        Findings<OrphanMemberItem> orphanMembers,
        Findings<IllegalRoleItem> illegalRoles,
        Findings<DuplicateOwnerItem> duplicateOwners,
        Findings<SharedConversationItem> sharedConversations,
        Findings<MissingOwnerItem> projectsWithoutOwner,
        Findings<ImDriftItem> imDrifts,
        Findings<MissingConversationItem> missingConversations
    ) {
    }
}
