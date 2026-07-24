package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.contract.ObjectAccessState;
import com.colla.platform.modules.platform.contract.PlatformObjectRegistry;
import com.colla.platform.modules.platform.contract.PlatformObjectResolver;
import com.colla.platform.modules.platform.contract.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PublicPlatformObjectRegistryAdapter implements PlatformObjectRegistry {
    private final PlatformObjectResolverRegistry registry;
    private final PlatformObjectRepository repository;

    public PublicPlatformObjectRegistryAdapter(
        PlatformObjectResolverRegistry registry,
        PlatformObjectRepository repository
    ) {
        this.registry = registry;
        this.repository = repository;
    }

    @Override
    public void register(PlatformObjectResolver resolver) {
        throw new UnsupportedOperationException("Resolvers are discovered by Spring at startup");
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(
        UUID workspaceId,
        UUID actorId,
        String objectType,
        UUID objectId
    ) {
        var value = registry.resolve(actor(workspaceId, actorId), objectType, objectId);
        return Optional.of(toPublic(value));
    }

    @Override
    public ObjectAccessState accessState(UUID workspaceId, UUID actorId, String objectType, UUID objectId) {
        return resolve(workspaceId, actorId, objectType, objectId)
            .map(PlatformObjectSummary::accessState)
            .orElse(ObjectAccessState.not_found);
    }

    @Override
    public Optional<PlatformObjectSummary> findLink(UUID workspaceId, String objectType, UUID objectId) {
        return repository.findObjectLink(workspaceId, objectType, objectId).map(link -> {
            ObjectAccessState state = link.deletedAt() == null ? ObjectAccessState.available : ObjectAccessState.deleted;
            return new PlatformObjectSummary(
                link.objectType(),
                link.objectId(),
                state,
                state == ObjectAccessState.available ? link.titleSnapshot() : null,
                null,
                null,
                state == ObjectAccessState.available ? link.webPath() : null,
                state == ObjectAccessState.available ? link.deepLink() : null,
                Map.of()
            );
        });
    }

    private CurrentUser actor(UUID workspaceId, UUID actorId) {
        return new CurrentUser(actorId, workspaceId, null, "", "", Set.of(), Set.of());
    }

    private PlatformObjectSummary toPublic(
        com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary summary
    ) {
        return new PlatformObjectSummary(
            summary.objectType(),
            summary.objectId(),
            ObjectAccessState.valueOf(summary.accessState().name()),
            summary.title(),
            summary.subtitle(),
            summary.status(),
            summary.webPath(),
            summary.deepLink(),
            summary.metadata()
        );
    }
}
