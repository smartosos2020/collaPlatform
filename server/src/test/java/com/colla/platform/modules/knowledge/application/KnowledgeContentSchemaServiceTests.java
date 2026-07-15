package com.colla.platform.modules.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentCanonicalDocument;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlockDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KnowledgeContentSchemaServiceTests {
    private static final UUID ITEM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ObjectMapper objectMapper;
    private KnowledgeContentSchemaService schemaService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        schemaService = new KnowledgeContentSchemaService(objectMapper);
    }

    @Test
    void canonicalDocumentExpressesTheM2BlockAndMarkSet() {
        List<KnowledgeContentBlockDraft> blocks = List.of(
            new KnowledgeContentBlockDraft("paragraph", "intro", 0),
            new KnowledgeContentBlockDraft("heading", "title", 1),
            new KnowledgeContentBlockDraft("bullet_list", "bullet", 2),
            new KnowledgeContentBlockDraft("ordered_list", "ordered", 3),
            new KnowledgeContentBlockDraft("task", "todo", 4),
            new KnowledgeContentBlockDraft("quote", "quote", 5),
            new KnowledgeContentBlockDraft("code_block", "code", 6),
            new KnowledgeContentBlockDraft("image", "image", 7),
            new KnowledgeContentBlockDraft("file", "file", 8),
            new KnowledgeContentBlockDraft("callout", "callout", 9),
            new KnowledgeContentBlockDraft("divider", "", 10),
            new KnowledgeContentBlockDraft("toc", "toc", 11)
        );

        KnowledgeContentCanonicalDocument result = schemaService.fromLegacy(ITEM_ID, blocks, null);
        Set<String> types = result.blocks().stream().map(block -> block.node().path("type").asText()).collect(java.util.stream.Collectors.toSet());

        assertTrue(types.containsAll(Set.of(
            "paragraph", "heading", "bulletList", "orderedList", "taskItem", "blockquote", "codeBlock",
            "image", "file", "callout", "horizontalRule", "toc"
        )));
        assertEquals(3, result.schemaVersion());
        assertTrue(result.document().path("content").isArray());
        result.document().path("content").forEach(node -> assertTrue(node.path("attrs").hasNonNull("blockId")));
    }

    @Test
    void repeatedLegacyConversionIsDeterministicAndPreservesExistingIds() {
        UUID existingId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        List<KnowledgeContentBlockDraft> blocks = List.of(
            new KnowledgeContentBlockDraft(existingId, null, "paragraph", "same", 0, 2, Map.of(), Map.of(), null, null, false),
            new KnowledgeContentBlockDraft("paragraph", "generated", 1)
        );

        KnowledgeContentCanonicalDocument first = schemaService.fromLegacy(ITEM_ID, blocks, null);
        KnowledgeContentCanonicalDocument second = schemaService.fromLegacy(ITEM_ID, blocks, null);

        assertEquals(first.checksum(), second.checksum());
        assertEquals(first.document(), second.document());
        assertEquals(existingId.toString(), first.document().path("content").get(0).path("attrs").path("blockId").asText());
        assertNotEquals(
            first.document().path("content").get(0).path("attrs").path("blockId").asText(),
            first.document().path("content").get(1).path("attrs").path("blockId").asText()
        );
    }

    @Test
    void parentRelationshipIsRepresentedByNestedCanonicalNodesAndStableParentAttrs() {
        UUID parentId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID childId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        KnowledgeContentCanonicalDocument result = schemaService.fromLegacy(ITEM_ID, List.of(
            new KnowledgeContentBlockDraft(parentId, null, "bullet_list", "parent", 0, 2, Map.of(), Map.of(), null, null, false),
            new KnowledgeContentBlockDraft(childId, parentId, "paragraph", "child", 0, 2, Map.of(), Map.of(), null, null, false)
        ), null);

        assertEquals(1, result.document().path("content").size());
        assertEquals("child", result.document().path("content").get(0).path("content").get(1).path("content").get(0).path("text").asText());
        assertEquals(parentId.toString(), result.document().path("content").get(0).path("attrs").path("blockId").asText());
    }

    @Test
    void tableAndEmbedBecomeStructuredProjections() {
        KnowledgeContentCanonicalDocument result = schemaService.fromLegacy(ITEM_ID, List.of(
            new KnowledgeContentBlockDraft(
                "table",
                "{\"columns\":[\"Name\",\"State\"],\"rows\":[[\"A\",\"active\"]]}",
                0
            ),
            new KnowledgeContentBlockDraft(
                "embed_object",
                "{\"objectType\":\"base\",\"objectId\":\"55555555-5555-5555-5555-555555555555\",\"title\":\"Base\"}",
                1
            )
        ), null);

        assertTrue(result.blocks().stream().anyMatch(block -> "table".equals(block.blockType())));
        assertEquals(1, result.embeds().size());
        assertEquals("base", result.embeds().get(0).objectType());
        assertEquals(UUID.fromString("55555555-5555-5555-5555-555555555555"), result.embeds().get(0).objectId());
        assertTrue(result.plainText().contains("active"));
    }

    @Test
    void markdownConversionSupportsHeadingsTasksCodeAndDividers() {
        KnowledgeContentCanonicalDocument result = schemaService.fromLegacy(
            ITEM_ID,
            List.of(),
            "# Title\n\n- item\n- [x] done\n\n> note\n\n```\nconst value = 1;\n```\n\n---"
        );

        Set<String> types = result.blocks().stream().map(block -> block.node().path("type").asText()).collect(java.util.stream.Collectors.toSet());
        assertTrue(types.containsAll(Set.of("heading", "bulletList", "taskItem", "blockquote", "codeBlock", "horizontalRule")));
        assertTrue(result.markdown().contains("# Title"));
        assertTrue(result.plainText().contains("const value = 1;"));
    }

    @Test
    void unknownNodeIsPreservedAsRecoverableUnsupportedContent() throws Exception {
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "doc");
        source.put("schemaVersion", 2);
        source.putArray("content").addObject().put("type", "futureWidget").putObject("attrs").put("value", "keep-me");

        KnowledgeContentCanonicalDocument result = schemaService.normalizeDocument(source, ITEM_ID);

        assertEquals("unsupported", result.document().path("content").get(0).path("type").asText());
        assertEquals("futureWidget", result.document().path("content").get(0).path("attrs").path("originalType").asText());
        assertTrue(result.issues().stream().anyMatch(issue -> "SCHEMA_UNKNOWN_NODE_PRESERVED".equals(issue.code())));
        assertTrue(result.document().path("content").get(0).path("attrs").path("originalNode").path("attrs").path("value").asText().equals("keep-me"));
    }

    @Test
    void futureSchemaAndMalformedPayloadAreRejected() throws Exception {
        ObjectNode future = objectMapper.createObjectNode();
        future.put("type", "doc");
        future.put("schemaVersion", 99);
        future.putArray("content");
        assertEquals("SCHEMA_UNSUPPORTED", assertThrows(KnowledgeContentSchemaService.KnowledgeContentSchemaException.class,
            () -> schemaService.normalizeDocument(future, ITEM_ID)).code());

        ObjectNode malformed = objectMapper.createObjectNode();
        malformed.put("type", "doc");
        malformed.put("schemaVersion", 3);
        malformed.put("content", "not-an-array");
        assertEquals("SCHEMA_INVALID_CONTENT", assertThrows(KnowledgeContentSchemaService.KnowledgeContentSchemaException.class,
            () -> schemaService.normalizeDocument(malformed, ITEM_ID)).code());
    }

    @Test
    void canonicalChecksumDoesNotDependOnObjectFieldOrder() throws Exception {
        ObjectNode first = objectMapper.readValue("{\"type\":\"doc\",\"schemaVersion\":3,\"content\":[]}", ObjectNode.class);
        ObjectNode second = objectMapper.readValue("{\"content\":[],\"schemaVersion\":3,\"type\":\"doc\"}", ObjectNode.class);

        assertEquals(schemaService.checksum(first), schemaService.checksum(second));
        assertFalse(schemaService.fromLegacy(ITEM_ID, List.of(), "text").plainText().isBlank());
    }
}
