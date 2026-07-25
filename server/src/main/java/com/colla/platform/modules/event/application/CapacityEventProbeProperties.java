package com.colla.platform.modules.event.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "colla.capacity.probe")
public class CapacityEventProbeProperties {
    private boolean enabled;
    private String secret = "";
    private int maxEventsPerRun = 100_000;
    private int maxPageSize = 1_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret == null ? "" : secret;
    }

    public int getMaxEventsPerRun() {
        return maxEventsPerRun;
    }

    public void setMaxEventsPerRun(int maxEventsPerRun) {
        this.maxEventsPerRun = maxEventsPerRun;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
}
