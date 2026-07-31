package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.failure;
import static com.colla.platform.modules.project.domain.WorkItemFieldModels.normalizeDescription;
import static com.colla.platform.modules.project.domain.WorkItemFieldModels.normalizeFieldKey;
import static com.colla.platform.modules.project.domain.WorkItemFieldModels.normalizeName;
import static com.colla.platform.modules.project.domain.WorkItemFieldModels.normalizeSortOrder;
import static com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.normalize;

import com.colla.platform.modules.project.application.WorkItemLayoutCanonicalizer.CanonicalLayout;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationSnapshot;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldStatus;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.FieldAccessPolicy;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutDefinition;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutKind;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.LayoutNode;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldOptionRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemFieldRepository.NewFieldDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemLayoutRepository.LayoutDefinitionInsert;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes the field and layout portions of a complete configuration snapshot into the
 * legacy authoring tables. Those tables remain the input of the dedicated field/layout editors,
 * so they must mirror an installed template before a later live edit refreshes the active draft.
 */
@Component
public class WorkItemConfigurationAuthorStateHydrator {
    private final WorkItemFieldRepository fieldRepository;
    private final WorkItemFieldOptionRepository optionRepository;
    private final WorkItemLayoutRepository layoutRepository;
    private final WorkItemFieldConfigCanonicalizer fieldCanonicalizer;
    private final WorkItemLayoutCanonicalizer layoutCanonicalizer;
    private final WorkItemConfigurationSnapshotAssembler assembler;
    private final WorkItemConfigurationSnapshotCanonicalizer snapshotCanonicalizer;

