package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.WorkItemLayoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemLayoutCanonicalizerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemTypeConfigCanonicalizer configCanonicalizer =
        new WorkItemTypeConfigCanonicalizer(objectMapper);
    private final WorkItemLayoutCanonicalizer canonicalizer = new WorkItemLayoutCanonicalizer(
        objectMapper,
        configCanonicalizer,
        new WorkItemLayoutConditionDsl(objectMapper, configCanonicalizer)
    );

    @Test
    void canonicalHashIsStableAcrossRequestOrdering() {
        UUID sectionId = UUID.randomUUID();
        UUID fieldNodeId = UUID.randomUUID();
        UUID fieldId = UUID.randomUUID();
        LayoutNode section = node(sectionId, null, "main", "section", null, null, 0);
        LayoutNode field = node(fieldNodeId, sectionId, "title_field", "field", fieldId, "title", 0);
        FieldAccessPolicy policy = new FieldAccessPolicy(
            UUID.randomUUID(),
            fieldId,
            "title",
            "title_access",
            objectMapper.createObjectNode().put("schemaVersion", 1).put("mode", "editable"),
            ""
        );

        var first = canonicalizer.canonicalize("CREATE", List.of(section, field), List.of(policy));
        var second = canonicalizer.canonicalize("create", List.of(field, section), List.of(policy));

        assertEquals(first.hash(), second.hash());
        assertEquals("create", first.layoutKind());
        assertEquals(sectionId, first.nodes().getFirst().id());
        assertEquals(64, first.policies().getFirst().configHash().length());
    }

    @Test
    void invalidGraphsAndDuplicateFieldReferencesFailClosed() {
        UUID fieldId = UUID.randomUUID();
        UUID missingParent = UUID.randomUUID();
        WorkItemLayoutException missing = assertThrows(
            WorkItemLayoutException.class,
            () -> canonicalizer.canonicalize(
                "detail",
                List.of(node(
                    UUID.randomUUID(), missingParent, "title_field", "field", fieldId, "title", 0
                )),
                List.of()
            )
        );
        assertEquals("INVALID_LAYOUT_TREE", missing.code());

        UUID sectionId = UUID.randomUUID();
        WorkItemLayoutException duplicate = assertThrows(
            WorkItemLayoutException.class,
            () -> canonicalizer.canonicalize(
                "detail",
                List.of(
                    node(sectionId, null, "main", "section", null, null, 0),
                    node(UUID.randomUUID(), sectionId, "title_one", "field", fieldId, "title", 0),
                    node(UUID.randomUUID(), sectionId, "title_two", "field", fieldId, "title", 1)
                ),
                List.of()
            )
        );
        assertEquals("INVALID_LAYOUT_FIELD_REFERENCE", duplicate.code());
    }

    @Test
    void depthAndColumnBudgetsAreEnforced() {
        UUID root = UUID.randomUUID();
        UUID nested = UUID.randomUUID();
        UUID nestedAgain = UUID.randomUUID();
        UUID column = UUID.randomUUID();
        WorkItemLayoutException depth = assertThrows(
            WorkItemLayoutException.class,
            () -> canonicalizer.canonicalize(
                "detail",
                List.of(
                    node(root, null, "root", "section", null, null, 0),
                    node(nested, root, "nested", "section", null, null, 0),
                    node(nestedAgain, nested, "nested_again", "section", null, null, 0),
                    node(column, nestedAgain, "column", "column", null, null, 0),
                    node(UUID.randomUUID(), column, "title", "field", UUID.randomUUID(), "title", 0)
                ),
                List.of()
            )
        );
        assertEquals("LAYOUT_DEPTH_LIMIT", depth.code());
    }

    private LayoutNode node(
        UUID id,
        UUID parentId,
        String key,
        String type,
        UUID fieldId,
        String fieldKey,
        int sortOrder
    ) {
        return new LayoutNode(
            id,
            parentId,
            key,
            type,
            fieldId,
            fieldKey,
            sortOrder,
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode().put("schemaVersion", 1)
        );
    }
}
