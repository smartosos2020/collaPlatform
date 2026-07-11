package com.colla.platform.modules.knowledge.application;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContentContext;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resolves an accessible item to its space-qualified canonical route. */
@Service
public class KnowledgeContentLocator {
    private final KnowledgeContentService contentService;

    public KnowledgeContentLocator(KnowledgeContentService contentService) {
        this.contentService = contentService;
    }

    public Optional<KnowledgeContentLocation> locate(CurrentUser currentUser, UUID itemId) {
        try {
            contentService.requireView(currentUser, itemId);
            KnowledgeContentContext context = contentService.knowledgeContext(currentUser, itemId);
            if (context == null) {
                return Optional.empty();
            }
            return Optional.of(new KnowledgeContentLocation(
                itemId,
                context.spaceId(),
                "/knowledge-bases/" + context.spaceId() + "/items/" + itemId,
                "colla://knowledge-content/" + itemId + "?spaceId=" + context.spaceId()
            ));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().equals(HttpStatus.FORBIDDEN)
                || exception.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    public record KnowledgeContentLocation(UUID itemId, UUID spaceId, String webPath, String deepLink) {
    }
}
