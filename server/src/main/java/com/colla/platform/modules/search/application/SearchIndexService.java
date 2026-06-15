package com.colla.platform.modules.search.application;

import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.colla.platform.modules.search.infrastructure.SearchRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchIndexService {
    private static final Set<String> INDEXED_AGGREGATE_TYPES = Set.of(
        "issue",
        "document",
        "base",
        "base_table",
        "base_record",
        "message"
    );

    private final SearchRepository searchRepository;

    public SearchIndexService(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public void refreshWorkspaceIndex(UUID workspaceId) {
        searchRepository.refreshWorkspaceIndex(workspaceId);
    }

    public void handleEvent(DomainEvent event) {
        if (INDEXED_AGGREGATE_TYPES.contains(event.aggregateType())) {
            refreshWorkspaceIndex(event.workspaceId());
        }
    }
}
