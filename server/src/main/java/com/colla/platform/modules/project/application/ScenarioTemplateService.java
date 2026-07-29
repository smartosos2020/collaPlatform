package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.ScenarioTemplateModels.MAX_COMPONENTS;
import static com.colla.platform.modules.project.domain.ScenarioTemplateModels.MAX_DEPENDENCIES;
import static com.colla.platform.modules.project.domain.ScenarioTemplateModels.MAX_TEMPLATES;
import static com.colla.platform.modules.project.domain.ScenarioTemplateModels.SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioComponent;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioFoundation;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioInstallCommand;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioInstallResult;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioInstallStep;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioManifest;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioTemplate;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioUpgradeConflict;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioValidationDiagnostic;
import com.colla.platform.modules.project.domain.ScenarioTemplateModels.ScenarioValidationResult;
import com.colla.platform.modules.project.infrastructure.ScenarioTemplateRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScenarioTemplateService {
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_.-]{1,95}");
    private static final Set<String> KINDS = Set.of(
        "work_item_type", "relation", "saved_view", "board",
        "project_plan", "workflow", "calendar", "automation", "notification",
        "risk_policy", "metric", "dashboard"
    );
    private static final List<String> PROHIBITED = List.of(
        "private_table_access", "arbitrary_sql", "script", "implicit_membership",
        "permission_snapshot", "enterprise_content_bypass", "legacy_write_cutover"
    );

    private final ScenarioTemplateCatalog catalog;
    private final ScenarioTemplateRepository repository;
    private final WorkItemRelationAccessDecisionService access;
    private final WorkItemTypeConfigurationService typeConfigurationService;
    private final ObjectMapper json;

    public ScenarioTemplateService(
        ScenarioTemplateCatalog catalog,
        ScenarioTemplateRepository repository,
        WorkItemRelationAccessDecisionService access,
        WorkItemTypeConfigurationService typeConfigurationService,
        ObjectMapper json
    ) {
        this.catalog = catalog;
        this.repository = repository;
        this.access = access;
        this.typeConfigurationService = typeConfigurationService;
        this.json = json;
    }

    @Transactional
    public ScenarioFoundation foundation(CurrentUser user, UUID spaceId) {
        access.requireVisible(user, spaceId);
        importCatalog();
        List<ScenarioTemplate> templates = repository.list(MAX_TEMPLATES + 1);
        boolean truncated = templates.size() > MAX_TEMPLATES;
        return new ScenarioFoundation(
            SCHEMA_VERSION,
            truncated ? templates.subList(0, MAX_TEMPLATES) : templates,
            truncated,
            KINDS.stream().sorted().toList(),
            PROHIBITED
        );
    }

    @Transactional
    public ScenarioValidationResult validate(
        CurrentUser user, UUID spaceId, String scenarioKey
    ) {
        access.requireVisible(user, spaceId);
        importCatalog();
        ScenarioTemplate template = repository.find(normalizeKey(scenarioKey))
            .orElseThrow(() -> failure(
                "SCENARIO_TEMPLATE_NOT_FOUND",
                "Scenario template is not available"
            ));
        return validate(template.currentVersion().manifest());
    }

    public ScenarioValidationResult validate(ScenarioManifest manifest) {
        List<ScenarioValidationDiagnostic> diagnostics = new ArrayList<>();
        if (manifest == null || manifest.schemaVersion() != SCHEMA_VERSION
            || !KEY.matcher(normalizeKey(manifest == null ? "" : manifest.scenarioKey())).matches()
            || manifest.components() == null
            || manifest.components().isEmpty()
            || manifest.components().size() > MAX_COMPONENTS) {
            diagnostics.add(new ScenarioValidationDiagnostic(
                "SCENARIO_MANIFEST_INVALID", "", "Manifest shape or component bound is invalid"
            ));
            return new ScenarioValidationResult(false, hash(manifest), List.of(), diagnostics);
        }
        Map<String, ScenarioComponent> components = new HashMap<>();
        for (ScenarioComponent component : manifest.components()) {
            if (component == null
                || !KEY.matcher(normalizeKey(component.componentKey())).matches()
                || !KINDS.contains(component.kind())
                || component.ownerContract() == null
                || component.ownerContract().isBlank()
                || component.dependencies() == null
                || component.dependencies().size() > MAX_DEPENDENCIES
                || components.putIfAbsent(component.componentKey(), component) != null) {
                diagnostics.add(new ScenarioValidationDiagnostic(
                    "SCENARIO_COMPONENT_INVALID",
                    component == null ? "" : component.componentKey(),
                    "Component key, kind, owner, dependency bound or uniqueness is invalid"
                ));
            }
        }
        if (!diagnostics.isEmpty()) {
            return new ScenarioValidationResult(false, hash(manifest), List.of(), diagnostics);
        }
        for (ScenarioComponent component : components.values()) {
            for (String dependency : component.dependencies()) {
                if (!components.containsKey(dependency)) {
                    diagnostics.add(new ScenarioValidationDiagnostic(
                        "SCENARIO_DEPENDENCY_MISSING",
                        component.componentKey(),
                        "Dependency is not present: " + dependency
                    ));
                }
            }
        }
        List<String> order = topologicalOrder(components, diagnostics);
        return new ScenarioValidationResult(
            diagnostics.isEmpty(), hash(manifest),
            diagnostics.isEmpty() ? order : List.of(), diagnostics
        );
    }

    @Transactional
    public ScenarioInstallResult dryRun(
        CurrentUser user, UUID spaceId, String scenarioKey, ScenarioInstallCommand command
    ) {
        return execute(user, spaceId, scenarioKey, "dry_run", command);
    }

    @Transactional
    public ScenarioInstallResult install(
        CurrentUser user, UUID spaceId, String scenarioKey, ScenarioInstallCommand command
    ) {
        return execute(user, spaceId, scenarioKey, "install", command);
    }

    @Transactional
    public ScenarioInstallResult retry(
        CurrentUser user, UUID spaceId, String scenarioKey, ScenarioInstallCommand command
    ) {
        return execute(user, spaceId, scenarioKey, "retry", command);
    }

    @Transactional
    public ScenarioInstallResult upgrade(
        CurrentUser user, UUID spaceId, String scenarioKey, ScenarioInstallCommand command
    ) {
        return execute(user, spaceId, scenarioKey, "upgrade", command);
    }

    @Transactional
    public ScenarioInstallResult detach(
        CurrentUser user, UUID spaceId, String scenarioKey, ScenarioInstallCommand command
    ) {
        return execute(user, spaceId, scenarioKey, "detach", command);
    }

    @Transactional(readOnly = true)
    public ScenarioInstallResult installation(
        CurrentUser user, UUID spaceId, String scenarioKey
    ) {
        access.requireManager(user, spaceId);
        return repository.findInstallation(
            user.workspaceId(), spaceId, normalizeKey(scenarioKey)
        ).orElse(null);
    }

    private ScenarioInstallResult execute(
        CurrentUser user,
        UUID spaceId,
        String scenarioKey,
        String operation,
        ScenarioInstallCommand input
    ) {
        access.requireManager(user, spaceId);
        importCatalog();
        ScenarioTemplate template = repository.find(normalizeKey(scenarioKey))
            .orElseThrow(() -> failure(
                "SCENARIO_TEMPLATE_NOT_FOUND", "Scenario template is not available"
            ));
        ScenarioValidationResult validation = validate(template.currentVersion().manifest());
        if (!validation.valid()) {
            throw failure("SCENARIO_CATALOG_INVALID", "Scenario manifest is invalid");
        }
        String requestId = requiredRequestId(input == null ? null : input.requestId());
        String localHash = normalizeHash(
            input == null ? null : input.localManifestHash(),
            template.currentVersion().manifestHash()
        );
        Map<String, String> resolutions = input == null
            || input.conflictResolutions() == null
            ? Map.of() : Map.copyOf(input.conflictResolutions());
        String requestHash = hash(Map.of(
            "scenarioKey", template.scenarioKey(),
            "operation", operation,
            "versionId", template.currentVersion().id().toString(),
            "localManifestHash", localHash,
            "conflictResolutions", resolutions
        ));
        Optional<ScenarioInstallResult> replay = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), operation, requestId, requestHash
        );
        if (replay.isPresent()) return replay.get();
        if ("detach".equals(operation)) {
            ScenarioInstallStep detachStep = new ScenarioInstallStep(
                null, "scenario.detach", "governance", "ScenarioTemplateService",
                "detach_reference", "completed",
                template.currentVersion().catalogVersion(), template.id().toString(),
                template.currentVersion().id().toString(), ""
            );
            return repository.recordRun(
                user.workspaceId(), spaceId, user.id(), template, operation,
                requestId, requestHash, localHash, List.of(detachStep), List.of()
            );
        }
        List<ScenarioUpgradeConflict> conflicts = upgradeConflicts(
            template.currentVersion().manifestHash(), localHash, resolutions
        );
        boolean blocked = "upgrade".equals(operation)
            && conflicts.stream().anyMatch(value -> !value.resolved());
        Map<String, ScenarioComponent> components = template.currentVersion().manifest()
            .components().stream().collect(java.util.stream.Collectors.toMap(
                ScenarioComponent::componentKey, value -> value
            ));
        Map<String, WorkItemTypeConfigurationModels.ConfiguredType> existingTypes =
            ("dry_run".equals(operation) || blocked)
                ? new HashMap<>()
                : typeConfigurationService.configuration(user, spaceId, null).items()
                    .stream().collect(java.util.stream.Collectors.toMap(
                        value -> value.definition().typeKey(), value -> value
                    ));
        List<ScenarioInstallStep> steps = new ArrayList<>();
        int index = 0;
        for (String componentKey : validation.installationOrder()) {
            ScenarioComponent component = components.get(componentKey);
            String stepOperation = "work_item_type".equals(component.kind())
                ? "configure_type" : "verify_owner_reference";
            String stepStatus = "dry_run".equals(operation)
                ? "planned" : (blocked ? "skipped" : "completed");
            String targetIdentity = "public-owner-reference:" + component.ownerContract();
            String targetVersion = template.currentVersion().catalogVersion();
            if (!"dry_run".equals(operation) && !blocked
                && "work_item_type".equals(component.kind())) {
                String typeKey = scenarioTypeKey(component);
                var configured = existingTypes.get(typeKey);
                if (configured == null) {
                    configured = typeConfigurationService.create(
                        user, spaceId, typeKey, component.description(), "appstore",
                        "Installed from scenario " + template.scenarioKey(),
                        1000 + index * 10,
                        "scenario-" + hash(Map.of(
                            "requestId", requestId, "componentKey", componentKey
                        )).substring(0, 40)
                    );
                    existingTypes.put(typeKey, configured);
                }
                targetIdentity = configured.definition().id().toString();
                targetVersion = Long.toString(configured.definition().aggregateVersion());
            }
            steps.add(new ScenarioInstallStep(
                null, component.componentKey(), component.kind(),
                component.ownerContract(), stepOperation, stepStatus,
                template.currentVersion().id().toString(), targetIdentity,
                targetVersion, blocked ? "SCENARIO_UPGRADE_CONFLICT" : ""
            ));
            index++;
        }
        String resultingLocalHash = conflicts.stream().anyMatch(value ->
            value.resolved() && "local".equals(value.resolution())
        ) ? localHash : template.currentVersion().manifestHash();
        return repository.recordRun(
            user.workspaceId(), spaceId, user.id(), template, operation,
            requestId, requestHash, resultingLocalHash, steps, conflicts
        );
    }

    private List<ScenarioUpgradeConflict> upgradeConflicts(
        String upstreamHash,
        String localHash,
        Map<String, String> resolutions
    ) {
        if (upstreamHash.equals(localHash)) return List.of();
        String resolution = resolutions.getOrDefault("local_manifest", "");
        boolean resolved = Set.of("local", "upstream").contains(resolution);
        return List.of(new ScenarioUpgradeConflict(
            "local_manifest", "LOCAL_MANIFEST_DIVERGED",
            upstreamHash, upstreamHash, localHash, resolved, resolution
        ));
    }

    private String scenarioTypeKey(ScenarioComponent component) {
        String normalized = component.configurationTemplateKey()
            .trim()
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_");
        if (!normalized.matches("^[a-z][a-z0-9_]{0,63}$")) {
            throw failure(
                "SCENARIO_COMPONENT_TYPE_KEY_INVALID",
                "Scenario component type key is not compatible with the owner contract"
            );
        }
        return normalized;
    }

    private String requiredRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw failure("SCENARIO_REQUEST_ID_INVALID", "Request ID is required and bounded");
        }
        return normalized;
    }

    private String normalizeHash(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        if (!normalized.matches("^[0-9a-f]{64}$")) {
            throw failure("SCENARIO_MANIFEST_HASH_INVALID", "Manifest hash is invalid");
        }
        return normalized;
    }

    private void importCatalog() {
        for (var template : catalog.templates()) {
            ScenarioValidationResult validation = validate(template.manifest());
            if (!validation.valid()) {
                throw failure(
                    "SCENARIO_CATALOG_INVALID",
                    "Built-in scenario catalog failed validation"
                );
            }
            UUID templateId = stableUuid("scenario-template:" + template.scenarioKey());
            UUID versionId = stableUuid(
                "scenario-template-version:" + ScenarioTemplateCatalog.CATALOG_VERSION
                    + ":" + template.scenarioKey() + ":" + validation.manifestHash()
            );
            repository.importTemplate(
                templateId, versionId, template.scenarioKey(), template.name(),
                template.description(), ScenarioTemplateCatalog.CATALOG_VERSION,
                validation.manifestHash(), template.manifest()
            );
        }
    }

    private List<String> topologicalOrder(
        Map<String, ScenarioComponent> components,
        List<ScenarioValidationDiagnostic> diagnostics
    ) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        components.keySet().forEach(key -> indegree.put(key, 0));
        for (ScenarioComponent component : components.values()) {
            for (String dependency : component.dependencies()) {
                if (!components.containsKey(dependency)) continue;
                indegree.compute(component.componentKey(), (key, value) -> value + 1);
                dependents.computeIfAbsent(dependency, key -> new ArrayList<>())
                    .add(component.componentKey());
            }
        }
        ArrayDeque<String> ready = new ArrayDeque<>(
            indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey).sorted().toList()
        );
        List<String> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            String key = ready.removeFirst();
            result.add(key);
            dependents.getOrDefault(key, List.of()).stream().sorted().forEach(value -> {
                int next = indegree.compute(value, (ignored, current) -> current - 1);
                if (next == 0) ready.addLast(value);
            });
        }
        if (result.size() != components.size()) {
            Set<String> resolved = new HashSet<>(result);
            components.keySet().stream().filter(key -> !resolved.contains(key)).sorted()
                .forEach(key -> diagnostics.add(new ScenarioValidationDiagnostic(
                    "SCENARIO_DEPENDENCY_CYCLE", key, "Component dependency graph contains a cycle"
                )));
        }
        return result;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String hash(Object value) {
        try {
            byte[] input = json.writeValueAsBytes(value);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Scenario manifest hash is unavailable", exception);
        }
    }
}
