package com.colla.platform.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "colla.web-security")
public class WebSecurityProperties {
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins == null || corsAllowedOrigins.isEmpty()
            ? new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"))
            : corsAllowedOrigins;
    }
}
