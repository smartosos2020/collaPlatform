package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationCapabilities;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationEndpoint;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationPage;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkItemRelationApiDtos {
    private WorkItemRelationApiDtos() {
    }

    public static RelationResponse response(RelationView view) {
        return new RelationResponse(
            view.id(),
            view.relationKey(),
            view.kind(),
            view.direction(),
            view.status(),
            view.version(),
            view.definitionVersionId(),
            view.definitionConfigHash(),
            endpoint(view.source()),
            endpoint(view.target()),
            view.perspective(),
            view.displayName(),
            view.reverse(),
            view.availableActions(),
            view.createdAt(),
            view.updatedAt()
        );
    }

    public static RelationPageResponse page(RelationPage page) {
        return new RelationPageResponse(
            page.items().stream().map(WorkItemRelationApiDtos::response).toList(),
            page.nextCursor()
        );
    }

    public static RelationCapabilitiesResponse capabilities(RelationCapabilities value) {
        return new RelationCapabilitiesResponse(
            value.relationKey(),
            value.visible(),
            value.canCreate(),
            value.canWithdraw(),
            value.canRestore(),
            value.denialReasons()
        );
    }

    private static EndpointResponse endpoint(RelationEndpoint endpoint) {
        return new EndpointResponse(
            endpoint.id(),
            endpoint.typeDefinitionId(),
            endpoint.typeVersionId(),
            endpoint.typeKey(),
            endpoint.displayKey(),
            endpoint.title(),
            endpoint.status(),
            endpoint.version()
        );
    }

    public record EndpointResponse(
        UUID id,
        UUID typeDefinitionId,
        UUID typeVersionId,
        String typeKey,
        String displayKey,
        String title,
        String status,
        long version
    ) {
    }

    public record RelationResponse(
        UUID id,
        String relationKey,
        String kind,
        String direction,
        String status,
        long version,
        UUID definitionVersionId,
        String definitionConfigHash,
        EndpointResponse source,
        EndpointResponse target,
        String perspective,
        String displayName,
        boolean reverse,
        List<String> availableActions,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record RelationPageResponse(List<RelationResponse> items, UUID nextCursor) {
    }

    public record RelationCapabilitiesResponse(
        String relationKey,
        boolean visible,
        boolean canCreate,
        boolean canWithdraw,
        boolean canRestore,
        List<String> denialReasons
    ) {
    }
}
