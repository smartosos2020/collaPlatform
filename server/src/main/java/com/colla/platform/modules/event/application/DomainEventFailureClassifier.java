package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.DomainEventHandlingException;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.EventDelivery;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.FailureDecision;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.FailureKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DomainEventFailureClassifier {
    private static final int MAX_ERROR_LENGTH = 2048;
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
        "(?i)(password|secret|token|access[_-]?key|private[_-]?key)\\s*[:=]\\s*[^\\s,;]+"
    );
    private final DomainEventDeliveryProperties properties;

    public DomainEventFailureClassifier(DomainEventDeliveryProperties properties) {
        this.properties = properties;
        properties.validate();
    }

    public FailureDecision classify(EventDelivery delivery, Throwable failure, Instant now) {
        FailureKind kind = failure instanceof DomainEventPermanentFailureException
            || failure instanceof DomainEventHandlingException.Permanent
            ? FailureKind.PERMANENT
            : failure instanceof DomainEventTransientFailureException
                || failure instanceof DomainEventHandlingException.Transient
                ? FailureKind.TRANSIENT
                : FailureKind.UNKNOWN;
        String summary = summarize(failure);
        boolean deadLetter = kind == FailureKind.PERMANENT || delivery.attemptCount() >= properties.getMaxAttempts();
        Instant nextAttemptAt = deadLetter ? null : now.plus(delay(delivery));
        return new FailureDecision(kind, deadLetter, nextAttemptAt, summary, fingerprint(failure, summary));
    }

    private Duration delay(EventDelivery delivery) {
        long exponent = Math.min(Math.max(delivery.attemptCount() - 1, 0), 20);
        long baseMillis = properties.getBaseRetryDelay().toMillis();
        long capped = Math.min(properties.getMaxRetryDelay().toMillis(), baseMillis * (1L << exponent));
        long spread = Math.max(1, capped / 5);
        long seed = Math.floorMod(delivery.id().getLeastSignificantBits(), spread * 2 + 1);
        return Duration.ofMillis(Math.max(1, capped - spread + seed));
    }

    private static String summarize(Throwable failure) {
        String raw = failure == null
            ? "unknown failure"
            : failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        String redacted = SECRET_ASSIGNMENT.matcher(raw).replaceAll("$1=[REDACTED]");
        return redacted.substring(0, Math.min(redacted.length(), MAX_ERROR_LENGTH));
    }

    private static String fingerprint(Throwable failure, String summary) {
        try {
            String source = (failure == null ? "unknown" : failure.getClass().getName()) + ":" + summary;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot fingerprint domain event failure", exception);
        }
    }
}
