package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemFieldApiDtos.FieldCollection;
import com.colla.platform.modules.project.api.WorkItemFieldApiDtos.FieldTypeCatalog;
import com.colla.platform.modules.project.api.WorkItemFieldApiDtos.FieldView;
import com.colla.platform.modules.project.application.WorkItemFieldConfigurationModels.ReorderField;
import com.colla.platform.modules.project.application.WorkItemFieldConfigurationService;
import com.colla.platform.modules.project.domain.WorkItemFieldOptionModels.ConfigureFieldOption;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/configuration")
public class WorkItemFieldConfigurationController {
    private final WorkItemFieldConfigurationService service;

    public WorkItemFieldConfigurationController(WorkItemFieldConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/field-types")
    public FieldTypeCatalog fieldTypes(@PathVariable UUID spaceId, Authentication authentication) {
        return WorkItemFieldApiDtos.fieldTypes(service.fieldTypes(currentUser(authentication), spaceId));
    }

    @GetMapping("/types/{typeId}/fields")
    public FieldCollection list(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @RequestParam(required = false) String status,
        Authentication authentication
    ) {
        return WorkItemFieldApiDtos.configuration(
            service.configuration(currentUser(authentication), spaceId, typeId, status)
        );
    }

    @GetMapping("/types/{typeId}/fields/{fieldId}")
    public FieldView detail(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID fieldId,
        Authentication authentication
    ) {
        return WorkItemFieldApiDtos.configuredField(
            service.detail(currentUser(authentication), spaceId, typeId, fieldId)
        );
    }

    @PostMapping("/types/{typeId}/fields")
    public FieldView create(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody CreateFieldRequest request,
        Authentication authentication
    ) {
        return WorkItemFieldApiDtos.configuredField(service.create(
            currentUser(authentication), spaceId, typeId, request.fieldKey(), request.name(), request.description(),
            request.fieldType(), request.config(), request.sortOrder() == null ? 0 : request.sortOrder(), requestId()
        ));
    }

    @PatchMapping("/types/{typeId}/fields/{fieldId}")
    public FieldView update(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID fieldId,
        @Valid @RequestBody UpdateFieldRequest request,
        Authentication authentication
    ) {
        return WorkItemFieldApiDtos.configuredField(service.update(
            currentUser(authentication), spaceId, typeId, fieldId, request.name(), request.description(),
            request.config(), request.aggregateVersion(), requestId()
        ));
    }

    @PutMapping("/types/{typeId}/fields:reorder")
    public FieldCollection reorder(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @Valid @RequestBody ReorderFieldsRequest request,
        Authentication authentication
    ) {
        List<ReorderField> items = request.items().stream()
            .map(item -> new ReorderField(item.fieldId(), item.sortOrder(), item.aggregateVersion()))
            .toList();
        return WorkItemFieldApiDtos.configuration(
            service.reorder(currentUser(authentication), spaceId, typeId, items, requestId())
        );
    }

    @PutMapping("/types/{typeId}/fields/{fieldId}/configuration")
    public FieldView configure(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID fieldId,
        @Valid @RequestBody ConfigureFieldRequest request,
        Authentication authentication
    ) {
        JsonNode config = WorkItemFieldApiDtos.configurationJson(
            request.schemaVersion(),
            request.required(),
            request.defaultValue(),
            request.validationRules(),
            request.typeConfig()
        );
        List<ConfigureFieldOption> options = request.options().stream()
            .map(option -> new ConfigureFieldOption(
                option.optionKey(), option.name(), option.color(), option.sortOrder(), option.status()
            ))
            .toList();
        return WorkItemFieldApiDtos.configuredField(service.configure(
            currentUser(authentication), spaceId, typeId, fieldId, config, options,
            request.aggregateVersion(), requestId()
        ));
    }

    @PostMapping("/types/{typeId}/fields/{fieldId}:disable")
    public FieldView disable(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID fieldId,
        @Valid @RequestBody LifecycleFieldRequest request,
        Authentication authentication
    ) {
        return transition(spaceId, typeId, fieldId, "disabled", request, authentication);
    }

    @PostMapping("/types/{typeId}/fields/{fieldId}:restore")
    public FieldView restore(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID fieldId,
        @Valid @RequestBody LifecycleFieldRequest request,
        Authentication authentication
    ) {
        return transition(spaceId, typeId, fieldId, "active", request, authentication);
    }

    @PostMapping("/types/{typeId}/fields/{fieldId}:retire")
    public FieldView retire(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        @PathVariable UUID fieldId,
        @Valid @RequestBody LifecycleFieldRequest request,
        Authentication authentication
    ) {
        return transition(spaceId, typeId, fieldId, "retired", request, authentication);
    }

    private FieldView transition(
        UUID spaceId,
        UUID typeId,
        UUID fieldId,
        String targetStatus,
        LifecycleFieldRequest request,
        Authentication authentication
    ) {
        return WorkItemFieldApiDtos.configuredField(service.transition(
            currentUser(authentication), spaceId, typeId, fieldId, targetStatus,
            request.aggregateVersion(), requestId()
        ));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private String requestId() {
        return RequestBoundaryContext.current().requestId();
    }

    public record CreateFieldRequest(
        @NotBlank String fieldKey,
        @NotBlank String name,
        String description,
        @NotBlank String fieldType,
        JsonNode config,
        @PositiveOrZero Integer sortOrder
    ) {
    }

    public record UpdateFieldRequest(
        @NotBlank String name,
        String description,
        JsonNode config,
        @PositiveOrZero long aggregateVersion
    ) {
    }

    public record ReorderFieldsRequest(@NotEmpty List<@Valid ReorderFieldEntry> items) {
    }

    public record ReorderFieldEntry(
        @NotNull UUID fieldId,
        @PositiveOrZero int sortOrder,
        @PositiveOrZero long aggregateVersion
    ) {
    }

    public record LifecycleFieldRequest(@PositiveOrZero long aggregateVersion) {
    }

    public record ConfigureFieldRequest(
        int schemaVersion,
        boolean required,
        JsonNode defaultValue,
        @NotNull List<@Valid ValidationRuleRequest> validationRules,
        JsonNode typeConfig,
        @NotNull List<@Valid FieldOptionRequest> options,
        @PositiveOrZero long aggregateVersion
    ) {
    }

    public record ValidationRuleRequest(
        @NotBlank String ruleKey,
        @NotBlank String kind,
        int schemaVersion,
        @NotNull JsonNode config
    ) {
    }

    public record FieldOptionRequest(
        @NotBlank String optionKey,
        @NotBlank String name,
        @NotBlank String color,
        @PositiveOrZero int sortOrder,
        @NotBlank String status
    ) {
    }
}
