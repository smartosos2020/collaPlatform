package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItemTreeNode;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContent;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentTemplate;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Application boundary for knowledge-base directory item operations. */
@Service
public class KnowledgeBaseItemService {
    private final KnowledgeBaseSpaceService spaceService;

    public KnowledgeBaseItemService(KnowledgeBaseSpaceService spaceService) {
        this.spaceService = spaceService;
    }

    public List<KnowledgeBaseItem> listItems(CurrentUser user, UUID spaceId, boolean includeArchived) {
        return spaceService.listItems(user, spaceId, includeArchived);
    }

    public List<KnowledgeBaseItemTreeNode> itemTree(CurrentUser user, UUID spaceId, boolean includeArchived) {
        return spaceService.itemTree(user, spaceId, includeArchived);
    }

    public KnowledgeContent createItem(CurrentUser user, UUID spaceId, UUID parentId, String title,
        String contentType, String content, String targetObjectType, UUID targetObjectId, String targetRoute,
        String displayMode, String targetTitleStrategy, String entryAlias) {
        return spaceService.createItem(user, spaceId, parentId, title, contentType, content, targetObjectType,
            targetObjectId, targetRoute, displayMode, targetTitleStrategy, entryAlias);
    }

    public KnowledgeContent createItemFromTemplate(CurrentUser user, UUID spaceId, UUID templateId,
        UUID parentId, String title) {
        return spaceService.createItemFromTemplate(user, spaceId, templateId, parentId, title);
    }

    public KnowledgeContent moveItem(CurrentUser user, UUID spaceId, UUID itemId, UUID parentId, Integer sortOrder) {
        return spaceService.moveItem(user, spaceId, itemId, parentId, sortOrder);
    }

    public KnowledgeContent archiveItem(CurrentUser user, UUID spaceId, UUID itemId) {
        return spaceService.archiveItem(user, spaceId, itemId);
    }

    public KnowledgeContent restoreItem(CurrentUser user, UUID spaceId, UUID itemId) {
        return spaceService.restoreItem(user, spaceId, itemId);
    }

    public List<KnowledgeContentTemplate> listTemplates(CurrentUser user, UUID spaceId) {
        return spaceService.listTemplates(user, spaceId);
    }
}
