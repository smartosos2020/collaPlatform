package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemRelationModels.MAX_ENDPOINT_TYPES;
import static com.colla.platform.modules.project.domain.WorkItemRelationModels.MAX_HIERARCHY_DEPTH;
import static com.colla.platform.modules.project.domain.WorkItemRelationModels.MAX_RELATION_DEFINITIONS;
import static com.colla.platform.modules.project.domain.WorkItemRelationModels.SEMANTIC_KEY;

import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDiagnostic;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.DiagnosticSeverity;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Cardinality;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.DeletionPolicy;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.Direction;
import com.colla.platform.modules.project.domain.WorkItemRelationModels.RelationKind;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemRelationDefinitionValidator {
    public void validate(
        JsonNode definitions,
        String boundTypeKey,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        if (!definitions.isArray()) {
            error(diagnostics, "invalid_relation_definitions", "$.relationDefinitions",
                "Relation definitions must be an array");
            return;
        }
        if (definitions.size() > MAX_RELATION_DEFINITIONS) {
            error(diagnostics, "relation_definition_budget_exceeded", "$.relationDefinitions",
                "At most " + MAX_RELATION_DEFINITIONS + " relation definitions are allowed");
        }
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < definitions.size(); index++) {
            JsonNode definition = definitions.get(index);
            String path = "$.relationDefinitions[" + index + "]";
            String key = definition.path("relationKey").asText("");
            if (!SEMANTIC_KEY.matcher(key).matches() || !keys.add(key)) {
                error(diagnostics, "duplicate_or_invalid_relation_key", path + ".relationKey",
                    "Relation keys must be stable, valid and unique");
            }
            RelationKind kind = parseKind(definition, path, diagnostics);
            Direction direction = parseDirection(definition, path, diagnostics);
            parseCardinality(definition, "sourceCardinality", path, diagnostics);
            parseCardinality(definition, "targetCardinality", path, diagnostics);
            parseDeletionPolicy(definition, path, diagnostics);
            validateLabel(definition, "forwardName", path, diagnostics);
            validateLabel(definition, "reverseName", path, diagnostics);
            Set<String> sources = validateTypes(
                definition.path("sourceTypeKeys"), path + ".sourceTypeKeys", diagnostics
            );
            validateTypes(definition.path("targetTypeKeys"), path + ".targetTypeKeys", diagnostics);
            if (!boundTypeKey.isBlank() && !sources.contains(boundTypeKey)) {
                error(diagnostics, "relation_source_type_unbound", path + ".sourceTypeKeys",
                    "The owning type key must be present in sourceTypeKeys");
            }
            boolean structural = kind == RelationKind.parent_child
                || kind == RelationKind.dependency
                || kind == RelationKind.blocking;
            if (structural && direction != Direction.directed) {
                error(diagnostics, "structural_relation_must_be_directed", path + ".direction",
                    "Parent, dependency and blocking relations must be directed");
            }
            if (structural && definition.path("allowSelf").asBoolean(false)) {
                error(diagnostics, "structural_relation_cannot_allow_self", path + ".allowSelf",
                    "Structural relations cannot allow self edges");
            }
            int maxDepth = definition.path("maxDepth").asInt(-1);
            if (maxDepth < 1 || maxDepth > MAX_HIERARCHY_DEPTH) {
                error(diagnostics, "invalid_relation_max_depth", path + ".maxDepth",
                    "Relation maxDepth must be between 1 and " + MAX_HIERARCHY_DEPTH);
            }
            if (!definition.path("sortOrder").canConvertToInt()
                || definition.path("sortOrder").asInt() < 0) {
                error(diagnostics, "invalid_relation_sort_order", path + ".sortOrder",
                    "Relation sortOrder must be a non-negative integer");
            }
        }
    }

    private RelationKind parseKind(
        JsonNode definition, String path, List<ConfigurationDiagnostic> diagnostics
    ) {
        try {
            return RelationKind.parse(definition.path("kind").asText(""));
        } catch (RuntimeException exception) {
            error(diagnostics, "invalid_relation_kind", path + ".kind", "Unknown relation kind");
            return RelationKind.normal;
        }
    }

    private Direction parseDirection(
        JsonNode definition, String path, List<ConfigurationDiagnostic> diagnostics
    ) {
        try {
            return Direction.parse(definition.path("direction").asText(""));
        } catch (RuntimeException exception) {
            error(diagnostics, "invalid_relation_direction", path + ".direction",
                "Unknown relation direction");
            return Direction.directed;
        }
    }

    private void parseCardinality(
        JsonNode definition,
        String field,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        try {
            Cardinality.parse(definition.path(field).asText(""));
        } catch (RuntimeException exception) {
            error(diagnostics, "invalid_relation_cardinality", path + "." + field,
                "Unknown relation cardinality");
        }
    }

    private void parseDeletionPolicy(
        JsonNode definition, String path, List<ConfigurationDiagnostic> diagnostics
    ) {
        try {
            DeletionPolicy.parse(definition.path("deletionPolicy").asText(""));
        } catch (RuntimeException exception) {
            error(diagnostics, "invalid_relation_deletion_policy", path + ".deletionPolicy",
                "Unknown relation deletion policy");
        }
    }

    private Set<String> validateTypes(
        JsonNode values,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        Set<String> result = new HashSet<>();
        if (!values.isArray() || values.isEmpty() || values.size() > MAX_ENDPOINT_TYPES) {
            error(diagnostics, "invalid_relation_type_matrix", path,
                "Endpoint type keys must be a non-empty bounded array");
            return result;
        }
        for (int index = 0; index < values.size(); index++) {
            String key = values.get(index).asText("");
            if (!SEMANTIC_KEY.matcher(key).matches() || !result.add(key)) {
                error(diagnostics, "duplicate_or_invalid_relation_type_key", path + "[" + index + "]",
                    "Endpoint type keys must be stable, valid and unique");
            }
        }
        return result;
    }

    private void validateLabel(
        JsonNode definition,
        String field,
        String path,
        List<ConfigurationDiagnostic> diagnostics
    ) {
        String value = definition.path(field).asText("");
        if (value.isBlank() || value.length() > 80) {
            error(diagnostics, "invalid_relation_label", path + "." + field,
                "Relation labels must contain between 1 and 80 characters");
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
