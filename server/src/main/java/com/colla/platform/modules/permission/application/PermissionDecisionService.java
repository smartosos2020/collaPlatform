package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.permission.domain.PermissionModels.PermissionDecision;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PermissionDecisionService {
    public PermissionDecision allow(UUID workspaceId, String objectType, UUID objectId, String action) {
        return new PermissionDecision(workspaceId, objectType, objectId, action, true, "allowed");
    }

    public PermissionDecision deny(UUID workspaceId, String objectType, UUID objectId, String action, String reason) {
        return new PermissionDecision(workspaceId, objectType, objectId, action, false, reason);
    }

    public void requireAllowed(PermissionDecision decision) {
        if (!decision.allowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, decision.reason());
        }
    }

    public boolean hasLevel(String currentLevel, String requiredLevel) {
        return levelRank(currentLevel) >= levelRank(requiredLevel);
    }

    public int levelRank(String permissionLevel) {
        return switch (permissionLevel) {
            case "manage" -> 3;
            case "edit" -> 2;
            case "view" -> 1;
            default -> 0;
        };
    }
}
