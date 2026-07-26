package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.modules.project.domain.WorkItemConfigurationModels.WorkItemConfigurationException;
import com.colla.platform.modules.project.domain.WorkItemLayoutModels.WorkItemLayoutException;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.application.WorkItemCompatibilityService.LegacyWriteClosedException;
import com.colla.platform.shared.errors.ApiErrorResponse;
import com.colla.platform.shared.errors.ApiErrorResponse.ApiError;
import com.colla.platform.shared.request.RequestBoundaryContext;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
    WorkItemTypeConfigurationController.class,
    WorkItemConfigurationDraftController.class,
    WorkItemConfigurationPublicationController.class,
    WorkItemConfigurationTemplateController.class,
    WorkItemFieldConfigurationController.class,
    WorkItemLayoutConfigurationController.class,
    WorkItemLayoutWorkbenchController.class,
    WorkItemLayoutAccessController.class,
    UserWorkItemTypeController.class,
    AdminProjectSpaceController.class,
    UserWorkItemController.class,
    UserWorkItemStateFlowController.class,
    UserWorkItemNodeWorkflowController.class,
    UserWorkItemStateBackfillController.class,
    UserWorkItemNodeBackfillController.class,
    WorkItemCompatibilityController.class,
    ProjectController.class
})
public class WorkItemTypeExceptionHandler {
    @ExceptionHandler(WorkItemTypeException.class)
    public ResponseEntity<ApiErrorResponse> handle(WorkItemTypeException exception) {
        String sourceCode = exception.code();
        String code = apiCode(sourceCode);
        return ResponseEntity.status(status(sourceCode)).body(response(code, exception.getMessage()));
    }

    @ExceptionHandler(WorkItemFieldException.class)
    public ResponseEntity<ApiErrorResponse> handleField(WorkItemFieldException exception) {
        String sourceCode = exception.code();
        return ResponseEntity.status(status(sourceCode)).body(response(apiCode(sourceCode), exception.getMessage()));
    }

    @ExceptionHandler(WorkItemLayoutException.class)
    public ResponseEntity<ApiErrorResponse> handleLayout(WorkItemLayoutException exception) {
        String sourceCode = exception.code();
        return ResponseEntity.status(status(sourceCode)).body(response(apiCode(sourceCode), exception.getMessage()));
    }

