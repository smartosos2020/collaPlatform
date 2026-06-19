package com.colla.platform.modules.workspace.application;

import com.colla.platform.modules.base.infrastructure.BaseRepository;
import com.colla.platform.modules.approval.application.ApprovalService;
import com.colla.platform.modules.doc.infrastructure.DocumentRepository;
import com.colla.platform.modules.im.infrastructure.ImRepository;
import com.colla.platform.modules.notification.infrastructure.NotificationRepository;
import com.colla.platform.modules.platform.application.PlatformObjectService;
import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import com.colla.platform.modules.workspace.domain.WorkspaceModels.WorkspaceDashboard;
import com.colla.platform.shared.auth.CurrentUser;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceDashboardService {
    private static final int DASHBOARD_LIMIT = 6;

    private final ProjectRepository projectRepository;
    private final ImRepository imRepository;
    private final NotificationRepository notificationRepository;
    private final DocumentRepository documentRepository;
    private final BaseRepository baseRepository;
    private final PlatformObjectService platformObjectService;
    private final ApprovalService approvalService;

    public WorkspaceDashboardService(
        ProjectRepository projectRepository,
        ImRepository imRepository,
        NotificationRepository notificationRepository,
        DocumentRepository documentRepository,
        BaseRepository baseRepository,
        PlatformObjectService platformObjectService,
        ApprovalService approvalService
    ) {
        this.projectRepository = projectRepository;
        this.imRepository = imRepository;
        this.notificationRepository = notificationRepository;
        this.documentRepository = documentRepository;
        this.baseRepository = baseRepository;
        this.platformObjectService = platformObjectService;
        this.approvalService = approvalService;
    }

    public WorkspaceDashboard dashboard(CurrentUser currentUser) {
        var conversations = imRepository.listConversations(currentUser.workspaceId(), currentUser.id()).stream()
            .filter(conversation -> conversation.unreadCount() > 0)
            .limit(DASHBOARD_LIMIT)
            .toList();
        var recentDocuments = documentRepository.listDocuments(currentUser.workspaceId(), currentUser.id(), false).stream()
            .limit(DASHBOARD_LIMIT)
            .toList();
        var recentBases = baseRepository.listBases(currentUser.workspaceId(), currentUser.id()).stream()
            .limit(DASHBOARD_LIMIT)
            .toList();
        return new WorkspaceDashboard(
            projectRepository.listMyIssues(currentUser.workspaceId(), currentUser.id()).stream()
                .filter(issue -> !"closed".equals(issue.status()))
                .limit(DASHBOARD_LIMIT)
                .toList(),
            approvalService.listTodos(currentUser).stream().limit(DASHBOARD_LIMIT).toList(),
            imRepository.totalUnreadCount(currentUser.workspaceId(), currentUser.id()),
            conversations,
            notificationRepository.unreadCount(currentUser.workspaceId(), currentUser.id()),
            notificationRepository.list(currentUser.workspaceId(), currentUser.id(), false, null, null, null, DASHBOARD_LIMIT),
            recentDocuments,
            recentBases,
            platformObjectService.recent(currentUser, DASHBOARD_LIMIT),
            platformObjectService.favorites(currentUser, DASHBOARD_LIMIT)
        );
    }
}
