package com.colla.platform.modules.platform.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * Provider SPI implemented by a business module and registered through the platform contract.
 */
public interface PlatformObjectResolver {

    String objectType();

    Optional<PlatformObjectSummary> resolve(UUID workspaceId, UUID actorId, UUID objectId);

    ObjectAccessState accessState(UUID workspaceId, UUID actorId, UUID objectId);
}
