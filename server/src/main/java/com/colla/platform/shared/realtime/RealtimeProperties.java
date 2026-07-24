package com.colla.platform.shared.realtime;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("colla.realtime")
public class RealtimeProperties {
    private String channel = "colla:local:realtime:v1";
    private int maxPayloadBytes = 16 * 1024;
    private int sendThreads = 4;
    private int executorQueueCapacity = 512;
    private int sessionQueueCapacity = 64;
    private int recentSignalCapacity = 10_000;
    private int maxConnections = 5_000;
    private Duration shutdownTimeout = Duration.ofSeconds(5);
    private boolean legacyFramesEnabled = true;
    private String legacyKnowledgeInboundPolicy = "reject";

    public void validate() {
        if (channel == null || channel.isBlank() || channel.length() > 160) {
            throw new IllegalArgumentException("colla.realtime.channel is invalid");
        }
        if (maxPayloadBytes < 1024 || maxPayloadBytes > 1024 * 1024) {
            throw new IllegalArgumentException("colla.realtime.max-payload-bytes is invalid");
        }
        if (sendThreads < 1 || sendThreads > 64 || executorQueueCapacity < 1
            || sessionQueueCapacity < 1 || recentSignalCapacity < 1 || recentSignalCapacity > 1_000_000
            || maxConnections < 1 || maxConnections > 1_000_000
            || shutdownTimeout == null || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("colla.realtime send budgets are invalid");
        }
        if (!"reject".equals(legacyKnowledgeInboundPolicy) && !"observe".equals(legacyKnowledgeInboundPolicy)) {
            throw new IllegalArgumentException("colla.realtime.legacy-knowledge-inbound-policy is invalid");
        }
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public int getSendThreads() {
        return sendThreads;
    }

    public void setSendThreads(int sendThreads) {
        this.sendThreads = sendThreads;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }

    public int getSessionQueueCapacity() {
        return sessionQueueCapacity;
    }

    public void setSessionQueueCapacity(int sessionQueueCapacity) {
        this.sessionQueueCapacity = sessionQueueCapacity;
    }

    public int getRecentSignalCapacity() {
        return recentSignalCapacity;
    }

    public void setRecentSignalCapacity(int recentSignalCapacity) {
        this.recentSignalCapacity = recentSignalCapacity;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public boolean isLegacyFramesEnabled() {
        return legacyFramesEnabled;
    }

    public void setLegacyFramesEnabled(boolean legacyFramesEnabled) {
        this.legacyFramesEnabled = legacyFramesEnabled;
    }

    public String getLegacyKnowledgeInboundPolicy() {
        return legacyKnowledgeInboundPolicy;
    }

    public void setLegacyKnowledgeInboundPolicy(String legacyKnowledgeInboundPolicy) {
        this.legacyKnowledgeInboundPolicy = legacyKnowledgeInboundPolicy;
    }
}
