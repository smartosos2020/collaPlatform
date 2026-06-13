package com.colla.platform.modules.search.application;

import com.colla.platform.modules.search.domain.SearchModels.SearchResponse;
import com.colla.platform.modules.search.infrastructure.SearchRepository;
import com.colla.platform.shared.auth.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SearchService {
    private final SearchRepository searchRepository;

    public SearchService(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public SearchResponse search(CurrentUser currentUser, String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must be at least 2 characters");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        return new SearchResponse(
            normalizedQuery,
            searchRepository.search(currentUser.workspaceId(), currentUser.id(), normalizedQuery, boundedLimit)
        );
    }
}
