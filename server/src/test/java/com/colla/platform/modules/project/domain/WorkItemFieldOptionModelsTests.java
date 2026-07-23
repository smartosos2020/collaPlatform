package com.colla.platform.modules.project.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import org.junit.jupiter.api.Test;

class WorkItemFieldOptionModelsTests {
    @Test
    void normalizesStableIdentityDisplayAndState() {
        ConfigureFieldOption normalized = WorkItemFieldOptionModels.normalize(
            new ConfigureFieldOption(" HIGH ", " High ", "#ef4444", 10, " ACTIVE ")
        );

        assertEquals("high", normalized.optionKey());
        assertEquals("High", normalized.name());
        assertEquals("#EF4444", normalized.color());
        assertEquals("active", normalized.status());
    }

    @Test
    void rejectsInvalidKeyNameColorOrderAndState() {
        assertInvalid(new ConfigureFieldOption("1bad", "Bad", "#FFFFFF", 0, "active"));
        assertInvalid(new ConfigureFieldOption("good", " ", "#FFFFFF", 0, "active"));
        assertInvalid(new ConfigureFieldOption("good", "Good", "red", 0, "active"));
        assertInvalid(new ConfigureFieldOption("good", "Good", "#FFFFFF", -1, "active"));
        assertInvalid(new ConfigureFieldOption("good", "Good", "#FFFFFF", 0, "retired"));
    }

    private void assertInvalid(ConfigureFieldOption option) {
        WorkItemFieldException failure = assertThrows(
            WorkItemFieldException.class,
            () -> WorkItemFieldOptionModels.normalize(option)
        );
        assertEquals("INVALID_FIELD_OPTION", failure.code());
    }
}
