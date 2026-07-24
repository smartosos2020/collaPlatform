package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.shared.auth.AuthenticatedUserResolver;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthenticatedUserResolverService implements AuthenticatedUserResolver {
    private final IdentityRepository identityRepository;

    public AuthenticatedUserResolverService(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    @Override
    public Optional<CurrentUser> resolve(UUID userId, UUID deviceId) {
        return identityRepository.findCurrentUser(userId, deviceId);
    }
}