    @ExceptionHandler(WorkItemConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleConfiguration(WorkItemConfigurationException exception) {
        String sourceCode = exception.code();
        return ResponseEntity.status(status(sourceCode)).body(response(apiCode(sourceCode), exception.getMessage()));
    }

    @ExceptionHandler(WorkItemRuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(WorkItemRuntimeException exception) {
        String sourceCode = exception.code();
        return ResponseEntity.status(status(sourceCode)).body(response(apiCode(sourceCode), exception.getMessage()));
    }

    @ExceptionHandler(LegacyWriteClosedException.class)
    public ResponseEntity<ApiErrorResponse> handleLegacyWriteClosed(LegacyWriteClosedException exception) {
        return ResponseEntity.status(HttpStatus.GONE)
            .header(HttpHeaders.LOCATION, exception.canonicalLocation())
            .body(response("legacy_write_closed", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .orElse("Invalid request");
        return ResponseEntity.badRequest().body(response("invalid_input", message));
    }

    private ApiErrorResponse response(String code, String message) {
        return new ApiErrorResponse(
            new ApiError(code, message),
            RequestBoundaryContext.current().requestId()
        );
    }

    private String apiCode(String sourceCode) {
        return switch (sourceCode) {
            case "TYPE_NOT_FOUND", "FIELD_NOT_FOUND", "LAYOUT_NOT_FOUND", "LAYOUT_NODE_NOT_FOUND",
                 "SPACE_NOT_FOUND", "NOT_FOUND_OR_HIDDEN" -> "not_found_or_hidden";
            case "TYPE_KEY_CONFLICT" -> "type_key_conflict";
            case "FIELD_KEY_CONFLICT" -> "field_key_conflict";
            case "VERSION_CONFLICT", "FIELD_VERSION_CONFLICT", "LAYOUT_VERSION_CONFLICT" -> "version_conflict";
            case "DRAFT_VERSION_CONFLICT" -> "draft_version_conflict";
            case "SYSTEM_TYPE_PROTECTED" -> "system_type_protected";
            case "SYSTEM_FIELD_PROTECTED" -> "system_field_protected";
            case "RETIRED_TYPE", "INVALID_LIFECYCLE_TRANSITION" -> "retired_type";
            case "RETIRED_FIELD", "INVALID_FIELD_LIFECYCLE_TRANSITION" -> "retired_field";
            case "INVALID_TYPE_KEY" -> "invalid_type_key";
            default -> sourceCode.toLowerCase(Locale.ROOT);
        };
    }

    private HttpStatus status(String code) {
        return switch (code) {
            case "TYPE_NOT_FOUND", "FIELD_NOT_FOUND", "LAYOUT_NOT_FOUND", "LAYOUT_NODE_NOT_FOUND",
                 "SPACE_NOT_FOUND", "NOT_FOUND_OR_HIDDEN" -> HttpStatus.NOT_FOUND;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "WORKFLOW_GUARD_REJECTED", "WORKFLOW_REQUIRED_FIELDS_MISSING",
                 "WORKFLOW_ACTION_UNAVAILABLE", "WORKFLOW_STATE_MAPPING_REQUIRED",
                 "NODE_ACTION_UNAVAILABLE", "NODE_RECOVERY_UNAVAILABLE",
                 "NODE_RECOVERY_SOURCE_MISMATCH", "NODE_RECOVERY_TARGET_MISSING",
                 "NODE_COMPENSATION_ACTION_UNREGISTERED", "NODE_UPGRADE_BLOCKED",
                 "NODE_UPGRADE_MAPPING_REQUIRED", "NODE_UPGRADE_MAPPING_INVALID",
                 "NODE_BACKFILL_ENTRY_INVALID" ->
                HttpStatus.UNPROCESSABLE_ENTITY;
            case "INVALID_TYPE_KEY", "INVALID_NAME", "INVALID_ICON", "INVALID_DESCRIPTION",
                 "INVALID_SORT_ORDER", "INVALID_STATUS", "INVALID_REORDER", "INVALID_REQUEST_ID",
                 "FIELD_TYPE_UNSUPPORTED", "INVALID_FIELD_CONFIGURATION", "INVALID_FIELD_KEY",
                 "INVALID_FIELD_NAME", "INVALID_FIELD_DESCRIPTION", "INVALID_FIELD_SORT_ORDER",
                 "INVALID_FIELD_STATUS", "INVALID_FIELD_REORDER", "INVALID_FIELD_SCOPE",
                 "INVALID_FIELD_OPTION", "INVALID_DEFAULT_VALUE", "INVALID_VALIDATION_RULE",
                 "INVALID_COMPLEX_FIELD_CONFIGURATION", "INVALID_COMPLEX_FIELD_REFERENCE",
                 "INVALID_LAYOUT_KIND", "INVALID_LAYOUT_NODE", "INVALID_LAYOUT_NODE_KEY",
                 "DUPLICATE_LAYOUT_NODE", "INVALID_LAYOUT_TREE", "LAYOUT_NODE_LIMIT",
                 "LAYOUT_COLUMN_LIMIT", "LAYOUT_DEPTH_LIMIT", "INVALID_LAYOUT_CONDITION",
                 "LAYOUT_CONDITION_LIMIT", "INVALID_LAYOUT_CONDITION_REFERENCE",
                 "INVALID_LAYOUT_CONDITION_OPERATOR", "INVALID_LAYOUT_CONDITION_VALUE",
                 "LAYOUT_CONDITION_HIDDEN_DEPENDENCY", "LAYOUT_CONDITION_CYCLE",
                 "INVALID_LAYOUT_FIELD_REFERENCE", "INVALID_FIELD_ACCESS_POLICY",
                 "DUPLICATE_FIELD_ACCESS_POLICY", "DUPLICATE_FIELD_ACCESS_POLICY_RULE",
                 "CONFLICTING_FIELD_ACCESS_POLICY_RULE", "FIELD_ACCESS_POLICY_LIMIT",
                 "INVALID_FIELD_ACCESS_POLICY_REFERENCE", "INVALID_FIELD_ACCESS_POLICY_OPERATOR",
                 "INVALID_FIELD_ACCESS_CONTEXT", "LAYOUT_POLICY_LIMIT",
                 "INVALID_LAYOUT_VERSION", "INVALID_LAYOUT_GRAPH", "INVALID_LAYOUT_COMMAND",
                 "IMMUTABLE_LAYOUT_NODE_IDENTITY", "LAYOUT_COPY_FIELD_DUPLICATE",
                 "INVALID_INPUT", "INVALID_WORK_ITEM_STATUS", "INVALID_WORK_ITEM_TITLE",
                 "INVALID_FIELD_VALUES", "INVALID_FIELD_VALUE", "INVALID_FIELD_REFERENCE",
                 "FIELD_VALIDATION_FAILED", "REQUIRED_FIELD_MISSING", "INVALID_QUERY_VALUE",
                 "QUERY_CAPABILITY_UNAVAILABLE", "INVALID_PARTICIPANT_ROLE",
                 "INVALID_RECOVERY_REASON", "DANGEROUS_CONFIRMATION_REQUIRED",
                 "INVALID_BACKFILL_MANIFEST", "INVALID_NODE_VOTE",
                 "NODE_TASK_ASSIGNEE_INVALID" ->
                HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
    }
}
