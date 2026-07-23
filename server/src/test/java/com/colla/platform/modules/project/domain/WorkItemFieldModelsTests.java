package com.colla.platform.modules.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldStatus;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import org.junit.jupiter.api.Test;

class WorkItemFieldModelsTests {
    @Test
    void normalizesImmutableKeyAndDisplayMetadata() {
        assertEquals("release_date", WorkItemFieldModels.normalizeFieldKey(" Release_Date "));
        assertEquals("Release date", WorkItemFieldModels.normalizeName("  Release date  "));
        assertEquals("Used by delivery", WorkItemFieldModels.normalizeDescription(" Used by delivery "));
        assertEquals(10, WorkItemFieldModels.normalizeSortOrder(10));

        WorkItemFieldException invalid = assertThrows(
            WorkItemFieldException.class,
            () -> WorkItemFieldModels.normalizeFieldKey("1-invalid")
        );
        assertEquals("INVALID_FIELD_KEY", invalid.code());
    }

    @Test
    void lifecycleAllowsDisableRestoreAndTerminalRetirementOnly() {
        assertTrue(FieldStatus.active.canTransitionTo(FieldStatus.disabled));
        assertTrue(FieldStatus.disabled.canTransitionTo(FieldStatus.active));
        assertTrue(FieldStatus.active.canTransitionTo(FieldStatus.retired));
        assertFalse(FieldStatus.retired.canTransitionTo(FieldStatus.active));
        assertEquals(FieldStatus.disabled, FieldStatus.parse(" DISABLED "));
    }
}
