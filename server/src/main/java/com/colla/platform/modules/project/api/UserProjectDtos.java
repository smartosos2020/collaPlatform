package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.domain.ProjectModels.IssueDetail;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectDetail;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectMember;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class UserProjectDtos {
    private UserProjectDtos() {
    }

    static UserProjectView project(ProjectSummary project) {
        return new UserProjectView(
            project.id(),
            project.projectKey(),
            project.name(),
            project.description(),
            project.status(),
            project.conversationId(),
            project.memberCount(),
            project.openIssueCount(),
            project.createdAt(),
            project.updatedAt(),
            new UserProjectCollaboration(project.conversationId(), project.memberCount(), "可协作"),
            List.of("open", "create_issue", "open_conversation")
        );
    }

    static UserProjectDetailView projectDetail(ProjectDetail detail) {
        return new UserProjectDetailView(
            detail.id(),
            detail.projectKey(),
            detail.name(),
            detail.description(),
            detail.status(),
            detail.conversationId(),
            detail.members().stream().map(UserProjectDtos::member).toList(),
            detail.openIssueCount(),
            detail.createdAt(),
            detail.updatedAt(),
            new UserProjectCollaboration(detail.conversationId(), detail.members().size(), "可协作"),
            List.of("open", "create_issue", "open_conversation")
        );
    }

    static UserIssueView issue(IssueSummary issue) {
        return new UserIssueView(
            issue.id(),
            issue.projectId(),
            issue.projectKey(),
            issue.issueKey(),
            issue.issueType(),
            issue.title(),
            issue.description(),
            issue.priority(),
            issue.status(),
            issue.workflowReason(),
            issue.workflowNote(),
            issue.resolution(),
            issue.assigneeId(),
            issue.assigneeName(),
            issue.reporterId(),
            issue.reporterName(),
            issue.dueAt(),
            issue.createdAt(),
            issue.updatedAt(),
            issue.resolvedAt(),
            issue.closedAt(),
            new UserIssueCollaboration(issue.assigneeId(), issue.assigneeName(), issue.reporterId(), issue.reporterName()),
            List.of("open", "comment", "transition")
        );
    }

    static UserIssueDetailView issueDetail(IssueDetail detail) {
        return new UserIssueDetailView(
            issue(detail.issue()),
            detail.comments(),
            detail.attachments(),
            detail.activities(),
            detail.verifications(),
            detail.relations(),
            detail.availableActions()
        );
    }

    private static UserProjectMemberView member(ProjectMember member) {
        return new UserProjectMemberView(
            member.userId(),
            member.username(),
            member.displayName(),
            member.projectRole(),
            member.joinedAt()
        );
    }

    record UserProjectView(
        UUID id,
        String projectKey,
        String name,
        String description,
        String status,
        UUID conversationId,
        int memberCount,
        int openIssueCount,
        Instant createdAt,
        Instant updatedAt,
        UserProjectCollaboration collaboration,
        List<String> availableActions
    ) {
    }

    record UserProjectDetailView(
        UUID id,
        String projectKey,
        String name,
        String description,
        String status,
        UUID conversationId,
        List<UserProjectMemberView> members,
        int openIssueCount,
        Instant createdAt,
        Instant updatedAt,
        UserProjectCollaboration collaboration,
        List<String> availableActions
    ) {
    }

    record UserProjectMemberView(UUID userId, String username, String displayName, String projectRole, Instant joinedAt) {
    }

    record UserIssueView(
        UUID id,
        UUID projectId,
        String projectKey,
        String issueKey,
        String issueType,
        String title,
        String description,
        String priority,
        String status,
        String workflowReason,
        String workflowNote,
        String resolution,
        UUID assigneeId,
        String assigneeName,
        UUID reporterId,
        String reporterName,
        LocalDate dueAt,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        UserIssueCollaboration collaboration,
        List<String> availableActions
    ) {
    }

    record UserIssueDetailView(
        UserIssueView issue,
        List<?> comments,
        List<?> attachments,
        List<?> activities,
        List<?> verifications,
        List<?> relations,
        List<?> availableActions
    ) {
    }

    record UserProjectCollaboration(UUID conversationId, int memberCount, String displayText) {
    }

    record UserIssueCollaboration(UUID assigneeId, String assigneeName, UUID reporterId, String reporterName) {
    }
}
