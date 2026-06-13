package com.colla.platform.modules.approval.infrastructure;

import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalActionLog;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalCountBucket;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalFlowNode;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalFormSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalInstanceSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalTaskSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRepository {
    List<ApprovalFormSummary> listForms(UUID workspaceId);

    Optional<ApprovalFormSummary> findForm(UUID workspaceId, UUID formId);

    List<ApprovalFlowNode> listFlowNodes(UUID workspaceId, UUID formId);

    ApprovalInstanceSummary createInstance(UUID workspaceId, UUID formId, String formKey, String title, UUID applicantId, Map<String, Object> payload);

    Optional<ApprovalInstanceSummary> findInstance(UUID workspaceId, UUID instanceId);

    Map<String, Object> findPayload(UUID workspaceId, UUID instanceId);

    List<ApprovalInstanceSummary> listMyInstances(UUID workspaceId, UUID userId, int limit);

    List<ApprovalTaskSummary> listTodos(UUID workspaceId, UUID userId, int limit);

    List<ApprovalTaskSummary> listTasks(UUID workspaceId, UUID instanceId);

    List<ApprovalActionLog> listActions(UUID workspaceId, UUID instanceId);

    Optional<ApprovalTaskSummary> findPendingTask(UUID workspaceId, UUID instanceId, UUID assigneeId, UUID taskId);

    List<ApprovalTaskSummary> listPendingTasks(UUID workspaceId, UUID instanceId);

    void createTask(UUID workspaceId, UUID instanceId, int nodeOrder, UUID assigneeId);

    void markTask(UUID workspaceId, UUID taskId, String status, String comment);

    void cancelPendingTasks(UUID workspaceId, UUID instanceId, UUID exceptTaskId);

    void transferTask(UUID workspaceId, UUID taskId, UUID targetAssigneeId, String comment);

    void updateInstanceStatus(UUID workspaceId, UUID instanceId, String status, int currentNodeOrder, UUID updatedBy);

    void addAction(UUID workspaceId, UUID instanceId, UUID actorId, String action, String fromStatus, String toStatus, String comment, Map<String, Object> metadata);

    List<UUID> resolveApprovers(UUID workspaceId, UUID applicantId, ApprovalFlowNode node);

    boolean hasPendingTaskAtNode(UUID workspaceId, UUID instanceId, int nodeOrder);

    boolean isParticipant(UUID workspaceId, UUID instanceId, UUID userId);

    long pendingTodoCount(UUID workspaceId, UUID userId);

    long submittedStatusCount(UUID workspaceId, UUID userId, String status);

    List<ApprovalCountBucket> countByForm(UUID workspaceId, UUID userId);

    List<ApprovalCountBucket> countByStatus(UUID workspaceId, UUID userId);
}
