package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.SNAPSHOT_SCHEMA_VERSION;
import static com.colla.platform.modules.project.domain.WorkItemConfigurationModels.failure;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationDraft;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ConfigurationSnapshot;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.PublishedConfigurationVersion;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.ValidationResult;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.ConfigurationTemplate;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.ConfigurationTemplateVersion;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.TemplateCommandReceipt;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.TemplateInstallation;
import com.colla.platform.modules.project.domain.WorkItemConfigurationTemplateModels.TemplateUpgradePreview;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypePresetCatalog;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository;
import com.colla.platform.modules.project.infrastructure.ConfigurationDraftRepository.UpdateDraft;
import com.colla.platform.modules.project.infrastructure.ConfigurationPublicationRepository;
import com.colla.platform.modules.project.infrastructure.ConfigurationTemplateRepository;
import com.colla.platform.modules.project.infrastructure.ConfigurationTemplateRepository.NewInstallation;
import com.colla.platform.modules.project.infrastructure.ConfigurationTemplateRepository.NewTemplateVersion;
import com.colla.platform.modules.project.infrastructure.ConfigurationTemplateRepository.NewWorkspaceTemplate;
import com.colla.platform.modules.project.infrastructure.ConfigurationTemplateRepository.PlatformTemplateImport;
import com.colla.platform.modules.project.infrastructure.ConfigurationTemplateRepository.TemplateCommandStart;
import com.colla.platform.modules.project.infrastructure.ConfigurationTemplateRepository.TemplateHistory;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemConfigurationTemplateService {
    private static final Set<String> MANAGER_ROLES = Set.of("owner", "admin");
    private static final Pattern TEMPLATE_KEY = Pattern.compile("^[a-z][a-z0-9_-]{1,95}$");

    private final ConfigurationTemplateRepository templateRepository;
    private final ConfigurationPublicationRepository publicationRepository;
    private final ConfigurationDraftRepository draftRepository;
    private final ProjectSpaceRepository spaceRepository;
    private final WorkItemTypeRepository typeRepository;
    private final WorkItemTypePresetCatalog presetCatalog;
    private final WorkItemConfigurationSnapshotCanonicalizer canonicalizer;
    private final WorkItemConfigurationValidator validator;
    private final WorkItemConfigurationThreeWayMerge mergeEngine;
    private final WorkItemTypeConfigCanonicalizer requestCanonicalizer;
    private final AuditLog auditLog;
    private final TransactionalOutbox outbox;
    private final ObjectMapper objectMapper;

    public WorkItemConfigurationTemplateService(
        ConfigurationTemplateRepository templateRepository,
        ConfigurationPublicationRepository publicationRepository,
        ConfigurationDraftRepository draftRepository,
        ProjectSpaceRepository spaceRepository,
        WorkItemTypeRepository typeRepository,
        WorkItemTypePresetCatalog presetCatalog,
        WorkItemConfigurationSnapshotCanonicalizer canonicalizer,
        WorkItemConfigurationValidator validator,
        WorkItemConfigurationThreeWayMerge mergeEngine,
        WorkItemTypeConfigCanonicalizer requestCanonicalizer,
        AuditLog auditLog,
        TransactionalOutbox outbox,
        ObjectMapper objectMapper
    ) {
        this.templateRepository = templateRepository;
        this.publicationRepository = publicationRepository;
        this.draftRepository = draftRepository;
        this.spaceRepository = spaceRepository;
        this.typeRepository = typeRepository;
        this.presetCatalog = presetCatalog;
        this.canonicalizer = canonicalizer;
        this.validator = validator;
        this.mergeEngine = mergeEngine;
        this.requestCanonicalizer = requestCanonicalizer;
        this.auditLog = auditLog;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<TemplateSummary> catalog(CurrentUser user, UUID spaceId) {
        requireManager(user, spaceId, null, false);
        importPlatformTemplates();
        return templateRepository.listVisible(user.workspaceId()).stream()
            .map(template -> summary(user.workspaceId(), template))
            .toList();
    }

    @Transactional
    public TemplateSummary createWorkspaceTemplate(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID sourceVersionId,
        String templateKey,
        String name,
        String description
    ) {
        requireManager(user, spaceId, typeId, true);
        String normalizedKey = templateKey == null ? "" : templateKey.trim().toLowerCase();
        if (!TEMPLATE_KEY.matcher(normalizedKey).matches()) {
            throw failure("INVALID_TEMPLATE_KEY", "Template key must use lowercase letters, numbers, '-' or '_'");
        }
        PublishedConfigurationVersion source = requireComplete(
            publicationRepository.findVersion(user.workspaceId(), spaceId, typeId, sourceVersionId)
                .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Published configuration is not available"))
        );
        UUID templateId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        try {
            templateRepository.insertWorkspaceTemplate(
                new NewWorkspaceTemplate(
                    templateId,
                    user.workspaceId(),
                    normalizedKey,
                    required(name, "Template name"),
                    description == null ? "" : description.trim(),
                    user.id()
                ),
                new NewTemplateVersion(
                    versionId,
                    templateId,
                    user.workspaceId(),
                    1,
                    source.snapshotSchemaVersion(),
                    source.configHash(),
                    source.snapshot(),
                    spaceId,
                    typeId,
                    source.id(),
                    null,
                    user.id()
                )
            );
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw failure("TEMPLATE_KEY_CONFLICT", "Template key already exists in this workspace", exception);
        }
        ConfigurationTemplate created = templateRepository.findVisible(user.workspaceId(), templateId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration template is not available"));
        record(user, spaceId, typeId, "work_item_configuration.template_created", created.id(), Map.of(
            "templateKey", created.templateKey(),
            "sourceVersionId", source.id().toString(),
            "configHash", source.configHash()
        ));
        return summary(user.workspaceId(), created);
    }

    @Transactional
    public TemplateSummary addWorkspaceTemplateVersion(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID templateId,
        UUID sourceVersionId
    ) {
        requireManager(user, spaceId, typeId, true);
        ConfigurationTemplate template = templateRepository.lockVisible(user.workspaceId(), templateId)
            .filter(value -> !value.platform() && user.workspaceId().equals(value.ownerWorkspaceId()))
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Workspace template is not available"));
        if (!"active".equals(template.status())) {
            throw failure("TEMPLATE_WITHDRAWN", "Withdrawn templates cannot receive new versions");
        }
        PublishedConfigurationVersion source = requireComplete(
            publicationRepository.findVersion(user.workspaceId(), spaceId, typeId, sourceVersionId)
                .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Published configuration is not available"))
        );
        ConfigurationTemplateVersion current = requireTemplateVersion(
            user.workspaceId(), template.id(), template.currentVersionId()
        );
        if (current.configHash().equals(source.configHash())) {
            return summary(user.workspaceId(), template);
        }
        UUID versionId = UUID.randomUUID();
        templateRepository.insertVersion(new NewTemplateVersion(
            versionId,
            template.id(),
            user.workspaceId(),
            current.versionNumber() + 1,
            source.snapshotSchemaVersion(),
            source.configHash(),
            source.snapshot(),
            spaceId,
            typeId,
            source.id(),
            null,
            user.id()
        ));
        if (templateRepository.switchCurrentVersion(
            template.id(), current.id(), versionId, user.id()
        ) != 1) {
            throw failure("TEMPLATE_VERSION_CONFLICT", "Template current version changed");
        }
        return summary(
            user.workspaceId(),
            templateRepository.findVisible(user.workspaceId(), template.id()).orElseThrow()
        );
    }

    @Transactional
    public TemplateSummary withdraw(CurrentUser user, UUID spaceId, UUID templateId) {
        requireManager(user, spaceId, null, true);
        ConfigurationTemplate template = templateRepository.findVisible(user.workspaceId(), templateId)
            .filter(value -> !value.platform() && user.workspaceId().equals(value.ownerWorkspaceId()))
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Workspace template is not available"));
        if ("active".equals(template.status())
            && templateRepository.withdraw(user.workspaceId(), templateId, user.id()) != 1) {
            throw failure("TEMPLATE_VERSION_CONFLICT", "Template changed");
        }
        return summary(
            user.workspaceId(),
            templateRepository.findVisible(user.workspaceId(), templateId).orElseThrow()
        );
    }

    @Transactional(readOnly = true)
    public InstallationDetail installation(CurrentUser user, UUID spaceId, UUID typeId) {
        requireManager(user, spaceId, typeId, false);
        return templateRepository.findInstallation(user.workspaceId(), spaceId, typeId)
            .map(this::installationView)
            .orElse(null);
    }

    @Transactional
    public TemplateCommandResult install(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID templateId,
        UUID templateVersionId,
        long expectedDraftVersion,
        String requestId
    ) {
        requireManager(user, spaceId, typeId, true);
        importPlatformTemplates();
        ConfigurationTemplate template = requireActiveTemplate(user.workspaceId(), templateId);
        ConfigurationTemplateVersion version = requireTemplateVersion(
            user.workspaceId(),
            template.id(),
            templateVersionId == null ? template.currentVersionId() : templateVersionId
        );
        Command command = begin(user, spaceId, typeId, "install", Map.of(
            "templateId", template.id().toString(),
            "templateVersionId", version.id().toString(),
            "expectedDraftVersion", expectedDraftVersion
        ), requestId);
        if (command.replay()) {
            return replay(command.receipt(), TemplateCommandResult.class);
        }
        ConfigurationDraft draft = lockDraft(user, spaceId, typeId, expectedDraftVersion);
        WorkItemTypeDefinition target = requireType(user, spaceId, typeId);
        ConfigurationSnapshot rebound = canonicalizer.canonicalize(
            rebind(version.snapshot(), target)
        );
        ValidationResult validation = validator.validate(rebound.payload());
        updateDraft(draft, rebound, validation, user.id());
        JsonNode lineage = objectMapper.valueToTree(Map.of(
            "templateId", template.id().toString(),
            "templateVersionId", version.id().toString(),
            "templateHash", version.configHash(),
            "installedHash", rebound.configHash()
        ));
        UUID installationId = templateRepository.findInstallation(user.workspaceId(), spaceId, typeId)
            .map(TemplateInstallation::id)
            .orElseGet(UUID::randomUUID);
        templateRepository.install(new NewInstallation(
            installationId,
            user.workspaceId(),
            spaceId,
            typeId,
            template.id(),
            version.id(),
            lineage,
            user.id()
        ));
        TemplateInstallation installed = templateRepository.findInstallation(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("TEMPLATE_INSTALLATION_CONFLICT", "Template installation is not available"));
        templateRepository.appendHistory(new TemplateHistory(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            typeId,
            installed.id(),
            "install",
            version.id(),
            version.id(),
            rebound.configHash(),
            lineage,
            user.id()
        ));
        TemplateCommandResult result = new TemplateCommandResult(
            installationView(installed),
            draftResult(draft, rebound, validation),
            Map.of("conflicts", 0),
            false
        );
        complete(command, result);
        record(user, spaceId, typeId, "work_item_configuration.template_installed", installed.id(), Map.of(
            "templateId", template.id().toString(),
            "templateVersionId", version.id().toString(),
            "configHash", rebound.configHash()
        ));
        return result;
    }

    @Transactional(readOnly = true)
    public TemplateUpgradePreview previewUpgrade(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID targetVersionId,
        Map<String, String> resolutions
    ) {
        requireManager(user, spaceId, typeId, false);
        return preview(user, spaceId, typeId, targetVersionId, resolutions, false);
    }

    @Transactional
    public TemplateCommandResult applyUpgrade(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID targetVersionId,
        long expectedDraftVersion,
        long expectedInstallationVersion,
        Map<String, String> resolutions,
        String requestId
    ) {
        requireManager(user, spaceId, typeId, true);
        Command command = begin(user, spaceId, typeId, "upgrade", Map.of(
            "targetVersionId", targetVersionId.toString(),
            "expectedDraftVersion", expectedDraftVersion,
            "expectedInstallationVersion", expectedInstallationVersion,
            "resolutions", resolutions == null ? Map.of() : resolutions
        ), requestId);
        if (command.replay()) {
            return replay(command.receipt(), TemplateCommandResult.class);
        }
        TemplateInstallation installation = templateRepository.lockInstallation(
            user.workspaceId(), spaceId, typeId
        ).filter(TemplateInstallation::attached)
            .orElseThrow(() -> failure("TEMPLATE_NOT_ATTACHED", "No attached configuration template"));
        if (installation.aggregateVersion() != expectedInstallationVersion) {
            throw failure("TEMPLATE_INSTALLATION_VERSION_CONFLICT", "Template installation changed");
        }
        ConfigurationDraft draft = lockDraft(user, spaceId, typeId, expectedDraftVersion);
        TemplateUpgradePreview preview = previewLocked(
            user, spaceId, typeId, installation, draft, targetVersionId, resolutions
        );
        if (!preview.conflicts().isEmpty()) {
            boolean unresolved = preview.conflicts().stream()
                .anyMatch(conflict -> resolutions == null || !resolutions.containsKey(conflict.keyPath()));
            if (unresolved) {
                throw failure("TEMPLATE_MERGE_CONFLICT", "Every template conflict requires an explicit resolution");
            }
        }
        ConfigurationSnapshot merged = canonicalizer.canonicalize(preview.mergedSnapshot());
        ValidationResult validation = validator.validate(merged.payload());
        updateDraft(draft, merged, validation, user.id());
        JsonNode lineage = objectMapper.valueToTree(Map.of(
            "baseVersionId", installation.upstreamVersionId().toString(),
            "upstreamVersionId", targetVersionId.toString(),
            "localHash", preview.localHash(),
            "mergedHash", preview.mergedHash(),
            "conflictCount", preview.conflicts().size()
        ));
        if (templateRepository.upgrade(
            user.workspaceId(),
            spaceId,
            typeId,
            installation.id(),
            installation.upstreamVersionId(),
            targetVersionId,
            lineage,
            user.id()
        ) != 1) {
            throw failure("TEMPLATE_INSTALLATION_VERSION_CONFLICT", "Template installation changed");
        }
        TemplateInstallation upgraded = templateRepository.findInstallation(
            user.workspaceId(), spaceId, typeId
        ).orElseThrow();
        templateRepository.appendHistory(new TemplateHistory(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            typeId,
            installation.id(),
            "upgrade",
            installation.upstreamVersionId(),
            targetVersionId,
            merged.configHash(),
            lineage,
            user.id()
        ));
        TemplateCommandResult result = new TemplateCommandResult(
            installationView(upgraded),
            draftResult(draft, merged, validation),
            preview.summary(),
            false
        );
        complete(command, result);
        record(user, spaceId, typeId, "work_item_configuration.template_upgraded", upgraded.id(), Map.of(
            "fromVersionId", installation.upstreamVersionId().toString(),
            "toVersionId", targetVersionId.toString(),
            "configHash", merged.configHash()
        ));
        return result;
    }

    @Transactional
    public TemplateCommandResult detach(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        long expectedInstallationVersion,
        String requestId
    ) {
        requireManager(user, spaceId, typeId, true);
        Command command = begin(user, spaceId, typeId, "detach", Map.of(
            "expectedInstallationVersion", expectedInstallationVersion
        ), requestId);
        if (command.replay()) {
            return replay(command.receipt(), TemplateCommandResult.class);
        }
        TemplateInstallation installation = templateRepository.lockInstallation(
            user.workspaceId(), spaceId, typeId
        ).orElseThrow(() -> failure("TEMPLATE_NOT_ATTACHED", "No template installation exists"));
        ConfigurationDraft draft = draftRepository.findActive(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        if (installation.aggregateVersion() != expectedInstallationVersion) {
            throw failure("TEMPLATE_INSTALLATION_VERSION_CONFLICT", "Template installation changed");
        }
        if (installation.attached()) {
            JsonNode lineage = objectMapper.valueToTree(Map.of(
                "detachedFromVersionId", installation.upstreamVersionId().toString(),
                "retainedDraftId", draft.id().toString(),
                "retainedDraftHash", draft.configHash()
            ));
            if (templateRepository.detach(
                user.workspaceId(),
                spaceId,
                typeId,
                installation.id(),
                lineage,
                user.id()
            ) != 1) {
                throw failure("TEMPLATE_INSTALLATION_VERSION_CONFLICT", "Template installation changed");
            }
            templateRepository.appendHistory(new TemplateHistory(
                UUID.randomUUID(),
                user.workspaceId(),
                spaceId,
                typeId,
                installation.id(),
                "detach",
                installation.upstreamVersionId(),
                installation.upstreamVersionId(),
                draft.configHash(),
                lineage,
                user.id()
            ));
        }
        TemplateInstallation detached = templateRepository.findInstallation(
            user.workspaceId(), spaceId, typeId
        ).orElseThrow();
        TemplateCommandResult result = new TemplateCommandResult(
            installationView(detached),
            draftResult(draft, new ConfigurationSnapshot(
                draft.snapshotSchemaVersion(), draft.snapshot(), draft.configHash()
            ), ValidationResult.of(draft.diagnostics())),
            Map.of("conflicts", 0),
            false
        );
        complete(command, result);
        record(user, spaceId, typeId, "work_item_configuration.template_detached", detached.id(), Map.of(
            "retainedDraftHash", draft.configHash()
        ));
        return result;
    }

    private TemplateUpgradePreview preview(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        UUID targetVersionId,
        Map<String, String> resolutions,
        boolean lock
    ) {
        TemplateInstallation installation = (lock
            ? templateRepository.lockInstallation(user.workspaceId(), spaceId, typeId)
            : templateRepository.findInstallation(user.workspaceId(), spaceId, typeId))
            .filter(TemplateInstallation::attached)
            .orElseThrow(() -> failure("TEMPLATE_NOT_ATTACHED", "No attached configuration template"));
        ConfigurationDraft draft = draftRepository.findActive(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        return previewLocked(user, spaceId, typeId, installation, draft, targetVersionId, resolutions);
    }

    private TemplateUpgradePreview previewLocked(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        TemplateInstallation installation,
        ConfigurationDraft draft,
        UUID requestedTargetVersionId,
        Map<String, String> resolutions
    ) {
        ConfigurationTemplate template = requireActiveTemplate(user.workspaceId(), installation.templateId());
        UUID targetVersionId = requestedTargetVersionId == null
            ? template.currentVersionId()
            : requestedTargetVersionId;
        ConfigurationTemplateVersion base = requireTemplateVersion(
            user.workspaceId(), template.id(), installation.upstreamVersionId()
        );
        ConfigurationTemplateVersion upstream = requireTemplateVersion(
            user.workspaceId(), template.id(), targetVersionId
        );
        WorkItemTypeDefinition target = requireType(user, spaceId, typeId);
        ConfigurationSnapshot baseSnapshot = canonicalizer.canonicalize(rebind(base.snapshot(), target));
        ConfigurationSnapshot upstreamSnapshot = canonicalizer.canonicalize(rebind(upstream.snapshot(), target));
        ConfigurationSnapshot localSnapshot = canonicalizer.canonicalize(rebind(draft.snapshot(), target));
        var merged = mergeEngine.merge(
            baseSnapshot.payload(),
            upstreamSnapshot.payload(),
            localSnapshot.payload(),
            resolutions
        );
        return new TemplateUpgradePreview(
            installation.id(),
            template.id(),
            base.id(),
            upstream.id(),
            baseSnapshot.configHash(),
            upstreamSnapshot.configHash(),
            localSnapshot.configHash(),
            merged.configHash(),
            merged.snapshot(),
            merged.conflicts(),
            Map.of("conflicts", merged.conflicts().size()),
            !base.id().equals(upstream.id())
        );
    }

    private void importPlatformTemplates() {
        for (var preset : presetCatalog.developmentPresets()) {
            JsonNode snapshot = platformSnapshot(preset);
            ConfigurationSnapshot canonical = canonicalizer.canonicalize(snapshot);
            UUID templateId = stableUuid("platform-template:" + preset.typeKey());
            UUID versionId = stableUuid(
                "platform-template-version:" + presetCatalog.version() + ":" + preset.typeKey()
            );
            templateRepository.importPlatformTemplate(new PlatformTemplateImport(
                templateId,
                versionId,
                "platform-" + preset.typeKey(),
                preset.name(),
                preset.description(),
                SNAPSHOT_SCHEMA_VERSION,
                canonical.configHash(),
                canonical.payload(),
                presetCatalog.version()
            ));
        }
    }

    private JsonNode platformSnapshot(WorkItemTypePresetCatalog.PresetTemplate preset) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("snapshotSchemaVersion", SNAPSHOT_SCHEMA_VERSION);
        ObjectNode type = root.putObject("typeDefinition");
        type.put("id", stableUuid("platform-type:" + preset.typeKey()).toString());
        type.put("workspaceId", stableUuid("platform-workspace").toString());
        type.put("spaceId", stableUuid("platform-space").toString());
        type.put("typeKey", preset.typeKey());
        type.put("name", preset.name());
        type.put("icon", preset.icon());
        type.put("description", preset.description());
        type.put("sortOrder", preset.sortOrder());
        type.put("status", "active");
        type.put("system", true);
        root.putArray("fields");
        ArrayNode layouts = root.putArray("layouts");
        for (String kind : List.of("create", "detail")) {
            ObjectNode layout = layouts.addObject();
            layout.put("id", stableUuid("platform-layout:" + preset.typeKey() + ":" + kind).toString());
            layout.put("layoutKind", kind);
            layout.put("status", "active");
            layout.putArray("nodes");
            layout.putArray("policies");
        }
        return root;
    }

    private JsonNode rebind(JsonNode source, WorkItemTypeDefinition target) {
        ObjectNode root = source.deepCopy();
        ObjectNode type = (ObjectNode) root.withObject("/typeDefinition");
        type.put("id", target.id().toString());
        type.put("workspaceId", target.workspaceId().toString());
        type.put("spaceId", target.spaceId().toString());
        type.put("typeKey", target.typeKey());
        type.put("name", target.name());
        type.put("icon", target.icon());
        type.put("description", target.description());
        type.put("sortOrder", target.sortOrder());
        type.put("status", target.status());
        type.put("system", target.system());
        for (JsonNode field : root.withArray("fields")) {
            ((ObjectNode) field).put(
                "id",
                stableUuid("template-field:" + target.id() + ":" + field.path("fieldKey").asText()).toString()
            );
            for (JsonNode option : field.withArray("options")) {
                ((ObjectNode) option).put(
                    "id",
                    stableUuid(
                        "template-option:" + target.id() + ":" + field.path("fieldKey").asText()
                            + ":" + option.path("optionKey").asText()
                    ).toString()
                );
            }
        }
        for (JsonNode layout : root.withArray("layouts")) {
            String kind = layout.path("layoutKind").asText();
            ((ObjectNode) layout).put(
                "id",
                stableUuid("template-layout:" + target.id() + ":" + kind).toString()
            );
            for (JsonNode node : layout.withArray("nodes")) {
                ((ObjectNode) node).put(
                    "id",
                    stableUuid(
                        "template-node:" + target.id() + ":" + kind + ":" + node.path("nodeKey").asText()
                    ).toString()
                );
            }
            for (JsonNode policy : layout.withArray("policies")) {
                ((ObjectNode) policy).put(
                    "id",
                    stableUuid(
                        "template-policy:" + target.id() + ":" + kind + ":"
                            + policy.path("fieldKey").asText() + ":" + policy.path("policyKey").asText()
                    ).toString()
                );
            }
        }
        return root;
    }

    private void updateDraft(
        ConfigurationDraft draft,
        ConfigurationSnapshot snapshot,
        ValidationResult validation,
        UUID actorId
    ) {
        if (draftRepository.update(new UpdateDraft(
            draft.workspaceId(),
            draft.spaceId(),
            draft.typeDefinitionId(),
            draft.id(),
            validation.valid() ? "valid" : "invalid",
            snapshot.schemaVersion(),
            snapshot.configHash(),
            snapshot.payload(),
            objectMapper.valueToTree(validation.diagnostics()),
            actorId,
            draft.aggregateVersion()
        )) != 1) {
            throw failure("DRAFT_VERSION_CONFLICT", "Configuration draft changed");
        }
    }

    private ConfigurationDraft lockDraft(CurrentUser user, UUID spaceId, UUID typeId, long expectedVersion) {
        ConfigurationDraft draft = draftRepository.lockActive(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration draft is not available"));
        if (draft.aggregateVersion() != expectedVersion) {
            throw failure("DRAFT_VERSION_CONFLICT", "Configuration draft changed");
        }
        return draft;
    }

    private WorkItemTypeDefinition requireType(CurrentUser user, UUID spaceId, UUID typeId) {
        return typeRepository.findById(user.workspaceId(), spaceId, typeId)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration type is not available"));
    }

    private ConfigurationTemplate requireActiveTemplate(UUID workspaceId, UUID templateId) {
        return templateRepository.findVisible(workspaceId, templateId)
            .filter(value -> "active".equals(value.status()))
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Active configuration template is not available"));
    }

    private ConfigurationTemplateVersion requireTemplateVersion(
        UUID workspaceId,
        UUID templateId,
        UUID versionId
    ) {
        return templateRepository.findVersion(workspaceId, templateId, versionId)
            .filter(value -> value.snapshotSchemaVersion() >= SNAPSHOT_SCHEMA_VERSION)
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Complete template version is not available"));
    }

    private PublishedConfigurationVersion requireComplete(PublishedConfigurationVersion version) {
        if (!version.completeSnapshot()) {
            throw failure("LEGACY_PARTIAL_SNAPSHOT", "Legacy partial snapshots cannot become templates");
        }
        return version;
    }

    private void requireManager(CurrentUser user, UUID spaceId, UUID typeId, boolean writable) {
        ProjectSpaceSummary space = spaceRepository.findById(user.workspaceId(), spaceId, user.id())
            .orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Configuration templates are not available"));
        if (!space.isMember()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Configuration templates are not available");
        }
        if (!MANAGER_ROLES.contains(space.currentUserRole())) {
            throw failure("FORBIDDEN", "Project space owner or admin role required");
        }
        if (writable && !"active".equals(space.status())) {
            throw failure("SPACE_UNAVAILABLE", "Project space must be active");
        }
        if (typeId != null && typeRepository.findById(user.workspaceId(), spaceId, typeId).isEmpty()) {
            throw failure("NOT_FOUND_OR_HIDDEN", "Configuration templates are not available");
        }
    }

    private TemplateSummary summary(UUID workspaceId, ConfigurationTemplate template) {
        ConfigurationTemplateVersion version = requireTemplateVersion(
            workspaceId, template.id(), template.currentVersionId()
        );
        return new TemplateSummary(
            template.id(),
            template.scope(),
            template.templateKey(),
            template.name(),
            template.description(),
            template.status(),
            new TemplateVersionSummary(
                version.id(),
                version.versionNumber(),
                version.snapshotSchemaVersion(),
                version.configHash(),
                version.sourceCatalogVersion()
            ),
            template.platform() ? "platform_catalog" : "workspace_snapshot",
            "active".equals(template.status())
                ? List.of("install", "create_version", "withdraw")
                : List.of()
        );
    }

    private InstallationDetail installationView(TemplateInstallation value) {
        return new InstallationDetail(
            value.id(),
            value.templateId(),
            value.installedVersionId(),
            value.upstreamVersionId(),
            value.status(),
            value.lastLineageSummary(),
            value.aggregateVersion(),
            value.updatedAt(),
            value.attached() ? List.of("preview_upgrade", "apply_upgrade", "detach") : List.of()
        );
    }

    private DraftResult draftResult(
        ConfigurationDraft previous,
        ConfigurationSnapshot snapshot,
        ValidationResult validation
    ) {
        return new DraftResult(
            previous.id(),
            previous.aggregateVersion() + (snapshot.configHash().equals(previous.configHash()) ? 0 : 1),
            validation.valid() ? "valid" : "invalid",
            snapshot.configHash()
        );
    }

    private Command begin(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String operation,
        Map<String, Object> payload,
        String requestId
    ) {
        String normalized = normalizeRequestId(requestId);
        String requestHash = requestCanonicalizer.hash(objectMapper.valueToTree(Map.of(
            "actorId", user.id().toString(),
            "operation", operation,
            "payload", payload
        )));
        UUID commandId = UUID.randomUUID();
        boolean started = templateRepository.tryStartCommand(new TemplateCommandStart(
            commandId,
            user.workspaceId(),
            spaceId,
            typeId,
            normalized,
            operation,
            requestHash,
            user.id()
        ));
        TemplateCommandReceipt receipt = templateRepository.findCommand(
            user.workspaceId(), spaceId, typeId, operation, normalized
        ).orElseThrow(() -> failure("IDEMPOTENCY_CONFLICT", "Template command receipt is unavailable"));
        if (!receipt.requestHash().equals(requestHash) || !receipt.createdBy().equals(user.id())) {
            throw failure("IDEMPOTENCY_KEY_REUSED", "Request id was already used with different input");
        }
        if (!started && !"completed".equals(receipt.status())) {
            throw failure("IDEMPOTENCY_IN_PROGRESS", "The original template command is in progress");
        }
        return new Command(!started, receipt);
    }

    private <T> T replay(TemplateCommandReceipt receipt, Class<T> type) {
        try {
            T value = objectMapper.treeToValue(receipt.responsePayload(), type);
            if (value instanceof TemplateCommandResult result) {
                return type.cast(new TemplateCommandResult(
                    result.installation(), result.draft(), result.mergeSummary(), true
                ));
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw failure("IDEMPOTENCY_CONFLICT", "Template command receipt cannot be decoded", exception);
        }
    }

    private void complete(Command command, TemplateCommandResult result) {
        templateRepository.completeCommand(command.receipt().id(), objectMapper.valueToTree(result));
    }

    private void record(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String action,
        UUID targetId,
        Map<String, Object> metadata
    ) {
        Map<String, Object> details = new LinkedHashMap<>(metadata);
        details.put("spaceId", spaceId.toString());
        if (typeId != null) details.put("typeDefinitionId", typeId.toString());
        auditLog.log(user, action, "work_item_configuration_template", targetId, details);
        outbox.append(
            user.workspaceId(),
            action,
            "work_item_configuration_template",
            targetId,
            user.id(),
            details,
            "cfg-tpl:" + targetId + ":" + UUID.randomUUID()
        );
    }

    private UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 8 || normalized.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Request id must contain 8 to 120 characters");
        }
        return normalized;
    }

    private String required(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw failure("INVALID_TEMPLATE", field + " is required");
        }
        return normalized;
    }

    public record TemplateVersionSummary(
        UUID id,
        int versionNumber,
        int snapshotSchemaVersion,
        String configHash,
        String sourceCatalogVersion
    ) {
    }

    public record TemplateSummary(
        UUID id,
        String scope,
        String templateKey,
        String name,
        String description,
        String status,
        TemplateVersionSummary currentVersion,
        String sourceKind,
        List<String> availableActions
    ) {
    }

    public record InstallationDetail(
        UUID id,
        UUID templateId,
        UUID installedVersionId,
        UUID upstreamVersionId,
        String status,
        JsonNode lastLineageSummary,
        long aggregateVersion,
        Instant updatedAt,
        List<String> availableActions
    ) {
    }

    public record DraftResult(UUID id, long aggregateVersion, String status, String configHash) {
    }

    public record TemplateCommandResult(
        InstallationDetail installation,
        DraftResult draft,
        Map<String, Integer> mergeSummary,
        boolean replayed
    ) {
    }

    private record Command(boolean replay, TemplateCommandReceipt receipt) {
    }
}
