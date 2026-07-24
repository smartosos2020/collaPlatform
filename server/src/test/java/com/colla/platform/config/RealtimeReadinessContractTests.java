package com.colla.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RealtimeReadinessContractTests {
    @Test
    void readinessGroupIncludesRealtimeRedisContributor() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(application)
            .contains("include: readinessState,runtimeRoleReadiness,domainEventWorkerReadiness,realtimeRedisReadiness");
    }
}
