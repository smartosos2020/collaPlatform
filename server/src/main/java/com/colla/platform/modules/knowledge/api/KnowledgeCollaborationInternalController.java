package com.colla.platform.modules.knowledge.api;

import com.colla.platform.modules.knowledge.application.KnowledgeCollaborationGatewayService;
import com.colla.platform.modules.knowledge.application.KnowledgeCollaborationGatewayService.CollaborationFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/knowledge-collaboration")
public class KnowledgeCollaborationInternalController {
    private final KnowledgeCollaborationGatewayService service;

    public KnowledgeCollaborationInternalController(KnowledgeCollaborationGatewayService service) {
        this.service = service;
    }

    @PostMapping("/authenticate")
    public Object authenticate(@RequestHeader("X-Colla-Collaboration-Secret") String secret, @RequestBody AuthRequest request) {
        requireSecret(secret);
        return service.authenticate(request.ticket(), request.documentName());
    }

    @PostMapping("/document/load")
    public Object load(@RequestHeader("X-Colla-Collaboration-Secret") String secret, @RequestBody AuthRequest request) {
        requireSecret(secret);
        return service.load(request.ticket(), request.documentName());
    }

    @PostMapping("/document/update")
    public Object update(@RequestHeader("X-Colla-Collaboration-Secret") String secret, @RequestBody UpdateRequest request) {
        requireSecret(secret);
        return service.appendUpdate(
            request.ticket(), request.documentName(), request.update(), request.clientId(), request.updateId(), request.schemaVersion()
        );
    }

    @PostMapping("/document/snapshot")
    public Object snapshot(@RequestHeader("X-Colla-Collaboration-Secret") String secret, @RequestBody SnapshotRequest request) {
        requireSecret(secret);
        JsonNode document = request.canonicalDocument().deepCopy();
        if (document.isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) document).put("collaborationTitle", request.title());
        }
        return service.storeSnapshot(
            request.ticket(), request.documentName(), request.snapshot(), request.stateVector(), document,
            request.schemaVersion(), request.clientId()
        );
    }

    @ExceptionHandler(CollaborationFailure.class)
    public ResponseEntity<Map<String, Object>> collaborationFailure(CollaborationFailure failure) {
        return ResponseEntity.status(failure.status()).body(Map.of("code", failure.code(), "message", failure.getMessage()));
    }

    private void requireSecret(String supplied) {
        if (!service.validInternalSecret(supplied)) {
            throw new CollaborationFailure(org.springframework.http.HttpStatus.UNAUTHORIZED, "COLLAB_INTERNAL_UNAUTHORIZED", "Invalid collaboration service secret");
        }
    }

    public record AuthRequest(String ticket, String documentName) {}
    public record UpdateRequest(String ticket, String documentName, String update, String clientId, String updateId, int schemaVersion) {}
    public record SnapshotRequest(
        String ticket, String documentName, String snapshot, String stateVector, JsonNode canonicalDocument,
        int schemaVersion, String clientId, String title
    ) {}
}
