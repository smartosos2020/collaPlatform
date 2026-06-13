package com.colla.platform.modules.file.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class FileModels {
    private FileModels() {
    }

    public record UploadUrlResponse(
        UUID uploadId,
        String objectKey,
        String uploadUrl,
        Map<String, String> headers,
        Instant expiresAt
    ) {
    }

    public record FileMetadata(
        UUID id,
        String objectKey,
        String originalName,
        String contentType,
        long sizeBytes,
        String status,
        UUID uploadedBy,
        Instant createdAt,
        Instant completedAt
    ) {
    }

    public record DownloadUrlResponse(String downloadUrl, Instant expiresAt) {
    }

    public record FileUsage(String targetType, UUID targetId) {
    }
}
