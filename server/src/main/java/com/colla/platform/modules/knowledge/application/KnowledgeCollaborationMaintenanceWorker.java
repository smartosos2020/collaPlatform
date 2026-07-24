package com.colla.platform.modules.knowledge.application;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.WORKER, RuntimeRole.COMBINED})
public class KnowledgeCollaborationMaintenanceWorker {
    private final KnowledgeContentCollaborationService collaborationService;

    public KnowledgeCollaborationMaintenanceWorker(KnowledgeContentCollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @Scheduled(fixedDelayString = "${colla.docs.collaboration.autosave-delay-ms:1000}")
    public void flushDirtySnapshots() {
        collaborationService.flushDirtySnapshots();
    }

    @Scheduled(fixedDelayString = "${colla.docs.collaboration.presence-cleanup-delay-ms:30000}")
    public void pruneInactiveRooms() {
        collaborationService.pruneInactiveRooms();
    }
}
