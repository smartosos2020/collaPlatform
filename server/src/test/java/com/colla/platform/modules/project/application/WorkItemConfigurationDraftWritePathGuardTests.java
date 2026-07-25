package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkItemConfigurationDraftWritePathGuardTests {
    private static final Path APPLICATION = Path.of(
        "src", "main", "java", "com", "colla", "platform", "modules", "project", "application"
    );

    @Test
    void everyConfigurationWriteSurfaceKeepsTheDraftRefreshBoundary() throws IOException {
        assertRefreshCount("WorkItemTypeConfigurationService.java", 5);
        assertRefreshCount("WorkItemFieldConfigurationService.java", 5);
        assertRefreshCount("WorkItemLayoutConfigurationService.java", 1);
        assertRefreshCount("WorkItemTypePresetReconciliationService.java", 1);

        String layout = source("WorkItemLayoutConfigurationService.java");
        assertTrue(layout.indexOf("draftService.refreshAfterMutation") < layout.indexOf("recordChange("));
        assertTrue(layout.contains("return persist("), "all layout commands must converge on persist()");
    }

    private void assertRefreshCount(String fileName, int expected) throws IOException {
        String source = source(fileName);
        int count = source.split("draftService\\.refreshAfterMutation", -1).length - 1;
        assertEquals(expected, count, fileName + " lost a configuration draft refresh boundary");
    }

    private String source(String fileName) throws IOException {
        return Files.readString(APPLICATION.resolve(fileName));
    }
}
