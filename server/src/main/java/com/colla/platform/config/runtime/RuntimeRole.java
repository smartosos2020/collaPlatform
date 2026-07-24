package com.colla.platform.config.runtime;

import java.util.Locale;

public enum RuntimeRole {
    API("api"),
    WORKER("worker"),
    EVENT_GATEWAY("event-gateway"),
    MAINTENANCE("maintenance"),
    COMBINED("combined");

    private final String value;

    RuntimeRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RuntimeRole parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("colla.runtime.role must be configured");
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (RuntimeRole role : values()) {
            if (role.value.equals(normalized)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unsupported colla.runtime.role: " + rawValue);
    }
}
