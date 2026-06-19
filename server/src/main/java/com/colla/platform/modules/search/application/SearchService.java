package com.colla.platform.modules.search.application;

import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.modules.search.domain.SearchModels.SearchResponse;
import com.colla.platform.modules.search.domain.SearchModels.SearchResult;
import com.colla.platform.modules.search.infrastructure.SearchRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SearchService {
    private final SearchRepository searchRepository;
    private final SearchIndexService searchIndexService;
    private final PlatformObjectResolverRegistry objectResolverRegistry;
    private final PermissionService permissionService;

    public SearchService(
        SearchRepository searchRepository,
        SearchIndexService searchIndexService,
        PlatformObjectResolverRegistry objectResolverRegistry,
        PermissionService permissionService
    ) {
        this.searchRepository = searchRepository;
        this.searchIndexService = searchIndexService;
        this.objectResolverRegistry = objectResolverRegistry;
        this.permissionService = permissionService;
    }

    public SearchResponse search(CurrentUser currentUser, String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must be at least 2 characters");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        searchIndexService.refreshWorkspaceIndex(currentUser.workspaceId());
        List<SearchResult> items = searchRepository.search(currentUser.workspaceId(), currentUser.id(), normalizedQuery, boundedLimit).stream()
            .map(result -> hydrateResult(currentUser, result))
            .limit(boundedLimit)
            .toList();
        return new SearchResponse(normalizedQuery, items);
    }

    public void reindex(CurrentUser currentUser) {
        permissionService.requireManageUsers(currentUser);
        searchIndexService.refreshWorkspaceIndex(currentUser.workspaceId());
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
                "对象当前为 " + summary.accessState().name() + " 状态，搜索结果不展示原始内容。"
            );
        }
        return new SearchResult(
            result.objectType(),
            result.objectId(),
            summary.title() == null ? result.title() : summary.title(),
            result.excerpt(),
            summary.webPath() == null ? result.webPath() : summary.webPath(),
            summary.deepLink() == null ? result.deepLink() : summary.deepLink(),
            result.score(),
            result.updatedAt(),
            summary.accessState().name(),
            availableExplanation(summary)
        );
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
