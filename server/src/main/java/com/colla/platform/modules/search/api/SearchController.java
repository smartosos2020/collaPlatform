package com.colla.platform.modules.search.api;

import com.colla.platform.modules.search.application.SearchService;
import com.colla.platform.modules.search.domain.SearchModels.SearchResponse;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(
        @RequestParam String q,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) UUID knowledgeBaseId,
        @RequestParam(required = false) UUID directoryId,
        @RequestParam(required = false) String contentType,
        @RequestParam(required = false) List<String> tags,
        @RequestParam(required = false) UUID maintainerId,
        @RequestParam(required = false) String knowledgeStatus,
        @RequestParam(required = false) String updatedFrom,
        @RequestParam(required = false) String updatedTo,
        Authentication authentication
    ) {
        return searchService.search(
            (CurrentUser) authentication.getPrincipal(),
            q,
            limit,
            knowledgeBaseId,
            directoryId,
            contentType,
            tags,
            maintainerId,
            knowledgeStatus,
            updatedFrom,
            updatedTo
        );
    }

}
