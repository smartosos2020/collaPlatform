package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.EventDelivery;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.FailureKind;
import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainEventFailureClassifierTests {

    @Test
    void classifiesPermanentAndRedactsSensitiveErrors() {
        DomainEventFailureClassifier classifier = new DomainEventFailureClassifier(properties(3));
        var decision = classifier.classify(
            delivery(1),
            new DomainEventPermanentFailureException("password=hunter2 cannot be used"),
            Instant.parse("2026-07-24T00:00:00Z")
        );

        assertThat(decision.kind()).isEqualTo(FailureKind.PERMANENT);
        assertThat(decision.deadLetter()).isTrue();
        assertThat(decision.nextAttemptAt()).isNull();
        assertThat(decision.errorSummary()).contains("password=[REDACTED]").doesNotContain("hunter2");
        assertThat(decision.errorFingerprint()).hasSize(64);
    }

    @Test
    void appliesBoundedBackoffAndDeadLettersAtAttemptLimit() {
        DomainEventFailureClassifier classifier = new DomainEventFailureClassifier(properties(3));
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        var retry = classifier.classify(delivery(1), new DomainEventTransientFailureException("database unavailable"), now);
        var terminal = classifier.classify(delivery(3), new IllegalStateException("unknown"), now);

        assertThat(retry.kind()).isEqualTo(FailureKind.TRANSIENT);
        assertThat(retry.deadLetter()).isFalse();
        assertThat(retry.nextAttemptAt()).isAfter(now).isBeforeOrEqualTo(now.plusSeconds(3));
        assertThat(terminal.kind()).isEqualTo(FailureKind.UNKNOWN);
        assertThat(terminal.deadLetter()).isTrue();
    }

    private static DomainEventDeliveryProperties properties(int maxAttempts) {
        DomainEventDeliveryProperties properties = new DomainEventDeliveryProperties();
        properties.setLeaseDuration(Duration.ofSeconds(10));
        properties.setMaxExecutionDuration(Duration.ofMinutes(1));
        properties.setBaseRetryDelay(Duration.ofSeconds(2));
        properties.setMaxRetryDelay(Duration.ofSeconds(10));
        properties.setMaxAttempts(maxAttempts);
        return properties;
    }

    private static EventDelivery delivery(int attemptCount) {
        UUID eventId = UUID.randomUUID();
        DomainEvent event = new DomainEvent(
            eventId,
            UUID.randomUUID(),
            "project.updated",
            1,
            "project",
            UUID.randomUUID(),
            1,
            null,
            "test:" + eventId,
            eventId,
            null,
            Instant.now(),
            Map.of(),
            0,
            Instant.now()
        );
        return new EventDelivery(
            UUID.randomUUID(),
            event,
            "test.project",
            1,
            false,
            "processing",
            attemptCount,
            "worker-a",
            1,
            Instant.now(),
            Instant.now().plusSeconds(10),
            null,
            null,
            null,
            null
        );
    }
}
