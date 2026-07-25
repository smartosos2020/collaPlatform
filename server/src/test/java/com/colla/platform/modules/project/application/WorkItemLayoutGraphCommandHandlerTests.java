package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.colla.platform.modules.project.application.WorkItemLayoutGraphCommandHandler.NodeCommand;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.WorkItemLayoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemLayoutGraphCommandHandlerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemLayoutGraphCommandHandler handler = new WorkItemLayoutGraphCommandHandler();

    @Test
    void addMoveCopyAndDeleteAreDeterministicAtomicGraphCommands() {
        LayoutNode first = node("first", null, "section", 0);
        LayoutNode second = node("second", null, "section", 1);

        var moved = handler.apply(
            List.of(first, second),
            List.of(),
            command("move", second.id(), null, 0, null, false)
        );
        assertEquals(List.of("second", "first"), moved.nodes().stream()
            .sorted(java.util.Comparator.comparingInt(LayoutNode::sortOrder))
            .map(LayoutNode::nodeKey)
            .toList());

        var copied = handler.apply(
            moved.nodes(),
            List.of(),
            command("copy", second.id(), null, 1, null, false)
        );
        assertEquals(3, copied.nodes().size());
        LayoutNode copy = copied.nodes().stream()
            .filter(node -> node.nodeKey().startsWith("second_copy_"))
            .findFirst()
            .orElseThrow();
        assertNotEquals(second.id(), copy.id());

        var deleted = handler.apply(
            copied.nodes(),
            List.of(),
            command("delete", copy.id(), null, copy.sortOrder(), null, true)
        );
        assertEquals(2, deleted.nodes().size());
    }

    @Test
    void protectsIdentityReferencedDeletesAndDuplicateFieldCopies() {
        LayoutNode section = node("section", null, "section", 0);
        LayoutNode field = new LayoutNode(
            UUID.randomUUID(),
            section.id(),
            "field_title",
            "field",
            UUID.randomUUID(),
            "title",
            0,
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode().put("schemaVersion", 1)
        );
        assertThrows(WorkItemLayoutException.class, () -> handler.apply(
            List.of(section, field),
            List.of(),
            command("delete", section.id(), null, 0, null, false)
        ));
        assertThrows(WorkItemLayoutException.class, () -> handler.apply(
            List.of(section, field),
            List.of(),
            command("copy", field.id(), section.id(), 1, null, false)
        ));
        LayoutNode changedIdentity = new LayoutNode(
            field.id(), section.id(), "changed", "field", field.fieldId(), field.fieldKey(), 0,
            field.config(), field.visibilityCondition()
        );
        assertThrows(WorkItemLayoutException.class, () -> handler.apply(
            List.of(section, field),
            List.of(),
            command("update", field.id(), section.id(), 0, changedIdentity, false)
        ));
    }

    private NodeCommand command(
        String operation,
        UUID nodeId,
        UUID parentId,
        int order,
        LayoutNode node,
        boolean confirm
    ) {
        return new NodeCommand(operation, nodeId, parentId, order, node, confirm, 0);
    }

    private LayoutNode node(String key, UUID parentId, String type, int order) {
        return new LayoutNode(
            UUID.randomUUID(),
            parentId,
            key,
            type,
            null,
            null,
            order,
            objectMapper.createObjectNode().put("title", key),
            objectMapper.createObjectNode().put("schemaVersion", 1)
        );
    }
}
