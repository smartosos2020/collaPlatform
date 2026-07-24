package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentCollaborationHealth;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;

public interface KnowledgeCollaborationHealthQuery {
    KnowledgeContentCollaborationHealth health(CurrentUser currentUser, UUID itemId);
}
