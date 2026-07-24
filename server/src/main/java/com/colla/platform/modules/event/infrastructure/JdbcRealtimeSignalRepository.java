package com.colla.platform.modules.event.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRealtimeSignalRepository implements RealtimeSignalRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcRealtimeSignalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean create(
        UUID signalId,
        UUID workspaceId,
        UUID sourceEventId,
        UUID recipientId,
        String signalType,
        String objectType,
        UUID objectId,
        long sourceVersion,
        String calibrationPath
    ) {
        return jdbcTemplate.update(
            """
                insert into realtime_signals
                    (id, workspace_id, source_event_id, recipient_id, signal_type, object_type,
                     object_id, source_version, calibration_path)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (source_event_id) do nothing
                """,
            signalId,
            workspaceId,
            sourceEventId,
            recipientId,
            signalType,
            objectType,
            objectId,
            sourceVersion,
            calibrationPath
        ) == 1;
    }
}
