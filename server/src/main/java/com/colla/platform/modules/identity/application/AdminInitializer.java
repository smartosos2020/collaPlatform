package com.colla.platform.modules.identity.application;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.modules.identity.config.InitAdminProperties;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.shared.auth.PasswordHasher;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.MAINTENANCE, RuntimeRole.COMBINED})
public class AdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

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
        identityRepository.lockAdminInitialization();
        if (identityRepository.hasAnyUser()) {
            log.info("maintenance_command command=admin-initialize result=skipped reason=active-user-exists");
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
        log.info("maintenance_command command=admin-initialize result=created userId={}", adminId);
    }
}
