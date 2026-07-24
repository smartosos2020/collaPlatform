package com.colla.platform.modules.event.infrastructure;

import java.util.UUID;

public interface RealtimeSignalRepository {
    boolean create(
        UUID signalId,
        UUID workspaceId,
        UUID sourceEventId,
        UUID recipientId,
        String signalType,
        String objectType,
        UUID objectId,
        long sourceVersion,
        String calibrationPath
    );
}
