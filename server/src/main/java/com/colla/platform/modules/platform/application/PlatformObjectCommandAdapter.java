package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.contract.PlatformObjectCommands;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlatformObjectCommandAdapter implements PlatformObjectCommands {
    private final PlatformObjectRepository repository;

    public PlatformObjectCommandAdapter(PlatformObjectRepository repository) {
        this.repository = repository;
    }

    @Override
    public void upsertLink(
        UUID workspaceId,
        String objectType,
        UUID objectId,
        String webPath,
        String deepLink,
        String titleSnapshot,
        UUID actorId
    ) {
        repository.upsertObjectLink(workspaceId, objectType, objectId, webPath, deepLink, titleSnapshot);
    }

    @Override
    public void removeLink(UUID workspaceId, String objectType, UUID objectId, UUID actorId) {
        repository.markObjectLinkDeleted(workspaceId, objectType, objectId);
    }

    @Override
    public void setFavorite(UUID workspaceId, UUID actorId, String objectType, UUID objectId, boolean favorite) {
        if (favorite) {
            repository.addFavorite(workspaceId, actorId, objectType, objectId);
        } else {
            repository.removeFavorite(workspaceId, actorId, objectType, objectId);
        }
    }
}
