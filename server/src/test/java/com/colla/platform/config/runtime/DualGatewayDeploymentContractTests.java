package com.colla.platform.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DualGatewayDeploymentContractTests {
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void productionComposeUsesTwoInternalGatewaysWithSharedRedisAndReadinessHealth() throws Exception {
        String compose = Files.readString(repository.resolve("deploy/docker-compose.prod.yml"));
        String gatewaySection = compose.substring(
            compose.indexOf("  event-gateway-a:"),
            compose.indexOf("  collaboration-a:")
        );

        assertThat(gatewaySection).contains(
            "event-gateway-a:",
            "event-gateway-b:",
            "service: api-a",
            "service: event-gateway-a",
            "COLLA_INSTANCE_ID: event-gateway-a",
            "COLLA_INSTANCE_ID: event-gateway-b",
            "REDIS_HOST: redis",
            "REDIS_PORT: 6379",
            "/actuator/health/readiness"
        );
        assertThat(occurrences(gatewaySection, "COLLA_RUNTIME_ROLE: event-gateway")).isEqualTo(2);
        assertThat(gatewaySection).doesNotContain(
            "ports:",
            "COLLA_RUNTIME_ROLE: combined",
            "COLLA_RUNTIME_ROLE: api",
            "COLLA_RUNTIME_ROLE: worker"
        );
    }

    @Test
    void nginxBalancesBothGatewaysWithoutStickySessionsOrQueryStringLogging() throws Exception {
        String nginx = Files.readString(repository.resolve("deploy/nginx/colla.conf"));

        assertThat(nginx).contains(
            "upstream event_gateway_nodes",
            "least_conn;",
            "server event-gateway-a:8080 resolve max_fails=2 fail_timeout=5s;",
            "server event-gateway-b:8080 resolve max_fails=2 fail_timeout=5s;",
            "proxy_pass http://event_gateway_nodes/ws/;",
            "proxy_set_header Upgrade $http_upgrade;",
            "proxy_set_header Connection $connection_upgrade;",
            "\"$request_method $uri $server_protocol\"",
            "proxy_read_timeout 3600s;",
            "proxy_next_upstream_tries 2;"
        );
        assertThat(nginx).doesNotContain(
            "\"$request\"",
            "$request_uri",
            "$args",
            "$query_string",
            "ip_hash",
            "sticky"
        );
    }

    @Test
    void smokeCoversBothGatewaysAndSingleNodeFallback() throws Exception {
        String smoke = Files.readString(repository.resolve("deploy/smoke/dual-gateway-smoke.mjs"));

        assertThat(smoke).contains(
            "\"event-gateway-a\"",
            "\"event-gateway-b\"",
            "assertDistribution",
            "stopService(\"event-gateway-a\")",
            "stopService(\"event-gateway-b\", true)",
            "restoreGateways"
        );
    }

    @Test
    void operationsUseSplitRuntimeServiceNamesInsteadOfTheRemovedServerService() throws Exception {
        String release = Files.readString(repository.resolve("tools/workbench/src/operations/release.ts"));
        String backup = Files.readString(repository.resolve("tools/workbench/src/operations/backup.ts"));
        String restore = Files.readString(repository.resolve("tools/workbench/src/operations/restore.ts"));
        String health = Files.readString(repository.resolve("tools/workbench/src/operations/health.ts"));

        assertThat(release).contains(
            "'api-a'",
            "'api-b'",
            "'worker-a'",
            "'worker-b'",
            "'event-gateway-a'",
            "'event-gateway-b'"
        ).doesNotContain("'server'");
        assertThat(backup).contains("'event-gateway-a'", "'event-gateway-b'").doesNotContain("services.includes('server')");
        assertThat(restore).contains("'event-gateway-a'", "'event-gateway-b'").doesNotContain("'nginx', 'web', 'server'");
        assertThat(health).contains("'api-a', 'api-b'").doesNotContain("'2m', 'server'");
    }

    private int occurrences(String source, String token) {
        return (source.length() - source.replace(token, "").length()) / token.length();
    }
}
