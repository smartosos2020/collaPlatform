package com.colla.platform.modules.im.api;

import com.colla.platform.modules.knowledge.application.KnowledgeContentService;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentModels.KnowledgeContent;
import com.colla.platform.modules.im.application.ImService;
import com.colla.platform.modules.im.api.UserImDtos.UserConversationDetailView;
import com.colla.platform.modules.im.api.UserImDtos.UserConversationView;
import com.colla.platform.modules.im.api.UserImDtos.UserMessagePageView;
import com.colla.platform.modules.im.api.UserImDtos.UserMessageView;
import com.colla.platform.modules.im.domain.ImModels.ConversationDetail;
import com.colla.platform.modules.im.domain.ImModels.ConversationMember;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.im.domain.ImModels.MessagePage;
import com.colla.platform.modules.im.domain.ImModels.MessageSummary;
import com.colla.platform.modules.im.domain.ImModels.UnreadState;
import com.colla.platform.modules.project.application.ProjectService;
import com.colla.platform.modules.project.domain.ProjectModels.IssueDetail;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/conversations")
public class ImController {
    private final ImService imService;
    private final ProjectService projectService;
    private final KnowledgeContentService contentService;

    public ImController(ImService imService, ProjectService projectService, KnowledgeContentService contentService) {
        this.imService = imService;
        this.projectService = projectService;
        this.contentService = contentService;
    }

    @GetMapping
    public List<UserConversationView> list(Authentication authentication) {
        return imService.listConversations(currentUser(authentication)).stream()
            .map(UserImDtos::conversation)
            .toList();
    }

    @PostMapping
    public UserConversationDetailView create(@Valid @RequestBody CreateConversationRequest request, Authentication authentication) {
        return UserImDtos.conversationDetail(imService.createConversation(
            currentUser(authentication),
            request.conversationType(),
            request.title(),
            request.memberIds()
        ));
    }

    @GetMapping("/{conversationId}")
    public UserConversationDetailView detail(@PathVariable UUID conversationId, Authentication authentication) {
        return UserImDtos.conversationDetail(imService.getConversation(currentUser(authentication), conversationId));
    }

    @GetMapping("/{conversationId}/members")
    public List<ConversationMember> members(@PathVariable UUID conversationId, Authentication authentication) {
        return imService.getConversation(currentUser(authentication), conversationId).members();
    }

    @PostMapping("/{conversationId}/members")
    public UserConversationDetailView addMembers(
        @PathVariable UUID conversationId,
        @Valid @RequestBody AddMembersRequest request,
        Authentication authentication
    ) {
        return UserImDtos.conversationDetail(imService.addMembers(currentUser(authentication), conversationId, request.memberIds()));
    }

    @DeleteMapping("/{conversationId}/members/{memberId}")
    public UserConversationDetailView removeMember(
        @PathVariable UUID conversationId,
        @PathVariable UUID memberId,
        Authentication authentication
    ) {
        return UserImDtos.conversationDetail(imService.removeMember(currentUser(authentication), conversationId, memberId));
    }

    @PostMapping("/{conversationId}/leave")
    public void leave(@PathVariable UUID conversationId, Authentication authentication) {
        imService.leaveConversation(currentUser(authentication), conversationId);
    }

    @PostMapping("/{conversationId}/close")
    public void close(@PathVariable UUID conversationId, Authentication authentication) {
        imService.closeDirectConversation(currentUser(authentication), conversationId);
    }

    @PostMapping("/{conversationId}/mute")
    public UserConversationDetailView mute(
        @PathVariable UUID conversationId,
        @RequestBody ConversationMutedRequest request,
        Authentication authentication
    ) {
        return UserImDtos.conversationDetail(imService.setConversationMuted(currentUser(authentication), conversationId, request.muted()));
    }

    @PostMapping("/{conversationId}/pin")
    public UserConversationDetailView pin(
        @PathVariable UUID conversationId,
        @RequestBody PinConversationRequest request,
        Authentication authentication
    ) {
        return UserImDtos.conversationDetail(imService.setConversationPinned(currentUser(authentication), conversationId, request.pinned()));
    }

    @GetMapping("/{conversationId}/messages")
    public UserMessagePageView messages(
        @PathVariable UUID conversationId,
        @RequestParam(required = false) UUID beforeId,
        @RequestParam(required = false) Long afterSeq,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        return UserImDtos.messagePage(imService.listMessages(currentUser(authentication), conversationId, beforeId, afterSeq, limit));
    }

    @GetMapping("/{conversationId}/messages/{messageId}/context")
    public UserMessagePageView messageContext(
        @PathVariable UUID conversationId,
        @PathVariable UUID messageId,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        return UserImDtos.messagePage(imService.listMessageContext(currentUser(authentication), conversationId, messageId, limit));
    }

