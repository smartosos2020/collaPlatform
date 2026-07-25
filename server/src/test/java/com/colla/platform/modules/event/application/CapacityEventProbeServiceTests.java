package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.event.contract.TransactionalOutbox.EventEnvelope;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerCursor;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerEntry;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityLedgerSlice;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.CapacityRunSummary;
import com.colla.platform.modules.event.domain.DomainEventModels.DomainEvent;
import com.colla.platform.modules.event.infrastructure.DomainEventDeliveryRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CapacityEventProbeServiceTests {
    private static final String SECRET = "s".repeat(64);
    private final CurrentUser admin = new CurrentUser(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "admin",
        "Administrator",
        Set.of("admin"),
        Set.of("admin.access")
    );

    @Test
    void producesOnlyTheFixedRealtimeProbeAndReturnsAStableAcknowledgement() {
        CapacityEventProbeProperties properties = enabledProperties();
        DomainEventRepository eventRepository = mock(DomainEventRepository.class);
        DomainEventDeliveryRepository deliveryRepository = mock(DomainEventDeliveryRepository.class);
        AtomicReference<EventEnvelope> appended = new AtomicReference<>();
        TransactionalOutbox outbox = envelope -> {
            appended.set(envelope);
            return envelope.eventId();
        };
        when(eventRepository.findById(any())).thenAnswer(invocation -> {
            EventEnvelope envelope = appended.get();
            if (envelope == null || !envelope.eventId().equals(invocation.getArgument(0))) {
                return Optional.empty();
            }
            return Optional.of(event(envelope, 7));
        });
        when(deliveryRepository.countCapacityProbeEvents(admin.workspaceId(), UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
        ))).thenReturn(0L);
        CapacityEventProbeService service = new CapacityEventProbeService(
            properties,
            outbox,
            eventRepository,
            deliveryRepository
        );
        UUID runId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        var acknowledgement = service.produce(admin, runId, SECRET, "aggregate-a", "request-0001");

        assertThat(acknowledgement.eventId()).isEqualTo(appended.get().eventId());
        assertThat(acknowledgement.aggregateId()).isEqualTo(appended.get().aggregateId());
        assertThat(acknowledgement.sequence()).isEqualTo(7);
        assertThat(acknowledgement.sideEffectId()).isNotNull();
        assertThat(acknowledgement.realtime().eventId()).isEqualTo(acknowledgement.sideEffectId());
        assertThat(acknowledgement.realtime().workspaceId()).isEqualTo(admin.workspaceId());
        assertThat(acknowledgement.realtime().sequenceScope()).isEqualTo("object");
        assertThat(acknowledgement.realtime().sequenceKey())
            .isEqualTo("capacity_probe:" + acknowledgement.aggregateId());
        assertThat(acknowledgement.realtime().businessObjectId()).isEqualTo(acknowledgement.aggregateId());
        assertThat(acknowledgement.realtime().calibrationPath())
            .isEqualTo("/api/admin/event-deliveries/capacity-runs/" + runId + "/ledger");
        assertThat(appended.get().eventType()).isEqualTo("realtime.signal.requested");
        assertThat(appended.get().eventVersion()).isEqualTo(1);
        assertThat(appended.get().aggregateType()).isEqualTo("capacity_probe");
        assertThat(appended.get().correlationId()).isEqualTo(runId);
        assertThat(appended.get().payload())
            .containsEntry("signalType", "capacity.probe")
            .containsEntry("objectType", "capacity_probe")
            .containsEntry(
                "calibrationPath",
                "/api/admin/event-deliveries/capacity-runs/" + runId + "/ledger"
            );
        assertThat(appended.get().payload()).doesNotContainKeys("eventType", "handlerKey", "recipientId");
        verify(deliveryRepository).lockCapacityRun(admin.workspaceId(), runId);

        var replay = service.produce(admin, runId, SECRET, "aggregate-a", "request-0001");
        assertThat(replay).isEqualTo(acknowledgement);
    }

    @Test
    void rejectsRequestKeyReuseWhenAggregateOrPayloadSemanticsConflict() {
        CapacityEventProbeProperties properties = enabledProperties();
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        DomainEventRepository events = mock(DomainEventRepository.class);
        DomainEventDeliveryRepository deliveries = mock(DomainEventDeliveryRepository.class);
        UUID runId = UUID.randomUUID();
        EventEnvelope expected = probeEnvelope(admin, runId, "aggregate-a", "request-0001");
        when(events.findById(expected.eventId())).thenReturn(Optional.of(event(expected, 1)));
        CapacityEventProbeService service = new CapacityEventProbeService(
            properties,
            outbox,
            events,
            deliveries
        );

        assertConflict(
            () -> service.produce(admin, runId, SECRET, "aggregate-b", "request-0001")
        );

        EventEnvelope payloadConflict = new EventEnvelope(
            expected.eventId(),
            expected.workspaceId(),
            expected.eventType(),
            expected.eventVersion(),
            expected.aggregateType(),
            expected.aggregateId(),
            expected.actorId(),
            expected.idempotencyKey(),
            expected.correlationId(),
            expected.causationId(),
            expected.occurredAt(),
            Map.of("signalType", "capacity.probe.changed")
        );
        when(events.findById(expected.eventId())).thenReturn(Optional.of(event(payloadConflict, 1)));

        assertConflict(
            () -> service.produce(admin, runId, SECRET, "aggregate-a", "request-0001")
        );
        verify(outbox, never()).append(any());
    }

    @Test
    void isDisabledByDefaultAndRequiresTheIndependentSecret() {
        CapacityEventProbeProperties disabled = new CapacityEventProbeProperties();
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        DomainEventRepository events = mock(DomainEventRepository.class);
        DomainEventDeliveryRepository deliveries = mock(DomainEventDeliveryRepository.class);
        CapacityEventProbeService disabledService = new CapacityEventProbeService(disabled, outbox, events, deliveries);

        assertStatus(
            HttpStatus.NOT_FOUND,
            () -> disabledService.produce(admin, UUID.randomUUID(), SECRET, "aggregate", "request")
        );
        verify(outbox, never()).append(any());

        CapacityEventProbeProperties enabled = enabledProperties();
        CapacityEventProbeService activeService = new CapacityEventProbeService(enabled, outbox, events, deliveries);
        assertStatus(
            HttpStatus.FORBIDDEN,
            () -> activeService.summary(admin, UUID.randomUUID(), "wrong")
        );
    }

    @Test
    void enforcesPerRunLimitsAndClampsLedgerPages() {
        CapacityEventProbeProperties properties = enabledProperties();
        properties.setMaxEventsPerRun(2);
        properties.setMaxPageSize(50);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        DomainEventRepository events = mock(DomainEventRepository.class);
        DomainEventDeliveryRepository deliveries = mock(DomainEventDeliveryRepository.class);
        when(events.findById(any())).thenReturn(Optional.empty());
        when(deliveries.countCapacityProbeEvents(any(), any())).thenReturn(2L);
        when(deliveries.capacityRunLedger(any(), any(), anyString(), any(), anyInt()))
            .thenReturn(new CapacityLedgerSlice(List.of(), null));
        CapacityEventProbeService service = new CapacityEventProbeService(properties, outbox, events, deliveries);
        UUID runId = UUID.randomUUID();

        assertStatus(
            HttpStatus.TOO_MANY_REQUESTS,
            () -> service.produce(admin, runId, SECRET, "aggregate", "request")
        );
        service.ledger(admin, runId, SECRET, null, 10_000);
        verify(deliveries).capacityRunLedger(admin.workspaceId(), runId, "realtime.signal", null, 50);
    }

    @Test
    void locksBeforeReturningAnExistingEventAtTheRunLimit() {
        CapacityEventProbeProperties properties = enabledProperties();
        properties.setMaxEventsPerRun(1);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        DomainEventRepository events = mock(DomainEventRepository.class);
        DomainEventDeliveryRepository deliveries = mock(DomainEventDeliveryRepository.class);
        UUID runId = UUID.randomUUID();
        EventEnvelope envelope = probeEnvelope(admin, runId, "aggregate", "request");
        when(events.findById(any())).thenReturn(Optional.of(event(envelope, 1)));
        when(deliveries.countCapacityProbeEvents(any(), any())).thenReturn(1L);
        CapacityEventProbeService service = new CapacityEventProbeService(properties, outbox, events, deliveries);

        assertThat(service.produce(admin, runId, SECRET, "aggregate", "request").eventId())
            .isEqualTo(envelope.eventId());
        var order = org.mockito.Mockito.inOrder(deliveries, events);
        order.verify(deliveries).lockCapacityRun(admin.workspaceId(), runId);
        order.verify(events).findById(any());
        verify(deliveries, never()).countCapacityProbeEvents(any(), any());
        verify(outbox, never()).append(any());
    }

    @Test
    void acceptsAdminPermissionWithoutRoleAndRejectsUnprivilegedActors() {
        CapacityEventProbeProperties properties = enabledProperties();
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        DomainEventRepository events = mock(DomainEventRepository.class);
        DomainEventDeliveryRepository deliveries = mock(DomainEventDeliveryRepository.class);
        when(deliveries.currentTime()).thenReturn(Instant.parse("2026-07-25T10:00:00Z"));
        when(deliveries.capacityRunSummary(any(), any(), anyString(), any()))
            .thenReturn(new CapacityRunSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        CapacityEventProbeService service = new CapacityEventProbeService(properties, outbox, events, deliveries);
        CurrentUser permissionAdmin = new CurrentUser(
            UUID.randomUUID(),
            admin.workspaceId(),
            null,
            "operator",
            "Operator",
            Set.of(),
            Set.of("admin.access")
        );
        CurrentUser member = new CurrentUser(
            UUID.randomUUID(),
            admin.workspaceId(),
            null,
            "member",
            "Member",
            Set.of("member"),
            Set.of()
        );

        assertThat(service.summary(permissionAdmin, UUID.randomUUID(), SECRET).total()).isZero();
        assertStatus(
            HttpStatus.FORBIDDEN,
            () -> service.summary(member, UUID.randomUUID(), SECRET)
        );
    }

    @Test
    void bindsSignedMembershipWatermarkCursorAndRejectsTampering() {
        CapacityEventProbeProperties properties = enabledProperties();
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        DomainEventRepository events = mock(DomainEventRepository.class);
        DomainEventDeliveryRepository deliveries = mock(DomainEventDeliveryRepository.class);
        UUID eventId = UUID.randomUUID();
        CapacityLedgerCursor next = new CapacityLedgerCursor(
            Instant.parse("2026-07-25T01:02:03.123456Z"),
            eventId,
            Instant.parse("2026-07-25T01:03:00.000001Z"),
            UUID.randomUUID()
        );
        when(deliveries.capacityRunLedger(any(), any(), anyString(), any(), anyInt()))
            .thenReturn(new CapacityLedgerSlice(
                List.of(new CapacityLedgerEntry(eventId, null, UUID.randomUUID(), 1, "pending", 0, 0, false)),
                next
            ));
        CapacityEventProbeService service = new CapacityEventProbeService(properties, outbox, events, deliveries);
        UUID runId = UUID.randomUUID();

        String cursor = service.ledger(admin, runId, SECRET, null, 10).nextCursor();
        assertThat(cursor).isNotBlank().doesNotContain(eventId.toString());
        service.ledger(admin, runId, SECRET, cursor, 10);

        ArgumentCaptor<CapacityLedgerCursor> captor = ArgumentCaptor.forClass(CapacityLedgerCursor.class);
        verify(deliveries, org.mockito.Mockito.times(2))
            .capacityRunLedger(any(), any(), anyString(), captor.capture(), anyInt());
        assertThat(captor.getAllValues().get(0)).isNull();
        assertThat(captor.getAllValues().get(1)).isEqualTo(next);
        int signatureOffset = cursor.indexOf('.') + 1;
        char replacement = cursor.charAt(signatureOffset) == 'A' ? 'B' : 'A';
        String tampered = cursor.substring(0, signatureOffset)
            + replacement
            + cursor.substring(signatureOffset + 1);
        assertStatus(
            HttpStatus.BAD_REQUEST,
            () -> service.ledger(admin, runId, SECRET, "not-a-cursor", 10)
        );
        assertStatus(
            HttpStatus.BAD_REQUEST,
            () -> service.ledger(admin, runId, SECRET, tampered, 10)
        );
        assertStatus(
            HttpStatus.BAD_REQUEST,
            () -> service.ledger(admin, UUID.randomUUID(), SECRET, cursor, 10)
        );
        CurrentUser otherWorkspaceAdmin = new CurrentUser(
            admin.id(),
            UUID.randomUUID(),
            null,
            "admin",
            "Administrator",
            Set.of("admin"),
            Set.of("admin.access")
        );
        assertStatus(
            HttpStatus.BAD_REQUEST,
            () -> service.ledger(otherWorkspaceAdmin, runId, SECRET, cursor, 10)
        );
        assertStatus(
            HttpStatus.BAD_REQUEST,
            () -> service.decodeCursor(cursor, admin.workspaceId(), runId, "other.handler")
        );
    }

    @Test
    void scopesSummaryToTheAuthenticatedWorkspaceAndRun() {
        CapacityEventProbeProperties properties = enabledProperties();
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        DomainEventRepository events = mock(DomainEventRepository.class);
        DomainEventDeliveryRepository deliveries = mock(DomainEventDeliveryRepository.class);
        UUID runId = UUID.randomUUID();
        Instant databaseNow = Instant.parse("2026-07-25T10:00:00Z");
        CapacityRunSummary expected = new CapacityRunSummary(4, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 2);
        when(deliveries.currentTime()).thenReturn(databaseNow);
        when(deliveries.capacityRunSummary(any(), any(), anyString(), any())).thenReturn(expected);
        CapacityEventProbeService service = new CapacityEventProbeService(properties, outbox, events, deliveries);

        assertThat(service.summary(admin, runId, SECRET)).isEqualTo(expected);
        verify(deliveries).currentTime();
        verify(deliveries).capacityRunSummary(
            org.mockito.ArgumentMatchers.eq(admin.workspaceId()),
            org.mockito.ArgumentMatchers.eq(runId),
            org.mockito.ArgumentMatchers.eq("realtime.signal"),
            org.mockito.ArgumentMatchers.eq(databaseNow)
        );
    }

    private static CapacityEventProbeProperties enabledProperties() {
        CapacityEventProbeProperties properties = new CapacityEventProbeProperties();
        properties.setEnabled(true);
        properties.setSecret(SECRET);
        return properties;
    }

    private static EventEnvelope probeEnvelope(
        CurrentUser actor,
        UUID runId,
        String aggregateKey,
        String requestKey
    ) {
        UUID eventId = namedUuid("capacity-event", actor.workspaceId(), runId, requestKey);
        UUID aggregateId = namedUuid("capacity-aggregate", actor.workspaceId(), runId, aggregateKey);
        String calibrationPath = "/api/admin/event-deliveries/capacity-runs/" + runId + "/ledger";
        return new EventEnvelope(
            eventId,
            actor.workspaceId(),
            "realtime.signal.requested",
            1,
            "capacity_probe",
            aggregateId,
            actor.id(),
            "capacity.probe:" + runId + ":" + requestKey,
            runId,
            null,
            Instant.now(),
            Map.of(
                "signalType", "capacity.probe",
                "objectType", "capacity_probe",
                "objectId", aggregateId.toString(),
                "sequenceScope", "OBJECT",
                "sequenceKey", "capacity_probe:" + aggregateId,
                "calibrationPath", calibrationPath,
                "safePayload", Map.of()
            )
        );
    }

    private static UUID namedUuid(String namespace, UUID workspaceId, UUID runId, String key) {
        return UUID.nameUUIDFromBytes(
            (namespace + ":" + workspaceId + ":" + runId + ":" + key)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private static DomainEvent event(EventEnvelope envelope, long sequence) {
        return new DomainEvent(
            envelope.eventId(),
            envelope.workspaceId(),
            envelope.eventType(),
            envelope.eventVersion(),
            envelope.aggregateType(),
            envelope.aggregateId(),
            sequence,
            envelope.actorId(),
            envelope.idempotencyKey(),
            envelope.correlationId(),
            envelope.causationId(),
            envelope.occurredAt(),
            envelope.payload(),
            0,
            envelope.occurredAt()
        );
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(status));
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                ResponseStatusException status = (ResponseStatusException) error;
                assertThat(status.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(status.getReason())
                    .isEqualTo("Capacity probe requestKey conflicts with existing aggregateKey or payload");
            });
    }
}
