package com.colla.platform.modules.event.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("domainEventWorkerReadiness")
public class DomainEventWorkerHealthIndicator implements HealthIndicator {
    private final DomainEventWorkerProperties properties;
    private final ObjectProvider<ReliableDomainEventWorker> workerProvider;

    public DomainEventWorkerHealthIndicator(
        DomainEventWorkerProperties properties,
        ObjectProvider<ReliableDomainEventWorker> workerProvider
    ) {
        this.properties = properties;
        this.workerProvider = workerProvider;
    }

    @Override
    public Health health() {
        ReliableDomainEventWorker worker = workerProvider.getIfAvailable();
        if (!properties.isEnabled() || worker == null) {
            return Health.up().withDetail("eventWorker", "disabled").build();
        }
        Health.Builder builder = worker.ready() ? Health.up() : Health.outOfService();
        return builder
            .withDetail("eventWorker", worker.readinessDetail())
            .withDetail("acceptingClaims", worker.acceptingClaims())
            .withDetail("activeTasks", worker.activeTasks())
            .withDetail("queueDepth", worker.queueDepth())
            .build();
    }
}
