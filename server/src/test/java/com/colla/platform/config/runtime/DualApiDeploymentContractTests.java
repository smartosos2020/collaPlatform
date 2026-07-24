package com.colla.platform.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DualApiDeploymentContractTests {
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void productionComposeUsesTwoStatelessApiInstancesAndSeparateRuntimeRoles() throws IOException {
        String compose = Files.readString(repository.resolve("deploy/docker-compose.prod.yml"));

        assertThat(compose).contains("api-a:", "api-b:", "COLLA_INSTANCE_ID: api-a", "COLLA_INSTANCE_ID: api-b");
        assertThat(occurrences(compose, "COLLA_RUNTIME_ROLE: api")).isEqualTo(2);
        assertThat(compose).contains(
            "COLLA_RUNTIME_ROLE: worker",
            "COLLA_RUNTIME_ROLE: event-gateway",
            "COLLA_RUNTIME_ROLE: maintenance",
            "SPRING_FLYWAY_ENABLED: \"false\"",
            "service_completed_successfully",
            "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE",
            "SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT",
            "SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT"
        );
        assertThat(compose).doesNotContain("COLLA_RUNTIME_ROLE: combined");
    }

    @Test
    void nginxBalancesApiNodesWithoutRetryingNonIdempotentRequests() throws IOException {
        String nginx = Files.readString(repository.resolve("deploy/nginx/colla.conf"));

        assertThat(nginx).contains(
            "resolver 127.0.0.11",
            "upstream api_nodes",
            "zone api_nodes",
            "server api-a:8080 resolve",
            "server api-b:8080 resolve",
            "proxy_pass http://api_nodes/api/",
            "upstream event_gateway_nodes",
            "proxy_pass http://event_gateway_nodes/ws/",
            "proxy_next_upstream error timeout http_502 http_503 http_504",
            "upstream=$upstream_addr"
        );
        assertThat(nginx).doesNotContain("proxy_next_upstream non_idempotent");
        assertThat(nginx).contains("location /actuator/health", "return 404");
    }

    private int occurrences(String source, String token) {
        return (source.length() - source.replace(token, "").length()) / token.length();
    }
}
