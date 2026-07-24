package com.colla.platform.modules.platform.contract;

import java.util.UUID;

/**
 * Commands owned by the platform object module.
 */
public interface PlatformObjectCommands {

    void upsertLink(
        UUID workspaceId,
        String objectType,
        UUID objectId,
        String webPath,
        String deepLink,
        String titleSnapshot,
        UUID actorId
    );

    void removeLink(UUID workspaceId, String objectType, UUID objectId, UUID actorId);

    void setFavorite(UUID workspaceId, UUID actorId, String objectType, UUID objectId, boolean favorite);
}
