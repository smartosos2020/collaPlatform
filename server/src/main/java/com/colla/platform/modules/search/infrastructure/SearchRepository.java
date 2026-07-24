package com.colla.platform.modules.search.infrastructure;

import com.colla.platform.modules.search.domain.SearchModels.SearchResult;
import com.colla.platform.modules.search.domain.SearchModels.SearchFilters;
import java.util.List;
import java.util.UUID;

public interface SearchRepository {
    List<String> SUPPORTED_OBJECT_TYPES = List.of(
        "issue",
        "knowledge_content",
        "base",
        "base_table",
        "base_record",
        "message"
    );

    List<SearchResult> search(UUID workspaceId, UUID userId, String query, SearchFilters filters, int limit);

    void refreshWorkspaceIndex(UUID workspaceId);

    boolean projectObject(
        UUID workspaceId,
        String objectType,
        UUID objectId,
        long sourceVersion,
        ProjectionOperation operation
    );

    RebuildPage rebuildBatch(UUID workspaceId, String objectType, UUID afterId, int limit);

    enum ProjectionOperation {
        UPSERT("upsert"),
        DELETE("delete");

        private final String databaseValue;

        ProjectionOperation(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String databaseValue() {
            return databaseValue;
        }
    }

    record RebuildPage(String objectType, UUID nextCursor, int processed, boolean done) {
    }
}
