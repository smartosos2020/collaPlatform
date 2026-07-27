package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ActivityItem;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.NudgeReceipt;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderPreference;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PersonalCollaborationRepository {
    List<ActivityItem> listActivities(
        UUID workspaceId,
        Set<UUID> visibleWorkItemIds,
        Long beforeSequence,
        int limit
    );

    long readThroughSequence(UUID workspaceId, UUID userId);

    void markRead(UUID workspaceId, UUID userId, long throughSequence, Instant updatedAt);

    ReminderPreference preference(UUID workspaceId, UUID userId);

    ReminderPreference updatePreference(
        UUID workspaceId,
        UUID userId,
        String timezone,
        int approachingMinutes,
        boolean enabled,
        Instant updatedAt
    );

    Optional<NudgeCommand> findNudge(
        UUID workspaceId,
        UUID senderId,
        String requestId
    );

    boolean createNudge(
        UUID receiptId,
        UUID workspaceId,
        UUID spaceId,
        UUID workItemId,
        UUID senderId,
        UUID recipientId,
        String requestId,
        String requestHash,
        Instant createdAt
    );

    Set<UUID> nudgeRecipients(UUID workspaceId, UUID spaceId, UUID workItemId);

    boolean recentlyNudged(
        UUID workspaceId,
        UUID workItemId,
        UUID senderId,
        UUID recipientId,
        Instant since
    );

    long activeProjectionRows(UUID workspaceId, UUID userId);

    long invalidProjectionRows(UUID workspaceId, UUID userId);

    void clearDiscardableProjection(UUID workspaceId, UUID userId);

    record NudgeCommand(NudgeReceipt receipt, String requestHash) {
    }
}
