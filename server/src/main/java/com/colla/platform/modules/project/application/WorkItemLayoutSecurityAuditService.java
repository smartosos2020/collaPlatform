package com.colla.platform.modules.project.application;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemLayoutSecurityAuditService {
    private final AuditLog auditLog;

    public WorkItemLayoutSecurityAuditService(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPolicyWriteDenied(
        CurrentUser user,
        UUID spaceId,
        String operation,
        String reasonCode
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("operation", operation);
        metadata.put("reasonCode", reasonCode);
        metadata.put("requestId", RequestBoundaryContext.current().requestId());
        metadata.put("sourceUi", RequestBoundaryContext.current().sourceUi());
        metadata.put("apiSurface", RequestBoundaryContext.current().apiSurface());
        auditLog.append(
            user.workspaceId(),
            user.id(),
            "work_item_layout.policy_write_denied",
            "project_space",
            spaceId,
            null,
            null,
            metadata
        );
    }
}
