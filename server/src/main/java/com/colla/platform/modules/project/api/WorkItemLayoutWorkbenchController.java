package com.colla.platform.modules.project.api;

import static com.colla.platform.modules.project.domain.WorkItemLayoutModels.failure;

import com.colla.platform.modules.project.api.WorkItemFieldApiDtos.FieldCollection;
import com.colla.platform.modules.project.api.WorkItemFieldApiDtos.FieldTypeCatalog;
import com.colla.platform.modules.project.api.WorkItemLayoutApiDtos.LayoutView;
import com.colla.platform.modules.project.api.WorkItemTypeApiDtos.SpaceConfigurationWorkItemType;
import com.colla.platform.modules.project.application.WorkItemFieldConfigurationService;
import com.colla.platform.modules.project.application.WorkItemLayoutAccessProjectionService;
import com.colla.platform.modules.project.application.WorkItemLayoutAccessProjectionService.LayoutAccessProjection;
import com.colla.platform.modules.project.application.WorkItemLayoutConfigurationService;
import com.colla.platform.modules.project.application.WorkItemTypeConfigurationService;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.WorkItemLayoutException;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/configuration/types/{typeId}/layout-workbench")
public class WorkItemLayoutWorkbenchController {
    private final WorkItemTypeConfigurationService typeService;
    private final WorkItemFieldConfigurationService fieldService;
    private final WorkItemLayoutConfigurationService layoutService;
    private final WorkItemLayoutAccessProjectionService projectionService;

    public WorkItemLayoutWorkbenchController(
        WorkItemTypeConfigurationService typeService,
        WorkItemFieldConfigurationService fieldService,
        WorkItemLayoutConfigurationService layoutService,
        WorkItemLayoutAccessProjectionService projectionService
    ) {
        this.typeService = typeService;
        this.fieldService = fieldService;
        this.layoutService = layoutService;
        this.projectionService = projectionService;
    }

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkbenchView get(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        Authentication authentication
    ) {
        CurrentUser user = (CurrentUser) authentication.getPrincipal();
        SpaceConfigurationWorkItemType type = WorkItemTypeApiDtos.configuredType(
            typeService.detail(user, spaceId, typeId)
        );
        FieldCollection fields = WorkItemFieldApiDtos.configuration(
            fieldService.configuration(user, spaceId, typeId, null)
        );
        FieldTypeCatalog fieldTypes = WorkItemFieldApiDtos.fieldTypes(
            fieldService.fieldTypes(user, spaceId)
        );
        Map<String, WorkbenchLayoutView> layouts = new LinkedHashMap<>();
        for (String kind : java.util.List.of("create", "detail")) {
            layouts.put(kind, layout(user, spaceId, typeId, kind));
        }
        return new WorkbenchView(
            type,
            fields,
            fieldTypes,
            java.util.Collections.unmodifiableMap(layouts)
        );
    }

    private WorkbenchLayoutView layout(
        CurrentUser user,
        UUID spaceId,
        UUID typeId,
        String kind
    ) {
        try {
            LayoutView configuration = WorkItemLayoutApiDtos.view(
                layoutService.get(user, spaceId, typeId, kind)
            );
            LayoutAccessProjection projection = projectionService.project(
                user, spaceId, typeId, kind
            );
            if (configuration.aggregateVersion() != projection.aggregateVersion()
                || !configuration.configHash().equals(projection.configHash())) {
                throw failure(
                    "LAYOUT_SNAPSHOT_CONFLICT",
                    "Layout configuration changed while the workbench snapshot was assembled"
                );
            }
            return new WorkbenchLayoutView(configuration, projection);
        } catch (WorkItemLayoutException exception) {
            if ("LAYOUT_NOT_FOUND".equals(exception.code())) {
                return null;
            }
            throw exception;
        }
    }

    public record WorkbenchView(
        SpaceConfigurationWorkItemType type,
        FieldCollection fields,
        FieldTypeCatalog fieldTypes,
        Map<String, WorkbenchLayoutView> layouts
    ) {
    }

    public record WorkbenchLayoutView(
        LayoutView configuration,
        LayoutAccessProjection runtimeProjection
    ) {
    }
}
