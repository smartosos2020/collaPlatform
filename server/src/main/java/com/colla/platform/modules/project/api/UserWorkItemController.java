package com.colla.platform.modules.project.api;

import com.colla.platform.modules.project.api.WorkItemApiDtos.WorkItemPageResponse;
import com.colla.platform.modules.project.api.WorkItemApiDtos.WorkItemResponse;
import com.colla.platform.modules.project.api.WorkItemApiDtos.ActivityPageResponse;
import com.colla.platform.modules.project.api.WorkItemApiDtos.ParticipantListResponse;
import com.colla.platform.modules.project.api.WorkItemApiDtos.ParticipantMutationResponse;
import com.colla.platform.modules.project.api.WorkItemApiDtos.AttachmentListResponse;
import com.colla.platform.modules.project.api.WorkItemApiDtos.AttachmentMutationResponse;
import com.colla.platform.modules.project.api.WorkItemApiDtos.CommentListResponse;
import com.colla.platform.modules.project.api.WorkItemApiDtos.CommentMutationResponse;
import com.colla.platform.modules.project.api.WorkItemApiDtos.WorkItemCreateFormResponse;
import com.colla.platform.modules.project.application.WorkItemService;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.request.RequestBoundaryContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-spaces/{spaceId}/work-items")
public class UserWorkItemController {
    private final WorkItemService service;
    private final ObjectMapper objectMapper;