    public WorkItemConfigurationAuthorStateHydrator(
        WorkItemFieldRepository fieldRepository,
        WorkItemFieldOptionRepository optionRepository,
        WorkItemLayoutRepository layoutRepository,
        WorkItemFieldConfigCanonicalizer fieldCanonicalizer,
        WorkItemLayoutCanonicalizer layoutCanonicalizer,
        WorkItemConfigurationSnapshotAssembler assembler,
        WorkItemConfigurationSnapshotCanonicalizer snapshotCanonicalizer
    ) {
        this.fieldRepository = fieldRepository;
        this.optionRepository = optionRepository;
        this.layoutRepository = layoutRepository;
        this.fieldCanonicalizer = fieldCanonicalizer;
        this.layoutCanonicalizer = layoutCanonicalizer;
        this.assembler = assembler;
        this.snapshotCanonicalizer = snapshotCanonicalizer;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ConfigurationSnapshot hydrate(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        ConfigurationSnapshot requested,
        UUID actorId
    ) {
        List<DesiredField> desiredFields = desiredFields(requested.payload().path("fields"));
        if (!desiredFields.isEmpty()) {
            hydrateFields(workspaceId, spaceId, typeId, desiredFields, actorId);
        }

        Map<String, FieldDefinition> fieldsByKey = indexFields(
            fieldRepository.listByType(workspaceId, spaceId, typeId, "")
        );
        if (!desiredFields.isEmpty()) {
            hydrateOptions(workspaceId, spaceId, typeId, desiredFields, fieldsByKey, actorId);
        }
        hydrateLayouts(
            workspaceId,
            spaceId,
            typeId,
            requested.payload().path("layouts"),
            fieldsByKey,
            actorId
        );

        ConfigurationSnapshot assembled = assembler.assemble(workspaceId, spaceId, typeId);
        ObjectNode hydrated = requested.payload().deepCopy();
        hydrated.set("fields", assembled.payload().path("fields").deepCopy());
        hydrated.set("layouts", assembled.payload().path("layouts").deepCopy());
        return snapshotCanonicalizer.canonicalize(hydrated);
    }

    private void hydrateFields(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        List<DesiredField> desired,
        UUID actorId
    ) {
        Map<String, FieldDefinition> existing = indexFields(
            fieldRepository.listByType(workspaceId, spaceId, typeId, "")
        );
        Set<String> desiredKeys = desired.stream()
            .map(DesiredField::fieldKey)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> extraKeys = new LinkedHashSet<>(existing.keySet());
        extraKeys.removeAll(desiredKeys);
        if (!extraKeys.isEmpty()) {
            throw failure(
                "TEMPLATE_AUTHOR_STATE_CONFLICT",
                "Template installation would discard existing fields: " + String.join(", ", extraKeys)
            );
        }

        for (DesiredField field : desired.stream()
            .sorted(Comparator.comparing(DesiredField::fieldKey))
            .toList()) {
            FieldDefinition current = existing.get(field.fieldKey());
            if (current == null) {
                fieldRepository.insert(new NewFieldDefinition(
                    field.id(),
                    workspaceId,
                    spaceId,
                    typeId,
                    field.fieldKey(),
                    field.name(),
                    field.description(),
                    field.fieldType(),
                    field.config(),
                    field.configHash(),
                    field.sortOrder(),
                    field.status(),
                    field.system(),
                    actorId
                ));
                continue;
            }
            if (!current.fieldType().equals(field.fieldType()) || current.system() != field.system()) {
                throw failure(
                    "TEMPLATE_AUTHOR_STATE_CONFLICT",
                    "Template field identity conflicts with the existing field: " + field.fieldKey()
                );
            }
            if ("retired".equals(current.status()) && !"retired".equals(field.status())) {
                throw failure(
                    "TEMPLATE_AUTHOR_STATE_CONFLICT",
                    "A retired field cannot be restored by a template: " + field.fieldKey()
                );
            }
            if (fieldRepository.hydrate(
                workspaceId,
                spaceId,
                typeId,
                current.id(),
                field.name(),
                field.description(),
                field.config(),
                field.configHash(),
                field.sortOrder(),
                field.status(),
                actorId,
                current.aggregateVersion()
            ) != 1) {
                throw failure(
                    "TEMPLATE_AUTHOR_STATE_VERSION_CONFLICT",
                    "A work item field changed while the template was being installed"
                );
            }
        }
    }

    private void hydrateOptions(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        List<DesiredField> desiredFields,
        Map<String, FieldDefinition> fieldsByKey,
        UUID actorId
    ) {
        Map<UUID, List<FieldOption>> existingByField = optionRepository
            .listByType(workspaceId, spaceId, typeId)
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(
                FieldOption::fieldDefinitionId,
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));
        for (DesiredField desiredField : desiredFields.stream()
            .sorted(Comparator.comparing(DesiredField::fieldKey))
            .toList()) {
            FieldDefinition field = requireField(fieldsByKey, desiredField.fieldKey());
            Map<String, FieldOption> existing = new LinkedHashMap<>();
            existingByField.getOrDefault(field.id(), List.of())
                .forEach(option -> existing.put(option.optionKey(), option));
            Set<String> desiredKeys = desiredField.options().stream()
                .map(DesiredOption::optionKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<String> extraKeys = new LinkedHashSet<>(existing.keySet());
            extraKeys.removeAll(desiredKeys);
            if (!extraKeys.isEmpty()) {
                throw failure(
                    "TEMPLATE_AUTHOR_STATE_CONFLICT",
                    "Template installation would discard existing options for field "
                        + desiredField.fieldKey() + ": " + String.join(", ", extraKeys)
                );
            }
            for (DesiredOption desired : desiredField.options().stream()
                .sorted(Comparator.comparing(DesiredOption::optionKey))
                .toList()) {
                ConfigureFieldOption option = desired.configuration();
                if (existing.containsKey(option.optionKey())) {
                    if (optionRepository.update(
                        workspaceId,
                        spaceId,
                        typeId,
                        field.id(),
                        option.optionKey(),
                        option,
                        actorId
                    ) != 1) {
                        throw failure(
                            "TEMPLATE_AUTHOR_STATE_VERSION_CONFLICT",
                            "A field option changed while the template was being installed"
                        );
                    }
                } else {
                    optionRepository.insert(
                        desired.id(),
                        workspaceId,
                        spaceId,
                        typeId,
                        field.id(),
                        option,
                        actorId
                    );
                }
            }
        }
    }

    private void hydrateLayouts(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        JsonNode requestedLayouts,
        Map<String, FieldDefinition> fieldsByKey,
        UUID actorId
    ) {
        if (!requestedLayouts.isArray()) {
            throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template layouts must be an array");
        }
        Set<String> kinds = new LinkedHashSet<>();
        List<DesiredLayout> desiredLayouts = new ArrayList<>();
        for (JsonNode requestedLayout : requestedLayouts) {
            String kind = LayoutKind.parse(requestedLayout.path("layoutKind").asText()).name();
            if (!kinds.add(kind)) {
                throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template layout kinds must be unique");
            }
            desiredLayouts.add(new DesiredLayout(kind, requestedLayout));
        }
        desiredLayouts.sort(Comparator.comparing(DesiredLayout::kind));
        for (DesiredLayout desiredLayout : desiredLayouts) {
            String kind = desiredLayout.kind();
            JsonNode requestedLayout = desiredLayout.snapshot();
            LayoutDefinition existing = layoutRepository
                .findAnyByKind(workspaceId, spaceId, typeId, kind)
                .orElse(null);
            if (existing != null && !"active".equals(existing.status())) {
                throw failure(
                    "TEMPLATE_AUTHOR_STATE_CONFLICT",
                    "Template layout conflicts with a disabled layout: " + kind
                );
            }
            UUID layoutId = existing == null
                ? uuid(requestedLayout.path("id"), "layout id")
                : existing.id();
            Map<String, LayoutNode> existingNodes = new LinkedHashMap<>();
            if (existing != null) {
                layoutRepository.listAllNodes(workspaceId, layoutId)
                    .forEach(node -> existingNodes.put(node.nodeKey(), node));
            }
            Map<String, FieldAccessPolicy> existingPolicies = new LinkedHashMap<>();
            if (existing != null) {
                layoutRepository.listAllPolicies(workspaceId, layoutId)
                    .forEach(policy -> existingPolicies.put(policy.policyKey(), policy));
            }

            JsonNode requestedNodes = requestedLayout.path("nodes");
            List<LayoutNode> nodes = requestedNodes.isArray() && requestedNodes.isEmpty()
                ? existing == null
                    ? List.of()
                    : layoutRepository.listNodes(workspaceId, layoutId)
                : nodes(requestedNodes, existingNodes, fieldsByKey);
            JsonNode requestedPolicies = requestedLayout.path("policies");
            List<FieldAccessPolicy> policies = requestedPolicies.isArray() && requestedPolicies.isEmpty()
                ? existing == null
                    ? List.of()
                    : layoutRepository.listPolicies(workspaceId, layoutId)
                : policies(requestedPolicies, existingPolicies, fieldsByKey);
            CanonicalLayout canonical = layoutCanonicalizer.canonicalize(kind, nodes, policies);
            if (existing == null) {
                layoutRepository.insertLayout(new LayoutDefinitionInsert(
                    layoutId,
                    workspaceId,
                    spaceId,
                    typeId,
                    kind,
                    canonical.hash(),
                    actorId
                ));
            } else if (layoutRepository.updateLayout(
                workspaceId,
                spaceId,
                typeId,
                layoutId,
                canonical.hash(),
                actorId,
                existing.aggregateVersion()
            ) != 1) {
                throw failure(
                    "TEMPLATE_AUTHOR_STATE_VERSION_CONFLICT",
                    "A work item layout changed while the template was being installed"
                );
            }
            layoutRepository.replaceNodes(
                workspaceId,
                spaceId,
                typeId,
                layoutId,
                canonical.nodes(),
                actorId
            );
            layoutRepository.replacePolicies(
                workspaceId,
                spaceId,
                typeId,
                layoutId,
                canonical.policies(),
                actorId
            );
        }
    }

    private List<LayoutNode> nodes(
        JsonNode requested,
        Map<String, LayoutNode> existing,
        Map<String, FieldDefinition> fieldsByKey
    ) {
        if (!requested.isArray()) {
            throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template layout nodes must be an array");
        }
        Map<String, JsonNode> requestedByKey = new LinkedHashMap<>();
        Map<String, UUID> idsByKey = new LinkedHashMap<>();
        for (JsonNode node : requested) {
            String nodeKey = required(node.path("nodeKey").asText(), "layout node key");
            if (requestedByKey.putIfAbsent(nodeKey, node) != null) {
                throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template layout node keys must be unique");
            }
            LayoutNode current = existing.get(nodeKey);
            idsByKey.put(nodeKey, current == null ? uuid(node.path("id"), "layout node id") : current.id());
        }

        List<LayoutNode> result = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : requestedByKey.entrySet()) {
            String nodeKey = entry.getKey();
            JsonNode node = entry.getValue();
            String nodeType = required(node.path("nodeType").asText(), "layout node type");
            String parentKey = node.path("parentKey").isNull()
                ? null
                : node.path("parentKey").asText(null);
            UUID parentId = parentKey == null ? null : idsByKey.get(parentKey);
            if (parentKey != null && parentId == null) {
                throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template layout node parent is missing");
            }
            String fieldKey = node.path("fieldKey").isNull()
                ? null
                : node.path("fieldKey").asText(null);
            UUID fieldId = fieldKey == null ? null : requireField(fieldsByKey, fieldKey).id();
            LayoutNode current = existing.get(nodeKey);
            if (current != null
                && (!current.nodeType().equals(nodeType)
                    || !java.util.Objects.equals(current.fieldId(), fieldId)
                    || !java.util.Objects.equals(current.fieldKey(), fieldKey))) {
                throw failure(
                    "TEMPLATE_AUTHOR_STATE_CONFLICT",
                    "Template layout node identity conflicts with the existing node: " + nodeKey
                );
            }
            result.add(new LayoutNode(
                idsByKey.get(nodeKey),
                parentId,
                nodeKey,
                nodeType,
                fieldId,
                fieldKey,
                node.path("sortOrder").asInt(-1),
                node.path("config").deepCopy(),
                node.path("visibilityCondition").deepCopy()
            ));
        }
        return List.copyOf(result);
    }

