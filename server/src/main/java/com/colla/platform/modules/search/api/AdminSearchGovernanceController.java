package com.colla.platform.modules.search.api;

import com.colla.platform.modules.search.application.SearchService;
import com.colla.platform.modules.search.application.SearchIndexMaintenanceService;
import com.colla.platform.modules.search.domain.SearchModels.AdminGovernanceSearchResponse;
import com.colla.platform.modules.search.infrastructure.SearchRepository.RebuildPage;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/admin/search-governance")
public class AdminSearchGovernanceController {
    private final SearchService searchService;
    private final SearchIndexMaintenanceService maintenanceService;

    public AdminSearchGovernanceController(
        SearchService searchService,
        SearchIndexMaintenanceService maintenanceService
    ) {
        this.searchService = searchService;
        this.maintenanceService = maintenanceService;
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
        maintenanceService.rebuildAll(
            (CurrentUser) authentication.getPrincipal(),
            "Compatibility workspace rebuild requested by administrator"
        );
    }

    @PostMapping("/reindex/batches")
    public RebuildPage reindexBatch(
        Authentication authentication,
        @Valid @RequestBody RebuildRequest request
    ) {
        return maintenanceService.rebuildBatch(
            (CurrentUser) authentication.getPrincipal(),
            request.objectType(),
            request.afterId(),
            request.limit(),
            request.reason()
        );
    }

    public record RebuildRequest(
        @NotBlank String objectType,
        UUID afterId,
        @Min(1) @Max(250) int limit,
        @NotBlank String reason
    ) {
    }
}