    public UserWorkItemController(WorkItemService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public WorkItemPageResponse list(
        @PathVariable UUID spaceId,
        @RequestParam(required = false) UUID typeId,
        @RequestParam(required = false) UUID cursor,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        return WorkItemApiDtos.page(service.list(currentUser(authentication), spaceId, typeId, cursor, limit));
    }

    @PostMapping
    public WorkItemResponse create(
        @PathVariable UUID spaceId,
        @Valid @RequestBody CreateWorkItemRequest request,
        Authentication authentication
    ) {
        return WorkItemApiDtos.response(service.create(
            currentUser(authentication),
            spaceId,
            request.typeId(),
            request.title(),
            request.fieldValues(),
            requestId()
        ));
    }

    @GetMapping("/types/{typeId}/create-form")
    public WorkItemCreateFormResponse createForm(
        @PathVariable UUID spaceId,
        @PathVariable UUID typeId,
        Authentication authentication
    ) {
        return WorkItemApiDtos.createForm(
            service.createForm(currentUser(authentication), spaceId, typeId)
        );
    }

    @GetMapping("/{workItemId}")
    public WorkItemResponse get(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        Authentication authentication
    ) {
        return WorkItemApiDtos.response(service.get(currentUser(authentication), spaceId, workItemId));
    }

    @GetMapping("/query")
    public WorkItemPageResponse query(
        @PathVariable UUID spaceId,
        @RequestParam UUID typeId,
        @RequestParam String fieldKey,
        @RequestParam(defaultValue = "eq") String operator,
        @RequestParam String value,
        @RequestParam(defaultValue = "desc") String sortDirection,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        JsonNode queryValue;
        try {
            queryValue = objectMapper.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw com.colla.platform.modules.project.domain.WorkItemModels.failure(
                "INVALID_QUERY_VALUE",
                "Query value must be valid JSON",
                exception
            );
        }
        return WorkItemApiDtos.page(service.query(
            currentUser(authentication),
            spaceId,
            typeId,
            fieldKey,
            operator,
            queryValue,
            sortDirection,
            limit
        ));
    }

    @GetMapping("/{workItemId}/participants")
    public ParticipantListResponse participants(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        Authentication authentication
    ) {
        return WorkItemApiDtos.participants(service.listParticipants(
            currentUser(authentication), spaceId, workItemId
        ));
    }

    @PutMapping("/{workItemId}/participants/{participantUserId}")
    public ParticipantMutationResponse putParticipant(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @PathVariable UUID participantUserId,
        @Valid @RequestBody ParticipantMutationRequest request,
        Authentication authentication
    ) {
        return WorkItemApiDtos.participantState(service.changeParticipant(
            currentUser(authentication),
            spaceId,
            workItemId,
            participantUserId,
            request.role(),
            false,
            request.expectedVersion(),
            requestId()
        ));
    }

    @DeleteMapping("/{workItemId}/participants/{participantUserId}")
    public ParticipantMutationResponse removeParticipant(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @PathVariable UUID participantUserId,
        @Valid @RequestBody ParticipantMutationRequest request,
        Authentication authentication
    ) {
        return WorkItemApiDtos.participantState(service.changeParticipant(
            currentUser(authentication),
            spaceId,
            workItemId,
            participantUserId,
            request.role(),
            true,
            request.expectedVersion(),
            requestId()
        ));
    }

    @GetMapping("/{workItemId}/activities")
    public ActivityPageResponse activities(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @RequestParam(required = false) Long beforeSequence,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        return WorkItemApiDtos.activities(service.listActivities(
            currentUser(authentication), spaceId, workItemId, beforeSequence, limit
        ));
    }

    @GetMapping("/{workItemId}/comments")
    public CommentListResponse comments(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        Authentication authentication
    ) {
        return WorkItemApiDtos.comments(
            service.listComments(currentUser(authentication), spaceId, workItemId)
        );
    }

    @PostMapping("/{workItemId}/comments")
    public CommentMutationResponse addComment(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @Valid @RequestBody AddCommentRequest request,
        Authentication authentication
    ) {
        return WorkItemApiDtos.commentState(service.addComment(
            currentUser(authentication),
            spaceId,
            workItemId,
            request.content(),
            request.expectedVersion(),
            requestId()
        ));
    }

    @GetMapping("/{workItemId}/attachments")
    public AttachmentListResponse attachments(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        Authentication authentication
    ) {
        return WorkItemApiDtos.attachments(
            service.listAttachments(currentUser(authentication), spaceId, workItemId)
        );
    }

    @PostMapping("/{workItemId}/attachments")
    public AttachmentMutationResponse addAttachment(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @Valid @RequestBody AddAttachmentRequest request,
        Authentication authentication
    ) {
        return WorkItemApiDtos.attachmentState(service.addAttachment(
            currentUser(authentication),
            spaceId,
            workItemId,
            request.fileId(),
            request.expectedVersion(),
            requestId()
        ));
    }

    @PatchMapping("/{workItemId}")
    public WorkItemResponse update(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @Valid @RequestBody UpdateWorkItemRequest request,
        Authentication authentication
    ) {
        return WorkItemApiDtos.response(service.update(
            currentUser(authentication),
            spaceId,
            workItemId,
            request.title(),
            request.fieldValues(),
            request.expectedVersion(),
            requestId()
        ));
    }

    @PostMapping("/{workItemId}:archive")
    public WorkItemResponse archive(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @Valid @RequestBody WorkItemTransitionRequest request,
        Authentication authentication
    ) {
        return WorkItemApiDtos.response(service.transition(
            currentUser(authentication),
            spaceId,
            workItemId,
            "archived",
            request.expectedVersion(),
            requestId()
        ));
    }

    @PostMapping("/{workItemId}:restore")
    public WorkItemResponse restore(
        @PathVariable UUID spaceId,
        @PathVariable UUID workItemId,
        @Valid @RequestBody WorkItemTransitionRequest request,
        Authentication authentication
    ) {
        return WorkItemApiDtos.response(service.transition(
            currentUser(authentication),
            spaceId,
            workItemId,
            "active",
            request.expectedVersion(),
            requestId()
        ));
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    private String requestId() {
        return RequestBoundaryContext.current().requestId();
    }

    public record CreateWorkItemRequest(
        @NotNull UUID typeId,
        @NotBlank String title,
        JsonNode fieldValues
    ) {
    }

    public record UpdateWorkItemRequest(
        String title,
        JsonNode fieldValues,
        @PositiveOrZero long expectedVersion
    ) {
    }

    public record WorkItemTransitionRequest(@PositiveOrZero long expectedVersion) {
    }

    public record ParticipantMutationRequest(
        @NotBlank String role,
        @PositiveOrZero long expectedVersion
    ) {
    }

    public record AddCommentRequest(
        @NotBlank String content,
        @PositiveOrZero long expectedVersion
    ) {
    }

    public record AddAttachmentRequest(
        @NotNull UUID fileId,
        @PositiveOrZero long expectedVersion
    ) {
    }
}
