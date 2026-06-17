package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectModels.IssueActivity;
import com.colla.platform.modules.project.domain.ProjectModels.IssueAttachment;
import com.colla.platform.modules.project.domain.ProjectModels.IssueComment;
import com.colla.platform.modules.project.domain.ProjectModels.IssueVerification;
import com.colla.platform.modules.project.domain.ProjectModels.IssueRelation;
import com.colla.platform.modules.project.domain.ProjectModels.IssueSummary;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectStats;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectDetail;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectMember;
import com.colla.platform.modules.project.domain.ProjectModels.ProjectSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    UUID createProject(UUID workspaceId, String projectKey, String name, String description, UUID conversationId, UUID createdBy);

    void updateProjectConversation(UUID workspaceId, UUID projectId, UUID conversationId);

    void addProjectMember(UUID workspaceId, UUID projectId, UUID userId, String projectRole, UUID createdBy);

    boolean isProjectMember(UUID workspaceId, UUID projectId, UUID userId);

    boolean canEditProject(UUID workspaceId, UUID projectId, UUID userId);

    List<ProjectSummary> listProjects(UUID workspaceId, UUID userId);

    Optional<ProjectDetail> findProject(UUID workspaceId, UUID projectId, UUID userId);

    Optional<ProjectSummary> findProjectById(UUID workspaceId, UUID projectId);

    List<ProjectMember> listProjectMembers(UUID workspaceId, UUID projectId);

    List<UUID> listProjectMemberIds(UUID workspaceId, UUID projectId);

    List<UUID> findActiveProjectUserIdsByUsernames(UUID workspaceId, UUID projectId, List<String> usernames);

    int nextIssueNumber(UUID projectId);

    UUID createIssue(
        UUID workspaceId,
        UUID projectId,
        String issueKey,
        String issueType,
        String title,
        String description,
        String priority,
        UUID assigneeId,
        UUID reporterId,
        LocalDate dueAt,
        UUID createdBy
    );

    List<IssueSummary> listIssues(
        UUID workspaceId,
        UUID projectId,
        UUID userId,
        String status,
        String issueType,
        String priority,
        UUID assigneeId,
        String sort
    );

    List<IssueSummary> listMyIssues(UUID workspaceId, UUID userId);

    ProjectStats projectStats(UUID workspaceId, UUID projectId);

    Optional<IssueSummary> findIssue(UUID workspaceId, UUID issueId);

    void updateIssue(UUID workspaceId, UUID issueId, String title, String description, String priority, UUID assigneeId, LocalDate dueAt, UUID updatedBy);

    void transitionIssue(UUID workspaceId, UUID issueId, String status, UUID updatedBy);

    UUID addComment(UUID workspaceId, UUID issueId, UUID authorId, String content);

    List<IssueComment> listComments(UUID workspaceId, UUID issueId);

    void addAttachment(UUID workspaceId, UUID issueId, UUID fileId, UUID createdBy);

    List<IssueAttachment> listAttachments(UUID workspaceId, UUID issueId);

    void addActivity(UUID workspaceId, UUID issueId, UUID actorId, String action, String fromValue, String toValue);

    List<IssueActivity> listActivities(UUID workspaceId, UUID issueId);

    UUID addVerification(
        UUID workspaceId,
        UUID issueId,
        UUID verifierId,
        String result,
        String note,
        String environment,
        String reproductionSteps,
        String fixVersion
    );

    List<IssueVerification> listVerifications(UUID workspaceId, UUID issueId);

    UUID addRelation(UUID workspaceId, UUID issueId, String targetType, UUID targetId, UUID createdBy);

    List<IssueRelation> listRelations(UUID workspaceId, UUID issueId);
}