    @GetMapping("/{conversationId}/messages/search")
    public UserMessagePageView searchMessages(
        @PathVariable UUID conversationId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String targetType,
        @RequestParam(defaultValue = "20") int limit,
        Authentication authentication
    ) {
        return UserImDtos.messagePage(imService.searchMessages(currentUser(authentication), conversationId, q, targetType, limit));
    }

    @PostMapping("/{conversationId}/messages")
    public UserMessageView sendMessage(
        @PathVariable UUID conversationId,
        @Valid @RequestBody SendMessageRequest request,
        Authentication authentication
    ) {
        return UserImDtos.message(imService.sendMessage(
            currentUser(authentication),
            conversationId,
            request.clientMessageId(),
            request.messageType(),
            request.content()
        ));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/convert-to-issue")
    public IssueDetail convertMessageToIssue(
        @PathVariable UUID conversationId,
        @PathVariable UUID messageId,
        @Valid @RequestBody ConvertMessageToIssueRequest request,
        Authentication authentication
    ) {
        return projectService.createIssueFromMessage(
            currentUser(authentication),
            request.projectId(),
            conversationId,
            messageId,
            request.issueType(),
            request.title(),
            request.description(),
            request.priority(),
            request.assigneeId(),
            request.dueAt()
        );
    }

    @PostMapping("/{conversationId}/messages/{messageId}/convert-to-knowledge-content")
    public KnowledgeContent convertMessageToKnowledgeContent(
        @PathVariable UUID conversationId,
        @PathVariable UUID messageId,
        @Valid @RequestBody ConvertMessageToKnowledgeContentRequest request,
        Authentication authentication
    ) {
        CurrentUser currentUser = currentUser(authentication);
        MessageSummary message = imService.getMessage(currentUser, conversationId, messageId);
        KnowledgeContent document = contentService.createItem(
            currentUser,
            request.parentId(),
            request.title() == null || request.title().isBlank() ? defaultKnowledgeContentTitleFromMessage(message) : request.title(),
            "markdown",
            knowledgeContentFromMessage(message),
            null,
            null,
            "view",
            false
        );
        return contentService.addRelation(currentUser, document.item().id(), "message", messageId);
    }

    @PatchMapping("/{conversationId}/messages/{messageId}")
    public UserMessageView editMessage(
        @PathVariable UUID conversationId,
        @PathVariable UUID messageId,
        @Valid @RequestBody EditMessageRequest request,
        Authentication authentication
    ) {
        return UserImDtos.message(imService.editMessage(currentUser(authentication), conversationId, messageId, request.content()));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/revoke")
    public UserMessageView revokeMessage(@PathVariable UUID conversationId, @PathVariable UUID messageId, Authentication authentication) {
        return UserImDtos.message(imService.revokeMessage(currentUser(authentication), conversationId, messageId));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/pin")
    public UserMessageView pinMessage(
        @PathVariable UUID conversationId,
        @PathVariable UUID messageId,
        @RequestBody PinMessageRequest request,
        Authentication authentication
    ) {
        return UserImDtos.message(imService.pinMessage(currentUser(authentication), conversationId, messageId, request.pinned()));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/reactions")
    public UserMessageView toggleReaction(
        @PathVariable UUID conversationId,
        @PathVariable UUID messageId,
        @Valid @RequestBody ToggleReactionRequest request,
        Authentication authentication
    ) {
        return UserImDtos.message(imService.toggleReaction(currentUser(authentication), conversationId, messageId, request.emoji()));
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

    public record ConversationMutedRequest(boolean muted) {
    }

    public record PinConversationRequest(boolean pinned) {
    }

    public record SendMessageRequest(
        String clientMessageId,
        String messageType,
        @NotBlank String content
    ) {
    }

    public record ConvertMessageToIssueRequest(
        UUID projectId,
        String issueType,
        @Size(max = 255) String title,
        String description,
        String priority,
        UUID assigneeId,
        LocalDate dueAt
    ) {
    }

    public record ConvertMessageToKnowledgeContentRequest(UUID parentId, @Size(max = 255) String title) {
    }

    private String defaultKnowledgeContentTitleFromMessage(MessageSummary message) {
        String normalized = message.content() == null ? "" : message.content().replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return message.senderName() + " 的消息记录";
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80);
    }

    private String knowledgeContentFromMessage(MessageSummary message) {
        String content = message.content() == null ? "" : message.content().trim();
        String quoted = content.isBlank() ? "> 空消息" : "> " + content.replace("\n", "\n> ");
        return String.join(
            "\n\n",
            "# " + defaultKnowledgeContentTitleFromMessage(message),
            "来源消息：" + message.senderName() + " · " + message.createdAt() + " /im?conversationId=" + message.conversationId() + "&messageId=" + message.id(),
            quoted,
            "::object-card{\"objectType\":\"message\",\"objectId\":\"" + message.id() + "\"}"
        );
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
