package com.colla.platform.modules.permission.application;

import com.colla.platform.modules.permission.domain.PermissionModels.PermissionDecision;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionExplanation;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionGrant;
import com.colla.platform.modules.permission.domain.PermissionModels.ResourcePermissionMatch;
import com.colla.platform.modules.permission.infrastructure.ResourcePermissionRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PermissionDecisionService {
    private final ResourcePermissionRepository resourcePermissionRepository;

    public PermissionDecisionService(ResourcePermissionRepository resourcePermissionRepository) {
        this.resourcePermissionRepository = resourcePermissionRepository;
    }

    public PermissionDecision allow(UUID workspaceId, String objectType, UUID objectId, String action) {
        return new PermissionDecision(workspaceId, objectType, objectId, action, true, "allowed", "owner", requiredLevel(action), "system", null);
    }

    public PermissionDecision deny(UUID workspaceId, String objectType, UUID objectId, String action, String reason) {
        return new PermissionDecision(workspaceId, objectType, objectId, action, false, reason, "none", requiredLevel(action), "none", null);
    }

    public void grantResource(
        UUID workspaceId,
        String resourceType,
        UUID resourceId,
        String subjectType,
        UUID subjectId,
        String permissionLevel,
        String sourceType,
        UUID sourceId,
        Instant expiresAt,
        UUID actorId
    ) {
        resourcePermissionRepository.upsertGrant(new ResourcePermissionGrant(
            workspaceId,
            normalizeObjectType(resourceType),
            resourceId,
            normalizeSubjectType(subjectType),
            subjectId,
            normalizeLevel(permissionLevel),
            normalizeSourceType(sourceType),
            sourceId,
            expiresAt,
            actorId
        ));
    }

    public PermissionDecision decide(CurrentUser currentUser, String objectType, UUID objectId, String actionOrRequiredLevel) {
        String normalizedObjectType = normalizeObjectType(objectType);
        String requiredLevel = requiredLevel(actionOrRequiredLevel);
        return resourcePermissionRepository.findBestMatch(currentUser.workspaceId(), currentUser.id(), normalizedObjectType, objectId)
            .map(match -> decisionFromMatch(currentUser, normalizedObjectType, objectId, actionOrRequiredLevel, requiredLevel, match))
            .orElseGet(() -> new PermissionDecision(
                currentUser.workspaceId(),
                normalizedObjectType,
                objectId,
                normalizeAction(actionOrRequiredLevel),
                false,
                "当前用户不在该对象的授权范围内。",
                "none",
                requiredLevel,
                "none",
                null
            ));
    }

    public PermissionExplanation explain(CurrentUser currentUser, String objectType, UUID objectId, String actionOrRequiredLevel, String accessState) {
        PermissionDecision decision = decide(currentUser, objectType, objectId, actionOrRequiredLevel);
        boolean allowed = decision.allowed() && "available".equals(accessState);
        String reason = allowed ? decision.reason() : blockedReason(decision, accessState);
        return new PermissionExplanation(
            normalizeObjectType(objectType),
            objectId,
            normalizeAction(actionOrRequiredLevel),
            allowed,
            accessState,
            reason,
            decision.currentLevel(),
            decision.requiredLevel(),
            decision.source()
        );
    }

    public boolean subjectExists(UUID workspaceId, String subjectType, UUID subjectId) {
        if (subjectId == null) {
            return false;
        }
        return resourcePermissionRepository.subjectExists(workspaceId, normalizeSubjectType(subjectType), subjectId);
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
            case "owner" -> 5;
            case "manage" -> 4;
            case "edit" -> 3;
            case "comment" -> 2;
            case "view" -> 1;
            default -> 0;
        };
    }

    public String requiredLevel(String actionOrRequiredLevel) {
        String value = normalizeAction(actionOrRequiredLevel);
        if (List.of("owner", "manage", "edit", "comment", "view").contains(value)) {
            return value;
        }
        return switch (value) {
            case "delete", "permission", "share", "manage" -> "manage";
            case "edit", "update", "save", "transition", "approve", "reject" -> "edit";
            case "comment", "reply" -> "comment";
            default -> "view";
        };
    }

    public String normalizeSubjectType(String subjectType) {
        String normalized = subjectType == null || subjectType.isBlank() ? "user" : subjectType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("user", "department", "user_group", "role").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid permission subject");
        }
        return normalized;
    }

    private PermissionDecision decisionFromMatch(
        CurrentUser currentUser,
        String objectType,
        UUID objectId,
        String actionOrRequiredLevel,
        String requiredLevel,
        ResourcePermissionMatch match
    ) {
        boolean allowed = levelRank(match.permissionLevel()) >= levelRank(requiredLevel);
        return new PermissionDecision(
            currentUser.workspaceId(),
            objectType,
            objectId,
            normalizeAction(actionOrRequiredLevel),
            allowed,
            allowed
                ? "当前权限为 " + match.permissionLevel() + "，来源：" + sourceDescription(match) + "。"
                : "当前权限为 " + match.permissionLevel() + "，需要 " + requiredLevel + " 权限。",
            match.permissionLevel(),
            requiredLevel,
            sourceDescription(match),
            match.permissionId()
        );
    }

    private String sourceDescription(ResourcePermissionMatch match) {
        String name = match.subjectName() == null || match.subjectName().isBlank() ? match.subjectId().toString() : match.subjectName();
        return switch (match.subjectType()) {
            case "user" -> "成员 " + name;
            case "department" -> "部门 " + name;
            case "user_group" -> "用户组 " + name;
            case "role" -> "角色 " + name;
            default -> match.sourceType();
        };
    }

    private String blockedReason(PermissionDecision decision, String accessState) {
        return switch (accessState) {
            case "available" -> decision.reason();
            case "forbidden" -> "当前用户不在该对象的授权范围内。";
            case "deleted" -> "对象已删除或归档，不能继续操作。";
            case "not_found", "invalid" -> "对象不存在或链接无效。";
            default -> "对象当前不可访问。";
        };
    }

    private String normalizeLevel(String permissionLevel) {
        String level = permissionLevel == null ? "" : permissionLevel.trim().toLowerCase(Locale.ROOT);
        if (!List.of("owner", "manage", "edit", "comment", "view").contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resource permission level");
        }
        return level;
    }

    private String normalizeObjectType(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Object type is required");
        }
        return objectType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = sourceType == null || sourceType.isBlank() ? "direct" : sourceType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("direct", "inherited", "owner", "system").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resource permission source");
        }
        return normalized;
    }

    private String normalizeAction(String action) {
        return action == null || action.isBlank() ? "view" : action.trim().toLowerCase(Locale.ROOT);
    }
}
