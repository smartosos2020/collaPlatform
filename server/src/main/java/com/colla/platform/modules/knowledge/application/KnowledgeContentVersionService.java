package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContent;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentVersion;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentVersionDiff;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Focused application boundary for content version workflows. */
@Service
public class KnowledgeContentVersionService {
    private final KnowledgeContentService workflow;

    public KnowledgeContentVersionService(KnowledgeContentService workflow) {
        this.workflow = workflow;
    }

    public List<KnowledgeContentVersion> listVersions(CurrentUser user, UUID itemId) {
        return workflow.listVersions(user, itemId);
    }

    public KnowledgeContent createCheckpoint(CurrentUser user, UUID itemId) {
        return workflow.createVersionCheckpoint(user, itemId);
    }

    public KnowledgeContent createNamedVersion(CurrentUser user, UUID itemId, String name, String summary) {
        return workflow.createNamedVersion(user, itemId, name, summary);
    }

    public KnowledgeContentVersionDiff diff(CurrentUser user, UUID itemId, int from, int to) {
        return workflow.diffVersions(user, itemId, from, to);
    }

    public KnowledgeContent restore(CurrentUser user, UUID itemId, int versionNo) {
        return workflow.restoreVersion(user, itemId, versionNo);
    }
}
