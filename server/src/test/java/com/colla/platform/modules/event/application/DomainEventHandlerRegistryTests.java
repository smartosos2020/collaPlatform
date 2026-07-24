package com.colla.platform.modules.event.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandler.Descriptor;
import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.contract.DomainEventHandler.Subscription;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DomainEventHandlerRegistryTests {

    @Test
    void resolvesExactTypeAndVersionInStableOrder() {
        DomainEventHandler second = handler("search.project", 2, "project.updated", 1);
        DomainEventHandler first = handler("notification.project", 1, "project.updated", 1);
        DomainEventHandlerRegistry registry = new DomainEventHandlerRegistry(List.of(second, first));

        assertThat(registry.matching("project.updated", 1))
            .extracting(item -> item.descriptor().handlerKey())
            .containsExactly("notification.project", "search.project");
        assertThat(registry.matching("project.updated", 2)).isEmpty();
        assertThat(registry.require("search.project", 2)).isSameAs(second);
    }

    @Test
    void rejectsDuplicateHandlerIdentity() {
        DomainEventHandler first = handler("search.project", 1, "project.created", 1);
        DomainEventHandler duplicate = handler("search.project", 1, "project.updated", 1);

        assertThatThrownBy(() -> new DomainEventHandlerRegistry(List.of(first, duplicate)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate");
    }

    private static DomainEventHandler handler(
        String key,
        int handlerVersion,
        String eventType,
        int eventVersion
    ) {
        return new DomainEventHandler() {
            @Override
            public Descriptor descriptor() {
                return new Descriptor(
                    key,
                    handlerVersion,
                    Set.of(new Subscription(eventType, eventVersion)),
                    false
                );
            }

            @Override
            public void handle(EventMessage event) {
            }
        };
    }
}
