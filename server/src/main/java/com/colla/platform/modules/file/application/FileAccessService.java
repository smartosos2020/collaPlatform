package com.colla.platform.modules.file.application;

import com.colla.platform.modules.file.contract.FileAccess;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FileAccessService implements FileAccess {
    private final FileRepository fileRepository;

    public FileAccessService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public Map<UUID, FileResult> resolve(UUID workspaceId, UUID actorId, Collection<UUID> fileIds) {
        Map<UUID, FileResult> result = new LinkedHashMap<>();
        for (UUID fileId : fileIds) {
            FileResult value = fileRepository.find(workspaceId, fileId)
                .map(file -> {
                    FileState state = switch (file.status()) {
                        case "completed" -> FileState.ACTIVE;
                        case "deleted" -> FileState.DELETED;
                        default -> FileState.PENDING;
                    };
                    return new FileResult(
                        fileId,
                        state == FileState.ACTIVE ? Availability.AVAILABLE : Availability.UNAVAILABLE,
                        state == FileState.ACTIVE
                            ? new FileMetadata(fileId, workspaceId, state, file.sizeBytes(), file.contentType())
                            : null
                    );
                })
                .orElse(new FileResult(fileId, Availability.HIDDEN, null));
            result.put(fileId, value);
        }
        return Map.copyOf(result);
    }

    @Override
    public void linkUsage(UUID workspaceId, UUID actorId, UUID fileId, String targetType, UUID targetId) {
        fileRepository.addUsage(workspaceId, fileId, targetType, targetId, actorId);
    }
}
