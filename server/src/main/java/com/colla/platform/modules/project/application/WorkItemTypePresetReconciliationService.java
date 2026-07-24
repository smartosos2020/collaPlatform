package com.colla.platform.modules.project.application;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.CreateWorkItemType;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.domain.WorkItemTypePresetCatalog;
import com.colla.platform.modules.project.domain.WorkItemTypePresetCatalog.PresetTemplate;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository.PresetSpaceTarget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemTypePresetReconciliationService {
    private final WorkItemTypePresetCatalog catalog;
    private final WorkItemTypeDefinitionService definitionService;
    private final WorkItemTypeRepository repository;
    private final AuditLog auditRepository;
    private final TransactionalOutbox eventRepository;

    public WorkItemTypePresetReconciliationService(
        WorkItemTypePresetCatalog catalog,
        WorkItemTypeDefinitionService definitionService,
        WorkItemTypeRepository repository,
        AuditLog auditRepository,
        TransactionalOutbox eventRepository
    ) {
        this.catalog = catalog;
        this.definitionService = definitionService;
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public ReconciliationResult reconcile(UUID workspaceId, UUID spaceId, UUID actorId) {
        PresetSpaceTarget target = repository.lockPresetSpace(workspaceId, spaceId)
            .orElseThrow(() -> new WorkItemTypeException("SPACE_NOT_FOUND", "Project space not found"));
        if (!"active".equals(target.status())) {
            return new ReconciliationResult(spaceId, catalog.version(), "skipped", List.of(), List.of(), List.of());
        }

        Map<String, WorkItemTypeDefinition> existing = new LinkedHashMap<>();
        definitionService.list(workspaceId, spaceId, null).forEach(type -> existing.put(type.typeKey(), type));
        List<String> conflicts = catalog.developmentPresets().stream()
            .map(PresetTemplate::typeKey)
            .filter(existing::containsKey)
            .filter(key -> !existing.get(key).system())
            .toList();
        if (!conflicts.isEmpty()) {
            return new ReconciliationResult(spaceId, catalog.version(), "conflict", List.of(), systemKeys(existing), conflicts);
        }

        List<String> installed = new ArrayList<>();
        List<UUID> installedTypeIds = new ArrayList<>();
        for (PresetTemplate preset : catalog.developmentPresets()) {
            if (existing.containsKey(preset.typeKey())) {
                continue;
            }
            WorkItemTypeDefinition created = definitionService.create(new CreateWorkItemType(
                workspaceId,
                spaceId,
                actorId,
                preset.typeKey(),
                preset.name(),
                preset.icon(),
                preset.description(),
                preset.sortOrder(),
                true
            ));
            installed.add(preset.typeKey());
            installedTypeIds.add(created.id());
        }
        if (!installed.isEmpty()) {
            Map<String, Object> metadata = Map.of(
                "source", "preset_reconciliation",
                "catalogVersion", catalog.version(),
                "installedKeys", List.copyOf(installed),
                "installedCount", installed.size()
            );
            auditRepository.append(
                workspaceId, actorId, "work_item_type.presets_reconciled", "project_space", spaceId,
                null, null, metadata
            );
            eventRepository.append(
                workspaceId,
                "work_item_type.presets_reconciled",
                "project_space",
                spaceId,
                actorId,
                metadata,
                "wit-presets:" + catalog.version() + ":" + spaceId + ":" + lifecycleKey(installedTypeIds)
            );
        }
        String status = installed.isEmpty() ? "current" : "installed";
        return new ReconciliationResult(spaceId, catalog.version(), status, List.copyOf(installed), systemKeysAfter(existing, installed), List.of());
    }

    private List<String> systemKeys(Map<String, WorkItemTypeDefinition> existing) {
        return catalog.developmentPresets().stream()
            .map(PresetTemplate::typeKey)
            .filter(existing::containsKey)
            .filter(key -> existing.get(key).system())
            .toList();
    }

    private UUID lifecycleKey(List<UUID> installedTypeIds) {
        String source = installedTypeIds.stream().map(UUID::toString).sorted().reduce("", (left, right) -> left + ":" + right);
        return UUID.nameUUIDFromBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private List<String> systemKeysAfter(Map<String, WorkItemTypeDefinition> existing, List<String> installed) {
        List<String> keys = new ArrayList<>(systemKeys(existing));
        keys.addAll(installed);
        return List.copyOf(keys);
    }

    public record ReconciliationResult(
        UUID spaceId,
        String catalogVersion,
        String status,
        List<String> installedKeys,
        List<String> existingSystemKeys,
        List<String> conflictKeys
    ) {
        public boolean successful() {
            return !"conflict".equals(status);
        }
    }
}
