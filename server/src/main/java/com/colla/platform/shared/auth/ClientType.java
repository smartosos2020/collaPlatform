package com.colla.platform.shared.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ClientType {
    WEB,
    DESKTOP,
    IOS,
    ANDROID;

    @JsonCreator
    public static ClientType fromValue(String value) {
        return ClientType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
