package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PlatformObjectResolverRegistry {
    private final PlatformObjectRepository objectRepository;
    private final List<PlatformObjectResolver> discoveredResolvers;
    private final Map<String, PlatformObjectResolver> resolvers = new LinkedHashMap<>();

    public PlatformObjectResolverRegistry(PlatformObjectRepository objectRepository, List<PlatformObjectResolver> discoveredResolvers) {
        this.objectRepository = objectRepository;
        this.discoveredResolvers = discoveredResolvers;
    }

    @PostConstruct
    void initialize() {
        for (PlatformObjectResolver resolver : discoveredResolvers) {
            resolvers.put(resolver.objectType(), resolver);
        }
        for (String objectType : objectRepository.listObjectTypes()) {
            resolvers.putIfAbsent(objectType, new ObjectLinkPlatformObjectResolver(objectType, objectRepository));
        }
    }

    public PlatformObjectSummary resolve(CurrentUser currentUser, String objectType, UUID objectId) {
        PlatformObjectResolver resolver = resolvers.get(objectType);
        if (resolver == null) {
            return PlatformObjectSummary.unavailable(objectType, objectId, ObjectAccessState.invalid);
        }
        return resolver.resolve(currentUser, objectId)
            .orElseGet(() -> PlatformObjectSummary.unavailable(objectType, objectId, ObjectAccessState.not_found));
    }
}
