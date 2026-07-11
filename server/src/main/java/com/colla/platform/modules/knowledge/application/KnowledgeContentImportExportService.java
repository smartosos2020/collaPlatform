package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseMarkdownImportItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseMarkdownImportResult;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContent;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Focused boundary for knowledge content import and export workflows. */
@Service
public class KnowledgeContentImportExportService {
    private final KnowledgeContentService workflow;

    public KnowledgeContentImportExportService(KnowledgeContentService workflow) {
        this.workflow = workflow;
    }

    public KnowledgeContent importMarkdown(CurrentUser user, UUID itemId, String title, String content) {
        return workflow.importMarkdown(user, itemId, title, content);
    }

    public KnowledgeContent importHtml(CurrentUser user, UUID itemId, String title, String html) {
        return workflow.importHtml(user, itemId, title, html);
    }

    public String exportMarkdown(CurrentUser user, UUID itemId) {
        return workflow.exportMarkdown(user, itemId);
    }

    public String exportHtml(CurrentUser user, UUID itemId) {
        return workflow.exportHtml(user, itemId);
    }

    public KnowledgeBaseMarkdownImportResult importBatch(CurrentUser user, UUID spaceId, UUID parentId,
        List<KnowledgeBaseMarkdownImportItem> items) {
        return workflow.importKnowledgeBaseMarkdownBatch(user, spaceId, parentId, items);
    }

    public String exportSpace(CurrentUser user, UUID spaceId) {
        return workflow.exportKnowledgeBaseMarkdown(user, spaceId);
    }
}
