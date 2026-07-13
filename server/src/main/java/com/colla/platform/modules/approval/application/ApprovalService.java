package com.colla.platform.modules.approval.application;

import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalFlowNode;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalFormSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalInstanceDetail;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalInstanceSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalStats;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalTaskSummary;
import com.colla.platform.modules.approval.infrastructure.ApprovalRepository;
import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.identity.domain.AuthModels.UserAccount;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApprovalService {
    private static final int LIST_LIMIT = 50;

    private final ApprovalRepository approvalRepository;
    private final IdentityRepository identityRepository;
    private final PlatformObjectRepository objectRepository;
    private final DomainEventRepository eventRepository;
    private final AuditService auditService;

    public ApprovalService(
        ApprovalRepository approvalRepository,
        IdentityRepository identityRepository,
        PlatformObjectRepository objectRepository,
        DomainEventRepository eventRepository,
        AuditService auditService
    ) {
        this.approvalRepository = approvalRepository;
        this.identityRepository = identityRepository;
        this.objectRepository = objectRepository;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
    }

    public List<ApprovalFormSummary> listForms(CurrentUser currentUser) {
        return approvalRepository.listForms(currentUser.workspaceId()).stream()
            .filter(ApprovalFormSummary::enabled)
            .toList();
    }

    public ApprovalFormSummary getForm(CurrentUser currentUser, UUID formId) {
        ApprovalFormSummary form = requireForm(currentUser, formId);
        if (!form.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval form not found");
        }
        return form;
    }

    public List<ApprovalInstanceSummary> listMine(CurrentUser currentUser) {
        return approvalRepository.listMyInstances(currentUser.workspaceId(), currentUser.id(), LIST_LIMIT);
    }

    public List<ApprovalTaskSummary> listTodos(CurrentUser currentUser) {
        return approvalRepository.listTodos(currentUser.workspaceId(), currentUser.id(), LIST_LIMIT);
    }

    public long pendingTodoCount(CurrentUser currentUser) {
        return approvalRepository.pendingTodoCount(currentUser.workspaceId(), currentUser.id());
    }

    public ApprovalInstanceDetail detail(CurrentUser currentUser, UUID instanceId) {
        ApprovalInstanceSummary instance = requireReadable(currentUser, instanceId);
        ApprovalFormSummary form = approvalRepository.findForm(currentUser.workspaceId(), instance.formId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval form not found"));
        return new ApprovalInstanceDetail(
            instance,
            form,
            approvalRepository.findPayload(currentUser.workspaceId(), instanceId),
            approvalRepository.listTasks(currentUser.workspaceId(), instanceId),
            approvalRepository.listActions(currentUser.workspaceId(), instanceId)
        );
    }

    @Transactional
    public ApprovalInstanceDetail start(CurrentUser currentUser, UUID formId, String title, Map<String, Object> payload) {
        ApprovalFormSummary form = getForm(currentUser, formId);
        validatePayload(form, payload == null ? Map.of() : payload);
        List<ApprovalFlowNode> nodes = approvalRepository.listFlowNodes(currentUser.workspaceId(), form.id());
        if (nodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval flow is not configured");
        }

        ApprovalInstanceSummary instance = approvalRepository.createInstance(
            currentUser.workspaceId(),
            form.id(),
            form.formKey(),
            normalizeTitle(title, form, currentUser),
            currentUser.id(),
            payload == null ? Map.of() : payload
        );
        ApprovalFlowNode firstNode = nodes.getFirst();
        createTasksForNode(currentUser, instance, firstNode);
        approvalRepository.updateInstanceStatus(currentUser.workspaceId(), instance.id(), "pending", firstNode.nodeOrder(), currentUser.id());
        approvalRepository.addAction(
            currentUser.workspaceId(),
            instance.id(),
            currentUser.id(),
            "started",
            null,
            "pending",
            null,
            Map.of("formKey", form.formKey())
        );
        registerObject(currentUser.workspaceId(), instance.id(), instance.title());
        auditService.log(currentUser, "approval.started", "approval", instance.id(), Map.of("formKey", form.formKey()));
        return detail(currentUser, instance.id());
    }

    @Transactional
    public ApprovalInstanceDetail withdraw(CurrentUser currentUser, UUID instanceId, String comment) {
        ApprovalInstanceSummary instance = requireReadable(currentUser, instanceId);
        if (!instance.applicantId().equals(currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only applicant can withdraw approval");
        }
        if (!"pending".equals(instance.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending approval can be withdrawn");
        }
        List<ApprovalTaskSummary> pendingTasks = approvalRepository.listPendingTasks(currentUser.workspaceId(), instance.id());
        approvalRepository.cancelPendingTasks(currentUser.workspaceId(), instance.id(), null);
        approvalRepository.updateInstanceStatus(currentUser.workspaceId(), instance.id(), "withdrawn", instance.currentNodeOrder(), currentUser.id());
        approvalRepository.addAction(currentUser.workspaceId(), instance.id(), currentUser.id(), "withdrawn", "pending", "withdrawn", comment, Map.of());
        notifyAssignees(currentUser, instance, pendingTasks, "approval_withdrawn", "审批已撤回", currentUser.displayName() + " 撤回了「" + instance.title() + "」");
        auditService.log(currentUser, "approval.withdrawn", "approval", instance.id(), Map.of());
        return detail(currentUser, instance.id());
    }

    @Transactional
    public ApprovalInstanceDetail approve(CurrentUser currentUser, UUID instanceId, UUID taskId, String comment) {
        ApprovalInstanceSummary instance = requirePendingReadable(currentUser, instanceId);
        ApprovalTaskSummary task = requirePendingTask(currentUser, instanceId, taskId);
        approvalRepository.markTask(currentUser.workspaceId(), task.id(), "approved", comment);

        String toStatus = "pending";
        List<ApprovalFlowNode> nodes = approvalRepository.listFlowNodes(currentUser.workspaceId(), instance.formId());
        if (!approvalRepository.hasPendingTaskAtNode(currentUser.workspaceId(), instance.id(), instance.currentNodeOrder())) {
            Optional<ApprovalFlowNode> nextNode = nodes.stream()
                .filter(node -> node.nodeOrder() > instance.currentNodeOrder())
                .findFirst();
            if (nextNode.isPresent()) {
                createTasksForNode(currentUser, instance, nextNode.get());
                approvalRepository.updateInstanceStatus(currentUser.workspaceId(), instance.id(), "pending", nextNode.get().nodeOrder(), currentUser.id());
            } else {
                approvalRepository.updateInstanceStatus(currentUser.workspaceId(), instance.id(), "approved", instance.currentNodeOrder(), currentUser.id());
                toStatus = "approved";
                notifyApplicant(currentUser, instance, "approval_approved", "审批已通过", "「" + instance.title() + "」已审批通过");
            }
        }

        approvalRepository.addAction(currentUser.workspaceId(), instance.id(), currentUser.id(), "approved", "pending", toStatus, comment, Map.of("taskId", task.id().toString()));
        auditService.log(currentUser, "approval.approved", "approval", instance.id(), Map.of("taskId", task.id().toString()));
        return detail(currentUser, instance.id());
    }

    @Transactional
    public ApprovalInstanceDetail reject(CurrentUser currentUser, UUID instanceId, UUID taskId, String comment) {
        ApprovalInstanceSummary instance = requirePendingReadable(currentUser, instanceId);
        ApprovalTaskSummary task = requirePendingTask(currentUser, instanceId, taskId);
        approvalRepository.markTask(currentUser.workspaceId(), task.id(), "rejected", comment);
        approvalRepository.cancelPendingTasks(currentUser.workspaceId(), instance.id(), task.id());
        approvalRepository.updateInstanceStatus(currentUser.workspaceId(), instance.id(), "rejected", instance.currentNodeOrder(), currentUser.id());
        approvalRepository.addAction(currentUser.workspaceId(), instance.id(), currentUser.id(), "rejected", "pending", "rejected", comment, Map.of("taskId", task.id().toString()));
        notifyApplicant(currentUser, instance, "approval_rejected", "审批已拒绝", "「" + instance.title() + "」被拒绝");
        auditService.log(currentUser, "approval.rejected", "approval", instance.id(), Map.of("taskId", task.id().toString()));
        return detail(currentUser, instance.id());
    }

    @Transactional
    public ApprovalInstanceDetail transfer(CurrentUser currentUser, UUID instanceId, UUID taskId, UUID assigneeId, String comment) {
        ApprovalInstanceSummary instance = requirePendingReadable(currentUser, instanceId);
        ApprovalTaskSummary task = requirePendingTask(currentUser, instanceId, taskId);
        UserAccount target = identityRepository.findUserById(assigneeId)
            .filter(user -> user.workspaceId().equals(currentUser.workspaceId()))
            .filter(user -> "active".equals(user.status()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignee not found"));
        approvalRepository.transferTask(currentUser.workspaceId(), task.id(), target.id(), comment);
        approvalRepository.addAction(
            currentUser.workspaceId(),
            instance.id(),
            currentUser.id(),
            "transferred",
            "pending",
            "pending",
            comment,
            Map.of("taskId", task.id().toString(), "assigneeId", target.id().toString())
        );
        notifyUser(currentUser, target.id(), instance, "approval_transferred", "审批待办已转交给你", currentUser.displayName() + " 将「" + instance.title() + "」转交给你处理");
        auditService.log(currentUser, "approval.transferred", "approval", instance.id(), Map.of("taskId", task.id().toString(), "assigneeId", target.id().toString()));
        return detail(currentUser, instance.id());
    }

    public ApprovalStats stats(CurrentUser currentUser) {
        return new ApprovalStats(
            approvalRepository.pendingTodoCount(currentUser.workspaceId(), currentUser.id()),
            approvalRepository.submittedStatusCount(currentUser.workspaceId(), currentUser.id(), "pending"),
            approvalRepository.submittedStatusCount(currentUser.workspaceId(), currentUser.id(), "approved"),
            approvalRepository.submittedStatusCount(currentUser.workspaceId(), currentUser.id(), "rejected"),
            approvalRepository.submittedStatusCount(currentUser.workspaceId(), currentUser.id(), "withdrawn"),
            approvalRepository.countByForm(currentUser.workspaceId(), currentUser.id()),
            approvalRepository.countByStatus(currentUser.workspaceId(), currentUser.id())
        );
    }

    ApprovalInstanceSummary requireSummary(CurrentUser currentUser, UUID instanceId) {
        return requireReadable(currentUser, instanceId);
    }

    private ApprovalFormSummary requireForm(CurrentUser currentUser, UUID formId) {
        return approvalRepository.findForm(currentUser.workspaceId(), formId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval form not found"));
    }

    private ApprovalInstanceSummary requireReadable(CurrentUser currentUser, UUID instanceId) {
        ApprovalInstanceSummary instance = approvalRepository.findInstance(currentUser.workspaceId(), instanceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval instance not found"));
        if (!currentUser.hasRole("admin") && !approvalRepository.isParticipant(currentUser.workspaceId(), instanceId, currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Approval access denied");
        }
        return instance;
    }

    private ApprovalInstanceSummary requirePendingReadable(CurrentUser currentUser, UUID instanceId) {
        ApprovalInstanceSummary instance = requireReadable(currentUser, instanceId);
        if (!"pending".equals(instance.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval is not pending");
        }
        return instance;
    }

    private ApprovalTaskSummary requirePendingTask(CurrentUser currentUser, UUID instanceId, UUID taskId) {
        return approvalRepository.findPendingTask(currentUser.workspaceId(), instanceId, currentUser.id(), taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Pending approval task not found"));
    }

    private void createTasksForNode(CurrentUser currentUser, ApprovalInstanceSummary instance, ApprovalFlowNode node) {
        for (UUID assigneeId : approvalRepository.resolveApprovers(currentUser.workspaceId(), instance.applicantId(), node)) {
            approvalRepository.createTask(currentUser.workspaceId(), instance.id(), node.nodeOrder(), assigneeId);
            notifyUser(currentUser, assigneeId, instance, "approval_task_created", "你有新的审批待办", "请处理「" + instance.title() + "」");
        }
    }

    private void validatePayload(ApprovalFormSummary form, Map<String, Object> payload) {
        Object fields = form.schema().get("fields");
        if (!(fields instanceof List<?> fieldList)) {
            return;
        }
        for (Object field : fieldList) {
            if (!(field instanceof Map<?, ?> fieldMap)) {
                continue;
            }
            Object required = fieldMap.get("required");
            Object key = fieldMap.get("key");
            if (Boolean.TRUE.equals(required) && key != null && !payload.containsKey(key.toString())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing approval field: " + key);
            }
        }
    }

    private String normalizeTitle(String title, ApprovalFormSummary form, CurrentUser currentUser) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return form.name() + " - " + currentUser.displayName();
    }

    private void registerObject(UUID workspaceId, UUID instanceId, String title) {
        objectRepository.upsertObjectLink(workspaceId, "approval", instanceId, "/approvals/" + instanceId, "colla://approval/" + instanceId, title);
    }

    private void notifyApplicant(CurrentUser actor, ApprovalInstanceSummary instance, String notificationType, String title, String body) {
        notifyUser(actor, instance.applicantId(), instance, notificationType, title, body);
    }

    private void notifyAssignees(CurrentUser actor, ApprovalInstanceSummary instance, List<ApprovalTaskSummary> tasks, String notificationType, String title, String body) {
        for (ApprovalTaskSummary task : tasks) {
            notifyUser(actor, task.assigneeId(), instance, notificationType, title, body);
        }
    }

    private void notifyUser(CurrentUser actor, UUID recipientId, ApprovalInstanceSummary instance, String notificationType, String title, String body) {
        if (recipientId.equals(actor.id())) {
            return;
        }
        UUID notificationNonce = UUID.randomUUID();
        eventRepository.append(
            actor.workspaceId(),
            "notification.created",
            "approval",
            instance.id(),
            actor.id(),
            Map.of(
                "recipientId", recipientId.toString(),
                "notificationType", notificationType,
                "title", title,
                "body", body,
                "targetType", "approval",
                "targetId", instance.id().toString(),
                "webPath", "/approvals/" + instance.id(),
                "dedupeKey", "approval:" + instance.id() + ":" + recipientId + ":" + notificationNonce
            ),
            "approval.ntf:" + notificationNonce
        );
    }
}
