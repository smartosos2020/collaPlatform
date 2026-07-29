package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioManifest;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioInstallResult;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioInstallStep;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioTemplate;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioUpgradeConflict;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioTemplateRepository {
    void importTemplate(
        UUID templateId,
        UUID versionId,
        String scenarioKey,
        String name,
        String description,
        String catalogVersion,
        String manifestHash,
        ScenarioManifest manifest
    );

    List<ScenarioTemplate> list(int limit);

    Optional<ScenarioTemplate> find(String scenarioKey);

    Optional<ScenarioInstallResult> findCommand(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        String operation,
        String requestId,
        String requestHash
    );

    Optional<ScenarioInstallResult> findInstallation(
        UUID workspaceId,
        UUID spaceId,
        String scenarioKey
    );

    ScenarioInstallResult recordRun(
        UUID workspaceId,
        UUID spaceId,
        UUID actorId,
        ScenarioTemplate template,
        String operation,
        String requestId,
        String requestHash,
        String localManifestHash,
        List<ScenarioInstallStep> steps,
        List<ScenarioUpgradeConflict> conflicts
    );
}
