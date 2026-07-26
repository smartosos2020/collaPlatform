package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.SNAPSHOT_SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.failure;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationSnapshot;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutDefinition;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldOptionRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorkItemConfigurationSnapshotAssembler {
    private final WorkItemTypeRepository typeRepository;
    private final WorkItemFieldRepository fieldRepository;
    private final WorkItemFieldOptionRepository optionRepository;
    private final WorkItemLayoutRepository layoutRepository;
    private final WorkItemStateFlowPresetCatalog stateFlowPresetCatalog;
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;

    public WorkItemConfigurationSnapshotAssembler(
        WorkItemTypeRepository typeRepository,
        WorkItemFieldRepository fieldRepository,
        WorkItemFieldOptionRepository optionRepository,
        WorkItemLayoutRepository layoutRepository,
        WorkItemStateFlowPresetCatalog stateFlowPresetCatalog,
        WorkItemConfigurationSnapshotCanonicalizer canonicalizer,
        ObjectMapper objectMapper
    ) {
        this.typeRepository = typeRepository;
        this.fieldRepository = fieldRepository;
        this.optionRepository = optionRepository;
        this.layoutRepository = layoutRepository;
        this.stateFlowPresetCatalog = stateFlowPresetCatalog;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
    }

    public ConfigurationSnapshot assemble(UUID workspaceId, UUID spaceId, UUID typeId) {
        WorkItemTypeDefinition type = typeRepository.findById(workspaceId, spaceId, typeId)
            .orElseThrow(() -> failure("TYPE_NOT_FOUND", "Work item type is not available in the requested scope"));
        List<FieldDefinition> fields = fieldRepository.listByType(workspaceId, spaceId, typeId, "");
        Map<UUID, List<FieldOption>> options = optionRepository.listByType(workspaceId, spaceId, typeId)
            .stream()
            .collect(Collectors.groupingBy(FieldOption::fieldDefinitionId));

        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotSchemaVersion", SNAPSHOT_SCHEMA_VERSION);
        root.set("typeDefinition", typeJson(type));
        ArrayNode fieldArray = root.putArray("fields");
        fields.stream()
            .sorted(Comparator.comparingInt(FieldDefinition::sortOrder)
                .thenComparing(FieldDefinition::fieldKey)
                .thenComparing(FieldDefinition::id))
            .forEach(field -> fieldArray.add(fieldJson(field, options.getOrDefault(field.id(), List.of()))));
        ArrayNode layouts = root.putArray("layouts");
        List.of("create", "detail").stream()
            .map(kind -> layoutRepository.findByKind(workspaceId, spaceId, typeId, kind).orElse(null))
            .filter(java.util.Objects::nonNull)
            .forEach(layout -> layouts.add(layoutJson(workspaceId, layout)));
        if (type.system()) {
            stateFlowPresetCatalog.stateFlowFor(type.typeKey())
                .ifPresent(stateFlow -> root.set("stateFlow", stateFlow));
        }
        return canonicalizer.canonicalize(root);
    }

    private ObjectNode typeJson(WorkItemTypeDefinition type) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", type.id().toString());
        result.put("workspaceId", type.workspaceId().toString());
        result.put("spaceId", type.spaceId().toString());
        result.put("typeKey", type.typeKey());
        result.put("name", type.name());
        result.put("icon", type.icon());
        result.put("description", type.description());
        result.put("sortOrder", type.sortOrder());
        result.put("status", type.status());
        result.put("system", type.system());
        return result;
    }

    private ObjectNode fieldJson(FieldDefinition field, List<FieldOption> options) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", field.id().toString());
        result.put("fieldKey", field.fieldKey());
        result.put("name", field.name());
        result.put("description", field.description());
        result.put("fieldType", field.fieldType());
        result.set("config", field.config());
        result.put("sortOrder", field.sortOrder());
        result.put("status", field.status());
        result.put("system", field.system());
        ArrayNode optionArray = result.putArray("options");
        options.stream()
            .sorted(Comparator.comparingInt(FieldOption::sortOrder)
                .thenComparing(FieldOption::optionKey)
                .thenComparing(FieldOption::id))
            .forEach(option -> optionArray.add(optionJson(option)));
        return result;
    }

    private ObjectNode optionJson(FieldOption option) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", option.id().toString());
        result.put("optionKey", option.optionKey());
        result.put("name", option.name());
        result.put("color", option.color());
        result.put("sortOrder", option.sortOrder());
        result.put("status", option.status());
        return result;
    }

    private ObjectNode layoutJson(UUID workspaceId, LayoutDefinition layout) {
        List<LayoutNode> nodes = layoutRepository.listNodes(workspaceId, layout.id());
        Map<UUID, LayoutNode> nodesById = nodes.stream().collect(Collectors.toMap(LayoutNode::id, Function.identity()));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", layout.id().toString());
        result.put("layoutKind", layout.layoutKind());
        result.put("status", layout.status());
        ArrayNode nodeArray = result.putArray("nodes");
        nodes.stream()
            .sorted(Comparator.comparingInt(LayoutNode::sortOrder)
                .thenComparing(LayoutNode::nodeKey)
                .thenComparing(LayoutNode::id))
            .forEach(node -> nodeArray.add(nodeJson(node, nodesById)));
        ArrayNode policyArray = result.putArray("policies");
        layoutRepository.listPolicies(workspaceId, layout.id()).stream()
            .sorted(Comparator.comparing(FieldAccessPolicy::fieldKey)
                .thenComparing(FieldAccessPolicy::policyKey)
                .thenComparing(FieldAccessPolicy::id))
            .forEach(policy -> policyArray.add(policyJson(policy)));
        return result;
    }

    private ObjectNode nodeJson(LayoutNode node, Map<UUID, LayoutNode> nodesById) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", node.id().toString());
        result.put("nodeKey", node.nodeKey());
        result.put("nodeType", node.nodeType());
        if (node.parentId() == null) {
            result.putNull("parentKey");
        } else {
            LayoutNode parent = nodesById.get(node.parentId());
            if (parent == null) {
                throw failure("INVALID_CONFIGURATION_GRAPH", "Layout node parent is unavailable");
            }
            result.put("parentKey", parent.nodeKey());
        }
        if (node.fieldKey() == null) {
            result.putNull("fieldKey");
        } else {
            result.put("fieldKey", node.fieldKey());
        }
        result.put("sortOrder", node.sortOrder());
        result.set("config", node.config());
        result.set("visibilityCondition", node.visibilityCondition());
        return result;
    }

    private ObjectNode policyJson(FieldAccessPolicy policy) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", policy.id().toString());
        result.put("fieldKey", policy.fieldKey());
        result.put("policyKey", policy.policyKey());
        result.set("policy", policy.policy());
        return result;
    }
}
