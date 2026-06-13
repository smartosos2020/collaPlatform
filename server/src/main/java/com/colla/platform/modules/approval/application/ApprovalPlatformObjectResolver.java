package com.colla.platform.modules.approval.application;

import com.colla.platform.modules.approval.domain.ApprovalModels.ApprovalInstanceSummary;
import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ApprovalPlatformObjectResolver implements PlatformObjectResolver {
    private final ApprovalService approvalService;

    public ApprovalPlatformObjectResolver(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public String objectType() {
        return "approval";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        try {
            ApprovalInstanceSummary approval = approvalService.requireSummary(currentUser, objectId);
            return Optional.of(new PlatformObjectSummary(
                objectType(),
                objectId,
                ObjectAccessState.available,
                approval.title(),
                approval.formName() + " / " + approval.applicantName(),
                approval.status(),
                "/approvals/" + objectId,
                "colla://approval/" + objectId,
                Map.of("formKey", approval.formKey(), "applicantId", approval.applicantId().toString())
            ));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
                return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.forbidden));
            }
            if (exception.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw exception;
        }
    }
}
