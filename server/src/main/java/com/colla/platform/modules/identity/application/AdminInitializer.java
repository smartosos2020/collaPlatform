package com.colla.platform.modules.identity.application;

import com.colla.platform.modules.identity.config.InitAdminProperties;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.shared.auth.PasswordHasher;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminInitializer implements ApplicationRunner {
    private final IdentityRepository identityRepository;
    private final PasswordHasher passwordHasher;
    private final InitAdminProperties properties;

    public AdminInitializer(IdentityRepository identityRepository, PasswordHasher passwordHasher, InitAdminProperties properties) {
        this.identityRepository = identityRepository;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (identityRepository.hasAnyUser()) {
            return;
        }
        UUID workspaceId = identityRepository.findDefaultWorkspaceId().orElseThrow();
        if (properties.getPassword() == null || properties.getPassword().isBlank()) {
            throw new IllegalStateException("Initial admin password must be configured");
        }
        UUID adminId = identityRepository.createUser(
            workspaceId,
            properties.getUsername(),
            passwordHasher.hash(properties.getPassword()),
            properties.getDisplayName(),
            properties.getEmail(),
            null
        );
        identityRepository.assignRole(workspaceId, adminId, "admin", adminId);
    }
}
