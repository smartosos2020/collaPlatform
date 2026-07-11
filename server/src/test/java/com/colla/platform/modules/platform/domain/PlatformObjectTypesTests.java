package com.colla.platform.modules.platform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlatformObjectTypesTests {
    @Test
    void documentIsReadAliasButKnowledgeContentIsCanonical() {
        assertEquals(PlatformObjectTypes.KNOWLEDGE_CONTENT, PlatformObjectTypes.canonicalize("knowledge-content"));
        assertEquals("document", PlatformObjectTypes.canonicalize("document"));
    }
}
