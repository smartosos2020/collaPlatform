package com.colla.platform.modules.platform.domain;

/** Canonical platform object identities and finite read aliases. */
@Deprecated(forRemoval = false)
public final class PlatformObjectTypes {
    public static final String KNOWLEDGE_CONTENT =
        com.colla.platform.modules.platform.contract.PlatformObjectTypes.KNOWLEDGE_CONTENT;

    private PlatformObjectTypes() {
    }

    public static String canonicalize(String objectType) {
        return com.colla.platform.modules.platform.contract.PlatformObjectTypes.canonicalize(objectType);
    }
}
