package com.colla.platform.modules.approval.api;

import com.colla.platform.modules.approval.application.ApprovalService;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalFormSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalInstanceDetail;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalInstanceSummary;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalStats;
import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalTaskSummary;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/forms")
    public List<ApprovalFormSummary> listForms(Authentication authentication) {
        return approvalService.listForms(currentUser(authentication));
    }

    @GetMapping("/forms/{formId}")
    public ApprovalFormSummary getForm(@PathVariable UUID formId, Authentication authentication) {
        return approvalService.getForm(currentUser(authentication), formId);
    }

    @GetMapping("/instances")
    public List<ApprovalInstanceSummary> listMine(Authentication authentication) {
        return approvalService.listMine(currentUser(authentication));
    }

    @PostMapping("/instances")
    public ApprovalInstanceDetail start(@Valid @RequestBody StartApprovalRequest request, Authentication authentication) {
        return approvalService.start(currentUser(authentication), request.formId(), request.title(), request.payload());
    }

    @GetMapping("/instances/{instanceId}")
    public ApprovalInstanceDetail detail(@PathVariable UUID instanceId, Authentication authentication) {
        return approvalService.detail(currentUser(authentication), instanceId);
    }

    @PostMapping("/instances/{instanceId}/withdraw")
    public ApprovalInstanceDetail withdraw(
        @PathVariable UUID instanceId,
        @RequestBody(required = false) ApprovalActionRequest request,
        Authentication authentication
    ) {
        return approvalService.withdraw(currentUser(authentication), instanceId, request == null ? null : request.comment());
    }

    @PostMapping("/instances/{instanceId}/approve")
    public ApprovalInstanceDetail approve(
        @PathVariable UUID instanceId,
        @RequestBody(required = false) ApprovalActionRequest request,
        Authentication authentication
    ) {
        return approvalService.approve(currentUser(authentication), instanceId, request == null ? null : request.taskId(), request == null ? null : request.comment());
    }

    @PostMapping("/instances/{instanceId}/reject")
    public ApprovalInstanceDetail reject(
        @PathVariable UUID instanceId,
        @RequestBody(required = false) ApprovalActionRequest request,
        Authentication authentication
    ) {
        return approvalService.reject(currentUser(authentication), instanceId, request == null ? null : request.taskId(), request == null ? null : request.comment());
    }

    @PostMapping("/instances/{instanceId}/transfer")
    public ApprovalInstanceDetail transfer(
        @PathVariable UUID instanceId,
        @Valid @RequestBody TransferApprovalRequest request,
        Authentication authentication
    ) {
        return approvalService.transfer(currentUser(authentication), instanceId, request.taskId(), request.assigneeId(), request.comment());
    }

    @GetMapping("/todos")
    public List<ApprovalTaskSummary> todos(Authentication authentication) {
        return approvalService.listTodos(currentUser(authentication));
    }

    @GetMapping("/stats")
    public ApprovalStats stats(Authentication authentication) {
        return approvalService.stats(currentUser(authentication));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record StartApprovalRequest(@NotNull UUID formId, String title, Map<String, Object> payload) {
    }

    public record ApprovalActionRequest(UUID taskId, String comment) {
    }

    public record TransferApprovalRequest(UUID taskId, @NotNull UUID assigneeId, String comment) {
    }
}
