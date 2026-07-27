package com.colla.platform.modules.platform.application;

import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.modules.platform.infrastructure.DashboardPersonalizationRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalizationCommandService {
    private final DashboardPersonalizationRepository repository;
    private final PlatformObjectService objectService;

    public PersonalizationCommandService(
        DashboardPersonalizationRepository repository,
        PlatformObjectService objectService
    ) {
        this.repository = repository;
        this.objectService = objectService;
    }

    @Transactional
    public PlatformObjectSummary setFavorite(
        CurrentUser user,
        String objectType,
        UUID objectId,
        String requestId,
        boolean favorite
    ) {
        String stableRequestId = requestId == null ? "" : requestId.trim();
        if (stableRequestId.isBlank() || stableRequestId.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }
        String operation = "set_favorite:" + objectType + ":" + objectId;
        String hash = sha256(Boolean.toString(favorite));
        var replay = repository.completedCommand(user.workspaceId(), user.id(), operation, stableRequestId, hash);
        if (replay.isPresent()) {
            return objectService.summary(user, objectType, objectId);
        }
        UUID commandId = UUID.randomUUID();
        if (!repository.tryStartCommand(commandId, user.workspaceId(), user.id(), operation, stableRequestId, hash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId was reused with another payload");
        }
        PlatformObjectSummary summary;
        if (favorite) {
            summary = objectService.addFavorite(user, objectType, objectId);
        } else {
            summary = objectService.summary(user, objectType, objectId);
            objectService.removeFavorite(user, objectType, objectId);
        }
        repository.completeCommand(commandId, favorite ? 1 : 0);
        return summary;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
