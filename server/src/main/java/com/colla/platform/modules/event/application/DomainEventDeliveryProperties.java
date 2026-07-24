package com.colla.platform.modules.event.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "colla.events.delivery")
public class DomainEventDeliveryProperties {
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration baseRetryDelay = Duration.ofSeconds(2);
    private Duration maxRetryDelay = Duration.ofMinutes(5);
    private Duration maxExecutionDuration = Duration.ofMinutes(10);
    private int maxAttempts = 8;
    private int maxClaimBatch = 100;
    private int maxMaintenanceBatch = 100;

    public void validate() {
        if (leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalStateException("colla.events.delivery.lease-duration must be positive");
        }
        if (baseRetryDelay.isNegative() || baseRetryDelay.isZero() || maxRetryDelay.compareTo(baseRetryDelay) < 0) {
            throw new IllegalStateException("colla.events.delivery retry delays are invalid");
        }
        if (maxExecutionDuration.compareTo(leaseDuration) < 0 || maxExecutionDuration.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException("colla.events.delivery.max-execution-duration must be between lease duration and one hour");
        }
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalStateException("colla.events.delivery.max-attempts must be between 1 and 100");
        }
        if (maxClaimBatch < 1 || maxClaimBatch > 1000 || maxMaintenanceBatch < 1 || maxMaintenanceBatch > 1000) {
            throw new IllegalStateException("colla.events.delivery batch limits must be between 1 and 1000");
        }
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getBaseRetryDelay() {
        return baseRetryDelay;
    }

    public void setBaseRetryDelay(Duration baseRetryDelay) {
        this.baseRetryDelay = baseRetryDelay;
    }

    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }

    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Duration getMaxExecutionDuration() {
        return maxExecutionDuration;
    }

    public void setMaxExecutionDuration(Duration maxExecutionDuration) {
        this.maxExecutionDuration = maxExecutionDuration;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getMaxClaimBatch() {
        return maxClaimBatch;
    }

    public void setMaxClaimBatch(int maxClaimBatch) {
        this.maxClaimBatch = maxClaimBatch;
    }

    public int getMaxMaintenanceBatch() {
        return maxMaintenanceBatch;
    }

    public void setMaxMaintenanceBatch(int maxMaintenanceBatch) {
        this.maxMaintenanceBatch = maxMaintenanceBatch;
    }
}
