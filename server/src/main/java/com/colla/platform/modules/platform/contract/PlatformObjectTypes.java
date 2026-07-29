package com.colla.platform.modules.platform.contract;

import java.util.Locale;

/** Public canonical platform object identities and finite read aliases. */
public final class PlatformObjectTypes {
    public static final String KNOWLEDGE_CONTENT = "knowledge_content";
    public static final String WORK_ITEM = "work_item";

    private PlatformObjectTypes() {
    }

    public static String canonicalize(String objectType) {
        if (objectType == null) {
            return null;
        }
        String normalized = objectType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return "canonical_work_item".equals(normalized) ? WORK_ITEM : normalized;
    }
}
