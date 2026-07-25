package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.failure;

import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkItemLayoutGraphCommandHandler {
    public CommandResult apply(
        List<LayoutNode> currentNodes,
        List<FieldAccessPolicy> currentPolicies,
        NodeCommand command
    ) {
        List<LayoutNode> nodes = new ArrayList<>(currentNodes);
        List<FieldAccessPolicy> policies = new ArrayList<>(currentPolicies);
        switch (command.operation()) {
            case "add" -> add(nodes, command);
            case "copy" -> copy(nodes, command);
            case "move", "reorder" -> move(nodes, command);
            case "update" -> update(nodes, command);
            case "delete" -> delete(nodes, policies, command);
            default -> throw failure("INVALID_LAYOUT_COMMAND", "Unsupported layout node command");
        }
        return new CommandResult(normalizeOrders(nodes), List.copyOf(policies));
    }

    private void add(List<LayoutNode> nodes, NodeCommand command) {
        if (command.node() == null || nodes.stream().anyMatch(node -> node.id().equals(command.node().id()))) {
            throw failure("INVALID_LAYOUT_COMMAND", "Add requires a new permanent node");
        }
        nodes.add(withPosition(command.node(), command.parentId(), command.targetSortOrder()));
        reorderAt(nodes, command.node().id(), command.parentId(), command.targetSortOrder());
    }

    private void update(List<LayoutNode> nodes, NodeCommand command) {
        int index = index(nodes, command.nodeId());
        LayoutNode current = nodes.get(index);
        LayoutNode requested = command.node();
        if (requested == null
            || !current.id().equals(requested.id())
            || !current.nodeKey().equals(requested.nodeKey())
            || !current.nodeType().equals(requested.nodeType())
            || !java.util.Objects.equals(current.fieldId(), requested.fieldId())
            || !java.util.Objects.equals(current.fieldKey(), requested.fieldKey())) {
            throw failure("IMMUTABLE_LAYOUT_NODE_IDENTITY", "Node identity cannot change");
        }
        nodes.set(index, new LayoutNode(
            current.id(), current.parentId(), current.nodeKey(), current.nodeType(),
            current.fieldId(), current.fieldKey(), current.sortOrder(),
            requested.config(), requested.visibilityCondition()
        ));
    }

    private void move(List<LayoutNode> nodes, NodeCommand command) {
        index(nodes, command.nodeId());
        reorderAt(nodes, command.nodeId(), command.parentId(), command.targetSortOrder());
    }

    private void copy(List<LayoutNode> nodes, NodeCommand command) {
        LayoutNode source = nodes.get(index(nodes, command.nodeId()));
        Set<UUID> subtree = descendants(nodes, source.id());
        subtree.add(source.id());
        if (nodes.stream().anyMatch(node -> subtree.contains(node.id()) && node.fieldId() != null)) {
            throw failure(
                "LAYOUT_COPY_FIELD_DUPLICATE",
                "A subtree containing fields cannot be copied because fields appear once per layout"
            );
        }
        List<LayoutNode> ordered = nodes.stream()
            .filter(node -> subtree.contains(node.id()))
            .sorted(Comparator.comparingInt(LayoutNode::sortOrder))
            .toList();
        Map<UUID, UUID> copies = new HashMap<>();
        ordered.forEach(node -> copies.put(node.id(), UUID.randomUUID()));
        String suffix = copies.get(source.id()).toString().replace("-", "").substring(0, 8);
        for (LayoutNode node : ordered) {
            UUID parentId = node.id().equals(source.id())
                ? command.parentId()
                : copies.get(node.parentId());
            nodes.add(new LayoutNode(
                copies.get(node.id()),
                parentId,
                copiedKey(node.nodeKey(), suffix, nodes),
                node.nodeType(),
                node.fieldId(),
                node.fieldKey(),
                node.id().equals(source.id()) ? command.targetSortOrder() : node.sortOrder(),
                node.config().deepCopy(),
                node.visibilityCondition().deepCopy()
            ));
        }
        reorderAt(nodes, copies.get(source.id()), command.parentId(), command.targetSortOrder());
    }

    private void delete(
        List<LayoutNode> nodes,
        List<FieldAccessPolicy> policies,
        NodeCommand command
    ) {
        LayoutNode target = nodes.get(index(nodes, command.nodeId()));
        Set<UUID> removing = descendants(nodes, target.id());
        removing.add(target.id());
        Set<UUID> referencedFields = nodes.stream()
            .filter(node -> removing.contains(node.id()) && node.fieldId() != null)
            .map(LayoutNode::fieldId)
            .collect(java.util.stream.Collectors.toSet());
        boolean hasReferences = !referencedFields.isEmpty()
            || nodes.stream().anyMatch(node -> removing.contains(node.parentId()));
        if (hasReferences && !command.confirmReferences()) {
            throw failure(
                "LAYOUT_DELETE_CONFIRMATION_REQUIRED",
                "Deleting a node with descendants or field references requires confirmation"
            );
        }
        nodes.removeIf(node -> removing.contains(node.id()));
        policies.removeIf(policy -> referencedFields.contains(policy.fieldId()));
    }

    private List<LayoutNode> normalizeOrders(List<LayoutNode> nodes) {
        Map<UUID, List<LayoutNode>> siblings = new LinkedHashMap<>();
        for (LayoutNode node : nodes) {
            siblings.computeIfAbsent(node.parentId(), ignored -> new ArrayList<>()).add(node);
        }
        List<LayoutNode> result = new ArrayList<>();
        for (List<LayoutNode> group : siblings.values()) {
            group.sort(Comparator.comparingInt(LayoutNode::sortOrder).thenComparing(LayoutNode::nodeKey));
            for (int index = 0; index < group.size(); index++) {
                LayoutNode node = group.get(index);
                result.add(new LayoutNode(
                    node.id(), node.parentId(), node.nodeKey(), node.nodeType(),
                    node.fieldId(), node.fieldKey(), index, node.config(), node.visibilityCondition()
                ));
            }
        }
        return List.copyOf(result);
    }

    private void reorderAt(List<LayoutNode> nodes, UUID nodeId, UUID parentId, int targetSortOrder) {
        int selectedIndex = index(nodes, nodeId);
        LayoutNode moving = nodes.remove(selectedIndex);
        List<LayoutNode> group = nodes.stream()
            .filter(node -> java.util.Objects.equals(node.parentId(), parentId))
            .sorted(Comparator.comparingInt(LayoutNode::sortOrder).thenComparing(LayoutNode::nodeKey))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int target = Math.max(0, Math.min(group.size(), targetSortOrder));
        group.add(target, withPosition(moving, parentId, target));
        Set<UUID> groupIds = group.stream().map(LayoutNode::id).collect(java.util.stream.Collectors.toSet());
        nodes.removeIf(node -> groupIds.contains(node.id()));
        for (int order = 0; order < group.size(); order++) {
            nodes.add(withPosition(group.get(order), parentId, order));
        }
    }

    private Set<UUID> descendants(List<LayoutNode> nodes, UUID root) {
        Set<UUID> result = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (LayoutNode node : nodes) {
                if ((root.equals(node.parentId()) || result.contains(node.parentId())) && result.add(node.id())) {
                    changed = true;
                }
            }
        } while (changed);
        return result;
    }

    private int index(List<LayoutNode> nodes, UUID nodeId) {
        if (nodeId == null) {
            throw failure("INVALID_LAYOUT_COMMAND", "Node command requires nodeId");
        }
        for (int index = 0; index < nodes.size(); index++) {
            if (nodeId.equals(nodes.get(index).id())) {
                return index;
            }
        }
        throw failure("LAYOUT_NODE_NOT_FOUND", "Layout node is not available");
    }

    private LayoutNode withPosition(LayoutNode node, UUID parentId, int sortOrder) {
        if (sortOrder < 0) {
            throw failure("INVALID_LAYOUT_COMMAND", "Target sort order must be non-negative");
        }
        return new LayoutNode(
            node.id(), parentId, node.nodeKey(), node.nodeType(), node.fieldId(), node.fieldKey(),
            sortOrder, node.config(), node.visibilityCondition()
        );
    }

    private String copiedKey(String source, String suffix, List<LayoutNode> nodes) {
        String base = source.length() > 45 ? source.substring(0, 45) : source;
        String candidate = base + "_copy_" + suffix;
        Set<String> keys = new HashSet<>();
        nodes.forEach(node -> keys.add(node.nodeKey()));
        int attempt = 2;
        while (keys.contains(candidate)) {
            candidate = base + "_copy_" + suffix + "_" + attempt++;
        }
        return candidate;
    }

    public record NodeCommand(
        String operation,
        UUID nodeId,
        UUID parentId,
        int targetSortOrder,
        LayoutNode node,
        boolean confirmReferences,
        long aggregateVersion
    ) {
        public NodeCommand {
            operation = operation == null ? "" : operation.trim().toLowerCase();
        }
    }

    public record CommandResult(List<LayoutNode> nodes, List<FieldAccessPolicy> policies) {
    }
}
