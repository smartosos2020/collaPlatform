package com.colla.platform.modules.knowledge.application;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentCollaborationHealth;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeContentRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnRuntimeRole(RuntimeRole.API)
public class DatabaseKnowledgeCollaborationHealthQuery implements KnowledgeCollaborationHealthQuery {
    private final KnowledgeContentService contentService;
    private final KnowledgeContentRepository contentRepository;

    public DatabaseKnowledgeCollaborationHealthQuery(
        KnowledgeContentService contentService,
        KnowledgeContentRepository contentRepository
    ) {
        this.contentService = contentService;
        this.contentRepository = contentRepository;
    }

    @Override
    public KnowledgeContentCollaborationHealth health(CurrentUser currentUser, UUID itemId) {
        contentService.requireView(currentUser, itemId);
        var state = contentRepository.findCollaborationState(currentUser.workspaceId(), itemId).orElse(null);
        long serverClock = state == null ? 0 : state.serverClock();
        return new KnowledgeContentCollaborationHealth(
            itemId,
            serverClock,
            0,
            false,
            state == null ? Long.toString(serverClock) : state.stateVector(),
            state == null ? null : state.lastSavedAt(),
            state == null ? null : state.updatedAt()
        );
    }
}
