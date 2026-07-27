package com.colla.platform.modules.project.contract;

import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Permission-calibrated personal activity, reminder, and nudge contract.
 *
 * <p>All returned object metadata is derived from the caller's current WorkItem
 * visibility. Stored projections and realtime messages are only invalidation
 * hints and are never an authorization source.</p>
 */
public interface PersonalCollaborationQuery {
    ActivityPage activities(CurrentUser user, Long beforeSequence, int limit);

    ReadState markActivitiesRead(CurrentUser user, long throughSequence);

    ReminderView reminders(CurrentUser user, String timezone);

    ReminderDispatchResult dispatchReminders(CurrentUser user, String timezone, String requestId);

    ReminderPreference preference(CurrentUser user);

    ReminderPreference updatePreference(
        CurrentUser user,
        String timezone,
        int approachingMinutes,
        boolean enabled
    );

    NudgeReceipt nudge(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID recipientId,
        String requestId
    );

    ConsistencyResult consistency(CurrentUser user, boolean dryRun, boolean rebuild);

    record ActivityItem(
        long sequence,
        UUID workItemId,
        UUID spaceId,
        String displayKey,
        String title,
        String activityType,
        long sourceVersion,
        Instant occurredAt,
        String deepLink
    ) {
    }

    record ActivityPage(
        List<ActivityItem> items,
        Long nextBeforeSequence,
        long readThroughSequence,
        long unreadCount,
        boolean truncated,
        Instant generatedAt
    ) {
        public ActivityPage {
            items = List.copyOf(items);
        }
    }

    record ReadState(long readThroughSequence, Instant updatedAt) {
    }

    enum ReminderState {
        approaching,
        due,
        overdue
    }

    record ReminderItem(
        UUID workItemId,
        UUID spaceId,
        String displayKey,
        String title,
        Instant dueAt,
        ReminderState state,
        String deepLink
    ) {
    }

    record ReminderView(
        List<ReminderItem> items,
        String timezone,
        Instant evaluatedAt,
        boolean enabled
    ) {
        public ReminderView {
            items = List.copyOf(items);
        }
    }

    record ReminderDispatchResult(int considered, int emitted, Instant dispatchedAt) {
    }

    record ReminderPreference(
        String timezone,
        int approachingMinutes,
        boolean enabled,
        Instant updatedAt
    ) {
    }

    record NudgeReceipt(
        UUID receiptId,
        UUID workItemId,
        UUID recipientId,
        String status,
        Instant createdAt,
        boolean replayed
    ) {
    }

    record ConsistencyResult(
        boolean dryRun,
        boolean rebuilt,
        long activeProjectionRows,
        long invalidProjectionRows,
        int refreshedItems,
        List<String> failures,
        Instant completedAt
    ) {
        public ConsistencyResult {
            failures = List.copyOf(failures);
        }
    }
}
