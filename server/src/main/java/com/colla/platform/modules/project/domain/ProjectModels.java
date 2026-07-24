package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.colla.platform.modules.platform.contract.PlatformObjectSummary;

public final class ProjectModels {
    private ProjectModels() {
    }

    public record ProjectSummary(
        UUID id,
        String projectKey,
        String name,
        String description,
        String status,
        UUID conversationId,
        int memberCount,
        int openIssueCount,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ProjectDetail(
        UUID id,
        String projectKey,
        String name,
        String description,
        String status,
        UUID conversationId,
        List<ProjectMember> members,
        int openIssueCount,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ProjectMember(
        UUID userId,
        String username,
        String displayName,
        String projectRole,
        Instant joinedAt
    ) {
    }

    public record IssueSummary(
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
        Instant closedAt
    ) {
    }

    public record IssueDetail(
        IssueSummary issue,
        List<IssueComment> comments,
        List<IssueAttachment> attachments,
        List<IssueActivity> activities,
        List<IssueVerification> verifications,
        List<IssueRelation> relations,
        List<IssueWorkflowAction> availableActions
    ) {
    }

    public record IssueWorkflowAction(
        String key,
        String label,
        String targetStatus,
        boolean requiresReason,
        boolean requiresTargetIssue,
        boolean requiresDueAt,
        String description
    ) {
    }

    public record IssueComment(
        UUID id,
        UUID issueId,
        UUID authorId,
        String authorName,
        String content,
        Instant createdAt
    ) {
    }

    public record IssueAttachment(
        UUID id,
        UUID issueId,
        UUID fileId,
        String fileName,
        UUID createdBy,
        Instant createdAt
    ) {
    }

    public record IssueActivity(
        UUID id,
        UUID issueId,
        UUID actorId,
        String actorName,
        String action,
        String fromValue,
        String toValue,
        Instant createdAt
    ) {
    }

    public record IssueVerification(
        UUID id,
        UUID issueId,
        UUID verifierId,
        String verifierName,
        String result,
        String note,
        String environment,
        String reproductionSteps,
        String fixVersion,
        Instant createdAt
    ) {
    }

    public record IssueRelation(
        UUID id,
        UUID issueId,
        String targetType,
        UUID targetId,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        PlatformObjectSummary target
    ) {
    }

    public record ProjectStats(
        UUID projectId,
        List<CountBucket> byStatus,
        List<CountBucket> byAssignee,
        List<CountBucket> byIteration,
        int overdueCount
    ) {
    }

    public record CountBucket(String key, String label, long count) {
    }
}
