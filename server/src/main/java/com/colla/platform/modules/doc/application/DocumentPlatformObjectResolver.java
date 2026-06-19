package com.colla.platform.modules.doc.application;

import com.colla.platform.modules.doc.domain.DocumentModels.DocumentSummary;
import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DocumentPlatformObjectResolver implements PlatformObjectResolver {
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
            return Optional.of(new PlatformObjectSummary(
                objectType(),
                objectId,
                ObjectAccessState.available,
                document.title(),
                "文档 / v" + document.currentVersionNo(),
                document.archived() ? "archived" : "active",
                "/docs/" + objectId,
                "colla://document/" + objectId,
                Map.of(
                    "versionNo", document.currentVersionNo(),
                    "permissionLevel", document.permissionLevel(),
                    "docType", document.docType(),
                    "archived", document.archived()
                )
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
