package com.colla.platform.modules.event.application;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandler.Descriptor;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DomainEventHandlerRegistry {
    private final List<DomainEventHandler> handlers;
    private final Map<String, DomainEventHandler> handlersByIdentity;

    public DomainEventHandlerRegistry(List<DomainEventHandler> handlers) {
        Map<String, DomainEventHandler> indexed = new LinkedHashMap<>();
        for (DomainEventHandler handler : handlers) {
            Descriptor descriptor = handler.descriptor();
            String identity = identity(descriptor);
            if (indexed.putIfAbsent(identity, handler) != null) {
                throw new IllegalStateException("Duplicate domain event handler registration: " + identity);
            }
        }
        this.handlersByIdentity = Map.copyOf(indexed);
        this.handlers = indexed.values().stream()
            .sorted(Comparator.comparing(handler -> identity(handler.descriptor())))
            .toList();
    }

    public List<DomainEventHandler> matching(String eventType, int eventVersion) {
        return handlers.stream()
            .filter(handler -> handler.descriptor().supports(eventType, eventVersion))
            .toList();
    }

    public List<Descriptor> descriptors() {
        return handlers.stream().map(DomainEventHandler::descriptor).toList();
    }

    public DomainEventHandler require(String handlerKey, int handlerVersion) {
        DomainEventHandler handler = handlersByIdentity.get(handlerKey + ":" + handlerVersion);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown domain event handler: " + handlerKey + ":" + handlerVersion);
        }
        return handler;
    }

    private static String identity(Descriptor descriptor) {
        return descriptor.handlerKey() + ":" + descriptor.handlerVersion();
    }
}
