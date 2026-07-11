package com.colla.platform.modules.workspace.domain;

import com.colla.platform.modules.base.domain.BaseModels.BaseSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalTaskSummary;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import java.util.List;

public final class WorkspaceModels {
    private WorkspaceModels() {
    }

    public record WorkspaceDashboard(
        List<IssueSummary> myIssues,
        List<ApprovalTaskSummary> approvalTodos,
        long unreadMessageCount,
        List<ConversationSummary> unreadConversations,
        long unreadNotificationCount,
        List<NotificationItem> latestNotifications,
        List<PlatformObjectSummary> recentKnowledgeContents,
        List<BaseSummary> recentBases,
        List<PlatformObjectSummary> recentObjects,
        List<PlatformObjectSummary> favoriteObjects
    ) {
    }
}
