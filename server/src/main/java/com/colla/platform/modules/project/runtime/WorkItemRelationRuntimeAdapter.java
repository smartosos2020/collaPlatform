package com.colla.platform.modules.project.runtime;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Cardinality;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.DeletionPolicy;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Direction;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.RelationKind;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationDefinitionBinding;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.WorkItemRelation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemRelationRuntimeAdapter {
    private final PublishedSnapshotAdapter snapshotAdapter;

    public WorkItemRelationRuntimeAdapter(PublishedSnapshotAdapter snapshotAdapter) {
        this.snapshotAdapter = snapshotAdapter;
    }

    public RelationDefinitionBinding requireForCreate(
        WorkItem source,
        WorkItem target,
        String relationKey
    ) {
        RelationDefinitionBinding binding = require(
            source.workspaceId(),
            source.spaceId(),
            source.typeDefinitionId(),
            source.typeVersionId(),
            source.configHash(),
            source.typeKey(),
            relationKey
        );
        if (!binding.sourceTypeKeys().contains(source.typeKey())
            || !binding.targetTypeKeys().contains(target.typeKey())) {
            throw failure(
                "RELATION_TYPE_MATRIX_REJECTED",
                "The bound relation definition does not allow these endpoint types"
            );
        }
        return binding;
    }

    public RelationDefinitionBinding requireForSource(WorkItem source, String relationKey) {
        return require(
            source.workspaceId(),
            source.spaceId(),
            source.typeDefinitionId(),
            source.typeVersionId(),
            source.configHash(),
            source.typeKey(),
            relationKey
        );
    }

    public RelationDefinitionBinding requireStored(WorkItemRelation relation) {
        return requireStored(
            relation.workspaceId(),
            relation.spaceId(),
            relation.definitionTypeId(),
            relation.definitionVersionId(),
            relation.definitionConfigHash(),
            relation.relationKey()
        );
    }

    public RelationDefinitionBinding requireStored(
        UUID workspaceId,
        UUID spaceId,
        UUID definitionTypeId,
        UUID definitionVersionId,
        String definitionConfigHash,
        String relationKey
    ) {
        return require(
            workspaceId,
            spaceId,
            definitionTypeId,
            definitionVersionId,
            definitionConfigHash,
            null,
            relationKey
        );
    }

    private RelationDefinitionBinding require(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        UUID versionId,
        String expectedHash,
        String sourceTypeKey,
        String relationKey
    ) {
        var configuration = snapshotAdapter.requireComplete(workspaceId, spaceId, typeId, versionId);
        if (!configuration.configHash().equals(expectedHash)) {
            throw failure(
                "RELATION_DEFINITION_INTEGRITY_FAILURE",
                "The relation definition snapshot hash does not match the stored binding"
            );
        }
        String boundTypeKey = sourceTypeKey == null
            ? configuration.snapshot().path("typeDefinition").path("typeKey").asText("")
            : sourceTypeKey;
        JsonNode definitions = configuration.snapshot().path("relationDefinitions");
        if (!definitions.isArray()) {
            throw failure(
                "RELATION_DEFINITION_UNAVAILABLE",
                "The bound snapshot does not contain relation definitions"
            );
        }
        for (JsonNode definition : definitions) {
            if (relationKey.equals(definition.path("relationKey").asText())) {
                return binding(typeId, versionId, expectedHash, boundTypeKey, definition);
            }
        }
        throw failure(
            "RELATION_DEFINITION_UNAVAILABLE",
            "The requested relation definition is not available in the bound snapshot"
        );
    }

    private RelationDefinitionBinding binding(
        UUID typeId,
        UUID versionId,
        String configHash,
        String sourceTypeKey,
        JsonNode definition
    ) {
        List<String> sources = strings(definition.path("sourceTypeKeys"));
        List<String> targets = strings(definition.path("targetTypeKeys"));
        if (!sources.contains(sourceTypeKey)) {
            throw failure(
                "RELATION_DEFINITION_INTEGRITY_FAILURE",
                "The relation definition is not bound to its source type"
            );
        }
        try {
            return new RelationDefinitionBinding(
                typeId,
                versionId,
                configHash,
                definition.path("relationKey").asText(),
                RelationKind.parse(definition.path("kind").asText()),
                Direction.parse(definition.path("direction").asText()),
                definition.path("forwardName").asText(),
                definition.path("reverseName").asText(),
                sources,
                targets,
                Cardinality.parse(definition.path("sourceCardinality").asText()),
                Cardinality.parse(definition.path("targetCardinality").asText()),
                DeletionPolicy.parse(definition.path("deletionPolicy").asText()),
                definition.path("allowSelf").asBoolean(false),
                definition.path("maxDepth").asInt(),
                definition.path("sortOrder").asInt()
            );
        } catch (RuntimeException exception) {
            throw failure(
                "RELATION_DEFINITION_INTEGRITY_FAILURE",
                "The stored relation definition is invalid",
                exception
            );
        }
    }

    private List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        if (values.isArray()) {
            values.forEach(value -> result.add(value.asText()));
        }
        return List.copyOf(result);
    }
}
