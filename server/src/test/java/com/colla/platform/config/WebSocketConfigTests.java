package com.colla.platform.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.shared.websocket.PlatformWebSocketHandler;
import com.colla.platform.shared.websocket.WebSocketAuthInterceptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

class WebSocketConfigTests {
    @Test
    void productionOriginsComeFromSharedWebSecurityConfiguration() {
        PlatformWebSocketHandler handler = mock(PlatformWebSocketHandler.class);
        WebSocketAuthInterceptor interceptor = mock(WebSocketAuthInterceptor.class);
        WebSecurityProperties properties = new WebSecurityProperties();
        properties.setCorsAllowedOrigins(List.of("https://colla.example.com", "https://admin.example.com"));
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(handler, "/ws/events")).thenReturn(registration);
        when(registration.addInterceptors(interceptor)).thenReturn(registration);

        new WebSocketConfig(handler, interceptor, properties).registerWebSocketHandlers(registry);

        verify(registration).setAllowedOrigins("https://colla.example.com", "https://admin.example.com");
    }
}
