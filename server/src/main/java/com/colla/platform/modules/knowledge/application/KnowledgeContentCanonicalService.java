package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentCanonicalDocument;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentMigrationPlan;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContent;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlock;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentBlockDraft;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeContentCanonicalService {
    private final KnowledgeContentSchemaService schemaService;

    public KnowledgeContentCanonicalService(KnowledgeContentSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    public KnowledgeContentMigrationPlan plan(UUID itemId, KnowledgeContent content) {
        List<KnowledgeContentBlockDraft> legacyBlocks = content.blocks().stream().map(this::toDraft).toList();
        KnowledgeContentCanonicalDocument canonical = schemaService.fromLegacy(itemId, legacyBlocks, content.content());
        return new KnowledgeContentMigrationPlan(
            itemId,
            content.blocks().stream().mapToInt(block -> block.schemaVersion()).min().orElse(2),
            canonical.schemaVersion(),
            schemaService.legacyChecksum(itemId, legacyBlocks, content.content()),
            canonical.checksum(),
            content.blocks().size(),
            canonical.blocks().size(),
            true,
            canonical.issues().stream().noneMatch(issue -> issue.isError()),
            "dry-run",
            canonical.issues(),
            canonical
        );
    }

    private KnowledgeContentBlockDraft toDraft(KnowledgeContentBlock block) {
        return new KnowledgeContentBlockDraft(
            block.id(),
            block.parentId(),
            block.blockType(),
            block.content(),
            block.sortOrder(),
            block.schemaVersion(),
            block.attrs(),
            block.richContent(),
            block.plainText(),
            block.anchorId(),
            false
        );
    }
}
