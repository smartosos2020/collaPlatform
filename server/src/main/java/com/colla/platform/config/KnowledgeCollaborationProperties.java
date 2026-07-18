package com.colla.platform.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "colla.knowledge-collaboration")
public class KnowledgeCollaborationProperties {
    private String publicUrl = "ws://localhost:1234";
    // HTTP base URL(s) of the collaboration nodes used for server-to-server calls
    // (e.g. state invalidation); comma-separated when multiple nodes run behind a balancer.
    private String internalUrl = "http://localhost:1234";
    // Default comes from application.yml via COLLA_COLLABORATION_INTERNAL_SECRET placeholder;
    // keep no secret literal in source so the sensitive-data scan stays clean.
    private String internalSecret;
    private Duration ticketTtl = Duration.ofMinutes(5);
    private int maxUpdateBytes = 1024 * 1024;
    private int retainedUpdates = 100;
    private Duration expiredTicketRetention = Duration.ofHours(1);

    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public String getInternalUrl() { return internalUrl; }
    public void setInternalUrl(String internalUrl) { this.internalUrl = internalUrl; }
    public String getInternalSecret() { return internalSecret; }
    public void setInternalSecret(String internalSecret) { this.internalSecret = internalSecret; }
    public Duration getTicketTtl() { return ticketTtl; }
    public void setTicketTtl(Duration ticketTtl) { this.ticketTtl = ticketTtl; }
    public int getMaxUpdateBytes() { return maxUpdateBytes; }
    public void setMaxUpdateBytes(int maxUpdateBytes) { this.maxUpdateBytes = maxUpdateBytes; }
    public int getRetainedUpdates() { return retainedUpdates; }
    public void setRetainedUpdates(int retainedUpdates) { this.retainedUpdates = retainedUpdates; }
    public Duration getExpiredTicketRetention() { return expiredTicketRetention; }
    public void setExpiredTicketRetention(Duration expiredTicketRetention) { this.expiredTicketRetention = expiredTicketRetention; }
}
