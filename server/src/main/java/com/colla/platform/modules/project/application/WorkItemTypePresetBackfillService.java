package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.application.WorkItemTypePresetReconciliationService.ReconciliationResult;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository.PresetSpaceTarget;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class WorkItemTypePresetBackfillService {
    private static final Logger log = LoggerFactory.getLogger(WorkItemTypePresetBackfillService.class);

    private final WorkItemTypeRepository repository;
    private final WorkItemTypePresetReconciliationService reconciliationService;
    private final boolean startupEnabled;

    public WorkItemTypePresetBackfillService(
        WorkItemTypeRepository repository,
        WorkItemTypePresetReconciliationService reconciliationService,
        @Value("${colla.project.work-item-types.preset-backfill-enabled:true}") boolean startupEnabled
    ) {
        this.repository = repository;
        this.reconciliationService = reconciliationService;
        this.startupEnabled = startupEnabled;
    }

    public BackfillReport reconcileExistingSpaces() {
        List<ReconciliationResult> results = new ArrayList<>();
        List<BackfillFailure> failures = new ArrayList<>();
        for (PresetSpaceTarget target : repository.listActivePresetSpaces()) {
            try {
                ReconciliationResult result = reconciliationService.reconcile(
                    target.workspaceId(), target.spaceId(), target.createdBy()
                );
                results.add(result);
                if (!result.successful()) {
                    failures.add(new BackfillFailure(target.spaceId(), "PRESET_KEY_CONFLICT", result.conflictKeys()));
                }
            } catch (RuntimeException exception) {
                failures.add(new BackfillFailure(target.spaceId(), exception.getClass().getSimpleName(), List.of()));
                log.error("Work item type preset backfill failed for space {}", target.spaceId(), exception);
            }
        }
        return new BackfillReport(List.copyOf(results), List.copyOf(failures));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        if (!startupEnabled) {
            return;
        }
        BackfillReport report = reconcileExistingSpaces();
        long installed = report.results().stream().mapToLong(result -> result.installedKeys().size()).sum();
        if (installed > 0 || !report.failures().isEmpty()) {
            log.info("Work item type preset backfill completed: installed={}, failures={}", installed, report.failures());
        }
    }

    public record BackfillReport(List<ReconciliationResult> results, List<BackfillFailure> failures) {
    }

    public record BackfillFailure(java.util.UUID spaceId, String code, List<String> conflictKeys) {
    }
}
