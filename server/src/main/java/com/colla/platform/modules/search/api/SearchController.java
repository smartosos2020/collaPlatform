package com.colla.platform.modules.search.api;

import com.colla.platform.modules.search.application.SearchService;
import com.colla.platform.modules.search.domain.SearchModels.SearchResponse;
import com.colla.platform.shared.auth.CurrentUser;
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
        Authentication authentication
    ) {
        return searchService.search((CurrentUser) authentication.getPrincipal(), q, limit);
    }
}
