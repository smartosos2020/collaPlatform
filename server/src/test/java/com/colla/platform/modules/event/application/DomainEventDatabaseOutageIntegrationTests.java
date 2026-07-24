package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.config.runtime.RuntimeRoleProperties;
import com.colla.platform.modules.event.infrastructure.JdbcDomainEventDeliveryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class DomainEventDatabaseOutageIntegrationTests {

    @Test
    void realPostgresOutageStopsClaimsDropsReadinessAndRecovers() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")) {
            postgres.start();
            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(postgres.getJdbcUrl());
            hikari.setUsername(postgres.getUsername());
            hikari.setPassword(postgres.getPassword());
            hikari.setMaximumPoolSize(1);
            hikari.setMinimumIdle(0);
            hikari.setConnectionTimeout(1_000);
            hikari.setValidationTimeout(500);
            hikari.addDataSourceProperty("socketTimeout", "1");
            hikari.addDataSourceProperty("connectTimeout", "1");

            try (HikariDataSource dataSource = new HikariDataSource(hikari)) {
                DomainEventDeliveryProperties delivery = new DomainEventDeliveryProperties();
                delivery.setLeaseDuration(Duration.ofSeconds(10));
                delivery.setMaxExecutionDuration(Duration.ofMinutes(1));
                JdbcDomainEventDeliveryRepository repository = new JdbcDomainEventDeliveryRepository(
                    new JdbcTemplate(dataSource),
                    new ObjectMapper()
                );
                DomainEventDeliveryCoordinator coordinator = new DomainEventDeliveryCoordinator(
                    repository,
                    new DomainEventFailureClassifier(delivery),
                    delivery
                );
                ReliableDomainEventWorker worker = worker(coordinator, delivery);
                worker.start();
                try {
                    worker.pollOnce();
                    assertThat(worker.ready()).isTrue();
                    assertThat(worker.readinessDetail()).isEqualTo("ready");

                    postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
                    try {
                        worker.pollOnce();
                        assertThat(worker.ready()).isFalse();
                        assertThat(worker.readinessDetail()).startsWith("poll-failed:");
                        assertThat(coordinator.stats(java.time.Instant.now())).isNotNull();
                    } catch (RuntimeException expectedWhilePaused) {
                        assertThat(worker.ready()).isFalse();
                    } finally {
                        postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
                    }

                    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                    while (!worker.ready() && System.nanoTime() < deadline) {
                        worker.pollOnce();
                        Thread.sleep(100);
                    }
                    assertThat(worker.ready()).isTrue();
                    assertThat(worker.readinessDetail()).isEqualTo("ready");
                } finally {
                    worker.stop();
                }
            }
        }
    }

    private static ReliableDomainEventWorker worker(
        DomainEventDeliveryCoordinator coordinator,
        DomainEventDeliveryProperties delivery
    ) {
        DomainEventWorkerProperties properties = new DomainEventWorkerProperties();
        properties.setEnabled(true);
        properties.setConcurrency(1);
        properties.setQueueCapacity(0);
        properties.setClaimBatch(1);
        properties.setConnectionBudget(3);
        properties.setExpectedInstances(1);
        properties.setPollInterval(Duration.ofMillis(100));
        properties.setRecoveryInterval(Duration.ofMillis(100));
        properties.setHeartbeatInterval(Duration.ofSeconds(2));
        properties.setShutdownGrace(Duration.ofSeconds(1));
        RuntimeRoleProperties runtime = new RuntimeRoleProperties();
        runtime.setRole("worker");
        runtime.setInstanceId("s03-db-outage-worker");
        return new ReliableDomainEventWorker(
            coordinator,
            new DomainEventHandlerRegistry(List.of()),
            properties,
            delivery,
            runtime,
            new SimpleMeterRegistry()
        );
    }
}
