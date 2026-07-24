package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.identity.contract.AuthenticationQuery;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationQueryService implements AuthenticationQuery {
    private final IdentityRepository identityRepository;

    public AuthenticationQueryService(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    @Override
    public Optional<AuthenticatedMember> findActiveMember(UUID workspaceId, UUID userId) {
        return identityRepository.findUserById(userId)
            .filter(user -> workspaceId.equals(user.workspaceId()) && "active".equals(user.status()))
            .map(user -> new AuthenticatedMember(workspaceId, user.id(), user.username(), user.displayName()));
    }
}
