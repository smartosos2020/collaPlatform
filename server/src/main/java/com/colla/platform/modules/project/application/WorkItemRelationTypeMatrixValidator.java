package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DiagnosticSeverity;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemRelationTypeMatrixValidator {
    private final WorkItemTypeRepository typeRepository;

    public WorkItemRelationTypeMatrixValidator(WorkItemTypeRepository typeRepository) {
        this.typeRepository = typeRepository;
    }

    public List<ConfigurationDiagnostic> validate(
        UUID workspaceId,
        UUID spaceId,
        UUID typeId,
        JsonNode snapshot
    ) {
        if (snapshot.path("snapshotSchemaVersion").asInt() < 4) {
            return List.of();
        }
        List<ConfigurationDiagnostic> diagnostics = new ArrayList<>();
        var owningType = typeRepository.findById(workspaceId, spaceId, typeId).orElse(null);
        JsonNode typeDefinition = snapshot.path("typeDefinition");
        if (owningType == null
            || !owningType.typeKey().equals(typeDefinition.path("typeKey").asText(""))
            || !workspaceId.toString().equals(typeDefinition.path("workspaceId").asText(""))
            || !spaceId.toString().equals(typeDefinition.path("spaceId").asText(""))) {
            error(diagnostics, "relation_definition_scope_mismatch", "$.typeDefinition",
                "Relation definitions must be bound to the requested workspace, space and type");
            return List.copyOf(diagnostics);
        }
        Set<String> availableTypeKeys = new HashSet<>();
        typeRepository.listBySpace(workspaceId, spaceId, "").forEach(type ->
            availableTypeKeys.add(type.typeKey())
        );
        JsonNode definitions = snapshot.path("relationDefinitions");
        for (int index = 0; index < definitions.size(); index++) {
            validateEndpointTypes(
                definitions.get(index).path("sourceTypeKeys"),
                "$.relationDefinitions[" + index + "].sourceTypeKeys",
                availableTypeKeys,
                diagnostics
            );
            validateEndpointTypes(
                definitions.get(index).path("targetTypeKeys"),
                "$.relationDefinitions[" + index + "].targetTypeKeys",
                availableTypeKeys,
                diagnostics
            );
        }
        return List.copyOf(diagnostics);
    }

    private void validateEndpointTypes(
        JsonNode values,
        String path,
        Set<String> availableTypeKeys,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        for (int index = 0; index < values.size(); index++) {
            String key = values.get(index).asText("");
            if (!availableTypeKeys.contains(key)) {
                error(diagnostics, "relation_type_key_not_in_space", path + "[" + index + "]",
                    "Relation endpoint type keys must resolve inside the requested space");
            }
        }
    }

    private void error(
        List<ConfigurationDiagnostic> diagnostics,
        String code,
        String path,
        String message
    ) {
        diagnostics.add(new ConfigurationDiagnostic(code, DiagnosticSeverity.error, path, message));
    }
}
