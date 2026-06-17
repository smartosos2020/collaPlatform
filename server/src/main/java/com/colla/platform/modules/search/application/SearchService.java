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
            return result;
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
            summary.accessState().name()
        );
    }
}
