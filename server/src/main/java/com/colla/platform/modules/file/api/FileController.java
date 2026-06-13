package com.colla.platform.modules.file.api;

import com.colla.platform.modules.file.application.FileService;
import com.colla.platform.modules.file.domain.FileModels.DownloadUrlResponse;
import com.colla.platform.modules.file.domain.FileModels.FileMetadata;
import com.colla.platform.modules.file.domain.FileModels.UploadUrlResponse;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload-url")
    public UploadUrlResponse uploadUrl(@Valid @RequestBody UploadUrlRequest request, Authentication authentication) {
        return fileService.createUploadUrl(
            currentUser(authentication),
            request.fileName(),
            request.contentType(),
            request.sizeBytes(),
            request.targetType(),
            request.targetId()
        );
    }

    @PostMapping("/complete")
    public FileMetadata complete(@Valid @RequestBody CompleteUploadRequest request, Authentication authentication) {
        return fileService.complete(currentUser(authentication), request.fileId(), request.targetType(), request.targetId());
    }

    @GetMapping("/{fileId}")
    public FileMetadata metadata(@PathVariable UUID fileId, Authentication authentication) {
        return fileService.metadata(currentUser(authentication), fileId);
    }

    @GetMapping("/{fileId}/download-url")
    public DownloadUrlResponse downloadUrl(@PathVariable UUID fileId, Authentication authentication) {
        return fileService.downloadUrl(currentUser(authentication), fileId);
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record UploadUrlRequest(
        @NotBlank String fileName,
        String contentType,
        @Positive long sizeBytes,
        String targetType,
        UUID targetId
    ) {
    }

    public record CompleteUploadRequest(UUID fileId, String targetType, UUID targetId) {
    }
}
