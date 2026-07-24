package com.colla.platform.modules.platform.contract;

import java.util.Locale;

/** Public canonical platform object identities and finite read aliases. */
public final class PlatformObjectTypes {
    public static final String KNOWLEDGE_CONTENT = "knowledge_content";

    private PlatformObjectTypes() {
    }

    public static String canonicalize(String objectType) {
        if (objectType == null) {
            return null;
        }
        return objectType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
