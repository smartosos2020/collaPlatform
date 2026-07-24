package com.colla.platform.modules.knowledge.application;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentCollaborationHealth;
import com.colla.platform.modules.knowledge.infrastructure.KnowledgeContentRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnRuntimeRole({RuntimeRole.API, RuntimeRole.COMBINED})
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
        var state = contentRepository.findCollaborationBinaryState(currentUser.workspaceId(), itemId).orElse(null);
        long snapshotSequence = state == null ? 0 : state.snapshotSequence();
        long latestSequence = contentRepository.findLatestCollaborationSequence(currentUser.workspaceId(), itemId);
        long serverClock = Math.max(snapshotSequence, latestSequence);
        return new KnowledgeContentCollaborationHealth(
            itemId,
            serverClock,
            0,
            latestSequence > snapshotSequence,
            state == null || state.stateVector() == null
                ? ""
                : java.util.Base64.getEncoder().encodeToString(state.stateVector()),
            state == null ? null : state.updatedAt(),
            state == null ? null : state.updatedAt()
        );
    }
}
