package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemFieldValueCodecTests {
    private static final String CONFIG_HASH = "a".repeat(64);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemFieldTypeRegistry registry = new WorkItemFieldTypeRegistry(objectMapper);
    private final WorkItemTypeConfigCanonicalizer canonicalizer =
        new WorkItemTypeConfigCanonicalizer(objectMapper);
    private final WorkItemFieldValueCodec codec =
        new WorkItemFieldValueCodec(registry, canonicalizer, objectMapper);

    @Test
    void canonicalizesAllElevenRegisteredTypesAndSemanticSetsToOneHash() throws Exception {
        UUID first = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("10000000-0000-0000-0000-000000000002");
        RuntimeConfiguration configuration = configuration(List.of(
            field("text_value", "text"),
            field("number_value", "number"),
            field("boolean_value", "boolean"),
            selectField("single_value", "single_select"),
            selectField("multi_value", "multi_select"),
            field("user_value", "user"),
            field("date_value", "date"),
            field("datetime_value", "datetime"),
            field("url_value", "url"),
            field("attachment_value", "attachment"),
            field("reference_value", "work_item_reference"),
            field("optional_null", "text")
        ));
        JsonNode firstInput = objectMapper.readTree("""
            {
              "text_value":"unchanged whitespace ",
              "number_value":1.00,
              "boolean_value":true,
              "single_value":"alpha",
              "multi_value":["beta","alpha","alpha"],
              "user_value":["%s","%s","%s"],
              "date_value":"2026-07-26",
              "datetime_value":"2026-07-26T01:02:03.987Z",
              "url_value":"https://example.com/a/../b",
              "attachment_value":["%s"],
              "reference_value":["%s"],
              "optional_null":null
            }
            """.formatted(second, first, first, first, second));
        JsonNode secondInput = objectMapper.readTree("""
            {
              "reference_value":["%s"],
              "attachment_value":["%s"],
              "url_value":"https://example.com/b",
              "datetime_value":"2026-07-26T01:02:03Z",
              "date_value":"2026-07-26",
              "user_value":["%s","%s"],
              "multi_value":["alpha","beta"],
              "single_value":"alpha",
              "boolean_value":true,
              "number_value":1,
              "text_value":"unchanged whitespace "
            }
            """.formatted(second, first, first, second));

        var firstResult = codec.canonicalize(configuration, firstInput);
        var secondResult = codec.canonicalize(configuration, secondInput);

        assertThat(firstResult.values()).isEqualTo(secondResult.values());
        assertThat(firstResult.hash()).isEqualTo(secondResult.hash());
        assertThat(firstResult.projections()).hasSize(11);
        assertThat(firstResult.values().path("number_value").decimalValue())
            .isEqualByComparingTo("1");
        assertThat(firstResult.values().path("multi_value"))
            .isEqualTo(objectMapper.readTree("[\"alpha\",\"beta\"]"));
        assertThat(firstResult.values().path("user_value"))
            .isEqualTo(objectMapper.readTree("[\"%s\",\"%s\"]".formatted(first, second)));
        assertThat(firstResult.values().path("datetime_value").asText())
            .isEqualTo("2026-07-26T01:02:03Z");
        assertThat(firstResult.values().path("url_value").asText())
            .isEqualTo("https://example.com/b");
    }

    @Test
    void rejectsUnregisteredIntervalAndComputedTypesInsteadOfGuessingStorageSemantics() {
        RuntimeConfiguration interval = configuration(List.of(field("duration", "interval")));
        RuntimeConfiguration computed = configuration(List.of(field("score", "computed")));

        assertUnsupported(interval, "{\"duration\":\"P1D\"}");
        assertUnsupported(computed, "{\"score\":42}");
    }

    private void assertUnsupported(RuntimeConfiguration configuration, String input) {
        assertThatThrownBy(() -> codec.canonicalize(configuration, objectMapper.readTree(input)))
            .isInstanceOf(WorkItemFieldException.class)
            .extracting(exception -> ((WorkItemFieldException) exception).code())
            .isEqualTo("FIELD_TYPE_UNSUPPORTED");
    }

    private RuntimeConfiguration configuration(List<ObjectNode> fields) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        ArrayNode values = snapshot.putArray("fields");
        fields.forEach(values::add);
        return new RuntimeConfiguration(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            1,
            CONFIG_HASH,
            snapshot
        );
    }

    private ObjectNode field(String key, String type) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("fieldKey", key);
        field.put("fieldType", type);
        field.put("status", "active");
        if ("interval".equals(type) || "computed".equals(type)) {
            field.putObject("config");
        } else {
            field.set("config", registry.require(type).defaultConfig().deepCopy());
        }
        field.putArray("options");
        return field;
    }

    private ObjectNode selectField(String key, String type) {
        ObjectNode field = field(key, type);
        addOption(field, "alpha");
        addOption(field, "beta");
        return field;
    }

    private void addOption(ObjectNode field, String key) {
        ObjectNode option = field.withArray("options").addObject();
        option.put("optionKey", key);
        option.put("status", "active");
    }
}
