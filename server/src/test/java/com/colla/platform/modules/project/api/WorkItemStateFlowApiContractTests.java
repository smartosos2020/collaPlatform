package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.AvailableAction;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;

class WorkItemStateFlowApiContractTests {
    private final WorkItemTypeExceptionHandler handler = new WorkItemTypeExceptionHandler();

    @Test
    void userWorkflowRouteStaysOutsideGovernanceSurface() {
        RequestMapping mapping = UserWorkItemStateFlowController.class
            .getAnnotation(RequestMapping.class);

        assertThat(mapping.value()).containsExactly(
            "/api/project-spaces/{spaceId}/work-items/{workItemId}/workflow"
        );
        assertThat(mapping.value()[0]).doesNotContain("/api/admin/");
        RequestMapping backfill = UserWorkItemStateBackfillController.class
            .getAnnotation(RequestMapping.class);
        assertThat(backfill.value()).containsExactly(
            "/api/project-spaces/{spaceId}/workflow-backfills"
        );
        assertThat(backfill.value()[0]).doesNotContain("/api/admin/");
    }

    @Test
    void workflowErrorsHaveStable403409And422Boundaries() {
        assertThat(handler.handleRuntime(
            new WorkItemRuntimeException("FORBIDDEN", "denied")
        ).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleRuntime(
            new WorkItemRuntimeException("WORKFLOW_VERSION_CONFLICT", "stale")
        ).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleRuntime(
            new WorkItemRuntimeException("WORKFLOW_GUARD_REJECTED", "guard")
        ).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(handler.handleRuntime(
            new WorkItemRuntimeException("WORKFLOW_REQUIRED_FIELDS_MISSING", "required")
        ).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(handler.handleRuntime(
            new WorkItemRuntimeException("NOT_FOUND_OR_HIDDEN", "hidden")
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleRuntime(
            new WorkItemRuntimeException("WORKFLOW_STATE_MAPPING_REQUIRED", "map")
        ).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(handler.handleRuntime(
            new WorkItemRuntimeException("DANGEROUS_CONFIRMATION_REQUIRED", "confirm")
        ).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void availableActionCarriesServerDefinedInputRequirements() {
        assertThat(Arrays.stream(AvailableAction.class.getRecordComponents())
            .map(RecordComponent::getName))
            .contains("actionKey", "label", "kind", "requiredFieldKeys", "policyVersion");
    }
}
