package com.colla.platform.modules.project.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkItemTypePresetCatalogTests {
    private final WorkItemTypePresetCatalog catalog = new WorkItemTypePresetCatalog();

    @Test
    void developmentCatalogHasVersionedStableKeysAndDisplayOnlySemantics() {
        assertThat(catalog.version()).isEqualTo("development-v1");
        assertThat(catalog.developmentPresets()).extracting(WorkItemTypePresetCatalog.PresetTemplate::typeKey)
            .containsExactly("project", "requirement", "task", "bug", "iteration", "release");
        assertThat(catalog.developmentPresets()).extracting(WorkItemTypePresetCatalog.PresetTemplate::sortOrder)
            .containsExactlyElementsOf(List.of(100, 200, 300, 400, 500, 600));
        assertThat(catalog.developmentPresets()).allSatisfy(preset -> {
            assertThat(preset.name()).isNotBlank();
            assertThat(preset.icon()).isNotBlank();
            assertThat(preset.description()).isNotBlank();
        });
    }
}
