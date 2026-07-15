package com.colla.platform.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "colla.knowledge-collaboration")
public class KnowledgeCollaborationProperties {
    private String publicUrl = "ws://localhost:1234";
    private String internalSecret = "colla-local-collaboration-secret";
    private Duration ticketTtl = Duration.ofMinutes(5);
    private int maxUpdateBytes = 1024 * 1024;

    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public String getInternalSecret() { return internalSecret; }
    public void setInternalSecret(String internalSecret) { this.internalSecret = internalSecret; }
    public Duration getTicketTtl() { return ticketTtl; }
    public void setTicketTtl(Duration ticketTtl) { this.ticketTtl = ticketTtl; }
    public int getMaxUpdateBytes() { return maxUpdateBytes; }
    public void setMaxUpdateBytes(int maxUpdateBytes) { this.maxUpdateBytes = maxUpdateBytes; }
}
