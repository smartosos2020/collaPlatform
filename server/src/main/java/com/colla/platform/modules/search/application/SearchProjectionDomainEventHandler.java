package com.colla.platform.modules.search.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SearchProjectionDomainEventHandler implements DomainEventHandler {
    private static final Set<String> DELETE_EVENTS = Set.of(
        "knowledge.content.archived",
        "base.record.deleted",
        "message.revoked"
    );
    private static final Set<String> EVENT_TYPES = Set.of(
        "issue.created",
        "issue.updated",
        "issue.assigned",
        "issue.verified",
        "knowledge.content.created",
        "knowledge.content.updated",
        "knowledge.content.blocks.updated",
        "knowledge.content.knowledge_metadata.updated",
        "knowledge.content.moved",
        "knowledge.content.archived",
        "knowledge.content.restored",
        "knowledge.content.copied",
        "knowledge.content.markdown.imported",
        "knowledge.content.html.imported",
        "knowledge.content.version.restored",
        "knowledge.content.comment.added",
        "knowledge.content.comment.reply.added",
        "knowledge.content.comment.resolved",
        "knowledge.content.comment.reopened",
        "base.created",
        "base.table.created",
        "base.record.created",
        "base.record.updated",
        "base.record.deleted",
        "message.created",
        "message.edited",
        "message.revoked"
    );
    private static final Descriptor DESCRIPTOR = new Descriptor(
        "search.projection",
        1,
        EVENT_TYPES.stream().map(eventType -> new Subscription(eventType, 1)).collect(java.util.stream.Collectors.toSet()),
        true
    );

    private final SearchIndexService searchIndexService;

    public SearchProjectionDomainEventHandler(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void handle(EventMessage event) {
        searchIndexService.applyProjection(
            event.workspaceId(),
            event.aggregateType(),
            event.aggregateId(),
            event.aggregateSequence(),
            DELETE_EVENTS.contains(event.eventType())
        );
    }
}
