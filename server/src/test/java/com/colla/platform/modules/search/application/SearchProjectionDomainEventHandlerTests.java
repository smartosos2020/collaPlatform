package com.colla.platform.modules.search.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchProjectionDomainEventHandlerTests {
    @Test
    void projectsUpdatesAtAggregateSequence() {
        SearchIndexService service = mock(SearchIndexService.class);
        SearchProjectionDomainEventHandler handler = new SearchProjectionDomainEventHandler(service);
        EventMessage event = event("knowledge.content.blocks.updated", "knowledge_content", 19);

        handler.handle(event);

        verify(service).applyProjection(
            event.workspaceId(),
            "knowledge_content",
            event.aggregateId(),
            19,
            false
        );
    }

    @Test
    void projectsDeleteWithoutDisclosingPayload() {
        SearchIndexService service = mock(SearchIndexService.class);
        SearchProjectionDomainEventHandler handler = new SearchProjectionDomainEventHandler(service);
        EventMessage event = event("message.revoked", "message", 4);

        handler.handle(event);

        verify(service).applyProjection(event.workspaceId(), "message", event.aggregateId(), 4, true);
    }

    private static EventMessage event(String eventType, String aggregateType, long sequence) {
        return new EventMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            eventType,
            1,
            aggregateType,
            UUID.randomUUID(),
            sequence,
            UUID.randomUUID(),
            "search-projection-test",
            UUID.randomUUID(),
            null,
            Instant.now(),
            Map.of("objectId", UUID.randomUUID().toString())
        );
    }
}
