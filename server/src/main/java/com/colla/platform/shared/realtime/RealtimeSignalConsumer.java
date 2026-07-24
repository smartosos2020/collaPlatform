package com.colla.platform.shared.realtime;

/**
 * Gateway-side port invoked only after an envelope passes common validation.
 */
public interface RealtimeSignalConsumer {
    void consume(RealtimeSignalEnvelope envelope);
}
