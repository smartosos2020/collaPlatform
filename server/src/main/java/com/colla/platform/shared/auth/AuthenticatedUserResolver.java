package com.colla.platform.shared.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * Inbound authentication port implemented by the identity module.
 */
public interface AuthenticatedUserResolver {

    Optional<CurrentUser> resolve(UUID userId, UUID deviceId);
}
