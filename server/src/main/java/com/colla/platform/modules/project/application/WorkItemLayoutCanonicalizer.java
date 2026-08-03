package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.MAX_COLUMNS_PER_PARENT;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.MAX_DEPTH;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.MAX_LAYOUT_COLUMNS;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.MAX_NODES;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.MAX_POLICIES;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.MIN_LAYOUT_COLUMNS;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.RELATION_CONTROL_SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.failure;
import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.stableKey;

import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutKind;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.NodeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class WorkItemLayoutCanonicalizer {
    private static final Pattern RELATION_KEY = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
    private static final Set<String> RELATION_MODES =
        Set.of("picker", "list", "hierarchy", "impact");
    private final ObjectMapper objectMapper;
    private final WorkItemTypeConfigCanonicalizer canonicalizer;
    private final WorkItemLayoutConditionDsl conditionDsl;
    private final WorkItemFieldAccessPolicySchema policySchema;

    public WorkItemLayoutCanonicalizer(
        ObjectMapper objectMapper,
        WorkItemTypeConfigCanonicalizer canonicalizer,
        WorkItemLayoutConditionDsl conditionDsl,
        WorkItemFieldAccessPolicySchema policySchema
    ) {
        this.objectMapper = objectMapper;
        this.canonicalizer = canonicalizer;
        this.conditionDsl = conditionDsl;
        this.policySchema = policySchema;
    }

    public CanonicalLayout canonicalize(
        String layoutKind,
        List<LayoutNode> requestedNodes,
        List<FieldAccessPolicy> requestedPolicies
    ) {
        String kind = LayoutKind.parse(layoutKind).name();
        List<LayoutNode> nodes = normalizeNodes(kind, requestedNodes);
        Map<UUID, Integer> depths = validateTree(nodes);
        List<LayoutNode> orderedNodes = nodes.stream()
            .sorted(Comparator.comparingInt((LayoutNode node) -> depths.get(node.id()))
                .thenComparing(node -> node.parentId() == null ? "" : node.parentId().toString())
                .thenComparingInt(LayoutNode::sortOrder)
                .thenComparing(LayoutNode::nodeKey))
            .toList();
        List<FieldAccessPolicy> policies = normalizePolicies(requestedPolicies);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("layoutKind", kind);
        ArrayNode nodeArray = root.putArray("nodes");
        orderedNodes.stream().sorted(Comparator.comparing(LayoutNode::nodeKey))
            .forEach(node -> nodeArray.add(nodeJson(node)));
        ArrayNode policyArray = root.putArray("policies");
        policies.stream().sorted(Comparator.comparing(FieldAccessPolicy::policyKey))
            .forEach(policy -> policyArray.add(policyJson(policy)));
        JsonNode config = canonicalizer.sort(root);
        return new CanonicalLayout(kind, orderedNodes, policies, canonicalizer.hash(config), config);
    }

    private List<LayoutNode> normalizeNodes(String layoutKind, List<LayoutNode> requested) {
        List<LayoutNode> values = requested == null ? List.of() : requested;
        if (values.size() > MAX_NODES) {
            throw failure("LAYOUT_NODE_LIMIT", "A layout can contain at most " + MAX_NODES + " nodes");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        Set<String> keys = new LinkedHashSet<>();
        Set<UUID> fieldIds = new LinkedHashSet<>();
        List<LayoutNode> result = new ArrayList<>();
        for (LayoutNode value : values) {
            if (value == null || value.id() == null) {
                throw failure("INVALID_LAYOUT_NODE", "Every layout node requires a permanent id");
            }
            String key = stableKey(value.nodeKey(), "INVALID_LAYOUT_NODE_KEY", "Layout node key");
            NodeType type = NodeType.parse(value.nodeType());
            if (!ids.add(value.id()) || !keys.add(key)) {
                throw failure("DUPLICATE_LAYOUT_NODE", "Layout node ids and keys must be unique");
            }
            if (value.sortOrder() < 0) {
                throw failure("INVALID_LAYOUT_NODE", "Layout node sort order must be non-negative");
            }
            JsonNode config = switch (type) {
                case relation -> relationConfig(layoutKind, value.config());
                case section, tab -> containerConfig(value.config());
                default -> object(value.config(), "INVALID_LAYOUT_NODE", "Layout node config");
            };
            JsonNode condition = conditionDsl.canonicalize(value.visibilityCondition());
            UUID fieldId = value.fieldId();
            String fieldKey = value.fieldKey() == null
                ? null
                : stableKey(value.fieldKey(), "INVALID_LAYOUT_FIELD_REFERENCE", "Layout field key");
            if (type == NodeType.field) {
                if (fieldId == null || fieldKey == null || !fieldIds.add(fieldId)) {
                    throw failure(
                        "INVALID_LAYOUT_FIELD_REFERENCE",
                        "Field nodes require one unique fieldId and matching fieldKey"
                    );
                }
            } else if (fieldId != null || fieldKey != null) {
                throw failure("INVALID_LAYOUT_FIELD_REFERENCE", "Only field nodes can reference a field");
            }
            result.add(new LayoutNode(
                value.id(), value.parentId(), key, type.name(), fieldId, fieldKey,
                value.sortOrder(), config, condition
            ));
        }
        return List.copyOf(result);
    }

    private Map<UUID, Integer> validateTree(List<LayoutNode> nodes) {
        Map<UUID, LayoutNode> byId = new LinkedHashMap<>();
        nodes.forEach(node -> byId.put(node.id(), node));
        Map<String, Set<Integer>> orders = new HashMap<>();
        Map<UUID, Integer> columnCounts = new HashMap<>();
        for (LayoutNode node : nodes) {
            if (node.id().equals(node.parentId())) {
                throw failure("INVALID_LAYOUT_TREE", "A layout node cannot parent itself");
            }
            LayoutNode parent = node.parentId() == null ? null : byId.get(node.parentId());
            if (node.parentId() != null && parent == null) {
                throw failure("INVALID_LAYOUT_TREE", "Layout node parent is missing");
            }
            validateParent(node, parent);
            String parentKey = node.parentId() == null ? "root" : node.parentId().toString();
            if (!orders.computeIfAbsent(parentKey, ignored -> new HashSet<>()).add(node.sortOrder())) {
                throw failure("INVALID_LAYOUT_TREE", "Sibling sort orders must be unique");
            }
            if (NodeType.parse(node.nodeType()) == NodeType.column && node.parentId() != null
                && columnCounts.merge(node.parentId(), 1, Integer::sum) > MAX_COLUMNS_PER_PARENT) {
                throw failure(
                    "LAYOUT_COLUMN_LIMIT",
                    "A layout parent can contain at most " + MAX_COLUMNS_PER_PARENT + " columns"
                );
            }
        }
        Map<UUID, Integer> depths = new HashMap<>();
        for (LayoutNode node : nodes) {
            depth(node, byId, depths, new LinkedHashSet<>());
        }
        return depths;
    }

    private int depth(
        LayoutNode node,
        Map<UUID, LayoutNode> byId,
        Map<UUID, Integer> depths,
        Set<UUID> path
    ) {
        Integer known = depths.get(node.id());
        if (known != null) {
            return known;
        }
        if (!path.add(node.id())) {
            throw failure("INVALID_LAYOUT_TREE", "Layout graph contains a cycle");
        }
        int value = node.parentId() == null ? 1 : depth(byId.get(node.parentId()), byId, depths, path) + 1;
        path.remove(node.id());
        if (value > MAX_DEPTH) {
            throw failure("LAYOUT_DEPTH_LIMIT", "Layout depth cannot exceed " + MAX_DEPTH);
        }
        depths.put(node.id(), value);
        return value;
    }

    private void validateParent(LayoutNode child, LayoutNode parent) {
        NodeType childType = NodeType.parse(child.nodeType());
        if (parent == null) {
            if (!Set.of(NodeType.section, NodeType.tab).contains(childType)) {
                throw failure("INVALID_LAYOUT_TREE", "Root layout nodes must be section or tab");
            }
            return;
        }
        NodeType parentType = NodeType.parse(parent.nodeType());
        Set<NodeType> allowed = switch (parentType) {
            case section, tab -> Set.of(
                NodeType.section, NodeType.column, NodeType.field, NodeType.relation, NodeType.summary
            );
            case column -> Set.of(NodeType.field, NodeType.relation, NodeType.summary);
            case field, relation, summary -> Set.of();
        };
        if (!allowed.contains(childType)) {
            throw failure(
                "INVALID_LAYOUT_TREE",
                "Layout node " + child.nodeKey() + " cannot be nested under " + parent.nodeKey()
            );
        }
    }

    private List<FieldAccessPolicy> normalizePolicies(List<FieldAccessPolicy> requested) {
        List<FieldAccessPolicy> values = requested == null ? List.of() : requested;
        if (values.size() > MAX_POLICIES) {
            throw failure("LAYOUT_POLICY_LIMIT", "A work item type can contain at most " + MAX_POLICIES + " policies");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        Set<String> keys = new LinkedHashSet<>();
        Set<UUID> fieldIds = new LinkedHashSet<>();
        List<FieldAccessPolicy> result = new ArrayList<>();
        for (FieldAccessPolicy value : values) {
            if (value == null || value.id() == null || value.fieldId() == null) {
                throw failure("INVALID_FIELD_ACCESS_POLICY", "Field access policies require permanent and field ids");
            }
            String fieldKey = stableKey(
                value.fieldKey(), "INVALID_LAYOUT_FIELD_REFERENCE", "Policy field key"
            );
            String policyKey = stableKey(
                value.policyKey(), "INVALID_FIELD_ACCESS_POLICY", "Field access policy key"
            );
            if (!ids.add(value.id()) || !keys.add(policyKey) || !fieldIds.add(value.fieldId())) {
                throw failure(
                    "DUPLICATE_FIELD_ACCESS_POLICY",
                    "Field access policy ids, keys, and field references must be unique"
                );
            }
            JsonNode policy = policySchema.canonicalize(value.policy());
            result.add(new FieldAccessPolicy(
                value.id(), value.fieldId(), fieldKey, policyKey, policy, canonicalizer.hash(policy)
            ));
        }
        return List.copyOf(result);
    }

    private JsonNode object(JsonNode value, String code, String label) {
        JsonNode normalized = value == null || value.isNull() ? objectMapper.createObjectNode() : value;
        if (!normalized.isObject()) {
            throw failure(code, label + " must be an object");
        }
        return canonicalizer.sort(normalized);
    }

    private JsonNode versionedObject(JsonNode value, String code, String label) {
        ObjectNode normalized = (ObjectNode) object(value, code, label);
        if (!normalized.has("schemaVersion")) {
            normalized.put("schemaVersion", 1);
        }
        if (!normalized.path("schemaVersion").isInt() || normalized.path("schemaVersion").asInt() != 1) {
            throw failure(code, label + " schemaVersion must be 1");
        }
        return canonicalizer.sort(normalized);
    }

    private JsonNode relationConfig(String layoutKind, JsonNode requested) {
        ObjectNode config = (ObjectNode) object(
            requested, "INVALID_RELATION_CONTROL", "Relation control config"
        );
        Set<String> allowed = Set.of(
            "schemaVersion", "relationKey", "mode", "title", "maxItems",
            "showReverse", "collapsedByDefault", "listFallback", "keyboardNavigation"
        );
        config.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw failure(
                    "INVALID_RELATION_CONTROL",
                    "Unknown relation control config property: " + name
                );
            }
        });
        if (!config.has("schemaVersion")) {
            config.put("schemaVersion", RELATION_CONTROL_SCHEMA_VERSION);
        }
        if (!config.path("schemaVersion").isInt()
            || config.path("schemaVersion").asInt() != RELATION_CONTROL_SCHEMA_VERSION) {
            throw failure("INVALID_RELATION_CONTROL", "Relation control schemaVersion must be 1");
        }
        String relationKey = config.path("relationKey").asText("").trim().toLowerCase();
        if (!RELATION_KEY.matcher(relationKey).matches()) {
            throw failure(
                "INVALID_RELATION_CONTROL",
                "Relation control requires a permanent relationKey"
            );
        }
        String mode = config.path("mode").asText("list").trim().toLowerCase();
        if (!RELATION_MODES.contains(mode)) {
            throw failure("INVALID_RELATION_CONTROL", "Unknown relation control mode");
        }
        if ("create".equals(layoutKind) && !"picker".equals(mode)) {
            throw failure(
                "INVALID_RELATION_CONTROL",
                "Create layouts may only use relation picker controls"
            );
        }
        int maxItems = config.path("maxItems").asInt(50);
        if (maxItems < 1 || maxItems > 200) {
            throw failure(
                "INVALID_RELATION_CONTROL",
                "Relation control maxItems must be between 1 and 200"
            );
        }
        config.put("relationKey", relationKey);
        config.put("mode", mode);
        config.put("maxItems", maxItems);
        config.put("showReverse", config.path("showReverse").asBoolean(true));
        config.put("collapsedByDefault", config.path("collapsedByDefault").asBoolean(false));
        config.put("listFallback", config.path("listFallback").asBoolean(true));
        config.put("keyboardNavigation", config.path("keyboardNavigation").asBoolean(true));
        return canonicalizer.sort(config);
    }

    private JsonNode containerConfig(JsonNode requested) {
        ObjectNode config = (ObjectNode) object(
            requested, "INVALID_LAYOUT_NODE", "Layout container config"
        );
        JsonNode requestedColumns = config.get("columns");
        int columns = requestedColumns == null ? 2 : requestedColumns.asInt(-1);
        if (requestedColumns != null && (!requestedColumns.isInt()
            || columns < MIN_LAYOUT_COLUMNS || columns > MAX_LAYOUT_COLUMNS)) {
            throw failure(
                "INVALID_LAYOUT_NODE_CONFIG",
                "Layout container columns must be between " + MIN_LAYOUT_COLUMNS
                    + " and " + MAX_LAYOUT_COLUMNS
            );
        }
        config.put("columns", columns);
        return canonicalizer.sort(config);
    }

    private JsonNode nodeJson(LayoutNode node) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", node.id().toString());
        if (node.parentId() == null) {
            result.putNull("parentId");
        } else {
            result.put("parentId", node.parentId().toString());
        }
        result.put("nodeKey", node.nodeKey());
        result.put("nodeType", node.nodeType());
        if (node.fieldId() == null) {
            result.putNull("fieldId");
            result.putNull("fieldKey");
        } else {
            result.put("fieldId", node.fieldId().toString());
            result.put("fieldKey", node.fieldKey());
        }
        result.put("sortOrder", node.sortOrder());
        result.set("config", node.config());
        result.set("visibilityCondition", node.visibilityCondition());
        return result;
    }

    private JsonNode policyJson(FieldAccessPolicy policy) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", policy.id().toString());
        result.put("fieldId", policy.fieldId().toString());
        result.put("fieldKey", policy.fieldKey());
        result.put("policyKey", policy.policyKey());
        result.set("policy", policy.policy());
        return result;
    }

    public record CanonicalLayout(
        String layoutKind,
        List<LayoutNode> nodes,
        List<FieldAccessPolicy> policies,
        String hash,
        JsonNode config
    ) {
    }
}
