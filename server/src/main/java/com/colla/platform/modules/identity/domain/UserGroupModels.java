package com.colla.platform.modules.identity.domain;

import java.time.Instant;
import java.util.UUID;

public final class UserGroupModels {
    private UserGroupModels() {
    }

    public record UserGroupSummary(
        UUID id,
        String code,
        String name,
        String description,
        String groupType,
        String status,
        int directMemberCount,
        int expandedMemberCount,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record UserGroupMember(
        UUID id,
        UUID groupId,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String subjectDetail,
        String subjectStatus,
        Instant createdAt
    ) {
    }

    public record ExpandedUserGroupMember(
        UUID userId,
        String username,
        String displayName,
        String email,
        String status,
        String sourceType,
        UUID sourceId,
        String sourceName
    ) {
    }
}
