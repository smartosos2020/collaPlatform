package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.application.WorkItemFieldConfigurationModels.Configuration;
import com.colla.platform.modules.project.application.WorkItemFieldConfigurationModels.ConfiguredField;
import com.colla.platform.modules.project.application.WorkItemFieldTypeRegistry.FieldTypeDescriptor;
import com.colla.platform.modules.project.api.WorkItemFieldConfigurationController.ValidationRuleRequest;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.FieldOption;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class WorkItemFieldApiDtos {
    private WorkItemFieldApiDtos() {
    }

    static FieldTypeCatalog fieldTypes(List<FieldTypeDescriptor> descriptors) {
        return new FieldTypeCatalog(descriptors.stream().map(WorkItemFieldApiDtos::fieldType).toList());
    }

    static FieldTypeView fieldType(FieldTypeDescriptor descriptor) {
        return new FieldTypeView(
            descriptor.key(), descriptor.storageKind(), descriptor.configSchemaVersion(), descriptor.operators(),
            descriptor.filterable(), descriptor.sortable(), descriptor.indexCapability(),
            descriptor.supportsOptions(), descriptor.validationRuleKinds(),
            descriptor.valueSchema(), descriptor.typeConfigSchema(),
            descriptor.referencePolicy(), descriptor.invalidReferencePolicy(),
            descriptor.configSchema(), descriptor.defaultConfig()
        );
    }

    static FieldCollection configuration(Configuration configuration) {
        return new FieldCollection(
            configuration.spaceId(), configuration.typeDefinitionId(), configuration.spaceStatus(),
            configuration.availableActions(),
            configuration.fields().stream().map(WorkItemFieldApiDtos::configuredField).toList()
        );
    }

    static FieldView configuredField(ConfiguredField configured) {
        FieldDefinition field = configured.definition();
        return new FieldView(
            field.id(), field.spaceId(), field.typeDefinitionId(), field.fieldKey(), field.name(),
            field.description(), field.fieldType(), field.config(), field.configHash(), field.sortOrder(),
            field.status(), field.system(), field.aggregateVersion(), field.createdBy(), field.createdAt(),
            field.updatedBy(), field.updatedAt(), configured.options().stream().map(WorkItemFieldApiDtos::option).toList(),
            configured.availableActions()
        );
    }

    static FieldOptionView option(FieldOption option) {
        return new FieldOptionView(
            option.id(), option.optionKey(), option.name(), option.color(), option.sortOrder(), option.status(),
            option.createdBy(), option.createdAt(), option.updatedBy(), option.updatedAt()
        );
    }

    static JsonNode configurationJson(
        int schemaVersion,
        boolean required,
        JsonNode defaultValue,
        List<ValidationRuleRequest> rules,
        JsonNode typeConfig
    ) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("schemaVersion", schemaVersion);
        result.put("required", required);
        result.set("defaultValue", defaultValue == null ? JsonNodeFactory.instance.nullNode() : defaultValue);
        var array = result.putArray("validationRules");
        for (ValidationRuleRequest rule : rules) {
            ObjectNode item = array.addObject();
            item.put("ruleKey", rule.ruleKey());
            item.put("kind", rule.kind());
            item.put("schemaVersion", rule.schemaVersion());
            item.set("config", rule.config());
        }
        if (typeConfig != null && !typeConfig.isNull()) {
            result.set("typeConfig", typeConfig);
        }
        return result;
    }

    record FieldTypeCatalog(List<FieldTypeView> items) {
    }

    record FieldTypeView(
        String key,
        String storageKind,
        int configSchemaVersion,
        List<String> operators,
        boolean filterable,
        boolean sortable,
        String indexCapability,
        boolean supportsOptions,
        List<String> validationRuleKinds,
        JsonNode valueSchema,
        JsonNode typeConfigSchema,
        String referencePolicy,
        String invalidReferencePolicy,
        JsonNode configSchema,
        JsonNode defaultConfig
    ) {
    }

    record FieldCollection(
        UUID spaceId,
        UUID typeDefinitionId,
        String spaceStatus,
        List<String> availableActions,
        List<FieldView> items
    ) {
    }

    record FieldView(
        UUID id,
        UUID spaceId,
        UUID typeDefinitionId,
        String fieldKey,
        String name,
        String description,
        String fieldType,
        JsonNode config,
        String configHash,
        int sortOrder,
        String status,
        boolean system,
        long aggregateVersion,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt,
        List<FieldOptionView> options,
        List<String> availableActions
    ) {
    }

    record FieldOptionView(
        UUID id,
        String optionKey,
        String name,
        String color,
        int sortOrder,
        String status,
        UUID createdBy,
        Instant createdAt,
        UUID updatedBy,
        Instant updatedAt
    ) {
    }
}
