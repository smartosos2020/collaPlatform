package com.colla.platform.modules.search.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeContentRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.modules.search.domain.SearchModels.SearchResponse;
import com.colla.platform.modules.search.domain.SearchModels.AdminGovernanceSearchResponse;
import com.colla.platform.modules.search.domain.SearchModels.AdminGovernanceSearchResult;
import com.colla.platform.modules.search.domain.SearchModels.SearchFilters;
import com.colla.platform.modules.search.domain.SearchModels.SearchResult;
import com.colla.platform.modules.search.infrastructure.SearchRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SearchService {
    private final SearchRepository searchRepository;
    private final SearchIndexService searchIndexService;
    private final PlatformObjectResolverRegistry objectResolverRegistry;
    private final KnowledgeContentRepository contentRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public SearchService(
        SearchRepository searchRepository,
        SearchIndexService searchIndexService,
        PlatformObjectResolverRegistry objectResolverRegistry,
        KnowledgeContentRepository contentRepository,
        PermissionService permissionService,
        AuditService auditService
    ) {
        this.searchRepository = searchRepository;
        this.searchIndexService = searchIndexService;
        this.objectResolverRegistry = objectResolverRegistry;
        this.contentRepository = contentRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public SearchResponse search(
        CurrentUser currentUser,
        String query,
        int limit,
        UUID knowledgeBaseId,
        UUID directoryId,
        String contentType,
        List<String> tags,
        UUID maintainerId,
        String knowledgeStatus,
        String updatedFrom,
        String updatedTo
    ) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must be at least 2 characters");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        SearchFilters filters = new SearchFilters(
            knowledgeBaseId,
            directoryId,
            normalizeContentType(contentType),
            normalizeTags(tags),
            maintainerId,
            normalizeKnowledgeStatus(knowledgeStatus),
            parseInstantOrDate(updatedFrom, false),
            parseInstantOrDate(updatedTo, true)
        );
        List<SearchResult> items = searchRepository.search(currentUser.workspaceId(), currentUser.id(), normalizedQuery, filters, boundedLimit).stream()
            .filter(result -> isUserContentResult(result.objectType()))
            .map(result -> hydrateResult(currentUser, result))
            .limit(boundedLimit)
            .toList();
        if (items.isEmpty() && knowledgeBaseId != null) {
            auditService.log(
                currentUser,
                "knowledge.search.no_result",
                "knowledge_base",
                knowledgeBaseId,
                Map.of("query", normalizedQuery)
            );
        }
        return new SearchResponse(normalizedQuery, "user_content", items);
    }

    public AdminGovernanceSearchResponse searchGovernance(CurrentUser currentUser, String query, int limit) {
        if (!permissionService.canAccessAdmin(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin governance search permission required");
        }
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        List<AdminGovernanceSearchResult> items = governanceCatalog().stream()
            .filter(item -> normalizedQuery.isBlank()
                || item.title().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || item.description().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || item.governanceType().toLowerCase(Locale.ROOT).contains(normalizedQuery))
            .limit(boundedLimit)
            .toList();
        return new AdminGovernanceSearchResponse(normalizedQuery, "admin_governance", items);
    }

    private SearchResult hydrateResult(CurrentUser currentUser, SearchResult result) {
        PlatformObjectSummary summary = objectResolverRegistry.resolve(currentUser, result.objectType(), result.objectId());
        if (summary.accessState() != ObjectAccessState.available) {
            return new SearchResult(
                result.objectType(),
                result.objectId(),
                unavailableTitle(summary.accessState()),
                null,
                null,
                null,
                result.score(),
                result.updatedAt(),
                summary.accessState().name(),
                "对象当前为 " + summary.accessState().name() + " 状态，搜索结果不展示原始内容。",
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null
            );
        }
        if ("knowledge_content".equals(result.objectType())) {
            KnowledgeBaseItem document = contentRepository.findItem(currentUser.workspaceId(), result.objectId()).orElse(null);
            if (document != null && "object_ref".equals(document.contentType()) && document.targetObjectType() != null && document.targetObjectId() != null) {
                PlatformObjectSummary targetSummary = objectResolverRegistry.resolve(currentUser, document.targetObjectType(), document.targetObjectId());
                if (targetSummary.accessState() != ObjectAccessState.available) {
                    return new SearchResult(
                        result.objectType(),
                        result.objectId(),
                        unavailableTitle(targetSummary.accessState()),
                        null,
                        null,
                        null,
                        result.score(),
                        result.updatedAt(),
                        targetSummary.accessState().name(),
                        "对象入口目标当前为 " + targetSummary.accessState().name() + " 状态，搜索结果不展示原始内容。",
                        result.knowledgeBaseId(),
                        result.knowledgeBaseName(),
                        result.parentItemId(),
                        result.directoryPath(),
                        List.of(),
                        null,
                        null,
                        null,
                        result.contentType(),
                        result.hitSource()
                    );
                }
            }
        }
        return new SearchResult(
            result.objectType(),
            result.objectId(),
            summary.title() == null ? result.title() : summary.title(),
            result.excerpt(),
            result.webPath() == null ? summary.webPath() : result.webPath(),
            summary.deepLink() == null ? result.deepLink() : summary.deepLink(),
            result.score(),
            result.updatedAt(),
            summary.accessState().name(),
            availableExplanation(summary),
            result.knowledgeBaseId(),
            result.knowledgeBaseName(),
            result.parentItemId(),
            result.directoryPath(),
            result.tags(),
            result.maintainerId(),
            result.maintainerName(),
            result.knowledgeStatus(),
            result.contentType(),
            result.hitSource()
        );
    }

    private boolean isUserContentResult(String objectType) {
        return List.of("issue", "knowledge_content", "base", "base_table", "base_record", "message", "approval").contains(objectType);
    }

    private List<AdminGovernanceSearchResult> governanceCatalog() {
        return List.of(
            new AdminGovernanceSearchResult("permission", "权限排查", "按成员、资源和动作排查权限来源、风险和缺口。", "/admin/permission-governance", "high"),
            new AdminGovernanceSearchResult("permission_risk", "权限风险处置", "检索过期授权、孤立授权、失效主体和高风险组合，并预览单项修复。", "/admin/permission-governance?severity=high", "critical"),
            new AdminGovernanceSearchResult("permission_grant", "授权上下文", "从授权主体定位成员、资源、权限解释和关联审计。", "/admin/permission-governance", "high"),
            new AdminGovernanceSearchResult("audit", "审计日志", "按来源 UI、动作、对象和操作者查询后台与用户侧审计。", "/admin/audit-logs", "medium"),
            new AdminGovernanceSearchResult("audit_permission", "权限变更审计", "快捷查看权限授予、撤销、请求决定和风险修复记录。", "/admin/audit-logs?permissionOnly=true", "high"),
            new AdminGovernanceSearchResult("application", "应用治理", "Base、项目、消息和审批的策略、风险和治理深链。", "/admin/app-governance", "medium"),
            new AdminGovernanceSearchResult("knowledge", "知识库治理", "知识空间、内容状态、订阅、权限和知识风险治理。", "/admin/knowledge-bases", "medium"),
            new AdminGovernanceSearchResult("identity", "组织与成员", "组织架构、成员、用户组和角色权限管理。", "/admin/users", "high")
        );
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("space", "folder", "markdown", "object_ref", "external_link").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document type filter");
        }
        return normalized;
    }

    private String normalizeKnowledgeStatus(String knowledgeStatus) {
        if (knowledgeStatus == null || knowledgeStatus.isBlank()) {
            return null;
        }
        String normalized = knowledgeStatus.trim().toLowerCase(Locale.ROOT);
        if (!List.of("draft", "verified", "needs_review", "outdated", "archived").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid knowledge status filter");
        }
        return normalized;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            for (String part : raw.split(",")) {
                String value = part.trim().toLowerCase(Locale.ROOT);
                if (!value.isBlank() && !normalized.contains(value)) {
                    normalized.add(value);
                }
            }
        }
        return normalized;
    }

    private Instant parseInstantOrDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            try {
                LocalDate date = LocalDate.parse(value.trim());
                return endOfDay
                    ? date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                    : date.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid updated time filter");
            }
        }
    }

    private String unavailableTitle(ObjectAccessState accessState) {
        return switch (accessState) {
            case forbidden -> "无权限对象";
            case deleted -> "已删除对象";
            case not_found -> "不存在对象";
            case invalid -> "无效对象";
            default -> "不可访问对象";
        };
    }

    private String availableExplanation(PlatformObjectSummary summary) {
        Object level = summary.metadata().get("permissionLevel");
        if (level != null) {
            return "当前用户通过 " + level + " 权限可查看该对象。";
        }
        return switch (summary.objectType()) {
            case "issue" -> "当前用户是项目成员，可查看该事项。";
            case "message" -> "当前用户是会话成员，可查看该消息。";
            case "approval" -> "当前用户是审批申请人、处理人或管理员，可查看该审批。";
            default -> "当前用户具备查看该对象的权限。";
        };
    }
}
