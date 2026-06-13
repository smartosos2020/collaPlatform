package com.colla.platform.modules.file.infrastructure;

import com.colla.platform.modules.file.domain.FileModels.FileMetadata;
import com.colla.platform.modules.file.domain.FileModels.FileUsage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository {
    FileMetadata createPending(UUID workspaceId, String objectKey, String originalName, String contentType, long sizeBytes, UUID uploadedBy);

    Optional<FileMetadata> complete(UUID workspaceId, UUID fileId, UUID uploadedBy);

    Optional<FileMetadata> find(UUID workspaceId, UUID fileId);

    void addUsage(UUID workspaceId, UUID fileId, String targetType, UUID targetId, UUID createdBy);

    List<FileUsage> listUsages(UUID workspaceId, UUID fileId);
}