    private List<FieldAccessPolicy> policies(
        JsonNode requested,
        Map<String, FieldAccessPolicy> existing,
        Map<String, FieldDefinition> fieldsByKey
    ) {
        if (!requested.isArray()) {
            throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template field policies must be an array");
        }
        List<FieldAccessPolicy> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode policy : requested) {
            String policyKey = required(policy.path("policyKey").asText(), "field policy key");
            if (!keys.add(policyKey)) {
                throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template field policy keys must be unique");
            }
            String fieldKey = required(policy.path("fieldKey").asText(), "field policy field key");
            UUID fieldId = requireField(fieldsByKey, fieldKey).id();
            FieldAccessPolicy current = existing.get(policyKey);
            if (current != null
                && (!current.fieldId().equals(fieldId) || !current.fieldKey().equals(fieldKey))) {
                throw failure(
                    "TEMPLATE_AUTHOR_STATE_CONFLICT",
                    "Template field policy identity conflicts with the existing policy: " + policyKey
                );
            }
            result.add(new FieldAccessPolicy(
                current == null ? uuid(policy.path("id"), "field policy id") : current.id(),
                fieldId,
                fieldKey,
                policyKey,
                policy.path("policy").deepCopy(),
                ""
            ));
        }
        return List.copyOf(result);
    }

    private List<DesiredField> desiredFields(JsonNode requested) {
        if (!requested.isArray()) {
            throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template fields must be an array");
        }
        List<DesiredField> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode field : requested) {
            String fieldKey = normalizeFieldKey(field.path("fieldKey").asText());
            if (!keys.add(fieldKey)) {
                throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template field keys must be unique");
            }
            List<DesiredOption> options = desiredOptions(field.path("options"));
            List<ConfigureFieldOption> configurations = options.stream()
                .map(DesiredOption::configuration)
                .toList();
            String fieldType = required(field.path("fieldType").asText(), "field type");
            var canonical = fieldCanonicalizer.canonicalize(
                fieldType,
                field.path("config"),
                configurations
            );
            result.add(new DesiredField(
                uuid(field.path("id"), "field id"),
                fieldKey,
                normalizeName(field.path("name").asText()),
                normalizeDescription(field.path("description").asText("")),
                fieldType,
                canonical.config(),
                canonical.hash(),
                normalizeSortOrder(field.path("sortOrder").asInt(-1)),
                FieldStatus.parse(field.path("status").asText()).name(),
                field.path("system").asBoolean(false),
                options
            ));
        }
        return List.copyOf(result);
    }

    private List<DesiredOption> desiredOptions(JsonNode requested) {
        if (!requested.isArray()) {
            throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template field options must be an array");
        }
        List<DesiredOption> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode option : requested) {
            ConfigureFieldOption normalized = normalize(new ConfigureFieldOption(
                option.path("optionKey").asText(),
                option.path("name").asText(),
                option.path("color").asText(),
                option.path("sortOrder").asInt(-1),
                option.path("status").asText()
            ));
            if (!keys.add(normalized.optionKey())) {
                throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template option keys must be unique");
            }
            result.add(new DesiredOption(
                uuid(option.path("id"), "field option id"),
                normalized
            ));
        }
        return List.copyOf(result);
    }

    private Map<String, FieldDefinition> indexFields(List<FieldDefinition> fields) {
        Map<String, FieldDefinition> result = new LinkedHashMap<>();
        fields.forEach(field -> result.put(field.fieldKey(), field));
        return result;
    }

    private FieldDefinition requireField(Map<String, FieldDefinition> fieldsByKey, String fieldKey) {
        FieldDefinition field = fieldsByKey.get(fieldKey);
        if (field == null) {
            throw failure(
                "INVALID_CONFIGURATION_SNAPSHOT",
                "Template layout references an unavailable field: " + fieldKey
            );
        }
        return field;
    }

    private UUID uuid(JsonNode value, String label) {
        try {
            return UUID.fromString(value.asText(""));
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template " + label + " must be a UUID");
        }
    }

    private String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw failure("INVALID_CONFIGURATION_SNAPSHOT", "Template " + label + " is required");
        }
        return normalized;
    }

    private record DesiredField(
        UUID id,
        String fieldKey,
        String name,
        String description,
        String fieldType,
        JsonNode config,
        String configHash,
        int sortOrder,
        String status,
        boolean system,
        List<DesiredOption> options
    ) {
    }

    private record DesiredOption(UUID id, ConfigureFieldOption configuration) {
        private String optionKey() {
            return configuration.optionKey();
        }
    }

    private record DesiredLayout(String kind, JsonNode snapshot) {
    }
}
