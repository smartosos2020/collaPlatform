package com.colla.platform.modules.platform.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * Public registry/query facade. Platform depends on provider SPI implementations, never provider private packages.
 */
public interface PlatformObjectRegistry {

    void register(PlatformObjectResolver resolver);

    Optional<PlatformObjectSummary> resolve(UUID workspaceId, UUID actorId, String objectType, UUID objectId);

    ObjectAccessState accessState(UUID workspaceId, UUID actorId, String objectType, UUID objectId);

    Optional<PlatformObjectSummary> findLink(UUID workspaceId, String objectType, UUID objectId);
}
