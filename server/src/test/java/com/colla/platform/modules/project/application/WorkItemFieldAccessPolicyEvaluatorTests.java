package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.application.WorkItemFieldAccessPolicyEvaluator.EvaluationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class WorkItemFieldAccessPolicyEvaluatorTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemTypeConfigCanonicalizer canonicalizer =
        new WorkItemTypeConfigCanonicalizer(objectMapper);
    private final WorkItemLayoutConditionDsl conditionDsl =
        new WorkItemLayoutConditionDsl(objectMapper, canonicalizer);
    private final WorkItemFieldAccessPolicyEvaluator evaluator =
        new WorkItemFieldAccessPolicyEvaluator(
            new WorkItemFieldAccessPolicySchema(objectMapper, canonicalizer, conditionDsl),
            conditionDsl,
            objectMapper
        );

    @Test
    void hiddenOverridesReadAndWriteWhileReadClearsRequired() throws Exception {
        JsonNode policy = objectMapper.readTree("""
            {"schemaVersion":1,"default":{"mode":"write","required":true},
             "rules":[
               {"ruleKey":"guest_read","roles":["guest"],"mode":"read","required":false},
               {"ruleKey":"blocked","roles":["guest"],"mode":"hidden","required":false,
                "when":{"schemaVersion":1,"expression":{"kind":"predicate","source":"field",
                  "fieldId":"00000000-0000-0000-0000-000000000001","fieldKey":"risk",
                  "operator":"eq","value":"blocked"}}}
             ]}
            """);
        var read = evaluator.evaluate(policy, context("guest", Map.of()));
        assertEquals("read", read.mode());
        assertFalse(read.required());
        var hidden = evaluator.evaluate(
            policy,
            context("guest", Map.of("risk", objectMapper.getNodeFactory().textNode("blocked")))
        );
        assertEquals("hidden", hidden.mode());
        assertFalse(hidden.required());
        assertTrue(hidden.matchedRuleKeys().contains("blocked"));
    }

    @Test
    void requiredAppliesOnlyToWritableFieldsAndRoleCeilingNeverExpands() throws Exception {
        JsonNode policy = objectMapper.readTree("""
            {"schemaVersion":1,"default":{"mode":"write","required":false},
             "rules":[
               {"ruleKey":"member_required","roles":["member"],"mode":"write","required":true},
               {"ruleKey":"guest_write","roles":["guest"],"mode":"write","required":true}
             ]}
            """);
        var member = evaluator.evaluate(policy, context("member", Map.of()));
        assertEquals("write", member.mode());
        assertTrue(member.required());
        var guest = evaluator.evaluate(policy, context("guest", Map.of()));
        assertEquals("read", guest.mode());
        assertFalse(guest.required());
        assertEquals("role_ceiling", guest.reasonCode());
        assertEquals("hidden", evaluator.evaluate(policy, context("enterprise_admin", Map.of())).mode());
    }

    @Test
    void resourceStatesFailClosedAndDecisionChainIsDeterministic() throws Exception {
        JsonNode policy = objectMapper.readTree("""
            {"schemaVersion":1,"default":{"mode":"write","required":false},"rules":[]}
            """);
        EvaluationContext disabled = new EvaluationContext(
            "owner", "disabled", "active", "active", "detail", true, Map.of()
        );
        var first = evaluator.evaluate(policy, disabled);
        var second = evaluator.evaluate(policy, disabled);
        assertEquals(first, second);
        assertEquals("read", first.mode());
        assertEquals("space_disabled", first.reasonCode());
        assertEquals(
            "hidden",
            evaluator.evaluate(
                policy,
                new EvaluationContext(
                    "owner", "active", "active", "retired", "detail", true, Map.of()
                )
            ).mode()
        );
    }

    @Test
    void evaluatesOneHundredTwentyFieldsDeterministicallyUnderConcurrentLoad() throws Exception {
        JsonNode policy = objectMapper.readTree("""
            {"schemaVersion":1,"default":{"mode":"write","required":false},
             "rules":[
               {"ruleKey":"guest_read","roles":["guest"],"mode":"read","required":false},
               {"ruleKey":"member_required","roles":["member"],"mode":"write","required":true}
             ]}
            """);
        EvaluationContext context = context("member", Map.of());
        var expected = evaluator.evaluate(policy, context);

        assertTimeout(Duration.ofSeconds(2), () -> {
            try (var executor = Executors.newFixedThreadPool(8)) {
                var futures = new ArrayList<java.util.concurrent.Future<?>>();
                for (int index = 0; index < 120; index++) {
                    futures.add(executor.submit(() -> assertEquals(
                        expected,
                        evaluator.evaluate(policy, context)
                    )));
                }
                for (var future : futures) {
                    future.get();
                }
            }
        });
        List<String> sources = expected.explanation().stream()
            .map(WorkItemFieldAccessPolicyEvaluator.DecisionStep::source)
            .toList();
        assertEquals(List.of("membership", "resource_state", "member_required", "effective"), sources);
    }

    private EvaluationContext context(String role, Map<String, JsonNode> values) {
        return new EvaluationContext(
            role, "active", "active", "active", "create", true, values
        );
    }
}
