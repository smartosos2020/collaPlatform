package com.colla.platform.shared.realtime;

/**
 * Worker-side port. Implementations may use Redis, but callers only observe a
 * transport-neutral result and leave retry decisions to domain-event delivery.
 */
public interface RealtimeSignalPublisher {

    PublishResult publish(RealtimeSignalEnvelope envelope);

    record PublishResult(boolean published, long subscriberCount, String failure) {
        public static PublishResult published(long subscriberCount) {
            return new PublishResult(true, Math.max(0, subscriberCount), null);
        }

        public static PublishResult failed(String failure) {
            String safeFailure = failure == null || failure.isBlank() ? "realtime transport failed" : failure;
            return new PublishResult(false, 0, safeFailure);
        }
    }
}
