package com.colla.platform.modules.approval.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApprovalModels {
    private ApprovalModels() {
    }

    public record ApprovalFormSummary(
        UUID id,
        String formKey,
        String name,
        String description,
        String category,
        Map<String, Object> schema,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ApprovalFlowNode(
        UUID id,
        UUID formId,
        int nodeOrder,
        String name,
        String approverType,
        String approverValue
    ) {
    }

    public record ApprovalInstanceSummary(
        UUID id,
        UUID formId,
        String formKey,
        String formName,
        String title,
        UUID applicantId,
        String applicantName,
        String status,
        int currentNodeOrder,
        Instant submittedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ApprovalTaskSummary(
        UUID id,
        UUID instanceId,
        String instanceTitle,
        String formName,
        String applicantName,
        int nodeOrder,
        UUID assigneeId,
        String assigneeName,
        String status,
        String comment,
        Instant actedAt,
        UUID transferredTo,
        String transferredToName,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ApprovalActionLog(
        UUID id,
        UUID instanceId,
        UUID actorId,
        String actorName,
        String action,
        String fromStatus,
        String toStatus,
        String comment,
        Map<String, Object> metadata,
        Instant createdAt
    ) {
    }

    public record ApprovalInstanceDetail(
        ApprovalInstanceSummary instance,
        ApprovalFormSummary form,
        Map<String, Object> payload,
        List<ApprovalTaskSummary> tasks,
        List<ApprovalActionLog> actions
    ) {
    }

    public record ApprovalStats(
        long pendingTodos,
        long submittedPending,
        long approved,
        long rejected,
        long withdrawn,
        List<ApprovalCountBucket> byForm,
        List<ApprovalCountBucket> byStatus
    ) {
    }

    public record ApprovalCountBucket(String key, String label, long count) {
    }
}
