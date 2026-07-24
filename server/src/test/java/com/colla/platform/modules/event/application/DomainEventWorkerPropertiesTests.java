package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DomainEventWorkerPropertiesTests {
    private final DomainEventDeliveryProperties delivery = new DomainEventDeliveryProperties();

    @Test
    void acceptsBoundedDefaultFleetContract() {
        new DomainEventWorkerProperties().validate(delivery);
    }

    @Test
    void rejectsHeartbeatQueueAndConnectionBudgetViolations() {
        DomainEventWorkerProperties heartbeat = new DomainEventWorkerProperties();
        heartbeat.setHeartbeatInterval(Duration.ofSeconds(30));
        assertThatThrownBy(() -> heartbeat.validate(delivery)).isInstanceOf(IllegalStateException.class);

        DomainEventWorkerProperties connection = new DomainEventWorkerProperties();
        connection.setConcurrency(8);
        connection.setConnectionBudget(6);
        assertThatThrownBy(() -> connection.validate(delivery)).isInstanceOf(IllegalStateException.class);

        DomainEventWorkerProperties fleet = new DomainEventWorkerProperties();
        fleet.setExpectedInstances(20);
        fleet.setConnectionBudget(6);
        fleet.setPostgresqlConnectionBudget(100);
        assertThatThrownBy(() -> fleet.validate(delivery)).isInstanceOf(IllegalStateException.class);
    }
}
