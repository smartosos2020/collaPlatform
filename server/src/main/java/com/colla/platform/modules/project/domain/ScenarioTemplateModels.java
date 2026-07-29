package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScenarioTemplateModels {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_TEMPLATES = 20;
    public static final int MAX_COMPONENTS = 64;
    public static final int MAX_DEPENDENCIES = 8;

    private ScenarioTemplateModels() {
    }

    public record ScenarioComponent(
        String componentKey,
        String kind,
        String ownerContract,
        String configurationTemplateKey,
        List<String> dependencies,
        boolean required,
        String description
    ) {
    }

    public record ScenarioManifest(
        int schemaVersion,
        String scenarioKey,
        List<ScenarioComponent> components,
        List<String> capabilities,
        List<String> prohibitedCapabilities
    ) {
    }

    public record ScenarioTemplateVersion(
        UUID id,
        int versionNumber,
        int schemaVersion,
        String manifestHash,
        ScenarioManifest manifest,
        String catalogVersion,
        Instant publishedAt
    ) {
    }

    public record ScenarioTemplate(
        UUID id,
        String scenarioKey,
        String name,
        String description,
        String status,
        ScenarioTemplateVersion currentVersion,
        Instant updatedAt
    ) {
    }

    public record ScenarioValidationDiagnostic(
        String code,
        String componentKey,
        String message
    ) {
    }

    public record ScenarioValidationResult(
        boolean valid,
        String manifestHash,
        List<String> installationOrder,
        List<ScenarioValidationDiagnostic> diagnostics
    ) {
    }

    public record ScenarioFoundation(
        int schemaVersion,
        List<ScenarioTemplate> templates,
        boolean truncated,
        List<String> supportedComponentKinds,
        List<String> prohibitedCapabilities
    ) {
    }

    public record ScenarioInstallCommand(
        String requestId,
        String localManifestHash,
        Map<String, String> conflictResolutions
    ) {
    }

    public record ScenarioInstallStep(
        UUID id,
        String componentKey,
        String kind,
        String ownerContract,
        String operation,
        String status,
        String sourceVersion,
        String targetIdentity,
        String targetVersion,
        String diagnosticCode
    ) {
    }

    public record ScenarioUpgradeConflict(
        String keyPath,
        String reason,
        String baseHash,
        String upstreamHash,
        String localHash,
        boolean resolved,
        String resolution
    ) {
    }

    public record ScenarioInstallResult(
        UUID runId,
        UUID installationId,
        String scenarioKey,
        String operation,
        String status,
        String baseManifestHash,
        String upstreamManifestHash,
        String localManifestHash,
        long aggregateVersion,
        boolean replayed,
        List<ScenarioInstallStep> steps,
        List<ScenarioUpgradeConflict> conflicts,
        Instant completedAt
    ) {
    }
}
