package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContent;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Focused application boundary for anchored comments and thread state. */
@Service
public class KnowledgeContentCommentService {
    private final KnowledgeContentService workflow;

    public KnowledgeContentCommentService(KnowledgeContentService workflow) {
        this.workflow = workflow;
    }

    public KnowledgeContent addComment(CurrentUser user, UUID itemId, UUID blockId, String content,
        String anchorType, Integer anchorStart, Integer anchorEnd, String anchorText, String anchorPrefix,
        String anchorSuffix) {
        return workflow.addComment(user, itemId, blockId, content, anchorType, anchorStart, anchorEnd,
            anchorText, anchorPrefix, anchorSuffix);
    }

    public KnowledgeContent addReply(CurrentUser user, UUID itemId, UUID commentId, String content) {
        return workflow.addCommentReply(user, itemId, commentId, content);
    }

    public KnowledgeContent resolve(CurrentUser user, UUID itemId, UUID commentId) {
        return workflow.resolveComment(user, itemId, commentId);
    }

    public KnowledgeContent reopen(CurrentUser user, UUID itemId, UUID commentId) {
        return workflow.reopenComment(user, itemId, commentId);
    }
}
