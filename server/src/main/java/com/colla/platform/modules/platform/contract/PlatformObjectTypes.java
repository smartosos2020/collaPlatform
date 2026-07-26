package com.colla.platform.modules.platform.contract;

import java.util.Locale;

/** Public canonical platform object identities and finite read aliases. */
public final class PlatformObjectTypes {
    public static final String KNOWLEDGE_CONTENT = "knowledge_content";
    public static final String WORK_ITEM = "work_item";
    public static final String LEGACY_ISSUE = "issue";
    public static final String LEGACY_PROJECT = "project";

    private PlatformObjectTypes() {
    }

    public static String canonicalize(String objectType) {
        if (objectType == null) {
            return null;
        }
        String normalized = objectType.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "project_issue", "legacy_issue" -> LEGACY_ISSUE;
            case "canonical_work_item" -> WORK_ITEM;
            default -> normalized;
        };
    }
}
