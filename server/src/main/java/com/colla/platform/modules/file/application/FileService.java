package com.colla.platform.modules.file.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.file.domain.FileModels.DownloadUrlResponse;
import com.colla.platform.modules.file.domain.FileModels.FileMetadata;
import com.colla.platform.modules.file.domain.FileModels.UploadUrlResponse;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.storage.StorageProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileService {
    private static final int URL_TTL_SECONDS = 900;

    private final FileRepository fileRepository;
    private final PlatformObjectResolverRegistry objectResolverRegistry;
    private final MinioClient minioClient;
    private final StorageProperties storageProperties;
    private final AuditService auditService;
    private final PlatformObjectRepository objectRepository;

    public FileService(
        FileRepository fileRepository,
        PlatformObjectResolverRegistry objectResolverRegistry,
        MinioClient minioClient,
        StorageProperties storageProperties,
        AuditService auditService,
        PlatformObjectRepository objectRepository
    ) {
        this.fileRepository = fileRepository;
        this.objectResolverRegistry = objectResolverRegistry;
        this.minioClient = minioClient;
        this.storageProperties = storageProperties;
        this.auditService = auditService;
        this.objectRepository = objectRepository;
    }

    @Transactional
    public UploadUrlResponse createUploadUrl(
        CurrentUser currentUser,
        String fileName,
        String contentType,
        long sizeBytes,
        String targetType,
        UUID targetId
    ) {
        if (sizeBytes <= 0 || sizeBytes > storageProperties.getMaxUploadSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file size");
        }
        if (targetType != null && targetId != null) {
            requireTargetAccess(currentUser, targetType, targetId);
        }

        String objectKey = currentUser.workspaceId() + "/" + UUID.randomUUID() + "/" + sanitize(fileName);
        FileMetadata file = fileRepository.createPending(
            currentUser.workspaceId(),
            objectKey,
            fileName,
            contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
            sizeBytes,
            currentUser.id()
        );
        String uploadUrl = presignedUrl(Method.PUT, objectKey);
        return new UploadUrlResponse(file.id(), objectKey, uploadUrl, Map.of("Content-Type", file.contentType()), expiresAt());
    }

    @Transactional
    public FileMetadata complete(CurrentUser currentUser, UUID fileId, String targetType, UUID targetId) {
        FileMetadata file = fileRepository.complete(currentUser.workspaceId(), fileId, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        if (targetType != null && targetId != null) {
            requireTargetAccess(currentUser, targetType, targetId);
            fileRepository.addUsage(currentUser.workspaceId(), file.id(), targetType, targetId, currentUser.id());
        }
        objectRepository.upsertObjectLink(
            currentUser.workspaceId(),
            "file",
            file.id(),
            "/files/" + file.id(),
            "colla://file/" + file.id(),
            file.originalName()
        );
        return file;
    }

    public FileMetadata metadata(CurrentUser currentUser, UUID fileId) {
        FileMetadata file = fileRepository.find(currentUser.workspaceId(), fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
        requireFileAccess(currentUser, file);
        return file;
    }

    public DownloadUrlResponse downloadUrl(CurrentUser currentUser, UUID fileId) {
        FileMetadata file = metadata(currentUser, fileId);
        auditService.log(
            currentUser,
            "file.download_url.created",
            "file",
            file.id(),
            Map.of("fileName", file.originalName(), "sizeBytes", Long.toString(file.sizeBytes()))
        );
        return new DownloadUrlResponse(presignedUrl(Method.GET, file.objectKey()), expiresAt());
    }

    private void requireFileAccess(CurrentUser currentUser, FileMetadata file) {
        if (currentUser.id().equals(file.uploadedBy())) {
            return;
        }
        boolean canAccessUsage = fileRepository.listUsages(currentUser.workspaceId(), file.id()).stream()
            .anyMatch(usage -> objectResolverRegistry.resolve(currentUser, usage.targetType(), usage.targetId()).accessState() == ObjectAccessState.available);
        if (!canAccessUsage) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "File access denied");
        }
    }

    private void requireTargetAccess(CurrentUser currentUser, String targetType, UUID targetId) {
        if (objectResolverRegistry.resolve(currentUser, targetType, targetId).accessState() != ObjectAccessState.available) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Target object access denied");
        }
    }

    private String presignedUrl(Method method, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(storageProperties.getBucket())
                    .object(objectKey)
                    .expiry(URL_TTL_SECONDS)
                    .build()
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create storage URL", exception);
        }
    }

    private Instant expiresAt() {
        return Instant.now().plusSeconds(URL_TTL_SECONDS);
    }

    private String sanitize(String fileName) {
        return fileName == null || fileName.isBlank() ? "upload.bin" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

}
