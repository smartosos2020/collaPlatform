package com.colla.platform.modules.im.api;

import com.colla.platform.modules.im.application.ImService;
import com.colla.platform.modules.im.domain.ImModels.ConversationDetail;
import com.colla.platform.modules.im.domain.ImModels.ConversationMember;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.im.domain.ImModels.MessagePage;
import com.colla.platform.modules.im.domain.ImModels.MessageSummary;
import com.colla.platform.modules.im.domain.ImModels.UnreadState;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ImController {
    private final ImService imService;

    public ImController(ImService imService) {
        this.imService = imService;
    }

    @GetMapping
    public List<ConversationSummary> list(Authentication authentication) {
        return imService.listConversations(currentUser(authentication));
    }

    @PostMapping
    public ConversationDetail create(@Valid @RequestBody CreateConversationRequest request, Authentication authentication) {
        return imService.createConversation(
            currentUser(authentication),
            request.conversationType(),
            request.title(),
            request.memberIds()
        );
    }

    @GetMapping("/{conversationId}")
    public ConversationDetail detail(@PathVariable UUID conversationId, Authentication authentication) {
        return imService.getConversation(currentUser(authentication), conversationId);
    }

    @GetMapping("/{conversationId}/members")
    public List<ConversationMember> members(@PathVariable UUID conversationId, Authentication authentication) {
        return imService.getConversation(currentUser(authentication), conversationId).members();
    }

    @PostMapping("/{conversationId}/members")
    public ConversationDetail addMembers(
        @PathVariable UUID conversationId,
        @Valid @RequestBody AddMembersRequest request,
        Authentication authentication
    ) {
        return imService.addMembers(currentUser(authentication), conversationId, request.memberIds());
    }

    @GetMapping("/{conversationId}/messages")
    public MessagePage messages(
        @PathVariable UUID conversationId,
        @RequestParam(required = false) UUID beforeId,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        return imService.listMessages(currentUser(authentication), conversationId, beforeId, limit);
    }

    @PostMapping("/{conversationId}/messages")
    public MessageSummary sendMessage(
        @PathVariable UUID conversationId,
        @Valid @RequestBody SendMessageRequest request,
        Authentication authentication
    ) {
        return imService.sendMessage(
            currentUser(authentication),
            conversationId,
            request.clientMessageId(),
            request.messageType(),
            request.content()
        );
    }

    @PatchMapping("/{conversationId}/messages/{messageId}")
    public MessageSummary editMessage(
        @PathVariable UUID conversationId,
        @PathVariable UUID messageId,
        @Valid @RequestBody EditMessageRequest request,
        Authentication authentication
    ) {
        return imService.editMessage(currentUser(authentication), conversationId, messageId, request.content());
    }

    @PostMapping("/{conversationId}/messages/{messageId}/revoke")
    public MessageSummary revokeMessage(@PathVariable UUID conversationId, @PathVariable UUID messageId, Authentication authentication) {
        return imService.revokeMessage(currentUser(authentication), conversationId, messageId);
    }

    @PostMapping("/{conversationId}/messages/{messageId}/pin")
    public MessageSummary pinMessage(
        @PathVariable UUID conversationId,
        @PathVariable UUID messageId,
        @RequestBody PinMessageRequest request,
        Authentication authentication
    ) {
        return imService.pinMessage(currentUser(authentication), conversationId, messageId, request.pinned());
    }

    @PostMapping("/{conversationId}/messages/{messageId}/reactions")
    public MessageSummary toggleReaction(
        @PathVariable UUID conversationId,
        @PathVariable UUID messageId,
        @Valid @RequestBody ToggleReactionRequest request,
        Authentication authentication
    ) {
        return imService.toggleReaction(currentUser(authentication), conversationId, messageId, request.emoji());
    }

    @PostMapping("/{conversationId}/read")
    public UnreadState markRead(
        @PathVariable UUID conversationId,
        @RequestBody(required = false) MarkReadRequest request,
        Authentication authentication
    ) {
        return imService.markRead(
            currentUser(authentication),
            conversationId,
            request == null ? null : request.messageId()
        );
    }

    private CurrentUser currentUser(Authentication authentication) {
        return (CurrentUser) authentication.getPrincipal();
    }

    public record CreateConversationRequest(
        String conversationType,
        @Size(max = 255) String title,
        List<UUID> memberIds
    ) {
    }

    public record AddMembersRequest(List<UUID> memberIds) {
    }

    public record SendMessageRequest(
        String clientMessageId,
        String messageType,
        @NotBlank String content
    ) {
    }

    public record EditMessageRequest(@NotBlank String content) {
    }

    public record PinMessageRequest(boolean pinned) {
    }

    public record ToggleReactionRequest(@NotBlank String emoji) {
    }

    public record MarkReadRequest(UUID messageId) {
    }
}
