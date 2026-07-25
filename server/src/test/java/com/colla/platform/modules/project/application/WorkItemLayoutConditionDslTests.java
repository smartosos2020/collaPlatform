package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemLayoutModels.WorkItemLayoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemLayoutConditionDslTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemLayoutConditionDsl dsl = new WorkItemLayoutConditionDsl(
        objectMapper,
        new WorkItemTypeConfigCanonicalizer(objectMapper)
    );

    @Test
    void canonicalizesExtractsAndEvaluatesTypedPredicatesWithoutExternalEffects() {
        UUID fieldId = UUID.randomUUID();
        ObjectNode condition = objectMapper.createObjectNode();
        condition.put("schemaVersion", 1);
        var expression = condition.putObject("expression");
        expression.put("kind", "all");
        var operands = expression.putArray("operands");
        operands.addObject()
            .put("kind", "predicate")
            .put("source", "field")
            .put("fieldId", fieldId.toString())
            .put("fieldKey", "estimate")
            .put("operator", "gte")
            .put("value", 5);
        operands.addObject()
            .put("kind", "predicate")
            .put("source", "context")
            .put("contextKey", "mode")
            .put("operator", "eq")
            .put("value", "edit");

        var canonical = dsl.canonicalize(condition);

        assertTrue(dsl.evaluate(
            canonical,
            Map.of("estimate", objectMapper.getNodeFactory().numberNode(8)),
            Map.of("mode", objectMapper.getNodeFactory().textNode("edit"))
        ));
        assertFalse(dsl.evaluate(
            canonical,
            Map.of("estimate", objectMapper.getNodeFactory().numberNode(3)),
            Map.of("mode", objectMapper.getNodeFactory().textNode("edit"))
        ));
        assertTrue(dsl.fieldReferences(canonical).stream().anyMatch(reference ->
            reference.fieldId().equals(fieldId) && reference.operator().equals("gte")
        ));
    }

    @Test
    void rejectsUnknownContextCodeLikeExpressionsAndOversizedDepth() {
        ObjectNode unknownContext = predicate("context", "eq");
        unknownContext.put("contextKey", "http_request");
        assertThrows(WorkItemLayoutException.class, () -> dsl.canonicalize(wrap(unknownContext)));

        ObjectNode code = objectMapper.createObjectNode().put("kind", "javascript").put("code", "fetch('/')");
        assertThrows(WorkItemLayoutException.class, () -> dsl.canonicalize(wrap(code)));

        ObjectNode nested = predicate("context", "eq");
        nested.put("contextKey", "mode");
        nested.put("value", "edit");
        for (int index = 0; index < 10; index++) {
            nested = objectMapper.createObjectNode().put("kind", "not").set("operand", nested);
        }
        ObjectNode tooDeep = nested;
        assertThrows(WorkItemLayoutException.class, () -> dsl.canonicalize(wrap(tooDeep)));
    }

    @Test
    void evaluatesDeepCompositeConditionsDeterministicallyWithinTheFrozenBudget() {
        ObjectNode deep = predicate("context", "eq");
        deep.put("contextKey", "mode");
        deep.put("value", "edit");
        for (int index = 0; index < 5; index++) {
            deep = objectMapper.createObjectNode().put("kind", "not").set("operand", deep);
        }

        ObjectNode root = objectMapper.createObjectNode().put("kind", "all");
        ArrayNode operands = root.putArray("operands");
        operands.add(deep);
        for (int index = 0; index < 32; index++) {
            operands.addObject()
                .put("kind", "predicate")
                .put("source", "context")
                .put("contextKey", "actor_role")
                .put("operator", "in")
                .putArray("value")
                .add("owner")
                .add("admin");
        }

        var canonical = dsl.canonicalize(wrap(root));
        assertFalse(dsl.evaluate(
            canonical,
            Map.of(),
            Map.of(
                "mode", objectMapper.getNodeFactory().textNode("edit"),
                "actor_role", objectMapper.getNodeFactory().textNode("owner")
            )
        ));
        assertTrue(dsl.evaluate(
            canonical,
            Map.of(),
            Map.of(
                "mode", objectMapper.getNodeFactory().textNode("view"),
                "actor_role", objectMapper.getNodeFactory().textNode("owner")
            )
        ));
        assertTrue(canonical.equals(dsl.canonicalize(canonical)));
    }

    private ObjectNode predicate(String source, String operator) {
        return objectMapper.createObjectNode()
            .put("kind", "predicate")
            .put("source", source)
            .put("operator", operator);
    }

    private ObjectNode wrap(ObjectNode expression) {
        ObjectNode condition = objectMapper.createObjectNode().put("schemaVersion", 1);
        condition.set("expression", expression);
        return condition;
    }
}
