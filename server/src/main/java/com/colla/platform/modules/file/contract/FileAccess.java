package com.colla.platform.modules.file.contract;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * File metadata and authorization boundary. Storage keys and signed URLs are intentionally absent.
 */
public interface FileAccess {

    Map<UUID, FileResult> resolve(UUID workspaceId, UUID actorId, Collection<UUID> fileIds);

    void linkUsage(UUID workspaceId, UUID actorId, UUID fileId, String targetType, UUID targetId);

    enum Availability {
        AVAILABLE,
        UNAVAILABLE,
        HIDDEN
    }

    enum FileState {
        PENDING,
        ACTIVE,
        DELETED
    }

    record FileMetadata(
        UUID fileId,
        UUID workspaceId,
        FileState state,
        long size,
        String mimeType
    ) {
    }

    record FileResult(UUID fileId, Availability availability, FileMetadata metadata) {
        public FileResult {
            if (availability != Availability.AVAILABLE) {
                metadata = null;
            }
        }
    }
}
