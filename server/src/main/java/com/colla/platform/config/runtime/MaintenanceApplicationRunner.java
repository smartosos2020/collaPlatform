package com.colla.platform.config.runtime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnRuntimeRole(RuntimeRole.MAINTENANCE)
public class MaintenanceApplicationRunner implements ApplicationListener<ApplicationReadyEvent> {
    private static final long SHUTDOWN_DELAY_MILLIS = 250;

    private final ConfigurableApplicationContext applicationContext;

    public MaintenanceApplicationRunner(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Thread.ofPlatform()
            .name("maintenance-shutdown")
            .start(() -> {
                try {
                    Thread.sleep(SHUTDOWN_DELAY_MILLIS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                applicationContext.close();
            });
    }
}
