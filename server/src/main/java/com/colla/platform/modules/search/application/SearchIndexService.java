package com.colla.platform.modules.search.application;

import com.colla.platform.modules.search.infrastructure.SearchRepository;
import com.colla.platform.modules.search.infrastructure.SearchRepository.ProjectionOperation;
import com.colla.platform.modules.search.infrastructure.SearchRepository.RebuildPage;
import com.colla.platform.modules.platform.contract.PlatformObjectTypes;
import com.colla.platform.modules.platform.contract.PlatformSearchProjectionProvider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SearchIndexService {
    private static final Set<String> INDEXED_AGGREGATE_TYPES = Set.of(
        "knowledge_content",
        "base",
        "base_table",
        "base_record",
        "message",
        PlatformObjectTypes.WORK_ITEM
    );

    private final SearchRepository searchRepository;
    private final Map<String, PlatformSearchProjectionProvider> externalProviders;

    public SearchIndexService(
        SearchRepository searchRepository,
        List<PlatformSearchProjectionProvider> externalProviders
    ) {
        this.searchRepository = searchRepository;
        this.externalProviders = externalProviders.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            provider -> PlatformObjectTypes.canonicalize(provider.objectType()),
            provider -> provider
        ));
    }

    public void refreshWorkspaceIndex(UUID workspaceId) {
        searchRepository.refreshWorkspaceIndex(workspaceId);
        for (PlatformSearchProjectionProvider provider : externalProviders.values()) {
            UUID afterId = null;
            boolean done;
            do {
                var documents = provider.listDocuments(workspaceId, afterId, 250);
                RebuildPage page = searchRepository.rebuildDocuments(
                    workspaceId, provider.objectType(), afterId, documents, 250
                );
                afterId = page.nextCursor();
                done = page.done();
            } while (!done);
        }
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
        PlatformSearchProjectionProvider provider = externalProviders.get(objectType);
        if (provider != null) {
            if (deleted) {
                return searchRepository.projectObject(
                    workspaceId, objectType, aggregateId, sourceVersion, ProjectionOperation.DELETE
                );
            }
            return provider.findDocument(workspaceId, aggregateId)
                .map(document -> searchRepository.projectDocument(workspaceId, document, sourceVersion))
                .orElseGet(() -> searchRepository.projectObject(
                    workspaceId, objectType, aggregateId, sourceVersion, ProjectionOperation.DELETE
                ));
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
        PlatformSearchProjectionProvider provider = externalProviders.get(canonicalType);
        if (provider != null) {
            int boundedLimit = Math.min(Math.max(limit, 1), 250);
            var documents = provider.listDocuments(workspaceId, afterId, boundedLimit);
            return searchRepository.rebuildDocuments(
                workspaceId, canonicalType, afterId, documents, boundedLimit
            );
        }
        return searchRepository.rebuildBatch(workspaceId, canonicalType, afterId, limit);
    }
}
