package com.colla.platform.modules.event.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "colla.events.worker")
public class DomainEventWorkerProperties {
    private boolean enabled;
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration recoveryInterval = Duration.ofSeconds(15);
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private Duration shutdownGrace = Duration.ofSeconds(25);
    private int claimBatch = 20;
    private int concurrency = 4;
    private int queueCapacity = 16;
    private int connectionBudget = 6;
    private int expectedInstances = 2;
    private int postgresqlConnectionBudget = 100;

    public void validate(DomainEventDeliveryProperties delivery) {
        if (pollInterval.compareTo(Duration.ofMillis(100)) < 0 || pollInterval.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalStateException("colla.events.worker.poll-interval must be between 100ms and one minute");
        }
        if (recoveryInterval.compareTo(pollInterval) < 0 || recoveryInterval.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalStateException("colla.events.worker.recovery-interval must be between poll interval and five minutes");
        }
        if (heartbeatInterval.isZero() || heartbeatInterval.isNegative()
            || heartbeatInterval.compareTo(delivery.getLeaseDuration()) >= 0) {
            throw new IllegalStateException("colla.events.worker.heartbeat-interval must be positive and shorter than the lease");
        }
        if (shutdownGrace.isNegative() || shutdownGrace.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalStateException("colla.events.worker.shutdown-grace must be between zero and 30 seconds");
        }
        if (concurrency < 1 || concurrency > 32 || queueCapacity < 0 || queueCapacity > 1000) {
            throw new IllegalStateException("colla.events.worker concurrency or queue capacity is outside the supported range");
        }
        if (claimBatch < 1 || claimBatch > delivery.getMaxClaimBatch()) {
            throw new IllegalStateException("colla.events.worker.claim-batch must fit the delivery claim limit");
        }
        if (connectionBudget < concurrency + 2) {
            throw new IllegalStateException("worker connection budget must reserve one connection per task plus claim and health capacity");
        }
        if (expectedInstances < 1 || expectedInstances > 32
            || (long) expectedInstances * connectionBudget > postgresqlConnectionBudget) {
            throw new IllegalStateException("worker fleet connection budget exceeds the configured PostgreSQL allowance");
        }
    }

    public int capacity() {
        return concurrency + queueCapacity;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public Duration getRecoveryInterval() { return recoveryInterval; }
    public void setRecoveryInterval(Duration recoveryInterval) { this.recoveryInterval = recoveryInterval; }
    public Duration getHeartbeatInterval() { return heartbeatInterval; }
    public void setHeartbeatInterval(Duration heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
    public Duration getShutdownGrace() { return shutdownGrace; }
    public void setShutdownGrace(Duration shutdownGrace) { this.shutdownGrace = shutdownGrace; }
    public int getClaimBatch() { return claimBatch; }
    public void setClaimBatch(int claimBatch) { this.claimBatch = claimBatch; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getConnectionBudget() { return connectionBudget; }
    public void setConnectionBudget(int connectionBudget) { this.connectionBudget = connectionBudget; }
    public int getExpectedInstances() { return expectedInstances; }
    public void setExpectedInstances(int expectedInstances) { this.expectedInstances = expectedInstances; }
    public int getPostgresqlConnectionBudget() { return postgresqlConnectionBudget; }
    public void setPostgresqlConnectionBudget(int postgresqlConnectionBudget) { this.postgresqlConnectionBudget = postgresqlConnectionBudget; }
}
