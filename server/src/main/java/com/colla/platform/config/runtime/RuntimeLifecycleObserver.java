package com.colla.platform.config.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RuntimeLifecycleObserver {
    private static final Logger log = LoggerFactory.getLogger(RuntimeLifecycleObserver.class);
    private final ApplicationContext applicationContext;
    private final RuntimeRoleProperties properties;

    public RuntimeLifecycleObserver(ApplicationContext applicationContext, RuntimeRoleProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
    }

    @EventListener
    public void onClosed(ContextClosedEvent event) {
        AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
        log.info(
            "runtime_shutdown role={} instanceId={} state=refusing-traffic timeoutSeconds=30",
            properties.role().value(),
            properties.getInstanceId()
        );
    }
}
