package com.colla.platform.modules.base.api;

import com.colla.platform.modules.base.application.BaseService;
import com.colla.platform.modules.base.domain.BaseModels.BaseDetail;
import com.colla.platform.modules.base.domain.BaseModels.BaseFilter;
import com.colla.platform.modules.base.domain.BaseModels.BaseCalendarView;
import com.colla.platform.modules.base.domain.BaseModels.BaseImportResult;
import com.colla.platform.modules.base.domain.BaseModels.BaseKanbanView;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecord;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecordDetail;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecordPage;
import com.colla.platform.modules.base.domain.BaseModels.BaseSort;
import com.colla.platform.modules.base.domain.BaseModels.BaseSummary;
import com.colla.platform.modules.base.domain.BaseModels.BaseTableDetail;
import com.colla.platform.modules.base.domain.BaseModels.BaseView;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BaseController {
    private final BaseService baseService;

    public BaseController(BaseService baseService) {
        this.baseService = baseService;
    }

    @GetMapping("/bases")
    public List<BaseSummary> bases(Authentication authentication) {
        return baseService.listBases(currentUser(authentication));
    }

    @PostMapping("/bases")
    public BaseDetail createBase(@Valid @RequestBody CreateBaseRequest request, Authentication authentication) {
        return baseService.createBase(currentUser(authentication), request.name(), request.description());
    }

    @GetMapping("/bases/{baseId}")
    public BaseDetail base(@PathVariable UUID baseId, Authentication authentication) {
        return baseService.getBase(currentUser(authentication), baseId);
    }

    @PatchMapping("/bases/{baseId}")
    public BaseDetail updateBase(@PathVariable UUID baseId, @Valid @RequestBody UpdateBaseRequest request, Authentication authentication) {
        return baseService.updateBase(currentUser(authentication), baseId, request.name(), request.description());
    }

    @PostMapping("/bases/{baseId}/members")
    public BaseDetail grantPermission(@PathVariable UUID baseId, @Valid @RequestBody GrantPermissionRequest request, Authentication authentication) {
        return baseService.grantPermission(currentUser(authentication), baseId, request.userId(), request.permissionLevel());
    }

    @PostMapping("/bases/{baseId}/tables")
    public BaseTableDetail createTable(@PathVariable UUID baseId, @Valid @RequestBody CreateTableRequest request, Authentication authentication) {
        return baseService.createTable(currentUser(authentication), baseId, request.name());
    }

    @GetMapping("/bases/{baseId}/tables/{tableId}")
    public BaseTableDetail table(@PathVariable UUID baseId, @PathVariable UUID tableId, Authentication authentication) {
        return baseService.getTable(currentUser(authentication), baseId, tableId);
    }

    @PatchMapping("/bases/{baseId}/tables/{tableId}")
    public BaseTableDetail updateTable(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @Valid @RequestBody UpdateTableRequest request,
        Authentication authentication
    ) {
        return baseService.updateTable(currentUser(authentication), baseId, tableId, request.name());
    }

    @PostMapping("/bases/{baseId}/tables/{tableId}/fields")
    public BaseTableDetail createField(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @Valid @RequestBody CreateFieldRequest request,
        Authentication authentication
    ) {
        return baseService.createField(
            currentUser(authentication),
            baseId,
            tableId,
            request.name(),
            request.fieldType(),
            request.config(),
            request.required()
        );
    }

    @GetMapping("/bases/{baseId}/tables/{tableId}/records")
    public BaseRecordPage records(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset,
        Authentication authentication
    ) {
        return baseService.listRecords(currentUser(authentication), baseId, tableId, List.of(), List.of(), limit, offset);
    }

    @GetMapping(value = "/bases/{baseId}/tables/{tableId}/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(@PathVariable UUID baseId, @PathVariable UUID tableId, Authentication authentication) {
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=base-export.csv")
            .body(baseService.exportCsv(currentUser(authentication), baseId, tableId));
    }

    @PostMapping("/bases/{baseId}/tables/{tableId}/import.csv")
    public BaseImportResult importCsv(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @RequestBody ImportCsvRequest request,
        Authentication authentication
    ) {
        return baseService.importCsv(currentUser(authentication), baseId, tableId, request.csv());
    }

    @PostMapping("/bases/{baseId}/tables/{tableId}/records/query")
    public BaseRecordPage queryRecords(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @RequestBody(required = false) QueryRecordsRequest request,
        Authentication authentication
    ) {
        QueryRecordsRequest query = request == null ? new QueryRecordsRequest(List.of(), List.of(), 50, 0) : request;
        return baseService.listRecords(
            currentUser(authentication),
            baseId,
            tableId,
            query.filters(),
            query.sorts(),
            query.limit() == null ? 50 : query.limit(),
            query.offset() == null ? 0 : query.offset()
        );
    }

    @GetMapping("/bases/{baseId}/tables/{tableId}/views/kanban")
    public BaseKanbanView kanbanView(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @RequestParam UUID groupFieldId,
        Authentication authentication
    ) {
        return baseService.kanbanView(currentUser(authentication), baseId, tableId, groupFieldId);
    }

    @GetMapping("/bases/{baseId}/tables/{tableId}/views/calendar")
    public BaseCalendarView calendarView(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @RequestParam UUID dateFieldId,
        Authentication authentication
    ) {
        return baseService.calendarView(currentUser(authentication), baseId, tableId, dateFieldId);
    }

    @PostMapping("/bases/{baseId}/tables/{tableId}/records")
    public BaseRecord createRecord(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @RequestBody(required = false) RecordValuesRequest request,
        Authentication authentication
    ) {
        return baseService.createRecord(currentUser(authentication), baseId, tableId, request == null ? Map.of() : request.values());
    }

    @PatchMapping("/bases/{baseId}/tables/{tableId}/records/{recordId}")
    public BaseRecord updateRecord(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @PathVariable UUID recordId,
        @RequestBody(required = false) RecordValuesRequest request,
        Authentication authentication
    ) {
        return baseService.updateRecord(currentUser(authentication), baseId, tableId, recordId, request == null ? Map.of() : request.values());
    }

    @DeleteMapping("/bases/{baseId}/tables/{tableId}/records/{recordId}")
    public void deleteRecord(@PathVariable UUID baseId, @PathVariable UUID tableId, @PathVariable UUID recordId, Authentication authentication) {
        baseService.deleteRecord(currentUser(authentication), baseId, tableId, recordId);
    }

    @PostMapping("/bases/{baseId}/tables/{tableId}/views")
    public BaseView createView(
        @PathVariable UUID baseId,
        @PathVariable UUID tableId,
        @Valid @RequestBody CreateViewRequest request,
        Authentication authentication
    ) {
        return baseService.createView(currentUser(authentication), baseId, tableId, request.name(), request.filters(), request.sorts(), request.visibleFieldIds());
    }

    @GetMapping("/base-records/{recordId}")
    public BaseRecord record(@PathVariable UUID recordId, Authentication authentication) {
        return baseService.getRecord(currentUser(authentication), recordId);
    }

    @GetMapping("/base-records/{recordId}/detail")
    public BaseRecordDetail recordDetail(@PathVariable UUID recordId, Authentication authentication) {
        return baseService.getRecordDetail(currentUser(authentication), recordId);
    }

    @PostMapping("/base-records/{recordId}/comments")
    public BaseRecordDetail addRecordComment(
        @PathVariable UUID recordId,
        @Valid @RequestBody CommentRequest request,
        Authentication authentication
    ) {
        return baseService.addRecordComment(currentUser(authentication), recordId, request.content());
    }

    @PostMapping("/base-records/{recordId}/relations")
    public BaseRecordDetail addRecordRelation(
        @PathVariable UUID recordId,
        @Valid @RequestBody RecordRelationRequest request,
        Authentication authentication
    ) {
        return baseService.addRecordRelation(currentUser(authentication), recordId, request.targetType(), request.targetId());
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateBaseRequest(@NotBlank @Size(max = 128) String name, String description) {
    }

    public record UpdateBaseRequest(@Size(max = 128) String name, String description) {
    }

    public record GrantPermissionRequest(UUID userId, @NotBlank String permissionLevel) {
    }

    public record CreateTableRequest(@NotBlank @Size(max = 128) String name) {
    }

    public record UpdateTableRequest(@Size(max = 128) String name) {
    }

    public record CreateFieldRequest(
        @NotBlank @Size(max = 128) String name,
        @NotBlank String fieldType,
        Map<String, Object> config,
        boolean required
    ) {
    }

    public record RecordValuesRequest(Map<String, Object> values) {
    }

    public record QueryRecordsRequest(List<BaseFilter> filters, List<BaseSort> sorts, Integer limit, Integer offset) {
    }

    public record CreateViewRequest(@NotBlank @Size(max = 128) String name, List<BaseFilter> filters, List<BaseSort> sorts, List<UUID> visibleFieldIds) {
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String content) {
    }

    public record RecordRelationRequest(@NotBlank String targetType, UUID targetId) {
    }

    public record ImportCsvRequest(@NotBlank String csv) {
    }
}
