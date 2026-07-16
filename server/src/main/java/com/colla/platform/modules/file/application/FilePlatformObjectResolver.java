package com.colla.platform.modules.file.application;

import com.colla.platform.modules.file.domain.FileModels.FileMetadata;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.application.PlatformObjectResolverRegistry;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class FilePlatformObjectResolver implements PlatformObjectResolver {
    private final FileRepository fileRepository;
    private final ObjectProvider<PlatformObjectResolverRegistry> resolverRegistryProvider;

    public FilePlatformObjectResolver(
        FileRepository fileRepository,
        ObjectProvider<PlatformObjectResolverRegistry> resolverRegistryProvider
    ) {
        this.fileRepository = fileRepository;
        this.resolverRegistryProvider = resolverRegistryProvider;
    }

    @Override
    public String objectType() {
        return "file";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        Optional<FileMetadata> candidate = fileRepository.find(currentUser.workspaceId(), objectId);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        FileMetadata file = candidate.get();
        boolean allowed = currentUser.id().equals(file.uploadedBy()) || fileRepository.listUsages(currentUser.workspaceId(), file.id()).stream()
            .anyMatch(usage -> resolverRegistryProvider.getObject()
                .resolve(currentUser, usage.targetType(), usage.targetId()).accessState() == ObjectAccessState.available);
        if (!allowed) {
            return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.forbidden));
        }
        if (!"completed".equals(file.status())) {
            return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.disabled));
        }
        return Optional.of(new PlatformObjectSummary(
            objectType(),
            objectId,
            ObjectAccessState.available,
            file.originalName(),
            file.contentType() + " / " + file.sizeBytes() + " bytes",
            file.status(),
            "/files/" + file.id(),
            "colla://file/" + file.id(),
            Map.of("contentType", file.contentType(), "sizeBytes", file.sizeBytes(), "sourceModule", "file")
        ));
    }
}
