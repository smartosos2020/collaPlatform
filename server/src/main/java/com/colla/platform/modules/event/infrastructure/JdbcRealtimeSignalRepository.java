package com.colla.platform.modules.event.infrastructure;

import com.colla.platform.shared.realtime.RealtimeSignalEnvelope;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Audience;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.AudienceKind;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.ObjectReference;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.Sequence;
import com.colla.platform.shared.realtime.RealtimeSignalEnvelope.SequenceScope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRealtimeSignalRepository implements RealtimeSignalRepository {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRealtimeSignalRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean create(UUID sourceEventId, RealtimeSignalEnvelope envelope) {
        return jdbcTemplate.update(
            """
                insert into realtime_signals
                    (id, workspace_id, source_event_id, recipient_id, signal_type, object_type,
                     object_id, source_version, calibration_path, envelope_version, signal_version,
                     audience_type, sequence_scope, sequence_key, sequence_value, occurred_at,
                     correlation_id, payload)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                on conflict (source_event_id) do nothing
                """,
            envelope.signalId(),
            envelope.workspaceId(),
            sourceEventId,
            envelope.audience().recipientId(),
            envelope.signalType(),
            envelope.object().type(),
            envelope.object().id(),
            envelope.sequence().value(),
            envelope.calibrationPath(),
            envelope.envelopeVersion(),
            envelope.signalVersion(),
            envelope.audience().kind().name().toLowerCase(),
            envelope.sequence().scope().name().toLowerCase(),
            envelope.sequence().key(),
            envelope.sequence().value(),
            envelope.occurredAt(),
            envelope.correlationId(),
            writePayload(envelope.payload())
        ) == 1;
    }

    @Override
    public Optional<StoredRealtimeSignal> find(UUID signalId) {
        return jdbcTemplate.query(
            """
                select id, workspace_id, source_event_id, recipient_id, signal_type, object_type,
                       object_id, calibration_path, envelope_version, signal_version, audience_type,
                       sequence_scope, sequence_key, sequence_value, occurred_at, correlation_id,
                       payload, transported_at
                from realtime_signals
                where id = ?
                """,
            this::map,
            signalId
        ).stream().findFirst();
    }

    @Override
    public boolean markTransported(UUID signalId, Instant transportedAt) {
        return jdbcTemplate.update(
            "update realtime_signals set transported_at = ? where id = ? and transported_at is null",
            transportedAt,
            signalId
        ) == 1;
    }

    private StoredRealtimeSignal map(ResultSet resultSet, int rowNumber) throws SQLException {
        AudienceKind audienceKind = AudienceKind.valueOf(resultSet.getString("audience_type").toUpperCase());
        Audience audience = new Audience(audienceKind, resultSet.getObject("recipient_id", UUID.class));
        RealtimeSignalEnvelope envelope = new RealtimeSignalEnvelope(
            resultSet.getInt("envelope_version"),
            resultSet.getString("signal_type"),
            resultSet.getInt("signal_version"),
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("workspace_id", UUID.class),
            audience,
            new ObjectReference(
                resultSet.getString("object_type"),
                resultSet.getObject("object_id", UUID.class)
            ),
            new Sequence(
                SequenceScope.valueOf(resultSet.getString("sequence_scope").toUpperCase()),
                resultSet.getString("sequence_key"),
                resultSet.getLong("sequence_value")
            ),
            instant(resultSet, "occurred_at"),
            resultSet.getObject("correlation_id", UUID.class),
            resultSet.getString("calibration_path"),
            readPayload(resultSet.getString("payload"))
        );
        return new StoredRealtimeSignal(
            resultSet.getObject("source_event_id", UUID.class),
            envelope,
            instant(resultSet, "transported_at")
        );
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize realtime payload", exception);
        }
    }

    private Map<String, Object> readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize persisted realtime payload", exception);
        }
    }
}
