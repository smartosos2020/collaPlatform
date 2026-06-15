package com.colla.platform.modules.search.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SearchModels {
    private SearchModels() {
    }

    public record SearchResult(
        String objectType,
        UUID objectId,
        String title,
        String excerpt,
        String webPath,
        String deepLink,
        double score,
        Instant updatedAt,
        String accessState
    ) {
    }

    public record SearchResponse(String query, List<SearchResult> items) {
    }
}
