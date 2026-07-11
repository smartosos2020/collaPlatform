package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseItem;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentContext;
import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.domain.PlatformObjectTypes;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class KnowledgeContentPlatformObjectResolver implements PlatformObjectResolver {
    /*
     * The platform object type remains "knowledge_content" for historical links, search
     * index rows, notifications and cross-module cards. User-facing labels and
     * primary navigation should treat it as knowledge content.
     */
    private final KnowledgeContentService contentService;
    private final KnowledgeContentLocator locator;

    public KnowledgeContentPlatformObjectResolver(KnowledgeContentService contentService, KnowledgeContentLocator locator) {
        this.contentService = contentService;
        this.locator = locator;
    }

    @Override
    public String objectType() {
        return PlatformObjectTypes.KNOWLEDGE_CONTENT;
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        try {
            KnowledgeBaseItem document = contentService.requireView(currentUser, objectId);
            KnowledgeContentContext knowledgeContext = contentService.knowledgeContext(currentUser, objectId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("versionNo", document.currentVersionNo());
            metadata.put("permissionLevel", document.permissionLevel());
            metadata.put("contentType", document.contentType());
            metadata.put("archived", document.archived());
            metadata.put("sourceModule", "knowledge");
            metadata.put("updatedAt", document.updatedAt().toString());
            metadata.put("backReferencePath", "/knowledge-bases");
            if (knowledgeContext != null) {
                metadata.put("knowledgeBaseId", knowledgeContext.spaceId().toString());
                metadata.put("knowledgeBaseName", knowledgeContext.spaceName());
                metadata.put("knowledgePath", knowledgeContext.pathText());
                metadata.put("backReferencePath", knowledgeContext.webPath());
            }
            KnowledgeContentLocator.KnowledgeContentLocation location = locator.locate(currentUser, objectId).orElse(null);
            return Optional.of(new PlatformObjectSummary(
                objectType(),
                objectId,
                ObjectAccessState.available,
                document.title(),
                knowledgeContext == null ? "知识内容 / v" + document.currentVersionNo() : knowledgeContext.spaceName() + " / " + knowledgeContext.pathText(),
                document.archived() ? "archived" : "active",
                location == null ? null : location.webPath(),
                location == null ? null : location.deepLink(),
                metadata
            ));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
                return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.forbidden));
            }
            if (exception.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw exception;
        }
    }
}


