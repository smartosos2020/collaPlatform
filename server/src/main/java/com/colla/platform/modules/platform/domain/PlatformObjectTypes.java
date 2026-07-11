package com.colla.platform.modules.platform.domain;

import java.util.Locale;

/** Canonical platform object identities and finite read aliases. */
public final class PlatformObjectTypes {
    public static final String KNOWLEDGE_CONTENT = "knowledge_content";

    private PlatformObjectTypes() {
    }

    public static String canonicalize(String objectType) {
        if (objectType == null) {
            return null;
        }
        String normalized = objectType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized;
    }
}
