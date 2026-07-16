package com.colla.platform.modules.knowledge.api;

import com.colla.platform.modules.knowledge.application.KnowledgeBaseItemService;
import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeObjectReference;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-object-references")
public class KnowledgeObjectReferenceController {
    private final KnowledgeBaseItemService itemService;

    public KnowledgeObjectReferenceController(KnowledgeBaseItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/{targetObjectType}/{targetObjectId}")
    public List<KnowledgeObjectReference> list(@PathVariable String targetObjectType,
                                               @PathVariable UUID targetObjectId,
                                               Authentication authentication) {
        return itemService.listObjectReferences((CurrentUser) authentication.getPrincipal(), targetObjectType, targetObjectId);
    }
}
