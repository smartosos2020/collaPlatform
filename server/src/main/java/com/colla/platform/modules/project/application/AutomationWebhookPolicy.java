package com.colla.platform.modules.project.application;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;

public final class AutomationWebhookPolicy {
    public static final int MAX_PAYLOAD_BYTES = 65536;
    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int RESPONSE_TIMEOUT_MS = 10000;
    private AutomationWebhookPolicy() {}
    public static URI validate(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
            || uri.getHost() == null || uri.getPort() == 0) throw new IllegalArgumentException("WEBHOOK_TARGET_REJECTED");
        try {
            boolean blocked = Arrays.stream(InetAddress.getAllByName(uri.getHost())).anyMatch(address ->
                address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress());
            if (blocked) throw new IllegalArgumentException("WEBHOOK_TARGET_REJECTED");
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("WEBHOOK_DNS_UNAVAILABLE");
        }
        return uri;
    }
    public static long retryDelaySeconds(int attempt) {
        return Math.min(3600, 5L << Math.min(Math.max(attempt, 0), 9));
    }
}
