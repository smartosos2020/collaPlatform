package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectNavigation;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectReference;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectTypeRule;
import com.colla.platform.modules.permission.application.PermissionDecisionService;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionDecision;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionExplanation;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlatformObjectService {
    private final PlatformObjectResolverRegistry resolverRegistry;
    private final PlatformObjectRepository objectRepository;
    private final PermissionDecisionService permissionDecisionService;

    public PlatformObjectService(
        PlatformObjectResolverRegistry resolverRegistry,
        PlatformObjectRepository objectRepository,
        PermissionDecisionService permissionDecisionService
    ) {
        this.resolverRegistry = resolverRegistry;
        this.objectRepository = objectRepository;
        this.permissionDecisionService = permissionDecisionService;
    }

    public PlatformObjectNavigation navigation(CurrentUser currentUser, String objectType, UUID objectId) {
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, objectType, objectId);
        if (summary.accessState() == ObjectAccessState.available) {
            recordAccess(currentUser, summary);
        }
        String webPath = summary.webPath();
        return new PlatformObjectNavigation(
            summary,
            webPath,
            summary.deepLink(),
            webPath == null ? "/" : webPath
        );
    }

    public PlatformObjectSummary markAccessed(CurrentUser currentUser, String objectType, UUID objectId) {
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, objectType, objectId);
        if (summary.accessState() == ObjectAccessState.available) {
            recordAccess(currentUser, summary);
        }
        return summary;
    }

    public PermissionExplanation explainPermission(CurrentUser currentUser, String objectType, UUID objectId, String action) {
        String normalizedAction = action == null || action.isBlank() ? "view" : action.trim().toLowerCase();
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, objectType, objectId);
        PermissionDecision resourceDecision = permissionDecisionService.decide(currentUser, objectType, objectId, normalizedAction);
        if (resourceDecision.permissionId() != null) {
            return permissionDecisionService.explain(currentUser, objectType, objectId, normalizedAction, summary.accessState().name());
        }
        String currentLevel = currentLevel(summary);
        String requiredLevel = requiredLevel(normalizedAction);
        boolean allowed = summary.accessState() == ObjectAccessState.available && levelRank(currentLevel) >= levelRank(requiredLevel);
        return new PermissionExplanation(
            summary.objectType(),
            summary.objectId(),
            normalizedAction,
            allowed,
            summary.accessState().name(),
            reason(summary, normalizedAction, currentLevel, requiredLevel, allowed),
            currentLevel,
            requiredLevel,
            source(summary)
        );
    }

    public List<PlatformObjectSummary> recent(CurrentUser currentUser, int limit) {
        return objectRepository.listRecentAccesses(currentUser.workspaceId(), currentUser.id(), limit).stream()
            .map(reference -> resolveReference(currentUser, reference))
            .filter(summary -> summary.accessState() == ObjectAccessState.available)
            .toList();
    }

    public List<PlatformObjectSummary> favorites(CurrentUser currentUser, int limit) {
        return objectRepository.listFavorites(currentUser.workspaceId(), currentUser.id(), limit).stream()
            .map(reference -> resolveReference(currentUser, reference))
            .filter(summary -> summary.accessState() == ObjectAccessState.available)
            .toList();
    }

    public List<PlatformObjectSummary> summaries(CurrentUser currentUser, String objectType, List<UUID> objectIds) {
        return objectIds.stream()
            .map(objectId -> resolverRegistry.resolve(currentUser, objectType, objectId))
            .filter(summary -> summary.accessState() == ObjectAccessState.available)
            .toList();
    }

    public PlatformObjectSummary addFavorite(CurrentUser currentUser, String objectType, UUID objectId) {
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, objectType, objectId);
        if (summary.accessState() == ObjectAccessState.available) {
            objectRepository.addFavorite(currentUser.workspaceId(), currentUser.id(), objectType, objectId);
            recordAccess(currentUser, summary);
        }
        return summary;
    }

    public void removeFavorite(CurrentUser currentUser, String objectType, UUID objectId) {
        objectRepository.removeFavorite(currentUser.workspaceId(), currentUser.id(), objectType, objectId);
    }

    public List<PlatformObjectTypeRule> objectTypes() {
        return objectRepository.listObjectTypeRules();
    }

    private PlatformObjectSummary resolveReference(CurrentUser currentUser, PlatformObjectReference reference) {
        return resolverRegistry.resolve(currentUser, reference.objectType(), reference.objectId());
    }

    private void recordAccess(CurrentUser currentUser, PlatformObjectSummary summary) {
        objectRepository.recordRecentAccess(
            currentUser.workspaceId(),
            currentUser.id(),
            summary.objectType(),
            summary.objectId(),
            summary.webPath(),
            summary.deepLink(),
            summary.title()
        );
    }

    private String currentLevel(PlatformObjectSummary summary) {
        if (summary.accessState() != ObjectAccessState.available) {
            return "none";
        }
        Object level = summary.metadata().get("permissionLevel");
        if (level instanceof String permissionLevel && !permissionLevel.isBlank()) {
            return permissionLevel;
        }
        return "view";
    }

    private String requiredLevel(String action) {
        return switch (action) {
            case "edit", "update", "comment", "transition", "approve", "reject" -> "edit";
            case "manage", "share", "delete", "permission" -> "manage";
            default -> "view";
        };
    }

    private int levelRank(String level) {
        return switch (level) {
            case "owner" -> 5;
            case "manage" -> 3;
            case "edit" -> 2;
            case "comment" -> 2;
            case "view" -> 1;
            default -> 0;
        };
    }

    private String reason(PlatformObjectSummary summary, String action, String currentLevel, String requiredLevel, boolean allowed) {
        if (summary.accessState() == ObjectAccessState.forbidden) {
            return "当前用户不在该对象的授权范围内。";
        }
        if (summary.accessState() == ObjectAccessState.deleted) {
            return "对象已删除或归档，不能继续操作。";
        }
        if (summary.accessState() != ObjectAccessState.available) {
            return "对象不存在或链接无效。";
        }
        if (!allowed) {
            return "当前权限为 " + currentLevel + "，执行 " + action + " 需要 " + requiredLevel + " 权限。";
        }
        return "当前权限为 " + currentLevel + "，满足 " + action + " 操作要求。";
    }

    private String source(PlatformObjectSummary summary) {
        return switch (summary.objectType()) {
            case "issue" -> "project_members";
            case "document" -> "resource_permissions";
            case "base", "base_table", "base_record" -> "base_members";
            case "approval" -> "approval_participants";
            case "message" -> "conversation_members";
            default -> "platform_object_resolver";
        };
    }
}
