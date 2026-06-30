package com.colla.platform.modules.search.infrastructure;

import com.colla.platform.modules.search.domain.SearchModels.SearchResult;
import com.colla.platform.modules.search.domain.SearchModels.SearchFilters;
import java.util.List;
import java.util.UUID;

public interface SearchRepository {
    List<SearchResult> search(UUID workspaceId, UUID userId, String query, SearchFilters filters, int limit);

    void refreshWorkspaceIndex(UUID workspaceId);
}
