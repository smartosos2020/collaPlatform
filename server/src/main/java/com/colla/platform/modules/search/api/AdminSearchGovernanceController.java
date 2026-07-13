package com.colla.platform.modules.search.api;

import com.colla.platform.modules.search.application.SearchService;
import com.colla.platform.modules.search.domain.SearchModels.AdminGovernanceSearchResponse;
import com.colla.platform.shared.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/admin/search-governance")
public class AdminSearchGovernanceController {
    private final SearchService searchService;

    public AdminSearchGovernanceController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public AdminGovernanceSearchResponse search(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "20") int limit,
        Authentication authentication
    ) {
        return searchService.searchGovernance((CurrentUser) authentication.getPrincipal(), q, limit);
    }

    @PostMapping("/reindex")
    public void reindex(Authentication authentication) {
        searchService.reindex((CurrentUser) authentication.getPrincipal());
    }
}
