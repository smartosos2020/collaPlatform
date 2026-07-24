package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.domain.PlatformObjectTypes;
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
    private final List<com.colla.platform.modules.platform.contract.PlatformObjectResolver> publicResolvers;
    private final Map<String, PlatformObjectResolver> resolvers = new LinkedHashMap<>();

    public PlatformObjectResolverRegistry(
        PlatformObjectRepository objectRepository,
        List<PlatformObjectResolver> discoveredResolvers,
        List<com.colla.platform.modules.platform.contract.PlatformObjectResolver> publicResolvers
    ) {
        this.objectRepository = objectRepository;
        this.discoveredResolvers = discoveredResolvers;
        this.publicResolvers = publicResolvers;
    }

    @PostConstruct
    void initialize() {
        for (PlatformObjectResolver resolver : discoveredResolvers) {
            String canonicalType = PlatformObjectTypes.canonicalize(resolver.objectType());
            resolvers.put(canonicalType, resolver);
        }
        for (com.colla.platform.modules.platform.contract.PlatformObjectResolver resolver : publicResolvers) {
            String canonicalType = PlatformObjectTypes.canonicalize(resolver.objectType());
            resolvers.put(canonicalType, new PlatformObjectResolver() {
                @Override
                public String objectType() {
                    return resolver.objectType();
                }

                @Override
                public java.util.Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
                    return resolver.resolve(currentUser.workspaceId(), currentUser.id(), objectId)
                        .map(PlatformObjectResolverRegistry::toInternal);
                }
            });
        }
        for (String objectType : objectRepository.listObjectTypes()) {
            String canonicalType = PlatformObjectTypes.canonicalize(objectType);
            resolvers.putIfAbsent(canonicalType, new ObjectLinkPlatformObjectResolver(canonicalType, objectRepository));
        }
    }

    public PlatformObjectSummary resolve(CurrentUser currentUser, String objectType, UUID objectId) {
        String canonicalType = PlatformObjectTypes.canonicalize(objectType);
        PlatformObjectResolver resolver = resolvers.get(canonicalType);
        if (resolver == null) {
            return PlatformObjectSummary.unavailable(canonicalType, objectId, ObjectAccessState.invalid);
        }
        return resolver.resolve(currentUser, objectId)
            .or(() -> new ObjectLinkPlatformObjectResolver(canonicalType, objectRepository)
                .resolve(currentUser, objectId))
            .orElseGet(() -> PlatformObjectSummary.unavailable(objectType, objectId, ObjectAccessState.not_found));
    }

    private static PlatformObjectSummary toInternal(
        com.colla.platform.modules.platform.contract.PlatformObjectSummary summary
    ) {
        return new PlatformObjectSummary(
            summary.objectType(),
            summary.objectId(),
            com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState.valueOf(summary.accessState().name()),
            summary.title(),
            summary.subtitle(),
            summary.status(),
            summary.webPath(),
            summary.deepLink(),
            summary.metadata()
        );
    }
}
