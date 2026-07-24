package com.colla.platform.modules.event.infrastructure;

import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RealtimeSignalRepository {
    boolean create(UUID sourceEventId, RealtimeSignalEnvelope envelope);

    Optional<StoredRealtimeSignal> find(UUID signalId);

    boolean markTransported(UUID signalId, Instant transportedAt);

    record StoredRealtimeSignal(
        UUID sourceEventId,
        RealtimeSignalEnvelope envelope,
        Instant transportedAt
    ) {
        public boolean transported() {
            return transportedAt != null;
        }
    }
}
