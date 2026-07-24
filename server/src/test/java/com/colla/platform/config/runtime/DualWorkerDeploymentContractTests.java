package com.colla.platform.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DualWorkerDeploymentContractTests {
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void productionComposeUsesTwoBoundedWorkerInstancesWithoutPublishedPorts() throws Exception {
        String compose = Files.readString(repository.resolve("deploy/docker-compose.prod.yml"));
        String workerSection = compose.substring(compose.indexOf("  worker-a:"), compose.indexOf("  event-gateway:"));

        assertThat(workerSection).contains(
            "worker-a:", "worker-b:", "COLLA_INSTANCE_ID: worker-a", "COLLA_INSTANCE_ID: worker-b",
            "COLLA_RUNTIME_ROLE: worker", "COLLA_EVENT_WORKER_ENABLED: \"true\"",
            "COLLA_EVENT_WORKER_CONNECTION_BUDGET", "stop_grace_period", "memory:", "cpus:"
        );
        assertThat(workerSection).doesNotContain("ports:", "COLLA_RUNTIME_ROLE: api", "COLLA_RUNTIME_ROLE: combined");
        assertThat(occurrences(workerSection, "SPRING_FLYWAY_ENABLED: \"false\"")).isEqualTo(2);
    }

    @Test
    void fleetAutomationSupportsDryRunScaleRollbackAndRollingReplacement() throws Exception {
        String script = Files.readString(repository.resolve("deploy/worker-fleet.mjs"));
        assertThat(script).contains(
            "--dry-run", "\"scale\"", "\"rollout\"", "\"status\"",
            "\"worker-a\"", "\"worker-b\"", "\"stop\"", "\"--force-recreate\""
        );
    }

    private int occurrences(String source, String token) {
        return (source.length() - source.replace(token, "").length()) / token.length();
    }
}
