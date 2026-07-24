package com.colla.platform.modules.search.application;

import com.colla.platform.modules.search.infrastructure.SearchRepository;
import com.colla.platform.modules.search.infrastructure.SearchRepository.ProjectionOperation;
import com.colla.platform.modules.search.infrastructure.SearchRepository.RebuildPage;
import com.colla.platform.modules.platform.contract.PlatformObjectTypes;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchIndexService {
    private static final Set<String> INDEXED_AGGREGATE_TYPES = Set.of(
        "issue",
        "knowledge_content",
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

    public boolean applyProjection(
        UUID workspaceId,
        String aggregateType,
        UUID aggregateId,
        long sourceVersion,
        boolean deleted
    ) {
        String objectType = PlatformObjectTypes.canonicalize(aggregateType);
        if (!INDEXED_AGGREGATE_TYPES.contains(objectType)) {
            return false;
        }
        return searchRepository.projectObject(
            workspaceId,
            objectType,
            aggregateId,
            sourceVersion,
            deleted ? ProjectionOperation.DELETE : ProjectionOperation.UPSERT
        );
    }

    public RebuildPage rebuildBatch(UUID workspaceId, String objectType, UUID afterId, int limit) {
        String canonicalType = PlatformObjectTypes.canonicalize(objectType);
        if (!INDEXED_AGGREGATE_TYPES.contains(canonicalType)) {
            throw new IllegalArgumentException("Unsupported search object type: " + objectType);
        }
        return searchRepository.rebuildBatch(workspaceId, canonicalType, afterId, limit);
    }
}
