package com.colla.platform.modules.im.application;

import com.colla.platform.modules.im.domain.ImModels.MessageSummary;
import com.colla.platform.modules.im.infrastructure.ImRepository;
import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MessagePlatformObjectResolver implements PlatformObjectResolver {
    private final ImRepository imRepository;

    public MessagePlatformObjectResolver(ImRepository imRepository) {
        this.imRepository = imRepository;
    }

    @Override
    public String objectType() {
        return "message";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        Optional<MessageSummary> message = imRepository.findMessageForUser(currentUser.workspaceId(), objectId, currentUser.id());
        if (message.isEmpty()) {
            return Optional.empty();
        }
        MessageSummary value = message.get();
        if (value.revokedAt() != null) {
            return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.deleted));
        }
        return Optional.of(new PlatformObjectSummary(
            objectType(),
            objectId,
            ObjectAccessState.available,
            value.senderName() + " 的消息",
            preview(value.content()),
            value.messageType(),
            "/im?conversationId=" + value.conversationId() + "&messageId=" + objectId,
            "colla://message/" + objectId,
            Map.of(
                "conversationId", value.conversationId().toString(),
                "senderId", value.senderId().toString(),
                "createdAt", value.createdAt().toString()
            )
        ));
    }

    private String preview(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "空消息";
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }
}
