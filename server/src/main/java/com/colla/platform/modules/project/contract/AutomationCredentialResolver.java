package com.colla.platform.modules.project.contract;

import java.util.Optional;
import java.util.UUID;

/** Public secret-owner port. Implementations return a value transiently; callers must never persist or log it. */
public interface AutomationCredentialResolver {
    Optional<char[]> resolve(UUID workspaceId, UUID actorId, String reference);
}
