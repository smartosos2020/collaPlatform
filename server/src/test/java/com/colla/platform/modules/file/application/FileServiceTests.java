package com.colla.platform.modules.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.file.domain.FileModels.FileMetadata;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.storage.StorageProperties;
import io.minio.MinioClient;
import io.minio.StatObjectResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FileServiceTests {
    private final FileRepository files = mock(FileRepository.class);
    private final PlatformObjectResolverRegistry resolvers = mock(PlatformObjectResolverRegistry.class);
    private final MinioClient minio = mock(MinioClient.class);
    private final AuditService audit = mock(AuditService.class);
    private final PlatformObjectRepository objects = mock(PlatformObjectRepository.class);
    private final StorageProperties storage = new StorageProperties();
    private FileService service;
    private CurrentUser user;

    @BeforeEach
    void setUp() {
        storage.setBucket("files");
        service = new FileService(files, resolvers, minio, storage, audit, objects);
        user = new CurrentUser(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "owner",
            "Owner",
            Set.of("member"),
            Set.of()
        );
    }

    @Test
    void verifiesObjectBeforeCompletingAndAuditsOnlyTheWinningTransition() throws Exception {
        FileMetadata pending = file("pending", user.id());
        FileMetadata completed = new FileMetadata(
            pending.id(),
            pending.objectKey(),
            pending.originalName(),
            pending.contentType(),
            pending.sizeBytes(),
            "completed",
            pending.uploadedBy(),
            pending.createdAt(),
            Instant.now()
        );
        StatObjectResponse object = mock(StatObjectResponse.class);
        when(object.size()).thenReturn(pending.sizeBytes());
        when(files.find(user.workspaceId(), pending.id())).thenReturn(Optional.of(pending), Optional.of(completed));
        when(files.completePending(user.workspaceId(), pending.id(), user.id())).thenReturn(true);
        when(minio.statObject(any())).thenReturn(object);

        FileMetadata result = service.complete(user, pending.id(), null, null);

        assertThat(result.status()).isEqualTo("completed");
        verify(files).completePending(user.workspaceId(), pending.id(), user.id());
        verify(audit).log(user, "file.upload.completed", "file", pending.id(), Map.of(
            "fileName", pending.originalName(),
            "sizeBytes", Long.toString(pending.sizeBytes())
        ));
    }

    @Test
    void completedUploadReplaysWithoutTouchingStorageOrDuplicatingAudit() throws Exception {
        FileMetadata completed = file("completed", user.id());
        when(files.find(user.workspaceId(), completed.id())).thenReturn(Optional.of(completed));

        assertThat(service.complete(user, completed.id(), null, null)).isSameAs(completed);

        verify(minio, never()).statObject(any());
        verify(files, never()).completePending(any(), any(), any());
        verify(audit, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsAnotherUsersPendingUploadBeforeStorageAccess() throws Exception {
        FileMetadata pending = file("pending", UUID.randomUUID());
        when(files.find(user.workspaceId(), pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.complete(user, pending.id(), null, null))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(minio, never()).statObject(any());
        verify(files, never()).completePending(any(), any(), any());
    }

    @Test
    void rejectsMismatchedObjectSizeWithoutChangingDatabaseState() throws Exception {
        FileMetadata pending = file("pending", user.id());
        StatObjectResponse object = mock(StatObjectResponse.class);
        when(object.size()).thenReturn(pending.sizeBytes() - 1);
        when(files.find(user.workspaceId(), pending.id())).thenReturn(Optional.of(pending));
        when(minio.statObject(any())).thenReturn(object);

        assertThatThrownBy(() -> service.complete(user, pending.id(), null, null))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verify(files, never()).completePending(any(), any(), any());
    }

    private FileMetadata file(String status, UUID uploadedBy) {
        return new FileMetadata(
            UUID.randomUUID(),
            user.workspaceId() + "/object.bin",
            "object.bin",
            "application/octet-stream",
            4,
            status,
            uploadedBy,
            Instant.now(),
            "completed".equals(status) ? Instant.now() : null
        );
    }
}
