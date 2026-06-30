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
        String accessState,
        String permissionExplanation,
        UUID knowledgeBaseId,
        UUID parentDocumentId,
        String directoryPath,
        List<String> tags,
        UUID maintainerId,
        String maintainerName,
        String knowledgeStatus,
        String docType,
        String hitSource
    ) {
    }

    public record SearchFilters(
        UUID knowledgeBaseId,
        UUID directoryId,
        String docType,
        List<String> tags,
        UUID maintainerId,
        String knowledgeStatus,
        Instant updatedFrom,
        Instant updatedTo
    ) {
    }

    public record SearchResponse(String query, List<SearchResult> items) {
    }
}
