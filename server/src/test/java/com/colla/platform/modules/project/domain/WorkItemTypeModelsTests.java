package com.colla.platform.modules.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.application.WorkItemTypeConfigCanonicalizer;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeStatus;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeVersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkItemTypeModelsTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkItemTypeConfigCanonicalizer canonicalizer = new WorkItemTypeConfigCanonicalizer(objectMapper);

    @Test
    void validatesPermanentTypeKeyAndDisplayFields() {
        assertEquals("project_task", WorkItemTypeModels.normalizeTypeKey(" Project_Task "));
        assertEquals("Task", WorkItemTypeModels.normalizeName(" Task "));
        assertEquals("", WorkItemTypeModels.normalizeIcon(null));
        assertEquals("Description", WorkItemTypeModels.normalizeDescription(" Description "));
        assertEquals(3, WorkItemTypeModels.normalizeSortOrder(3));

        assertCode("INVALID_TYPE_KEY", () -> WorkItemTypeModels.normalizeTypeKey("2-task"));
        assertCode("INVALID_TYPE_KEY", () -> WorkItemTypeModels.normalizeTypeKey("project-task"));
        assertCode("INVALID_NAME", () -> WorkItemTypeModels.normalizeName(" "));
        assertCode("INVALID_SORT_ORDER", () -> WorkItemTypeModels.normalizeSortOrder(-1));
    }

    @Test
    void enforcesTypeAndVersionLifecycleContracts() {
        assertTrue(WorkItemTypeStatus.active.canTransitionTo(WorkItemTypeStatus.disabled));
        assertTrue(WorkItemTypeStatus.active.canTransitionTo(WorkItemTypeStatus.retired));
        assertTrue(WorkItemTypeStatus.disabled.canTransitionTo(WorkItemTypeStatus.active));
        assertTrue(WorkItemTypeStatus.disabled.canTransitionTo(WorkItemTypeStatus.retired));
        assertFalse(WorkItemTypeStatus.retired.canTransitionTo(WorkItemTypeStatus.active));
        assertTrue(WorkItemTypeVersionStatus.published.immutable());
        assertTrue(WorkItemTypeVersionStatus.superseded.immutable());
        assertFalse(WorkItemTypeVersionStatus.draft.immutable());
    }

    @Test
    void canonicalSkeletonHasStableHashAndNoFutureStageConfiguration() throws Exception {
        var skeleton = canonicalizer.initialPublishedSkeleton("task", "Task", "check", "Track work");
        assertEquals(64, skeleton.hash().length());
        assertEquals(1, skeleton.config().path("schemaVersion").asInt());
        assertEquals("task", skeleton.config().path("typeKey").asText());
        assertEquals("Task", skeleton.config().path("display").path("name").asText());
        assertFalse(skeleton.config().has("fields"));
        assertFalse(skeleton.config().has("layout"));
        assertFalse(skeleton.config().has("workflow"));
        assertFalse(skeleton.config().has("roles"));

        var first = objectMapper.readTree("{\"typeKey\":\"task\",\"display\":{\"name\":\"Task\",\"icon\":\"check\"},\"schemaVersion\":1}");
        var second = objectMapper.readTree("{\"schemaVersion\":1,\"display\":{\"icon\":\"check\",\"name\":\"Task\"},\"typeKey\":\"task\"}");
        assertEquals(canonicalizer.hash(first), canonicalizer.hash(second));
    }

    private void assertCode(String code, Runnable action) {
        WorkItemTypeException exception = assertThrows(WorkItemTypeException.class, action::run);
        assertEquals(code, exception.code());
    }
}
