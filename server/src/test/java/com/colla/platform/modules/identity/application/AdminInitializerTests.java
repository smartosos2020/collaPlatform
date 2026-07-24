package com.colla.platform.modules.identity.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.identity.config.InitAdminProperties;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.shared.auth.PasswordHasher;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;

class AdminInitializerTests {
    @Test
    void locksAndRechecksBeforeCreatingTheInitialAdministrator() throws Exception {
        IdentityRepository repository = mock(IdentityRepository.class);
        PasswordHasher hasher = mock(PasswordHasher.class);
        InitAdminProperties properties = properties();
        UUID workspaceId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(repository.hasAnyUser()).thenReturn(false);
        when(repository.findDefaultWorkspaceId()).thenReturn(java.util.Optional.of(workspaceId));
        when(hasher.hash(properties.getPassword())).thenReturn("hash");
        when(repository.createUser(
            workspaceId,
            properties.getUsername(),
            "hash",
            properties.getDisplayName(),
            properties.getEmail(),
            null
        )).thenReturn(adminId);

        new AdminInitializer(repository, hasher, properties).run(new DefaultApplicationArguments());

        InOrder order = inOrder(repository);
        order.verify(repository).lockAdminInitialization();
        order.verify(repository).hasAnyUser();
        order.verify(repository).createUser(
            workspaceId,
            properties.getUsername(),
            "hash",
            properties.getDisplayName(),
            properties.getEmail(),
            null
        );
        order.verify(repository).assignRole(workspaceId, adminId, "admin", adminId);
    }

    @Test
    void concurrentReplaySkipsAfterTakingTheDatabaseLock() throws Exception {
        IdentityRepository repository = mock(IdentityRepository.class);
        PasswordHasher hasher = mock(PasswordHasher.class);
        when(repository.hasAnyUser()).thenReturn(true);

        new AdminInitializer(repository, hasher, properties()).run(new DefaultApplicationArguments());

        InOrder order = inOrder(repository);
        order.verify(repository).lockAdminInitialization();
        order.verify(repository).hasAnyUser();
        verify(repository, never()).createUser(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private InitAdminProperties properties() {
        InitAdminProperties properties = new InitAdminProperties();
        properties.setUsername("admin");
        properties.setPassword("admin123456");
        properties.setDisplayName("Administrator");
        properties.setEmail("admin@colla.local");
        return properties;
    }
}
