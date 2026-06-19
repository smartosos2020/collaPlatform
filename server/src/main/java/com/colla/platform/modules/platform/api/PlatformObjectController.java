package com.colla.platform.modules.platform.api;

import com.colla.platform.modules.platform.application.InternalLinkService;
import com.colla.platform.modules.platform.application.PlatformObjectService;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ParsedInternalLink;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectNavigation;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectTypeRule;
import com.colla.platform.modules.permission.domain.PermissionModels.PermissionExplanation;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformObjectController {
    private final PlatformObjectResolverRegistry resolverRegistry;
    private final InternalLinkService internalLinkService;
    private final PlatformObjectService platformObjectService;

    public PlatformObjectController(
        PlatformObjectResolverRegistry resolverRegistry,
        InternalLinkService internalLinkService,
        PlatformObjectService platformObjectService
    ) {
        this.resolverRegistry = resolverRegistry;
        this.internalLinkService = internalLinkService;
        this.platformObjectService = platformObjectService;
    }

    @GetMapping("/objects/{type}/{id}/summary")
    public PlatformObjectSummary summary(@PathVariable String type, @PathVariable UUID id, Authentication authentication) {
        return resolverRegistry.resolve(currentUser(authentication), type, id);
    }

    @GetMapping("/object-types")
    public List<PlatformObjectTypeRule> objectTypes() {
        return platformObjectService.objectTypes();
    }

    @GetMapping("/objects/{type}/{id}/navigation")
    public PlatformObjectNavigation navigation(@PathVariable String type, @PathVariable UUID id, Authentication authentication) {
        return platformObjectService.navigation(currentUser(authentication), type, id);
    }

    @GetMapping("/objects/{type}/{id}/permission-explanation")
    public PermissionExplanation permissionExplanation(
        @PathVariable String type,
        @PathVariable UUID id,
        @RequestParam(defaultValue = "view") String action,
        Authentication authentication
    ) {
        return platformObjectService.explainPermission(currentUser(authentication), type, id, action);
    }

    @PostMapping("/objects/{type}/{id}/access")
    public PlatformObjectSummary markAccessed(@PathVariable String type, @PathVariable UUID id, Authentication authentication) {
        return platformObjectService.markAccessed(currentUser(authentication), type, id);
    }

    @GetMapping("/recent")
    public List<PlatformObjectSummary> recent(@RequestParam(defaultValue = "10") int limit, Authentication authentication) {
        return platformObjectService.recent(currentUser(authentication), limit);
    }

    @GetMapping("/favorites")
    public List<PlatformObjectSummary> favorites(@RequestParam(defaultValue = "20") int limit, Authentication authentication) {
        return platformObjectService.favorites(currentUser(authentication), limit);
    }

    @PostMapping("/objects/{type}/{id}/favorite")
    public PlatformObjectSummary addFavorite(@PathVariable String type, @PathVariable UUID id, Authentication authentication) {
        return platformObjectService.addFavorite(currentUser(authentication), type, id);
    }

    @PostMapping("/objects/{type}/{id}/favorite/remove")
    public void removeFavorite(@PathVariable String type, @PathVariable UUID id, Authentication authentication) {
        platformObjectService.removeFavorite(currentUser(authentication), type, id);
    }

    @PostMapping("/links/resolve")
    public ParsedInternalLink resolveLink(@Valid @RequestBody ResolveLinkRequest request, Authentication authentication) {
        return internalLinkService.resolve(currentUser(authentication), request.link());
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record ResolveLinkRequest(@NotBlank String link) {
    }
}
