package com.colla.platform.shared.realtime;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.shared.websocket.WebSocketMessageSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.EVENT_GATEWAY, RuntimeRole.COMBINED})
public class LocalSessionRealtimeSignalConsumer implements RealtimeSignalConsumer {
    private final WebSocketMessageSender messageSender;

    public LocalSessionRealtimeSignalConsumer(WebSocketMessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public void consume(RealtimeSignalEnvelope envelope) {
        messageSender.sendRealtime(envelope);
    }
}
