package com.colla.platform.modules.im.application;

import com.colla.platform.modules.im.contract.ProjectMessaging;
import com.colla.platform.modules.im.infrastructure.ImRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectMessagingAdapter implements ProjectMessaging {
    private final ImRepository repository;
    private final ImService imService;

    public ProjectMessagingAdapter(ImRepository repository, ImService imService) {
        this.repository = repository;
        this.imService = imService;
    }

    @Override
    public UUID createProjectConversation(UUID workspaceId, UUID actorId, String name, Collection<Member> members) {
        UUID conversationId = repository.createConversation(workspaceId, "project", name, actorId, actorId);
        for (Member member : members) {
            repository.addMember(workspaceId, conversationId, member.userId(), member.role());
        }
        imService.publishConversationChanged(workspaceId, actorId, conversationId, List.of());
        return conversationId;
    }

    @Override
    public void addMember(UUID workspaceId, UUID actorId, UUID conversationId, Member member) {
        repository.addMember(workspaceId, conversationId, member.userId(), member.role());
        imService.publishConversationChanged(workspaceId, actorId, conversationId, List.of(member.userId()));
    }

    @Override
    public Optional<MessageSnapshot> findVisibleMessage(
        UUID workspaceId,
        UUID actorId,
        UUID conversationId,
        UUID messageId
    ) {
        return repository.findMessageForUser(workspaceId, conversationId, messageId, actorId)
            .map(message -> new MessageSnapshot(
                message.id(),
                message.conversationId(),
                message.senderId(),
                message.senderName(),
                message.content(),
                message.revokedAt()
            ));
    }

    @Override
    public void sendSystemMessage(
        UUID workspaceId,
        UUID actorId,
        UUID conversationId,
        String idempotencyKey,
        String content
    ) {
        imService.sendMessage(
            new CurrentUser(actorId, workspaceId, null, "", "", Set.of(), Set.of()),
            conversationId,
            idempotencyKey,
            "system",
            content
        );
    }
}
