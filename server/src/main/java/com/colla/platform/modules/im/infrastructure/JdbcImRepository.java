package com.colla.platform.modules.im.infrastructure;

import com.colla.platform.modules.im.domain.ImModels.ConversationDetail;
import com.colla.platform.modules.im.domain.ImModels.ConversationMember;
import com.colla.platform.modules.im.domain.ImModels.ConversationSummary;
import com.colla.platform.modules.im.domain.ImModels.MessageLink;
import com.colla.platform.modules.im.domain.ImModels.MessageMention;
import com.colla.platform.modules.im.domain.ImModels.MessageReactionSummary;
import com.colla.platform.modules.im.domain.ImModels.MessageSummary;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcImRepository implements ImRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcImRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID createConversation(UUID workspaceId, String conversationType, String title, UUID ownerId, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into conversations
                    (id, workspace_id, conversation_type, title, owner_id, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, now(), now())
                """,
            id,
            workspaceId,
            conversationType,
            title,
            ownerId,
            createdBy
        );
        return id;
    }

    @Override
    public void addMember(UUID workspaceId, UUID conversationId, UUID userId, String memberRole) {
        jdbcTemplate.update(
            """
                with latest_message as (
                    select id, created_at
                    from messages
                    where workspace_id = ? and conversation_id = ? and deleted_at is null
                    order by message_seq desc
                    limit 1
                )
                insert into conversation_members
                    (id, workspace_id, conversation_id, user_id, member_role, last_read_message_id, last_read_at, joined_at)
                select ?, ?, ?, ?, ?, latest_message.id, latest_message.created_at, now()
                from (select 1) seed
                left join latest_message on true
                on conflict (conversation_id, user_id)
                do update set member_role = excluded.member_role,
                              last_read_message_id = excluded.last_read_message_id,
                              last_read_at = excluded.last_read_at,
                              joined_at = now(),
                              muted = false,
                              pinned_at = null,
                              archived_at = null
                """,
            workspaceId,
            conversationId,
            UUID.randomUUID(),
            workspaceId,
            conversationId,
            userId,
            memberRole
        );
    }

    @Override
    public void removeMember(UUID workspaceId, UUID conversationId, UUID userId) {
        jdbcTemplate.update(
            """
                update conversation_members
                set archived_at = now()
                where workspace_id = ? and conversation_id = ? and user_id = ? and archived_at is null
                """,
            workspaceId,
            conversationId,
            userId
        );
    }

    @Override
    public void setConversationMuted(UUID workspaceId, UUID conversationId, UUID userId, boolean muted) {
        jdbcTemplate.update(
            """
                update conversation_members
                set muted = ?
                where workspace_id = ? and conversation_id = ? and user_id = ? and archived_at is null
                """,
            muted,
            workspaceId,
            conversationId,
            userId
        );
    }

    @Override
    public void setConversationPinned(UUID workspaceId, UUID conversationId, UUID userId, boolean pinned) {
        jdbcTemplate.update(
            """
                update conversation_members
                set pinned_at = case when ? then now() else null end
                where workspace_id = ? and conversation_id = ? and user_id = ? and archived_at is null
                """,
            pinned,
            workspaceId,
            conversationId,
            userId
        );
    }

    @Override
    public void updateConversationType(UUID workspaceId, UUID conversationId, String conversationType) {
        jdbcTemplate.update(
            """
                update conversations
                set conversation_type = ?, updated_at = now()
                where workspace_id = ? and id = ? and archived_at is null
                """,
            conversationType,
            workspaceId,
            conversationId
        );
    }

    @Override
    public boolean isMember(UUID workspaceId, UUID conversationId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from conversation_members
                where workspace_id = ? and conversation_id = ? and user_id = ? and archived_at is null
                """,
            Integer.class,
            workspaceId,
            conversationId,
            userId
        );
        return count != null && count > 0;
    }

    @Override
    public boolean isOwner(UUID workspaceId, UUID conversationId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from conversation_members
                where workspace_id = ? and conversation_id = ? and user_id = ? and member_role = 'owner' and archived_at is null
                """,
            Integer.class,
            workspaceId,
            conversationId,
            userId
        );
        return count != null && count > 0;
    }

    @Override
    public List<ConversationSummary> listConversations(UUID workspaceId, UUID userId) {
        List<ConversationRow> rows = jdbcTemplate.query(
            """
                select c.id, c.conversation_type, c.title, c.last_message_id, c.last_message_at, c.created_at,
                       cm.muted, cm.pinned_at,
                       (select count(*) from conversation_members cm2 where cm2.conversation_id = c.id and cm2.archived_at is null) member_count
                from conversations c
                join conversation_members cm on cm.conversation_id = c.id
                where c.workspace_id = ? and cm.user_id = ? and cm.archived_at is null and c.archived_at is null
                order by cm.pinned_at desc nulls last, c.last_message_at desc nulls last, c.created_at desc
                """,
            (rs, rowNum) -> mapConversationRow(rs),
            workspaceId,
            userId
        );
        return rows.stream()
            .map(row -> toConversationSummary(row, workspaceId, userId))
            .toList();
    }

    @Override
    public Optional<ConversationDetail> findConversation(UUID workspaceId, UUID conversationId, UUID userId) {
        if (!isMember(workspaceId, conversationId, userId)) {
            return Optional.empty();
        }
        try {
            ConversationRow row = jdbcTemplate.queryForObject(
                """
                    select c.id, c.conversation_type, c.title, c.last_message_id, c.last_message_at, c.created_at,
                           cm.muted, cm.pinned_at,
                           (select count(*) from conversation_members cm2 where cm2.conversation_id = c.id and cm2.archived_at is null) member_count
                    from conversations c
                    join conversation_members cm on cm.conversation_id = c.id and cm.user_id = ? and cm.archived_at is null
                    where c.workspace_id = ? and c.id = ? and c.archived_at is null
                    """,
                (rs, rowNum) -> mapConversationRow(rs),
                userId,
                workspaceId,
                conversationId
            );
            return Optional.of(new ConversationDetail(
                row.id(),
                row.conversationType(),
                row.title(),
                row.memberCount(),
                listMembers(workspaceId, row.id()),
                row.muted(),
                row.pinnedAt(),
                nullableMessage(workspaceId, row.id(), row.lastMessageId()),
                unreadCount(workspaceId, row.id(), userId),
                row.lastMessageAt(),
                row.createdAt()
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<ConversationMember> listMembers(UUID workspaceId, UUID conversationId) {
        return jdbcTemplate.query(
            """
                select u.id user_id, u.username, u.display_name, cm.member_role, cm.joined_at
                from conversation_members cm
                join users u on u.id = cm.user_id
                where cm.workspace_id = ? and cm.conversation_id = ? and cm.archived_at is null and u.deleted_at is null
                order by cm.joined_at
                """,
            (rs, rowNum) -> new ConversationMember(
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("member_role"),
                rs.getTimestamp("joined_at").toInstant()
            ),
            workspaceId,
            conversationId
        );
    }

    @Override
    public List<UUID> listMemberIds(UUID workspaceId, UUID conversationId) {
        return jdbcTemplate.queryForList(
            """
                select user_id
                from conversation_members
                where workspace_id = ? and conversation_id = ? and archived_at is null
                """,
            UUID.class,
            workspaceId,
            conversationId
        );
    }

    @Override
    public List<UUID> findActiveUserIdsByUsernames(UUID workspaceId, List<String> usernames) {
        if (usernames.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", usernames.stream().map(item -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        args.addAll(usernames);
        return jdbcTemplate.queryForList(
            """
                select id
                from users
                where workspace_id = ? and status = 'active' and deleted_at is null and username in (%s)
                """.formatted(placeholders),
            UUID.class,
            args.toArray()
        );
    }

    @Override
    public MessageSummary insertMessage(
        UUID workspaceId,
        UUID conversationId,
        UUID senderId,
        String clientMessageId,
        String messageType,
        String content
    ) {
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                """
                    insert into messages
                        (id, workspace_id, conversation_id, sender_id, client_message_id, message_type, content, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, now())
                    """,
                id,
                workspaceId,
                conversationId,
                senderId,
                clientMessageId,
                messageType,
                content
            );
        } catch (DuplicateKeyException exception) {
            return findMessageByClientId(workspaceId, conversationId, senderId, clientMessageId)
                .orElseThrow(() -> exception);
        }
        return findMessage(workspaceId, conversationId, id).orElseThrow();
    }

    @Override
    public Optional<MessageSummary> findMessage(UUID workspaceId, UUID conversationId, UUID messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        try {
            MessageRow row = jdbcTemplate.queryForObject(
                """
                    select m.id, m.conversation_id, m.sender_id, u.display_name sender_name,
                           m.message_type, m.content, m.client_message_id, m.created_at,
                           m.message_seq, m.edited_at, m.revoked_at, m.pinned_at, m.pinned_by
                    from messages m
                    join users u on u.id = m.sender_id
                    where m.workspace_id = ? and m.conversation_id = ? and m.id = ? and m.deleted_at is null
                    """,
                (rs, rowNum) -> mapMessageRow(rs),
                workspaceId,
                conversationId,
                messageId
            );
            return Optional.of(toMessageSummary(workspaceId, row, null));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<MessageSummary> findMessageForUser(UUID workspaceId, UUID conversationId, UUID messageId, UUID userId) {
        if (messageId == null) {
            return Optional.empty();
        }
        try {
            MessageRow row = jdbcTemplate.queryForObject(
                """
                    select m.id, m.conversation_id, m.sender_id, u.display_name sender_name,
                           m.message_type, m.content, m.client_message_id, m.created_at,
                           m.message_seq, m.edited_at, m.revoked_at, m.pinned_at, m.pinned_by
                    from messages m
                    join users u on u.id = m.sender_id
                    where m.workspace_id = ? and m.conversation_id = ? and m.id = ? and m.deleted_at is null
                    """,
                (rs, rowNum) -> mapMessageRow(rs),
                workspaceId,
                conversationId,
                messageId
            );
            return Optional.of(toMessageSummary(workspaceId, row, userId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<MessageSummary> findMessageForUser(UUID workspaceId, UUID messageId, UUID userId) {
        if (messageId == null) {
            return Optional.empty();
        }
        try {
            MessageRow row = jdbcTemplate.queryForObject(
                """
                    select m.id, m.conversation_id, m.sender_id, u.display_name sender_name,
                           m.message_type, m.content, m.client_message_id, m.created_at,
                           m.message_seq, m.edited_at, m.revoked_at, m.pinned_at, m.pinned_by
                    from messages m
                    join users u on u.id = m.sender_id
                    join conversations c on c.id = m.conversation_id and c.archived_at is null
                    join conversation_members cm on cm.conversation_id = c.id and cm.user_id = ? and cm.archived_at is null
                    where m.workspace_id = ? and m.id = ? and m.deleted_at is null
                    """,
                (rs, rowNum) -> mapMessageRow(rs),
                userId,
                workspaceId,
                messageId
            );
            return Optional.of(toMessageSummary(workspaceId, row, userId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<MessageSummary> listMessages(UUID workspaceId, UUID conversationId, UUID userId, UUID beforeId, int limit) {
        if (!isMember(workspaceId, conversationId, userId)) {
            return List.of();
        }
        if (beforeId == null) {
            return listLatestMessages(workspaceId, conversationId, userId, limit);
        }
        List<MessageRow> rows = jdbcTemplate.query(
            """
                select m.id, m.conversation_id, m.sender_id, u.display_name sender_name,
                       m.message_type, m.content, m.client_message_id, m.created_at,
                       m.message_seq, m.edited_at, m.revoked_at, m.pinned_at, m.pinned_by
                from messages m
                join users u on u.id = m.sender_id
                where m.workspace_id = ? and m.conversation_id = ? and m.deleted_at is null
                  and m.message_seq < (select message_seq from messages where id = ?)
                order by m.message_seq desc
                limit ?
                """,
            (rs, rowNum) -> mapMessageRow(rs),
            workspaceId,
            conversationId,
            beforeId,
            limit
        );
        return rows.stream()
            .map(row -> toMessageSummary(workspaceId, row, userId))
            .toList();
    }

    @Override
    public List<MessageSummary> listMessagesAfterSeq(UUID workspaceId, UUID conversationId, UUID userId, long afterSeq, int limit) {
        if (!isMember(workspaceId, conversationId, userId)) {
            return List.of();
        }
        List<MessageRow> rows = jdbcTemplate.query(
            """
                select m.id, m.conversation_id, m.sender_id, u.display_name sender_name,
                       m.message_type, m.content, m.client_message_id, m.created_at,
                       m.message_seq, m.edited_at, m.revoked_at, m.pinned_at, m.pinned_by
                from messages m
                join users u on u.id = m.sender_id
                where m.workspace_id = ? and m.conversation_id = ? and m.deleted_at is null
                  and m.message_seq > ?
                order by m.message_seq asc
                limit ?
                """,
            (rs, rowNum) -> mapMessageRow(rs),
            workspaceId,
            conversationId,
            afterSeq,
            limit
        );
        return rows.stream()
            .map(row -> toMessageSummary(workspaceId, row, userId))
            .toList();
    }

    @Override
    public List<MessageSummary> listMessageContext(UUID workspaceId, UUID conversationId, UUID userId, UUID messageId, int limit) {
        if (!isMember(workspaceId, conversationId, userId)) {
            return List.of();
        }
        List<MessageRow> rows = jdbcTemplate.query(
            """
                with target as (
                    select message_seq
                    from messages
                    where workspace_id = ? and conversation_id = ? and id = ? and deleted_at is null
                )
                select m.id, m.conversation_id, m.sender_id, u.display_name sender_name,
                       m.message_type, m.content, m.client_message_id, m.created_at,
                       m.message_seq, m.edited_at, m.revoked_at, m.pinned_at, m.pinned_by
                from messages m
                join users u on u.id = m.sender_id
                join target on m.message_seq <= target.message_seq
                where m.workspace_id = ? and m.conversation_id = ? and m.deleted_at is null
                order by m.message_seq desc
                limit ?
                """,
            (rs, rowNum) -> mapMessageRow(rs),
            workspaceId,
            conversationId,
            messageId,
            workspaceId,
            conversationId,
            limit
        );
        return rows.stream()
            .map(row -> toMessageSummary(workspaceId, row, userId))
            .toList();
    }

    @Override
    public List<MessageSummary> searchMessages(UUID workspaceId, UUID conversationId, UUID userId, String query, String targetType, int limit) {
        if (!isMember(workspaceId, conversationId, userId)) {
            return List.of();
        }
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedTargetType = targetType == null ? "" : targetType.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isBlank() && normalizedTargetType.isBlank()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder(
            """
                select m.id, m.conversation_id, m.sender_id, u.display_name sender_name,
                       m.message_type, m.content, m.client_message_id, m.created_at,
                       m.message_seq, m.edited_at, m.revoked_at, m.pinned_at, m.pinned_by
                from messages m
                join users u on u.id = m.sender_id
                where m.workspace_id = ? and m.conversation_id = ? and m.deleted_at is null and m.revoked_at is null
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        args.add(conversationId);
        if (!normalizedQuery.isBlank()) {
            sql.append(" and lower(m.content) like lower(?)");
            args.add("%" + normalizedQuery + "%");
        }
        if (!normalizedTargetType.isBlank()) {
            sql.append(
                """
                 and exists (
                    select 1
                    from message_links ml
                    where ml.workspace_id = m.workspace_id and ml.message_id = m.id and ml.target_type = ?
                 )
                """
            );
            args.add(normalizedTargetType);
        }
        sql.append(" order by m.message_seq desc limit ?");
        args.add(limit);

        List<MessageRow> rows = jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> mapMessageRow(rs),
            args.toArray()
        );
        return rows.stream()
            .map(row -> toMessageSummary(workspaceId, row, userId))
            .toList();
    }

    private List<MessageSummary> listLatestMessages(UUID workspaceId, UUID conversationId, UUID userId, int limit) {
        List<MessageRow> rows = jdbcTemplate.query(
            """
                select m.id, m.conversation_id, m.sender_id, u.display_name sender_name,
                       m.message_type, m.content, m.client_message_id, m.created_at,
                       m.message_seq, m.edited_at, m.revoked_at, m.pinned_at, m.pinned_by
                from messages m
                join users u on u.id = m.sender_id
                where m.workspace_id = ? and m.conversation_id = ? and m.deleted_at is null
                order by m.message_seq desc
                limit ?
                """,
            (rs, rowNum) -> mapMessageRow(rs),
            workspaceId,
            conversationId,
            limit
        );
        return rows.stream()
            .map(row -> toMessageSummary(workspaceId, row, userId))
            .toList();
    }

    @Override
    public void editMessage(UUID workspaceId, UUID conversationId, UUID messageId, UUID senderId, String content) {
        jdbcTemplate.update(
            """
                update messages
                set content = ?, edited_at = now()
                where workspace_id = ? and conversation_id = ? and id = ? and sender_id = ? and deleted_at is null and revoked_at is null
                """,
            content,
            workspaceId,
            conversationId,
            messageId,
            senderId
        );
    }

    @Override
    public void revokeMessage(UUID workspaceId, UUID conversationId, UUID messageId, UUID senderId) {
        jdbcTemplate.update(
            """
                update messages
                set content = '', revoked_at = now()
                where workspace_id = ? and conversation_id = ? and id = ? and sender_id = ? and deleted_at is null and revoked_at is null
                """,
            workspaceId,
            conversationId,
            messageId,
            senderId
        );
    }

    @Override
    public void setPinned(UUID workspaceId, UUID conversationId, UUID messageId, UUID actorId, boolean pinned) {
        jdbcTemplate.update(
            """
                update messages
                set pinned_at = case when ? then now() else null end,
                    pinned_by = case when ? then ? else null end
                where workspace_id = ? and conversation_id = ? and id = ? and deleted_at is null and revoked_at is null
                """,
            pinned,
            pinned,
            actorId,
            workspaceId,
            conversationId,
            messageId
        );
    }

    @Override
    public void toggleReaction(UUID workspaceId, UUID conversationId, UUID messageId, UUID userId, String emoji) {
        int removed = jdbcTemplate.update(
            """
                delete from message_reactions
                where workspace_id = ? and conversation_id = ? and message_id = ? and user_id = ? and emoji = ?
                """,
            workspaceId,
            conversationId,
            messageId,
            userId,
            emoji
        );
        if (removed == 0) {
            jdbcTemplate.update(
                """
                    insert into message_reactions (id, workspace_id, conversation_id, message_id, user_id, emoji, created_at)
                    values (?, ?, ?, ?, ?, ?, now())
                    on conflict (message_id, user_id, emoji) do nothing
                    """,
                UUID.randomUUID(),
                workspaceId,
                conversationId,
                messageId,
                userId,
                emoji
            );
        }
    }

    @Override
    public void updateConversationLastMessage(UUID workspaceId, UUID conversationId, UUID messageId) {
        jdbcTemplate.update(
            """
                update conversations
                set last_message_id = ?, last_message_at = (select created_at from messages where id = ?), updated_at = now()
                where workspace_id = ? and id = ?
                """,
            messageId,
            messageId,
            workspaceId,
            conversationId
        );
    }

    @Override
    public void addMention(UUID workspaceId, UUID conversationId, UUID messageId, UUID mentionedUserId) {
        jdbcTemplate.update(
            """
                insert into message_mentions
                    (id, workspace_id, conversation_id, message_id, mentioned_user_id, created_at)
                values (?, ?, ?, ?, ?, now())
                on conflict (message_id, mentioned_user_id) do nothing
                """,
            UUID.randomUUID(),
            workspaceId,
            conversationId,
            messageId,
            mentionedUserId
        );
    }

    @Override
    public void addLink(
        UUID workspaceId,
        UUID conversationId,
        UUID messageId,
        String sourceUrl,
        String targetType,
        UUID targetId,
        String webPath,
        String deepLink,
        PlatformObjectSummary summary
    ) {
        jdbcTemplate.update(
            """
                insert into message_links
                    (id, workspace_id, conversation_id, message_id, source_url, target_type, target_id,
                     web_path, deep_link, card_snapshot, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            conversationId,
            messageId,
            sourceUrl,
            targetType,
            targetId,
            webPath,
            deepLink,
            writeSummary(summary)
        );
    }

    @Override
    public List<MessageMention> listMentions(UUID workspaceId, UUID messageId) {
        return jdbcTemplate.query(
            """
                select u.id user_id, u.username, u.display_name
                from message_mentions mm
                join users u on u.id = mm.mentioned_user_id
                where mm.workspace_id = ? and mm.message_id = ?
                order by u.display_name
                """,
            (rs, rowNum) -> new MessageMention(
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name")
            ),
            workspaceId,
            messageId
        );
    }

    @Override
    public List<MessageLink> listLinks(UUID workspaceId, UUID messageId) {
        return jdbcTemplate.query(
            """
                select id, source_url, target_type, target_id, web_path, deep_link, card_snapshot
                from message_links
                where workspace_id = ? and message_id = ?
                order by created_at
                """,
            (rs, rowNum) -> new MessageLink(
                rs.getObject("id", UUID.class),
                rs.getString("source_url"),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                rs.getString("web_path"),
                rs.getString("deep_link"),
                readSummary(rs.getString("card_snapshot"))
            ),
            workspaceId,
            messageId
        );
    }

    @Override
    public List<MessageReactionSummary> listReactions(UUID workspaceId, UUID messageId, UUID currentUserId) {
        return jdbcTemplate.query(
            """
                select emoji,
                       count(*)::int reaction_count,
                       bool_or(user_id = ?) reacted_by_me
                from message_reactions
                where workspace_id = ? and message_id = ?
                group by emoji
                order by reaction_count desc, emoji
                """,
            (rs, rowNum) -> new MessageReactionSummary(
                rs.getString("emoji"),
                rs.getInt("reaction_count"),
                rs.getBoolean("reacted_by_me")
            ),
            currentUserId,
            workspaceId,
            messageId
        );
    }

    @Override
    public void markRead(UUID workspaceId, UUID conversationId, UUID userId, UUID messageId) {
        UUID targetMessageId = messageId == null ? latestMessageId(workspaceId, conversationId) : messageId;
        Timestamp lastReadAt = targetMessageId == null
            ? Timestamp.from(Instant.now())
            : jdbcTemplate.queryForObject("select created_at from messages where id = ?", Timestamp.class, targetMessageId);
        jdbcTemplate.update(
            """
                update conversation_members
                set last_read_message_id = ?, last_read_at = ?
                where workspace_id = ? and conversation_id = ? and user_id = ? and archived_at is null
                  and (
                      ? is null
                      or last_read_message_id is null
                      or (select target_message.message_seq from messages target_message where target_message.id = ?)
                         >= (select read_marker.message_seq from messages read_marker where read_marker.id = last_read_message_id)
                  )
                """,
            targetMessageId,
            lastReadAt,
            workspaceId,
            conversationId,
            userId,
            targetMessageId,
            targetMessageId
        );
    }

    @Override
    public long unreadCount(UUID workspaceId, UUID conversationId, UUID userId) {
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from messages m
                join conversation_members cm on cm.conversation_id = m.conversation_id and cm.user_id = ? and cm.archived_at is null
                where m.workspace_id = ? and m.conversation_id = ? and m.sender_id <> ?
                  and m.deleted_at is null
                  and (
                      cm.last_read_message_id is null
                      or m.message_seq > (
                          select read_marker.message_seq
                          from messages read_marker
                          where read_marker.id = cm.last_read_message_id
                      )
                  )
                """,
            Long.class,
            userId,
            workspaceId,
            conversationId,
            userId
        );
        return count == null ? 0 : count;
    }

    @Override
    public long totalUnreadCount(UUID workspaceId, UUID userId) {
        Long count = jdbcTemplate.queryForObject(
            """
                select count(*)
                from messages m
                join conversation_members cm on cm.conversation_id = m.conversation_id and cm.user_id = ? and cm.archived_at is null
                where m.workspace_id = ? and m.sender_id <> ? and m.deleted_at is null
                  and (
                      cm.last_read_message_id is null
                      or m.message_seq > (
                          select read_marker.message_seq
                          from messages read_marker
                          where read_marker.id = cm.last_read_message_id
                      )
                  )
                """,
            Long.class,
            userId,
            workspaceId,
            userId
        );
        return count == null ? 0 : count;
    }

    private ConversationRow mapConversationRow(ResultSet rs) throws SQLException {
        return new ConversationRow(
            rs.getObject("id", UUID.class),
            rs.getString("conversation_type"),
            rs.getString("title"),
            rs.getObject("last_message_id", UUID.class),
            toInstant(rs, "last_message_at"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getBoolean("muted"),
            toInstant(rs, "pinned_at"),
            rs.getInt("member_count")
        );
    }

    private ConversationSummary toConversationSummary(ConversationRow row, UUID workspaceId, UUID userId) {
        return new ConversationSummary(
            row.id(),
            row.conversationType(),
            row.title(),
            row.memberCount(),
            row.muted(),
            row.pinnedAt(),
            nullableMessage(workspaceId, row.id(), row.lastMessageId()),
            unreadCount(workspaceId, row.id(), userId),
            row.lastMessageAt(),
            row.createdAt()
        );
    }

    private MessageRow mapMessageRow(ResultSet rs) throws SQLException {
        return new MessageRow(
            rs.getObject("id", UUID.class),
            rs.getObject("conversation_id", UUID.class),
            rs.getObject("sender_id", UUID.class),
            rs.getString("sender_name"),
            rs.getString("message_type"),
            rs.getString("content"),
            rs.getString("client_message_id"),
            rs.getLong("message_seq"),
            rs.getTimestamp("created_at").toInstant(),
            toInstant(rs, "edited_at"),
            toInstant(rs, "revoked_at"),
            toInstant(rs, "pinned_at"),
            rs.getObject("pinned_by", UUID.class)
        );
    }

    private MessageSummary toMessageSummary(UUID workspaceId, MessageRow row, UUID currentUserId) {
        return new MessageSummary(
            row.id(),
            row.conversationId(),
            row.senderId(),
            row.senderName(),
            row.messageType(),
            row.content(),
            row.clientMessageId(),
            row.messageSeq(),
            row.createdAt(),
            row.editedAt(),
            row.revokedAt(),
            row.pinnedAt(),
            row.pinnedBy(),
            listMentions(workspaceId, row.id()),
            listLinks(workspaceId, row.id()),
            listReactions(workspaceId, row.id(), currentUserId)
        );
    }

    @Override
    public Optional<MessageSummary> findMessageByClientId(UUID workspaceId, UUID conversationId, UUID senderId, String clientMessageId) {
        try {
            MessageRow row = jdbcTemplate.queryForObject(
                """
                    select m.id, m.conversation_id, m.sender_id, u.display_name sender_name,
                           m.message_type, m.content, m.client_message_id, m.created_at,
                           m.message_seq, m.edited_at, m.revoked_at, m.pinned_at, m.pinned_by
                    from messages m
                    join users u on u.id = m.sender_id
                    where m.workspace_id = ? and m.conversation_id = ? and m.sender_id = ? and m.client_message_id = ?
                    """,
                (rs, rowNum) -> mapMessageRow(rs),
                workspaceId,
                conversationId,
                senderId,
                clientMessageId
            );
            return Optional.of(toMessageSummary(workspaceId, row, senderId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private MessageSummary nullableMessage(UUID workspaceId, UUID conversationId, UUID messageId) {
        return findMessage(workspaceId, conversationId, messageId).orElse(null);
    }

    private UUID latestMessageId(UUID workspaceId, UUID conversationId) {
        try {
            return jdbcTemplate.queryForObject(
                """
                    select id
                    from messages
                    where workspace_id = ? and conversation_id = ? and deleted_at is null
                    order by message_seq desc
                    limit 1
                    """,
                UUID.class,
                workspaceId,
                conversationId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String writeSummary(PlatformObjectSummary summary) {
        if (summary == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid card snapshot", exception);
        }
    }

    private PlatformObjectSummary readSummary(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, PlatformObjectSummary.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid card snapshot", exception);
        }
    }

    private record ConversationRow(
        UUID id,
        String conversationType,
        String title,
        UUID lastMessageId,
        Instant lastMessageAt,
        Instant createdAt,
        boolean muted,
        Instant pinnedAt,
        int memberCount
    ) {
    }

    private record MessageRow(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderName,
        String messageType,
        String content,
        String clientMessageId,
        long messageSeq,
        Instant createdAt,
        Instant editedAt,
        Instant revokedAt,
        Instant pinnedAt,
        UUID pinnedBy
    ) {
    }
}
