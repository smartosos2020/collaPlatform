package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.domain.WorkItemFieldModels.WorkItemFieldException;
import com.colla.platform.shared.errors.ApiErrorResponse;
import com.colla.platform.shared.errors.ApiErrorResponse.ApiError;
import com.colla.platform.shared.request.RequestBoundaryContext;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
    WorkItemTypeConfigurationController.class,
    WorkItemFieldConfigurationController.class,
    UserWorkItemTypeController.class,
    AdminProjectSpaceController.class
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
            case "TYPE_NOT_FOUND", "FIELD_NOT_FOUND", "SPACE_NOT_FOUND", "NOT_FOUND_OR_HIDDEN" -> "not_found_or_hidden";
            case "TYPE_KEY_CONFLICT" -> "type_key_conflict";
            case "FIELD_KEY_CONFLICT" -> "field_key_conflict";
            case "VERSION_CONFLICT", "FIELD_VERSION_CONFLICT" -> "version_conflict";
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
            case "TYPE_NOT_FOUND", "FIELD_NOT_FOUND", "SPACE_NOT_FOUND", "NOT_FOUND_OR_HIDDEN" -> HttpStatus.NOT_FOUND;
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "INVALID_TYPE_KEY", "INVALID_NAME", "INVALID_ICON", "INVALID_DESCRIPTION",
                 "INVALID_SORT_ORDER", "INVALID_STATUS", "INVALID_REORDER", "INVALID_REQUEST_ID",
                 "FIELD_TYPE_UNSUPPORTED", "INVALID_FIELD_CONFIGURATION", "INVALID_FIELD_KEY",
                 "INVALID_FIELD_NAME", "INVALID_FIELD_DESCRIPTION", "INVALID_FIELD_SORT_ORDER",
                 "INVALID_FIELD_STATUS", "INVALID_FIELD_REORDER", "INVALID_FIELD_SCOPE",
                 "INVALID_FIELD_OPTION", "INVALID_DEFAULT_VALUE", "INVALID_VALIDATION_RULE",
                 "INVALID_COMPLEX_FIELD_CONFIGURATION", "INVALID_COMPLEX_FIELD_REFERENCE",
                 "INVALID_INPUT" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
    }
}
