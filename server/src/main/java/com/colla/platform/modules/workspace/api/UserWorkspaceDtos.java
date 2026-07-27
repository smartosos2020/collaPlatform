package com.colla.platform.modules.workspace.api;

import com.colla.platform.modules.base.domain.BaseModels.BaseSummary;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.notification.domain.NotificationModels.NotificationItem;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkPage;
import com.colla.platform.modules.project.contract.DraftSummaryQuery.DraftSummary;
import com.colla.platform.modules.platform.contract.DashboardPersonalization.DashboardLayout;
import com.colla.platform.modules.workspace.domain.WorkspaceModels.WorkspaceDashboard;
import java.util.List;

final class UserWorkspaceDtos {
    private UserWorkspaceDtos() {
    }

    static UserWorkspaceDashboardView dashboard(WorkspaceDashboard dashboard) {
        return new UserWorkspaceDashboardView(
            dashboard.personalWork(),
            dashboard.myIssues(),
            dashboard.approvalTodos(),
            dashboard.unreadMessageCount(),
            dashboard.unreadConversations(),
            dashboard.unreadNotificationCount(),
            dashboard.latestNotifications(),
            dashboard.recentKnowledgeContents(),
            dashboard.recentBases(),
            dashboard.recentObjects(),
            dashboard.favoriteObjects(),
            dashboard.draftSummaries(),
            dashboard.dashboardLayout(),
            new UserWorkspaceNavigationSummary(
                dashboard.myIssues().size(),
                dashboard.recentKnowledgeContents().size(),
                dashboard.recentBases().size(),
                dashboard.unreadConversations().size(),
                dashboard.unreadNotificationCount()
            ),
            List.of("continue_work", "open_messages", "open_notifications", "open_recent_content")
        );
    }

    record UserWorkspaceDashboardView(
        PersonalWorkPage personalWork,
        List<IssueSummary> myIssues,
        List<?> approvalTodos,
        long unreadMessageCount,
        List<ConversationSummary> unreadConversations,
        long unreadNotificationCount,
        List<NotificationItem> latestNotifications,
        List<PlatformObjectSummary> recentKnowledgeContents,
        List<BaseSummary> recentBases,
        List<PlatformObjectSummary> recentObjects,
        List<PlatformObjectSummary> favoriteObjects,
        List<DraftSummary> draftSummaries,
        DashboardLayout dashboardLayout,
        UserWorkspaceNavigationSummary navigationSummary,
        List<String> availableActions
    ) {
    }

    record UserWorkspaceNavigationSummary(
        int issueCount,
        int knowledgeContentCount,
        int baseCount,
        int unreadConversationCount,
        long unreadNotificationCount
    ) {
    }
}
