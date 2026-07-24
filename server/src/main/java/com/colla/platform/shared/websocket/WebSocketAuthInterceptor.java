package com.colla.platform.shared.websocket;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.shared.auth.AuthenticatedUserResolver;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.JwtTokenService;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
public class WebSocketAuthInterceptor implements HandshakeInterceptor {
    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final JwtTokenService jwtTokenService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public WebSocketAuthInterceptor(JwtTokenService jwtTokenService, AuthenticatedUserResolver authenticatedUserResolver) {
        this.jwtTokenService = jwtTokenService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        String token = token(request.getURI());
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            JwtTokenService.JwtClaims claims = jwtTokenService.parseAccessToken(token);
            CurrentUser currentUser = authenticatedUserResolver.resolve(claims.userId(), claims.deviceId()).orElseThrow();
            attributes.put(CURRENT_USER_ATTRIBUTE, currentUser);
            return true;
        } catch (RuntimeException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

    private String token(URI uri) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
    }
}
