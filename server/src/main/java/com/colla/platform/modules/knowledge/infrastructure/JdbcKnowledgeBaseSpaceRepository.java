package com.colla.platform.modules.knowledge.infrastructure;

import com.colla.platform.modules.knowledge.domain.KnowledgeBaseItemModels.KnowledgeBaseSpaceSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcKnowledgeBaseSpaceRepository implements KnowledgeBaseSpaceRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeBaseSpaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void backfillLegacySpaces(UUID workspaceId) {
        jdbcTemplate.update(
            """
                insert into knowledge_base_spaces
                    (id, workspace_id, name, code, description, icon, cover_url, status, visibility,
                     root_item_id, home_item_id, owner_id, default_permission_level,
                     created_by, created_at, updated_by, updated_at)
                select d.id,
                       d.workspace_id,
                       d.title,
                       'kb-' || substring(replace(d.id::text, '-', '') from 1 for 12),
                       d.description,
                       null,
                       d.cover_url,
                       case when d.archived_at is not null or d.status = 'archived' then 'archived' else 'active' end,
                       'private',
                       d.id,
                       d.id,
                       d.created_by,
                       d.default_permission_level,
                       d.created_by,
                       d.created_at,
                       d.updated_by,
                       d.updated_at
                from knowledge_base_items d
                where d.workspace_id = ?
                  and d.content_type = 'space'
                  and d.knowledge_base = true
                  and d.deleted_at is null
                  and not exists (
                      select 1
                      from knowledge_base_spaces k
                      where k.workspace_id = d.workspace_id
                        and k.root_item_id = d.id
                        and k.deleted_at is null
                  )
                """,
            workspaceId
        );
    }

    @Override
    public boolean codeExists(UUID workspaceId, String code, UUID excludeId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from knowledge_base_spaces
                    where workspace_id = ?
                      and code = ?
                      and deleted_at is null
                      and (?::uuid is null or id <> ?)
                )
                """,
            Boolean.class,
            workspaceId,
            code,
            excludeId,
            excludeId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public UUID createSpace(
        UUID workspaceId,
        String name,
        String code,
        String description,
        String icon,
        String coverUrl,
        String visibility,
        UUID rootItemId,
        UUID homeItemId,
        UUID ownerId,
        String defaultPermissionLevel,
        UUID actorId
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into knowledge_base_spaces
                    (id, workspace_id, name, code, description, icon, cover_url, status, visibility,
                     root_item_id, home_item_id, owner_id, default_permission_level,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'active', ?, ?, ?, ?, ?, ?, now(), ?, now())
                """,
            id,
            workspaceId,
            name,
            code,
            description,
            icon,
            coverUrl,
            visibility,
            rootItemId,
            homeItemId,
            ownerId,
            defaultPermissionLevel,
            actorId,
            actorId
        );
        return id;
    }

    @Override
    public void updateSpace(
        UUID workspaceId,
        UUID spaceId,
        String name,
        String code,
        String description,
        String icon,
        String coverUrl,
        String visibility,
        UUID homeItemId,
        String defaultPermissionLevel,
        UUID actorId
    ) {
        jdbcTemplate.update(
            """
                update knowledge_base_spaces
                set name = ?,
                    code = ?,
                    description = ?,
                    icon = ?,
                    cover_url = ?,
                    visibility = ?,
                    home_item_id = ?,
                    default_permission_level = ?,
                    updated_by = ?,
                    updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            name,
            code,
            description,
            icon,
            coverUrl,
            visibility,
            homeItemId,
            defaultPermissionLevel,
            actorId,
            workspaceId,
            spaceId
        );
    }

    @Override
    public void updateStatus(UUID workspaceId, UUID spaceId, String status, UUID actorId) {
        jdbcTemplate.update(
            """
                update knowledge_base_spaces
                set status = ?,
                    updated_by = ?,
                    updated_at = now()
                where workspace_id = ? and id = ? and deleted_at is null
                """,
            status,
            actorId,
            workspaceId,
            spaceId
        );
    }

    @Override
    public List<KnowledgeBaseSpaceSummary> listSpaces(UUID workspaceId, boolean includeArchived) {
        return jdbcTemplate.query(
            """
                select k.id, k.name, k.code, k.description, k.icon, k.cover_url, k.status, k.visibility,
                       k.root_item_id, k.home_item_id, k.owner_id,
                       coalesce(owner.display_name, owner.username) owner_name,
                       k.default_permission_level, k.created_at, k.updated_at,
                       (
                           with recursive subtree as (
                               select d.id
                               from knowledge_base_items d
                               where d.workspace_id = k.workspace_id
                                 and d.id = k.root_item_id
                                 and d.deleted_at is null
                               union all
                               select child.id
                               from knowledge_base_items child
                               join subtree parent on child.parent_id = parent.id
                               where child.workspace_id = k.workspace_id
                                 and child.deleted_at is null
                           )
                           select count(*) from subtree
                       ) document_count
                from knowledge_base_spaces k
                join users owner on owner.id = k.owner_id
                where k.workspace_id = ?
                  and k.deleted_at is null
                  and (? or k.status <> 'archived')
                order by
                  case k.status when 'active' then 0 when 'disabled' then 1 else 2 end,
                  k.updated_at desc nulls last,
                  k.created_at desc
                """,
            this::mapSummary,
            workspaceId,
            includeArchived
        );
    }

    @Override
    public Optional<KnowledgeBaseSpaceSummary> findSpace(UUID workspaceId, UUID spaceId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select k.id, k.name, k.code, k.description, k.icon, k.cover_url, k.status, k.visibility,
                           k.root_item_id, k.home_item_id, k.owner_id,
                           coalesce(owner.display_name, owner.username) owner_name,
                           k.default_permission_level, k.created_at, k.updated_at,
                           (
                               with recursive subtree as (
                                   select d.id
                                   from knowledge_base_items d
                                   where d.workspace_id = k.workspace_id
                                     and d.id = k.root_item_id
                                     and d.deleted_at is null
                                   union all
                                   select child.id
                                   from knowledge_base_items child
                                   join subtree parent on child.parent_id = parent.id
                                   where child.workspace_id = k.workspace_id
                                     and child.deleted_at is null
                               )
                               select count(*) from subtree
                           ) document_count
                    from knowledge_base_spaces k
                    join users owner on owner.id = k.owner_id
                    where k.workspace_id = ?
                      and k.id = ?
                      and k.deleted_at is null
                    """,
                this::mapSummary,
                workspaceId,
                spaceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<KnowledgeBaseSpaceSummary> findSpaceByRootItemId(UUID workspaceId, UUID rootItemId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select k.id, k.name, k.code, k.description, k.icon, k.cover_url, k.status, k.visibility,
                           k.root_item_id, k.home_item_id, k.owner_id,
                           coalesce(owner.display_name, owner.username) owner_name,
                           k.default_permission_level, k.created_at, k.updated_at,
                           (
                               with recursive subtree as (
                                   select d.id
                                   from knowledge_base_items d
                                   where d.workspace_id = k.workspace_id
                                     and d.id = k.root_item_id
                                     and d.deleted_at is null
                                   union all
                                   select child.id
                                   from knowledge_base_items child
                                   join subtree parent on child.parent_id = parent.id
                                   where child.workspace_id = k.workspace_id
                                     and child.deleted_at is null
                               )
                               select count(*) from subtree
                           ) document_count
                    from knowledge_base_spaces k
                    join users owner on owner.id = k.owner_id
                    where k.workspace_id = ?
                      and k.root_item_id = ?
                      and k.deleted_at is null
                    """,
                this::mapSummary,
                workspaceId,
                rootItemId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<KnowledgeBaseSpaceSummary> findSpaceByItemId(UUID workspaceId, UUID itemId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    with recursive ancestors as (
                        select d.id, d.parent_id
                        from knowledge_base_items d
                        where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                        union all
                        select parent.id, parent.parent_id
                        from knowledge_base_items parent
                        join ancestors child on child.parent_id = parent.id
                        where parent.workspace_id = ? and parent.deleted_at is null
                    )
                    select k.id, k.name, k.code, k.description, k.icon, k.cover_url, k.status, k.visibility,
                           k.root_item_id, k.home_item_id, k.owner_id,
                           coalesce(owner.display_name, owner.username) owner_name,
                           k.default_permission_level, k.created_at, k.updated_at,
                           (
                               with recursive subtree as (
                                   select d.id
                                   from knowledge_base_items d
                                   where d.workspace_id = k.workspace_id
                                     and d.id = k.root_item_id
                                     and d.deleted_at is null
                                   union all
                                   select child.id
                                   from knowledge_base_items child
                                   join subtree parent on child.parent_id = parent.id
                                   where child.workspace_id = k.workspace_id
                                     and child.deleted_at is null
                               )
                               select count(*) from subtree
                           ) document_count
                    from ancestors a
                    join knowledge_base_spaces k on k.workspace_id = ?
                        and k.root_item_id = a.id
                        and k.deleted_at is null
                    join users owner on owner.id = k.owner_id
                    order by k.created_at desc
                    limit 1
                    """,
                this::mapSummary,
                workspaceId,
                itemId,
                workspaceId,
                workspaceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> findDisabledRootForDocument(UUID workspaceId, UUID itemId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    with recursive ancestors as (
                        select d.id, d.parent_id
                        from knowledge_base_items d
                        where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                        union all
                        select parent.id, parent.parent_id
                        from knowledge_base_items parent
                        join ancestors child on child.parent_id = parent.id
                        where parent.workspace_id = ? and parent.deleted_at is null
                    )
                    select k.root_item_id
                    from ancestors a
                    join knowledge_base_spaces k on k.workspace_id = ?
                        and k.root_item_id = a.id
                        and k.deleted_at is null
                        and k.status = 'disabled'
                    limit 1
                    """,
                UUID.class,
                workspaceId,
                itemId,
                workspaceId,
                workspaceId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isSubscribed(UUID workspaceId, UUID subscriberId, String targetType, UUID targetId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from knowledge_subscriptions
                    where workspace_id = ?
                      and subscriber_id = ?
                      and target_type = ?
                      and target_id = ?
                      and deleted_at is null
                )
                """,
            Boolean.class,
            workspaceId,
            subscriberId,
            targetType,
            targetId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void subscribe(UUID workspaceId, UUID subscriberId, String targetType, UUID targetId) {
        jdbcTemplate.update(
            """
                insert into knowledge_subscriptions (id, workspace_id, subscriber_id, target_type, target_id, created_at, deleted_at)
                values (?, ?, ?, ?, ?, now(), null)
                on conflict (workspace_id, subscriber_id, target_type, target_id)
                do update set deleted_at = null, created_at = now()
                """,
            UUID.randomUUID(),
            workspaceId,
            subscriberId,
            targetType,
            targetId
        );
    }

    @Override
    public void unsubscribe(UUID workspaceId, UUID subscriberId, String targetType, UUID targetId) {
        jdbcTemplate.update(
            """
                update knowledge_subscriptions
                set deleted_at = now()
                where workspace_id = ?
                  and subscriber_id = ?
                  and target_type = ?
                  and target_id = ?
                  and deleted_at is null
                """,
            workspaceId,
            subscriberId,
            targetType,
            targetId
        );
    }

    @Override
    public List<UUID> listSubscribedItemIds(UUID workspaceId, UUID subscriberId, UUID rootItemId, int limit) {
        return jdbcTemplate.queryForList(
            """
                with recursive subtree(id, parent_id) as (
                    select d.id, d.parent_id
                    from knowledge_base_items d
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    union all
                    select child.id, child.parent_id
                    from knowledge_base_items child
                    join subtree parent on parent.id = child.parent_id
                    where child.workspace_id = ? and child.deleted_at is null
                )
                select d.id
                from knowledge_base_items d
                join subtree s on s.id = d.id
                where d.content_type = 'markdown'
                  and d.deleted_at is null
                  and d.archived_at is null
                  and (
                      exists (
                          select 1
                          from knowledge_subscriptions ks
                          join knowledge_base_spaces k on k.id = ks.target_id
                              and k.workspace_id = ks.workspace_id
                              and k.root_item_id = ?
                              and k.deleted_at is null
                          where ks.workspace_id = ?
                            and ks.subscriber_id = ?
                            and ks.target_type = 'knowledge_base'
                            and ks.deleted_at is null
                      )
                      or exists (
                          select 1
                          from knowledge_subscriptions ks
                          where ks.workspace_id = ?
                            and ks.subscriber_id = ?
                            and ks.target_type = 'knowledge_content'
                            and ks.deleted_at is null
                            and (
                                ks.target_id = d.id
                                or exists (
                                    with recursive subscribed_subtree(id) as (
                                        select sd.id
                                        from knowledge_base_items sd
                                        where sd.workspace_id = ks.workspace_id and sd.id = ks.target_id and sd.deleted_at is null
                                        union all
                                        select child.id
                                        from knowledge_base_items child
                                        join subscribed_subtree parent on parent.id = child.parent_id
                                        where child.workspace_id = ks.workspace_id and child.deleted_at is null
                                    )
                                    select 1 from subscribed_subtree where id = d.id
                                )
                            )
                      )
                  )
                order by d.updated_at desc
                limit ?
                """,
            UUID.class,
            workspaceId,
            rootItemId,
            workspaceId,
            rootItemId,
            workspaceId,
            subscriberId,
            workspaceId,
            subscriberId,
            Math.min(Math.max(limit, 1), 50)
        );
    }

    @Override
    public List<UUID> listSubscriberIdsForDocument(UUID workspaceId, UUID itemId) {
        return jdbcTemplate.queryForList(
            """
                with recursive ancestors(id, parent_id) as (
                    select d.id, d.parent_id
                    from knowledge_base_items d
                    where d.workspace_id = ? and d.id = ? and d.deleted_at is null
                    union all
                    select parent.id, parent.parent_id
                    from knowledge_base_items parent
                    join ancestors child on child.parent_id = parent.id
                    where parent.workspace_id = ? and parent.deleted_at is null
                ),
                kb as (
                    select k.id
                    from knowledge_base_spaces k
                    join ancestors a on a.id = k.root_item_id
                    where k.workspace_id = ? and k.deleted_at is null
                    limit 1
                )
                select distinct ks.subscriber_id
                from knowledge_subscriptions ks
                where ks.workspace_id = ?
                  and ks.deleted_at is null
                  and (
                      (ks.target_type = 'knowledge_base' and ks.target_id in (select id from kb))
                      or (ks.target_type = 'knowledge_content' and ks.target_id in (select id from ancestors))
                  )
                """,
            UUID.class,
            workspaceId,
            itemId,
            workspaceId,
            workspaceId,
            workspaceId
        );
    }

    private KnowledgeBaseSpaceSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeBaseSpaceSummary(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("code"),
            rs.getString("description"),
            rs.getString("icon"),
            rs.getString("cover_url"),
            rs.getString("status"),
            rs.getString("visibility"),
            rs.getObject("root_item_id", UUID.class),
            rs.getObject("home_item_id", UUID.class),
            rs.getObject("owner_id", UUID.class),
            rs.getString("owner_name"),
            rs.getString("default_permission_level"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("document_count")
        );
    }
}

