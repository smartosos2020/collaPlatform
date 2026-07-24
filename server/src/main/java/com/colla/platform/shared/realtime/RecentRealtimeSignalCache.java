package com.colla.platform.shared.realtime;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-gateway bounded replay guard. Entries are deliberately not partitioned by
 * user so both memory and metrics stay independent of audience cardinality.
 */
final class RecentRealtimeSignalCache {
    private final int capacity;
    private final Map<SignalKey, Boolean> recent = new LinkedHashMap<>();

    RecentRealtimeSignalCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Recent realtime signal capacity must be positive");
        }
        this.capacity = capacity;
    }

    synchronized boolean firstSeen(RealtimeSignalEnvelope envelope) {
        SignalKey key = SignalKey.from(envelope);
        if (recent.containsKey(key)) {
            return false;
        }
        recent.put(key, Boolean.TRUE);
        if (recent.size() > capacity) {
            Iterator<SignalKey> iterator = recent.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    synchronized int size() {
        return recent.size();
    }

    private record SignalKey(
        UUID signalId,
        RealtimeSignalEnvelope.SequenceScope sequenceScope,
        String sequenceKey,
        long sequenceValue
    ) {
        private static SignalKey from(RealtimeSignalEnvelope envelope) {
            return new SignalKey(
                envelope.signalId(),
                envelope.sequence().scope(),
                envelope.sequence().key(),
                envelope.sequence().value()
            );
        }
    }
}
