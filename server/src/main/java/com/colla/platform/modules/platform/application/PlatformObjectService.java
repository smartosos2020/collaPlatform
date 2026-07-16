package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectAction;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectCard;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectChoicePage;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectNavigation;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectReference;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectTypeRule;
import com.colla.platform.modules.platform.domain.PlatformObjectTypes;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.modules.permission.application.PermissionDecisionService;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionDecision;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionExplanation;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionActionCategory;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlatformObjectService {
    private static final Set<String> SELECTABLE_OBJECT_TYPES = Set.of("base", "project", "file", PlatformObjectTypes.KNOWLEDGE_CONTENT);
    private final PlatformObjectResolverRegistry resolverRegistry;
    private final PlatformObjectRepository objectRepository;
    private final PermissionDecisionService permissionDecisionService;
    private final PermissionService permissionService;

    public PlatformObjectService(
        PlatformObjectResolverRegistry resolverRegistry,
        PlatformObjectRepository objectRepository,
        PermissionDecisionService permissionDecisionService,
        PermissionService permissionService
    ) {
        this.resolverRegistry = resolverRegistry;
        this.objectRepository = objectRepository;
        this.permissionDecisionService = permissionDecisionService;
        this.permissionService = permissionService;
    }

    public PlatformObjectNavigation navigation(CurrentUser currentUser, String objectType, UUID objectId) {
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, PlatformObjectTypes.canonicalize(objectType), objectId);
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
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, PlatformObjectTypes.canonicalize(objectType), objectId);
        if (summary.accessState() == ObjectAccessState.available) {
            recordAccess(currentUser, summary);
        }
        return summary;
    }

    public PlatformObjectCard card(CurrentUser currentUser, String objectType, UUID objectId, String context) {
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, PlatformObjectTypes.canonicalize(objectType), objectId);
        String presentationContext = normalizePresentationContext(context);
        boolean adminContextAllowed = "admin".equals(presentationContext) && permissionService.canAccessAdmin(currentUser);
        if ("admin".equals(presentationContext) && !adminContextAllowed) {
            presentationContext = "user";
        }
        return new PlatformObjectCard(
            summary,
            presentationContext,
            cardActions(summary, presentationContext, adminContextAllowed),
            permissionHint(summary, presentationContext)
        );
    }

    public PermissionExplanation explainPermission(CurrentUser currentUser, String objectType, UUID objectId, String action, String context) {
        String normalizedAction = action == null || action.isBlank() ? "view" : action.trim().toLowerCase();
        String presentationContext = normalizePresentationContext(context);
        boolean adminContextAllowed = "admin".equals(presentationContext) && permissionService.canAccessAdmin(currentUser);
        if ("admin".equals(presentationContext) && !adminContextAllowed) {
            presentationContext = "user";
        }
        PermissionActionCategory actionCategory = permissionService.categorizeAction(objectType, normalizedAction);
        String canonicalType = PlatformObjectTypes.canonicalize(objectType);
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, canonicalType, objectId);
        PermissionDecision resourceDecision = permissionDecisionService.decide(currentUser, objectType, objectId, normalizedAction);
        if (resourceDecision.permissionId() != null) {
            PermissionExplanation explanation = permissionDecisionService.explain(currentUser, objectType, objectId, normalizedAction, summary.accessState().name());
            return contextualExplanation(explanation, actionCategory, presentationContext);
        }
        String currentLevel = currentLevel(summary);
        String requiredLevel = requiredLevel(normalizedAction);
        boolean allowed = summary.accessState() == ObjectAccessState.available && levelRank(currentLevel) >= levelRank(requiredLevel);
        return new PermissionExplanation(
            summary.objectType(),
            summary.objectId(),
            normalizedAction,
            actionCategory,
            presentationContext,
            allowed,
            summary.accessState().name(),
            reason(summary, normalizedAction, currentLevel, requiredLevel, allowed),
            allowed ? "可以继续操作。" : "请联系对象负责人或提交权限申请。",
            "admin".equals(presentationContext) ? source(summary) + " -> " + currentLevel + " >= " + requiredLevel : "",
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

    public PlatformObjectChoicePage choices(
        CurrentUser currentUser,
        List<String> objectTypes,
        String query,
        String source,
        int limit,
        int offset
    ) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 50);
        int normalizedOffset = Math.max(offset, 0);
        List<String> normalizedTypes = normalizeChoiceTypes(objectTypes);
        String normalizedSource = source == null || source.isBlank() ? "all" : source.trim().toLowerCase(Locale.ROOT);
        if (!List.of("all", "recent").contains(normalizedSource)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Invalid object choice source"
            );
        }
        List<PlatformObjectReference> references = "recent".equals(normalizedSource)
            ? objectRepository.listRecentAccesses(currentUser.workspaceId(), currentUser.id(), 50).stream()
                .filter(reference -> normalizedTypes.contains(PlatformObjectTypes.canonicalize(reference.objectType())))
                .filter(reference -> matchesQuery(reference.titleSnapshot(), query))
                .toList()
            : objectRepository.listObjectCandidates(currentUser.workspaceId(), normalizedTypes, query, 500);
        List<PlatformObjectSummary> available = references.stream()
            .map(reference -> resolveReference(currentUser, reference))
            .filter(summary -> summary.accessState() == ObjectAccessState.available)
            .toList();
        List<PlatformObjectSummary> page = available.stream()
            .skip(normalizedOffset)
            .limit(normalizedLimit)
            .toList();
        return new PlatformObjectChoicePage(page, available.size(), normalizedLimit, normalizedOffset);
    }

    public PlatformObjectSummary addFavorite(CurrentUser currentUser, String objectType, UUID objectId) {
        String canonicalType = PlatformObjectTypes.canonicalize(objectType);
        PlatformObjectSummary summary = resolverRegistry.resolve(currentUser, canonicalType, objectId);
        if (summary.accessState() == ObjectAccessState.available) {
            objectRepository.addFavorite(currentUser.workspaceId(), currentUser.id(), canonicalType, objectId);
            recordAccess(currentUser, summary);
        }
        return summary;
    }

    public void removeFavorite(CurrentUser currentUser, String objectType, UUID objectId) {
        objectRepository.removeFavorite(
            currentUser.workspaceId(), currentUser.id(), PlatformObjectTypes.canonicalize(objectType), objectId
        );
    }

    public List<PlatformObjectTypeRule> objectTypes() {
        return objectRepository.listObjectTypeRules();
    }

    private PlatformObjectSummary resolveReference(CurrentUser currentUser, PlatformObjectReference reference) {
        return resolverRegistry.resolve(currentUser, reference.objectType(), reference.objectId());
    }

    private List<String> normalizeChoiceTypes(List<String> objectTypes) {
        List<String> source = objectTypes == null || objectTypes.isEmpty()
            ? List.copyOf(SELECTABLE_OBJECT_TYPES)
            : objectTypes;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String objectType : source) {
            if (objectType == null || objectType.isBlank()) {
                continue;
            }
            String canonicalType = PlatformObjectTypes.canonicalize(objectType.trim());
            if (!SELECTABLE_OBJECT_TYPES.contains(canonicalType)) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Object type is not selectable"
                );
            }
            normalized.add(canonicalType);
        }
        return List.copyOf(normalized);
    }

    private boolean matchesQuery(String title, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return title != null && title.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    private List<PlatformObjectAction> cardActions(PlatformObjectSummary summary, String presentationContext, boolean adminContextAllowed) {
        List<PlatformObjectAction> actions = new ArrayList<>();
        if (summary.accessState() == ObjectAccessState.available && summary.webPath() != null) {
            actions.add(new PlatformObjectAction("open", "打开", summary.webPath(), "primary"));
        }
        if (summary.accessState() != ObjectAccessState.available) {
            actions.add(new PlatformObjectAction("request_permission", "申请权限", null, "default"));
        }
        if ("admin".equals(presentationContext) && adminContextAllowed) {
            actions.add(new PlatformObjectAction(
                "inspect_permissions",
                "权限排查",
                "/admin/permission-governance?resourceType=" + summary.objectType() + "&resourceId=" + summary.objectId(),
                "default"
            ));
            actions.add(new PlatformObjectAction(
                "audit_logs",
                "审计日志",
                "/admin/audit-logs?targetType=" + summary.objectType() + "&targetId=" + summary.objectId(),
                "default"
            ));
        }
        return actions;
    }

    private String permissionHint(PlatformObjectSummary summary, String presentationContext) {
        if ("admin".equals(presentationContext)) {
            return "后台上下文展示治理入口、权限排查和审计深链。";
        }
        return summary.accessState() == ObjectAccessState.available
            ? "用户上下文只展示打开、协作和可执行动作。"
            : "用户上下文不暴露后台治理入口。";
    }

    private PermissionExplanation contextualExplanation(
        PermissionExplanation explanation,
        PermissionActionCategory actionCategory,
        String presentationContext
    ) {
        return new PermissionExplanation(
            explanation.objectType(),
            explanation.objectId(),
            explanation.action(),
            actionCategory,
            presentationContext,
            explanation.allowed(),
            explanation.accessState(),
            explanation.reason(),
            explanation.allowed() ? "可以继续操作。" : "请联系对象负责人或提交权限申请。",
            "admin".equals(presentationContext) ? explanation.source() : "",
            explanation.currentLevel(),
            explanation.requiredLevel(),
            explanation.source()
        );
    }

    private String normalizePresentationContext(String context) {
        if (context == null || context.isBlank()) {
            return "user";
        }
        String normalized = context.trim().toLowerCase(Locale.ROOT);
        return "admin".equals(normalized) ? "admin" : "user";
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
            case PlatformObjectTypes.KNOWLEDGE_CONTENT -> "resource_permissions";
            case "base", "base_table", "base_record" -> "base_members";
            case "approval" -> "approval_participants";
            case "message" -> "conversation_members";
            default -> "platform_object_resolver";
        };
    }
}
