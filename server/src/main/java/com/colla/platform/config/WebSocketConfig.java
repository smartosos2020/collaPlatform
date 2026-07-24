package com.colla.platform.config;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.shared.websocket.PlatformWebSocketHandler;
import com.colla.platform.shared.websocket.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
public class WebSocketConfig implements WebSocketConfigurer {
    private final PlatformWebSocketHandler handler;
    private final WebSocketAuthInterceptor authInterceptor;

    public WebSocketConfig(PlatformWebSocketHandler handler, WebSocketAuthInterceptor authInterceptor) {
        this.handler = handler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/events")
            .addInterceptors(authInterceptor)
            .setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173");
    }
}
