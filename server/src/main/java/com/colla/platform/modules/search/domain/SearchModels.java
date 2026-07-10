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
        String knowledgeBaseName,
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

    public record SearchResponse(String query, String searchScope, List<SearchResult> items) {
    }

    public record AdminGovernanceSearchResult(
        String governanceType,
        String title,
        String description,
        String adminPath,
        String riskLevel
    ) {
    }

    public record AdminGovernanceSearchResponse(String query, String searchScope, List<AdminGovernanceSearchResult> items) {
    }
}
