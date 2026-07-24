package com.colla.platform.config.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.CollaPlatformApplication;
import com.colla.platform.config.SecurityConfig;
import com.colla.platform.config.WebSocketConfig;
import com.colla.platform.modules.event.application.DomainEventWorker;
import com.colla.platform.modules.event.application.ReliableDomainEventWorker;
import com.colla.platform.modules.identity.application.AdminInitializer;
import com.colla.platform.modules.knowledge.api.KnowledgeCollaborationInternalController;
import com.colla.platform.modules.knowledge.api.KnowledgeContentController;
import com.colla.platform.modules.knowledge.application.DatabaseKnowledgeCollaborationHealthQuery;
import com.colla.platform.modules.knowledge.application.KnowledgeCollaborationGatewayService;
import com.colla.platform.modules.event.application.RealtimeSignalTransportDomainEventHandler;
import com.colla.platform.shared.realtime.LocalSessionRealtimeSignalConsumer;
import com.colla.platform.shared.realtime.RedisRealtimeSignalPublisher;
import com.colla.platform.shared.realtime.RedisRealtimeSignalSubscriber;
import com.colla.platform.shared.websocket.PlatformWebSocketHandler;
import com.colla.platform.shared.websocket.WebSocketAuthInterceptor;
import com.colla.platform.shared.websocket.WebSocketSessionRegistry;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableScheduling;

class RuntimeRoleBeanContractTests {
    @Test
    void schedulingIsNotEnabledGlobally() {
        assertThat(CollaPlatformApplication.class.isAnnotationPresent(EnableScheduling.class)).isFalse();
        assertThat(RuntimeRoleSchedulingConfiguration.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
        assertThat(SecurityConfig.class.getAnnotation(ConditionalOnWebApplication.class).type())
            .isEqualTo(ConditionalOnWebApplication.Type.SERVLET);
    }

    @Test
    void workerAndGatewayBeansDeclareExplicitRoleConditions() {
        assertRoles(DomainEventWorker.class, RuntimeRole.WORKER, RuntimeRole.COMBINED);
        assertRoles(ReliableDomainEventWorker.class, RuntimeRole.WORKER, RuntimeRole.COMBINED);
        assertThat(ReliableDomainEventWorker.class.getAnnotation(ConditionalOnProperty.class))
            .as("reliable worker explicit enable switch")
            .isNotNull();
        assertRoles(DatabaseKnowledgeCollaborationHealthQuery.class, RuntimeRole.API, RuntimeRole.COMBINED);
        assertRoles(KnowledgeCollaborationGatewayService.class, RuntimeRole.API, RuntimeRole.COMBINED);
        assertRoles(KnowledgeCollaborationInternalController.class, RuntimeRole.API, RuntimeRole.COMBINED);
        assertRoles(KnowledgeContentController.class, RuntimeRole.API, RuntimeRole.COMBINED);
        assertRoles(RealtimeSignalTransportDomainEventHandler.class, RuntimeRole.WORKER, RuntimeRole.COMBINED);
        assertRoles(RedisRealtimeSignalPublisher.class, RuntimeRole.WORKER, RuntimeRole.COMBINED);
        assertRoles(RedisRealtimeSignalSubscriber.class, RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED);
        assertRoles(LocalSessionRealtimeSignalConsumer.class, RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED);
        assertRoles(WebSocketConfig.class, RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED);
        assertRoles(PlatformWebSocketHandler.class, RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED);
        assertRoles(WebSocketAuthInterceptor.class, RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED);
        assertRoles(WebSocketSessionRegistry.class, RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED);
        assertRoles(AdminInitializer.class, RuntimeRole.MAINTENANCE, RuntimeRole.COMBINED);
        assertRoles(MaintenanceApplicationRunner.class, RuntimeRole.MAINTENANCE);
        assertThat(ApplicationListener.class).isAssignableFrom(MaintenanceApplicationRunner.class);
    }

    private void assertRoles(Class<?> type, RuntimeRole... expected) {
        ConditionalOnRuntimeRole annotation = type.getAnnotation(ConditionalOnRuntimeRole.class);
        assertThat(annotation).as(type.getSimpleName()).isNotNull();
        assertThat(Set.copyOf(Arrays.asList(annotation.value()))).containsExactlyInAnyOrder(expected);
    }
}
