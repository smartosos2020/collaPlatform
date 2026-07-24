package com.colla.platform.modules.identity.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * Authentication-facing identity query without exposing sessions, credentials or repositories.
 */
public interface AuthenticationQuery {

    Optional<AuthenticatedMember> findActiveMember(UUID workspaceId, UUID userId);

    record AuthenticatedMember(UUID workspaceId, UUID userId, String username, String displayName) {
    }
}
