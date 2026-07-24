package com.colla.platform.modules.knowledge.api;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.modules.knowledge.application.KnowledgeCollaborationGatewayService;
import com.colla.platform.modules.knowledge.application.KnowledgeCollaborationGatewayService.CollaborationFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/knowledge-collaboration")
@ConditionalOnRuntimeRole({RuntimeRole.API, RuntimeRole.COMBINED})
public class KnowledgeCollaborationInternalController {
    private final KnowledgeCollaborationGatewayService service;

    public KnowledgeCollaborationInternalController(KnowledgeCollaborationGatewayService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Object health(@RequestHeader("X-Colla-Collaboration-Secret") String secret) {
        requireSecret(secret);
        boolean persistenceReady = service.persistenceReady();
        return Map.of(
            "status", persistenceReady ? "UP" : "DOWN",
            "persistenceReady", persistenceReady,
            "protocolVersion", KnowledgeCollaborationGatewayService.PROTOCOL_VERSION,
            "schemaVersion", KnowledgeCollaborationGatewayService.SCHEMA_VERSION
        );
    }

    @PostMapping("/authenticate")
    public Object authenticate(@RequestHeader("X-Colla-Collaboration-Secret") String secret, @RequestBody AuthRequest request) {
        requireSecret(secret);
        return service.authenticate(request.ticket(), request.documentName());
    }

    @PostMapping("/authorize")
    public Object authorize(@RequestHeader("X-Colla-Collaboration-Secret") String secret, @RequestBody AuthRequest request) {
        requireSecret(secret);
        return service.authorizeSession(request.ticket(), request.documentName());
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
            request.ticket(), request.documentName(), request.update(), request.clientId(), request.updateId(),
            request.schemaVersion(), request.generation()
        );
    }

    @PostMapping("/document/snapshot")
    public Object snapshot(@RequestHeader("X-Colla-Collaboration-Secret") String secret, @RequestBody SnapshotRequest request) {
        requireSecret(secret);
        JsonNode document = request.canonicalDocument().deepCopy();
        if (document.isObject()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) document).put("collaborationTitle", request.title());
        }
        if (request.nodeId() != null && !request.nodeId().isBlank()) {
            return service.storeSnapshotFromNode(
                request.documentName(), request.snapshot(), request.stateVector(), document,
                request.schemaVersion(), request.clientId(), request.nodeId(),
                request.generation(), request.snapshotSequence()
            );
        }
        return service.storeSnapshot(
            request.ticket(), request.documentName(), request.snapshot(), request.stateVector(), document,
            request.schemaVersion(), request.clientId(), request.generation(), request.snapshotSequence()
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
    public record UpdateRequest(
        String ticket, String documentName, String update, String clientId, String updateId,
        int schemaVersion, long generation
    ) {}
    public record SnapshotRequest(
        String ticket, String documentName, String snapshot, String stateVector, JsonNode canonicalDocument,
        int schemaVersion, String clientId, String title, String nodeId, long generation, long snapshotSequence
    ) {}
}
