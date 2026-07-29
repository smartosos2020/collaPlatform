package com.colla.platform.modules.workspace.domain;

import com.colla.platform.modules.base.domain.BaseModels.BaseSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalTaskSummary;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkPage;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkItem;
import com.colla.platform.modules.project.contract.DraftSummaryQuery.DraftSummary;
import com.colla.platform.modules.platform.contract.DashboardPersonalization.DashboardLayout;
import java.util.List;

public final class WorkspaceModels {
    private WorkspaceModels() {
    }

    public record WorkspaceDashboard(
        PersonalWorkPage personalWork,
        List<PersonalWorkItem> myWorkItems,
        List<ApprovalTaskSummary> approvalTodos,
        long unreadMessageCount,
        List<ConversationSummary> unreadConversations,
        long unreadNotificationCount,
        List<NotificationItem> latestNotifications,
        List<PlatformObjectSummary> recentKnowledgeContents,
        List<BaseSummary> recentBases,
        List<PlatformObjectSummary> recentObjects,
        List<PlatformObjectSummary> favoriteObjects,
        List<DraftSummary> draftSummaries,
        DashboardLayout dashboardLayout
    ) {
    }
}
