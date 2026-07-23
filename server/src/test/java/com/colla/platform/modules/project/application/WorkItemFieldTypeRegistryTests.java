package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkItemFieldTypeRegistryTests {
    private final WorkItemFieldTypeRegistry registry = new WorkItemFieldTypeRegistry(new ObjectMapper());

    @Test
    void publishesTheFrozenInitialTypeAndCapabilityMatrixInStableOrder() {
        assertEquals(List.of(
            "text", "number", "boolean", "single_select", "multi_select", "user",
            "date", "datetime", "url", "attachment", "work_item_reference"
        ), registry.list().stream().map(WorkItemFieldTypeRegistry.FieldTypeDescriptor::key).toList());

        var text = registry.require(" TEXT ");
        assertEquals("string", text.storageKind());
        assertEquals(List.of("eq", "neq", "contains", "is_empty"), text.operators());
        assertTrue(text.filterable());
        assertTrue(text.sortable());
        assertTrue(text.configSchema().path("properties").isObject());
        assertFalse(text.configSchema().path("additionalProperties").asBoolean());
        assertEquals(List.of("length", "regex", "format", "allowed_values"), text.validationRuleKinds());
        assertFalse(text.supportsOptions());

        var select = registry.require("single_select");
        assertTrue(select.supportsOptions());
        assertEquals(1, select.defaultConfig().path("schemaVersion").asInt());

        var attachment = registry.require("attachment");
        assertEquals("file_refs", attachment.storageKind());
        assertFalse(attachment.sortable());
        assertEquals("file_array", attachment.indexCapability());
        assertEquals("file_module", attachment.referencePolicy());
        assertEquals("unavailable_without_snapshot", attachment.invalidReferencePolicy());
        assertEquals("array", attachment.valueSchema().path("type").asText());
        assertTrue(attachment.typeConfigSchema().path("properties").has("maxFiles"));

        var url = registry.require("url");
        assertFalse(url.sortable());
        assertEquals("not_applicable", url.referencePolicy());

        var reference = registry.require("work_item_reference");
        assertTrue(reference.typeConfigSchema().path("properties").has("targetTypeIds"));
        assertEquals("deferred", reference.defaultConfig().at("/typeConfig/relationCapability").asText());
    }

    @Test
    void rejectsUnregisteredFieldTypesWithAStableCode() {
        WorkItemFieldException failure = assertThrows(
            WorkItemFieldException.class,
            () -> registry.require("currency")
        );
        assertEquals("FIELD_TYPE_UNSUPPORTED", failure.code());
    }
}
