package com.colla.platform.modules.doc.application;

import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.doc.domain.DocumentModels.KnowledgeContext;
import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DocumentPlatformObjectResolver implements PlatformObjectResolver {
    /*
     * The platform object type remains "document" for historical links, search
     * index rows, notifications and cross-module cards. User-facing labels and
     * primary navigation should treat it as knowledge content.
     */
    private final DocumentService documentService;

    public DocumentPlatformObjectResolver(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String objectType() {
        return "document";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        try {
            DocumentSummary document = documentService.requireView(currentUser, objectId);
            KnowledgeContext knowledgeContext = documentService.knowledgeContext(currentUser, objectId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("versionNo", document.currentVersionNo());
            metadata.put("permissionLevel", document.permissionLevel());
            metadata.put("docType", document.docType());
            metadata.put("archived", document.archived());
            metadata.put("sourceModule", "knowledge");
            metadata.put("updatedAt", document.updatedAt().toString());
            metadata.put("backReferencePath", "/docs/" + objectId + "/relations");
            if (knowledgeContext != null) {
                metadata.put("knowledgeBaseId", knowledgeContext.spaceId().toString());
                metadata.put("knowledgeBaseName", knowledgeContext.spaceName());
                metadata.put("knowledgePath", knowledgeContext.pathText());
                metadata.put("backReferencePath", knowledgeContext.webPath());
            }
            return Optional.of(new PlatformObjectSummary(
                objectType(),
                objectId,
                ObjectAccessState.available,
                document.title(),
                knowledgeContext == null ? "知识内容 / v" + document.currentVersionNo() : knowledgeContext.spaceName() + " / " + knowledgeContext.pathText(),
                document.archived() ? "archived" : "active",
                knowledgeContext == null ? "/docs/" + objectId : knowledgeContext.webPath(),
                "colla://document/" + objectId,
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
