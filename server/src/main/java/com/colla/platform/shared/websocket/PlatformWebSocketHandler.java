package com.colla.platform.shared.websocket;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.config.runtime.RuntimeRoleProperties;
import com.colla.platform.shared.realtime.RealtimeProperties;
import com.colla.platform.shared.realtime.RealtimeRedisAvailability;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.colla.platform.shared.auth.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
public class PlatformWebSocketHandler extends TextWebSocketHandler {
    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final RealtimeProperties realtimeProperties;
    private final RealtimeRedisAvailability redisAvailability;
    private final String instanceId;

    public PlatformWebSocketHandler(
        WebSocketSessionRegistry registry,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry,
        RealtimeProperties realtimeProperties,
        RuntimeRoleProperties runtimeRoleProperties,
        RealtimeRedisAvailability redisAvailability
    ) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.realtimeProperties = realtimeProperties;
        this.redisAvailability = redisAvailability;
        this.instanceId = runtimeRoleProperties.getInstanceId();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!redisAvailability.isAvailable()) {
            session.close(new CloseStatus(1013, "Realtime transport unavailable"));
            return;
        }
        CurrentUser currentUser = currentUser(session);
        registry.register(currentUser.id(), session);
        if (!redisAvailability.isAvailable()) {
            registry.unregister(session);
            session.close(new CloseStatus(1013, "Realtime transport unavailable"));
            return;
        }
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "connection.ready",
            "instanceId", instanceId
        ))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String type;
        try {
            type = objectMapper.readTree(message.getPayload()).path("type").asText("");
        } catch (Exception exception) {
            countInbound("malformed", "rejected");
            sendProtocolNotice(session, "malformed");
            return;
        }

        String command = classifyCommand(type);
        String outcome = "observe".equals(realtimeProperties.getLegacyKnowledgeInboundPolicy())
            ? "observed"
            : "rejected";
        countInbound(command, outcome);
        if ("rejected".equals(outcome) || !"other".equals(command)) {
            sendProtocolNotice(session, command);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        CurrentUser currentUser = currentUser(session);
        registry.unregister(currentUser.id(), session);
    }

    @EventListener
    public void onShutdown(ContextClosedEvent event) {
        registry.closeAll();
    }

    private CurrentUser currentUser(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        return (CurrentUser) attributes.get(WebSocketAuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private void sendProtocolNotice(WebSocketSession session, String command) throws Exception {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
            "type", "protocol.upgrade_required",
            "protocol", "colla-yjs-v1",
            "endpoint", "/collaboration",
            "command", command
        ))));
    }

    private void countInbound(String command, String outcome) {
        meterRegistry.counter(
            "colla.realtime.websocket.inbound",
            "command", command,
            "outcome", outcome
        ).increment();
    }

    private String classifyCommand(String type) {
        return switch (type) {
            case "knowledge.content.join", "knowledge.content.leave" -> "membership";
            case "knowledge.content.update" -> "update";
            case "knowledge.content.awareness.update" -> "awareness";
            case "knowledge.content.snapshot.request" -> "snapshot";
            default -> "other";
        };
    }
}
