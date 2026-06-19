package com.colla.platform.modules.base.infrastructure;

import com.colla.platform.modules.base.domain.BaseModels.BaseDetail;
import com.colla.platform.modules.base.domain.BaseModels.BaseField;
import com.colla.platform.modules.base.domain.BaseModels.BaseFilter;
import com.colla.platform.modules.base.domain.BaseModels.BaseMember;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecord;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecordActivity;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecordComment;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecordRelationRecord;
import com.colla.platform.modules.base.domain.BaseModels.BaseSort;
import com.colla.platform.modules.base.domain.BaseModels.BaseSummary;
import com.colla.platform.modules.base.domain.BaseModels.BaseTableSummary;
import com.colla.platform.modules.base.domain.BaseModels.BaseView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBaseRepository implements BaseRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<BaseFilter>> FILTER_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<BaseSort>> SORT_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<UUID>> UUID_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcBaseRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public UUID createBase(UUID workspaceId, String name, String description, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into bases
                    (id, workspace_id, name, description, status, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, 'active', ?, now(), ?, now())
                """,
            id,
            workspaceId,
            name,
            description,
            createdBy,
            createdBy
        );
        return id;
    }

    @Override
    public void updateBase(UUID workspaceId, UUID baseId, String name, String description, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update bases
                set name = ?, description = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and archived_at is null
                """,
            name,
            description,
            updatedBy,
            workspaceId,
            baseId
        );
    }

    @Override
    public void upsertMember(UUID workspaceId, UUID baseId, UUID userId, String permissionLevel, UUID actorId) {
        jdbcTemplate.update(
            """
                insert into base_members
                    (id, workspace_id, base_id, user_id, permission_level, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, now(), ?, now())
                on conflict (base_id, user_id)
                do update set permission_level = excluded.permission_level, revoked_at = null, updated_by = excluded.updated_by, updated_at = now()
                """,
            UUID.randomUUID(),
            workspaceId,
            baseId,
            userId,
            permissionLevel,
            actorId,
            actorId
        );
    }

    @Override
    public Optional<String> findPermissionLevel(UUID workspaceId, UUID baseId, UUID userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select case
                        when b.created_by = ? then 'manage'
                        else bm.permission_level
                    end
                    from bases b
                    left join base_members bm on bm.base_id = b.id and bm.user_id = ? and bm.revoked_at is null
                    where b.workspace_id = ? and b.id = ? and b.archived_at is null
                    """,
                String.class,
                userId,
                userId,
                workspaceId,
                baseId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<BaseSummary> listBases(UUID workspaceId, UUID userId) {
        return jdbcTemplate.query(
            """
                select b.id, b.name, b.description, b.status,
                       coalesce(bm.permission_level, case when b.created_by = ? then 'manage' end) permission_level,
                       (select count(*) from base_tables bt where bt.base_id = b.id and bt.archived_at is null) table_count,
                       (select count(*) from base_records br join base_tables bt on bt.id = br.table_id where bt.base_id = b.id and br.deleted_at is null and bt.archived_at is null) record_count,
                       b.created_by, cu.display_name created_by_name, b.created_at,
                       b.updated_by, uu.display_name updated_by_name, b.updated_at
                from bases b
                join users cu on cu.id = b.created_by
                join users uu on uu.id = b.updated_by
                left join base_members bm on bm.base_id = b.id and bm.user_id = ? and bm.revoked_at is null
                where b.workspace_id = ? and b.archived_at is null
                  and (b.created_by = ? or bm.user_id is not null)
                order by b.updated_at desc
                """,
            this::mapBaseSummary,
            userId,
            userId,
            workspaceId,
            userId
        );
    }

    @Override
    public Optional<BaseSummary> findBase(UUID workspaceId, UUID baseId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select b.id, b.name, b.description, b.status, 'manage' permission_level,
                           (select count(*) from base_tables bt where bt.base_id = b.id and bt.archived_at is null) table_count,
                           (select count(*) from base_records br join base_tables bt on bt.id = br.table_id where bt.base_id = b.id and br.deleted_at is null and bt.archived_at is null) record_count,
                           b.created_by, cu.display_name created_by_name, b.created_at,
                           b.updated_by, uu.display_name updated_by_name, b.updated_at
                    from bases b
                    join users cu on cu.id = b.created_by
                    join users uu on uu.id = b.updated_by
                    where b.workspace_id = ? and b.id = ? and b.archived_at is null
                    """,
                this::mapBaseSummary,
                workspaceId,
                baseId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<BaseDetail> findBaseDetail(UUID workspaceId, UUID baseId, UUID userId) {
        Optional<String> permission = findPermissionLevel(workspaceId, baseId, userId);
        if (permission.isEmpty()) {
            return Optional.empty();
        }
        return findBase(workspaceId, baseId)
            .map(base -> new BaseDetail(
                withPermission(base, permission.get()),
                listTables(workspaceId, baseId),
                listMembers(workspaceId, baseId)
            ));
    }

    @Override
    public List<BaseMember> listMembers(UUID workspaceId, UUID baseId) {
        return jdbcTemplate.query(
            """
                select bm.id, bm.user_id, u.username, u.display_name, bm.permission_level, bm.created_at
                from base_members bm
                join users u on u.id = bm.user_id
                where bm.workspace_id = ? and bm.base_id = ? and bm.revoked_at is null
                order by bm.created_at
                """,
            (rs, rowNum) -> new BaseMember(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("permission_level"),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            baseId
        );
    }

    @Override
    public UUID createTable(UUID workspaceId, UUID baseId, String name, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into base_tables
                    (id, workspace_id, base_id, name, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, now(), ?, now())
                """,
            id,
            workspaceId,
            baseId,
            name,
            createdBy,
            createdBy
        );
        return id;
    }

    @Override
    public void updateTable(UUID workspaceId, UUID tableId, String name, UUID updatedBy) {
        jdbcTemplate.update(
            """
                update base_tables
                set name = ?, updated_by = ?, updated_at = now()
                where workspace_id = ? and id = ? and archived_at is null
                """,
            name,
            updatedBy,
            workspaceId,
            tableId
        );
    }

    @Override
    public Optional<BaseTableSummary> findTable(UUID workspaceId, UUID tableId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select bt.id, bt.base_id, bt.name, bt.primary_field_id,
                           (select count(*) from base_fields bf where bf.table_id = bt.id and bf.archived_at is null) field_count,
                           (select count(*) from base_records br where br.table_id = bt.id and br.deleted_at is null) record_count,
                           bt.created_at, bt.updated_at
                    from base_tables bt
                    where bt.workspace_id = ? and bt.id = ? and bt.archived_at is null
                    """,
                this::mapTableSummary,
                workspaceId,
                tableId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<BaseTableSummary> listTables(UUID workspaceId, UUID baseId) {
        return jdbcTemplate.query(
            """
                select bt.id, bt.base_id, bt.name, bt.primary_field_id,
                       (select count(*) from base_fields bf where bf.table_id = bt.id and bf.archived_at is null) field_count,
                       (select count(*) from base_records br where br.table_id = bt.id and br.deleted_at is null) record_count,
                       bt.created_at, bt.updated_at
                from base_tables bt
                where bt.workspace_id = ? and bt.base_id = ? and bt.archived_at is null
                order by bt.created_at
                """,
            this::mapTableSummary,
            workspaceId,
            baseId
        );
    }

    @Override
    public UUID createField(
        UUID workspaceId,
        UUID tableId,
        String fieldKey,
        String name,
        String fieldType,
        Map<String, Object> config,
        boolean required,
        int sortOrder,
        UUID createdBy
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into base_fields
                    (id, workspace_id, table_id, field_key, name, field_type, config, required,
                     sort_order, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, now(), ?, now())
                """,
            id,
            workspaceId,
            tableId,
            fieldKey,
            name,
            fieldType,
            toJson(config),
            required,
            sortOrder,
            createdBy,
            createdBy
        );
        return id;
    }

    @Override
    public void setPrimaryField(UUID workspaceId, UUID tableId, UUID fieldId) {
        jdbcTemplate.update(
            "update base_tables set primary_field_id = ?, updated_at = now() where workspace_id = ? and id = ?",
            fieldId,
            workspaceId,
            tableId
        );
    }

    @Override
    public Optional<BaseField> findField(UUID workspaceId, UUID fieldId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, table_id, field_key, name, field_type, config, required, sort_order, created_at, updated_at
                    from base_fields
                    where workspace_id = ? and id = ? and archived_at is null
                    """,
                this::mapField,
                workspaceId,
                fieldId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<BaseField> listFields(UUID workspaceId, UUID tableId) {
        return jdbcTemplate.query(
            """
                select id, table_id, field_key, name, field_type, config, required, sort_order, created_at, updated_at
                from base_fields
                where workspace_id = ? and table_id = ? and archived_at is null
                order by sort_order, created_at
                """,
            this::mapField,
            workspaceId,
            tableId
        );
    }

    @Override
    public int nextRecordNumber(UUID tableId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) + 1 from base_records where table_id = ?", Integer.class, tableId);
        return count == null ? 1 : count;
    }

    @Override
    public UUID createRecord(UUID workspaceId, UUID tableId, int recordNo, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into base_records
                    (id, workspace_id, table_id, record_no, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?, now(), ?, now())
                """,
            id,
            workspaceId,
            tableId,
            recordNo,
            createdBy,
            createdBy
        );
        return id;
    }

    @Override
    public void updateRecordTouched(UUID workspaceId, UUID recordId, UUID updatedBy) {
        jdbcTemplate.update(
            "update base_records set updated_by = ?, updated_at = now() where workspace_id = ? and id = ? and deleted_at is null",
            updatedBy,
            workspaceId,
            recordId
        );
    }

    @Override
    public void deleteRecord(UUID workspaceId, UUID recordId, UUID deletedBy) {
        jdbcTemplate.update(
            "update base_records set deleted_at = now(), updated_by = ?, updated_at = now() where workspace_id = ? and id = ? and deleted_at is null",
            deletedBy,
            workspaceId,
            recordId
        );
    }

    @Override
    public Optional<BaseRecord> findRecord(UUID workspaceId, UUID recordId) {
        List<BaseRecord> records = mapRecords(jdbcTemplate.query(
            """
                select br.id, br.table_id, br.record_no, br.created_by, cu.display_name created_by_name,
                       br.created_at, br.updated_by, uu.display_name updated_by_name, br.updated_at,
                       bt.primary_field_id
                from base_records br
                join base_tables bt on bt.id = br.table_id
                join users cu on cu.id = br.created_by
                join users uu on uu.id = br.updated_by
                where br.workspace_id = ? and br.id = ? and br.deleted_at is null
                """,
            this::mapRecordShell,
            workspaceId,
            recordId
        ));
        return records.stream().findFirst();
    }

    @Override
    public List<BaseRecord> listRecords(UUID workspaceId, UUID tableId, List<BaseFilter> filters, List<BaseSort> sorts, int limit, int offset) {
        QueryParts query = recordQuery(workspaceId, tableId, filters, sorts);
        query.sql().append(" limit ? offset ?");
        query.args().add(limit);
        query.args().add(offset);
        return mapRecords(jdbcTemplate.query(query.sql().toString(), this::mapRecordShell, query.args().toArray()));
    }

    @Override
    public int countRecords(UUID workspaceId, UUID tableId, List<BaseFilter> filters) {
        QueryParts query = recordQuery(workspaceId, tableId, filters, List.of());
        String countSql = "select count(*) from (" + query.sql() + ") records";
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, query.args().toArray());
        return count == null ? 0 : count;
    }

    @Override
    public void upsertValue(UUID workspaceId, UUID recordId, UUID fieldId, Object value, String valueText, Number valueNumber, String valueDate, UUID updatedBy) {
        jdbcTemplate.update(
            """
                insert into base_record_values
                    (id, workspace_id, record_id, field_id, value_json, value_text, value_number, value_date, updated_by, updated_at)
                values (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, now())
                on conflict (record_id, field_id)
                do update set value_json = excluded.value_json,
                              value_text = excluded.value_text,
                              value_number = excluded.value_number,
                              value_date = excluded.value_date,
                              updated_by = excluded.updated_by,
                              updated_at = now()
                """,
            UUID.randomUUID(),
            workspaceId,
            recordId,
            fieldId,
            toJson(value),
            valueText,
            valueNumber,
            valueDate == null ? null : Date.valueOf(valueDate),
            updatedBy
        );
    }

    @Override
    public UUID createView(UUID workspaceId, UUID tableId, String name, List<BaseFilter> filters, List<BaseSort> sorts, List<UUID> visibleFieldIds, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into base_views
                    (id, workspace_id, table_id, name, filters, sorts, visible_field_ids, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, now(), ?, now())
                """,
            id,
            workspaceId,
            tableId,
            name,
            toJson(filters),
            toJson(sorts),
            toJson(visibleFieldIds == null ? List.of() : visibleFieldIds),
            createdBy,
            createdBy
        );
        return id;
    }

    @Override
    public List<BaseView> listViews(UUID workspaceId, UUID tableId) {
        return jdbcTemplate.query(
            """
                select id, table_id, name, filters, sorts, visible_field_ids, created_at, updated_at
                from base_views
                where workspace_id = ? and table_id = ? and archived_at is null
                order by created_at
                """,
            (rs, rowNum) -> new BaseView(
                rs.getObject("id", UUID.class),
                rs.getObject("table_id", UUID.class),
                rs.getString("name"),
                readFilters(rs.getString("filters")),
                readSorts(rs.getString("sorts")),
                readUuidList(rs.getString("visible_field_ids")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            ),
            workspaceId,
            tableId
        );
    }

    @Override
    public UUID createRecordComment(UUID workspaceId, UUID recordId, UUID authorId, String content) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into base_record_comments
                    (id, workspace_id, record_id, author_id, content, created_at)
                values (?, ?, ?, ?, ?, now())
                """,
            id,
            workspaceId,
            recordId,
            authorId,
            content
        );
        return id;
    }

    @Override
    public List<BaseRecordComment> listRecordComments(UUID workspaceId, UUID recordId) {
        return jdbcTemplate.query(
            """
                select c.id, c.record_id, c.author_id, u.display_name author_name, c.content, c.created_at
                from base_record_comments c
                join users u on u.id = c.author_id
                where c.workspace_id = ? and c.record_id = ? and c.deleted_at is null
                order by c.created_at
                """,
            (rs, rowNum) -> new BaseRecordComment(
                rs.getObject("id", UUID.class),
                rs.getObject("record_id", UUID.class),
                rs.getObject("author_id", UUID.class),
                rs.getString("author_name"),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            recordId
        );
    }

    @Override
    public void upsertRecordRelation(UUID workspaceId, UUID recordId, String targetType, UUID targetId, String relationType, UUID createdBy) {
        jdbcTemplate.update(
            """
                insert into base_record_relations
                    (id, workspace_id, record_id, target_type, target_id, relation_type, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?, now())
                on conflict (record_id, target_type, target_id, relation_type)
                do update set deleted_at = null
                """,
            UUID.randomUUID(),
            workspaceId,
            recordId,
            targetType,
            targetId,
            relationType,
            createdBy
        );
    }

    @Override
    public List<BaseRecordRelationRecord> listRecordRelations(UUID workspaceId, UUID recordId) {
        return jdbcTemplate.query(
            """
                select r.id, r.record_id, r.target_type, r.target_id, r.relation_type,
                       r.created_by, u.display_name created_by_name, r.created_at
                from base_record_relations r
                join users u on u.id = r.created_by
                where r.workspace_id = ? and r.record_id = ? and r.deleted_at is null
                order by r.created_at
                """,
            this::mapRelationRecord,
            workspaceId,
            recordId
        );
    }

    @Override
    public List<BaseRecordRelationRecord> listReverseRecordRelations(UUID workspaceId, String targetType, UUID targetId) {
        return jdbcTemplate.query(
            """
                select r.id, r.record_id, r.target_type, r.target_id, r.relation_type,
                       r.created_by, u.display_name created_by_name, r.created_at
                from base_record_relations r
                join users u on u.id = r.created_by
                where r.workspace_id = ? and r.target_type = ? and r.target_id = ? and r.deleted_at is null
                order by r.created_at
                """,
            this::mapRelationRecord,
            workspaceId,
            targetType,
            targetId
        );
    }

    @Override
    public void appendRecordActivity(UUID workspaceId, UUID recordId, UUID actorId, String action, Map<String, Object> metadata) {
        jdbcTemplate.update(
            """
                insert into base_record_activity_logs
                    (id, workspace_id, record_id, actor_id, action, metadata, created_at)
                values (?, ?, ?, ?, ?, ?::jsonb, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            recordId,
            actorId,
            action,
            toJson(metadata == null ? Map.of() : metadata)
        );
    }

    @Override
    public List<BaseRecordActivity> listRecordActivities(UUID workspaceId, UUID recordId) {
        return jdbcTemplate.query(
            """
                select a.id, a.record_id, a.actor_id, u.display_name actor_name,
                       a.action, a.metadata, a.created_at
                from base_record_activity_logs a
                join users u on u.id = a.actor_id
                where a.workspace_id = ? and a.record_id = ?
                order by a.created_at desc
                limit 30
                """,
            (rs, rowNum) -> new BaseRecordActivity(
                rs.getObject("id", UUID.class),
                rs.getObject("record_id", UUID.class),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_name"),
                rs.getString("action"),
                readMap(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant()
            ),
            workspaceId,
            recordId
        );
    }

    private QueryParts recordQuery(UUID workspaceId, UUID tableId, List<BaseFilter> filters, List<BaseSort> sorts) {
        StringBuilder sql = new StringBuilder("""
            select br.id, br.table_id, br.record_no, br.created_by, cu.display_name created_by_name,
                   br.created_at, br.updated_by, uu.display_name updated_by_name, br.updated_at,
                   bt.primary_field_id
            from base_records br
            join base_tables bt on bt.id = br.table_id
            join users cu on cu.id = br.created_by
            join users uu on uu.id = br.updated_by
            """);
        List<Object> args = new ArrayList<>();
        int index = 0;
        for (BaseFilter filter : filters) {
            sql.append(" join base_record_values fv").append(index).append(" on fv").append(index).append(".record_id = br.id and fv").append(index).append(".field_id = ?");
            args.add(filter.fieldId());
            index++;
        }
        int sortIndex = 0;
        for (BaseSort sort : sorts) {
            sql.append(" left join base_record_values sv").append(sortIndex).append(" on sv").append(sortIndex).append(".record_id = br.id and sv").append(sortIndex).append(".field_id = ?");
            args.add(sort.fieldId());
            sortIndex++;
        }
        sql.append(" where br.workspace_id = ? and br.table_id = ? and br.deleted_at is null");
        args.add(workspaceId);
        args.add(tableId);

        index = 0;
        for (BaseFilter filter : filters) {
            appendFilter(sql, args, "fv" + index, filter);
            index++;
        }

        if (sorts.isEmpty()) {
            sql.append(" order by br.updated_at desc");
        } else {
            sql.append(" order by ");
            List<String> sortParts = new ArrayList<>();
            for (int i = 0; i < sorts.size(); i++) {
                String direction = "desc".equalsIgnoreCase(sorts.get(i).direction()) ? "desc" : "asc";
                sortParts.add("coalesce(sv" + i + ".value_text, sv" + i + ".value_number::text, sv" + i + ".value_date::text) " + direction);
            }
            sql.append(String.join(", ", sortParts));
            sql.append(", br.updated_at desc");
        }
        return new QueryParts(sql, args);
    }

    private void appendFilter(StringBuilder sql, List<Object> args, String alias, BaseFilter filter) {
        String operator = filter.operator() == null ? "eq" : filter.operator();
        if ("contains".equals(operator)) {
            sql.append(" and lower(").append(alias).append(".value_text) like ?");
            args.add("%" + filter.value().toString().toLowerCase() + "%");
            return;
        }
        if ("gt".equals(operator) || "lt".equals(operator)) {
            if (filter.value() instanceof Number number) {
                sql.append(" and ").append(alias).append(".value_number ").append("gt".equals(operator) ? ">" : "<").append(" ?");
                args.add(number);
            } else {
                sql.append(" and ").append(alias).append(".value_date ").append("gt".equals(operator) ? ">" : "<").append(" ?");
                args.add(Date.valueOf(filter.value().toString()));
            }
            return;
        }
        sql.append(" and ").append(alias).append(".value_text = ?");
        args.add(filter.value() == null ? null : filter.value().toString());
    }

    private List<BaseRecord> mapRecords(List<RecordShell> shells) {
        if (shells.isEmpty()) {
            return List.of();
        }
        Map<UUID, RecordShell> shellById = new LinkedHashMap<>();
        for (RecordShell shell : shells) {
            shellById.put(shell.id(), shell);
        }
        String placeholders = String.join(",", shellById.keySet().stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>(shellById.keySet());
        Map<UUID, Map<String, Object>> values = new LinkedHashMap<>();
        Map<UUID, String> primaryTexts = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
                select v.record_id, v.field_id, f.name, v.value_json
                from base_record_values v
                join base_fields f on f.id = v.field_id
                where v.record_id in (%s)
                order by f.sort_order, f.created_at
                """.formatted(placeholders),
            rs -> {
                UUID recordId = rs.getObject("record_id", UUID.class);
                UUID fieldId = rs.getObject("field_id", UUID.class);
                Object value = readObject(rs.getString("value_json"));
                values.computeIfAbsent(recordId, id -> new LinkedHashMap<>()).put(fieldId.toString(), value);
                values.get(recordId).put(rs.getString("name"), value);
                UUID primaryFieldId = shellById.get(recordId).primaryFieldId();
                if (fieldId.equals(primaryFieldId) || primaryTexts.get(recordId) == null) {
                    primaryTexts.put(recordId, value == null ? "" : value.toString());
                }
            },
            args.toArray()
        );
        return shells.stream()
            .map(shell -> new BaseRecord(
                shell.id(),
                shell.tableId(),
                shell.recordNo(),
                primaryTexts.getOrDefault(shell.id(), "#" + shell.recordNo()),
                values.getOrDefault(shell.id(), Map.of()),
                shell.createdBy(),
                shell.createdByName(),
                shell.createdAt(),
                shell.updatedBy(),
                shell.updatedByName(),
                shell.updatedAt()
            ))
            .toList();
    }

    private BaseSummary mapBaseSummary(ResultSet rs, int rowNum) throws SQLException {
        return new BaseSummary(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("permission_level"),
            rs.getInt("table_count"),
            rs.getInt("record_count"),
            rs.getObject("created_by", UUID.class),
            rs.getString("created_by_name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("updated_by", UUID.class),
            rs.getString("updated_by_name"),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private BaseTableSummary mapTableSummary(ResultSet rs, int rowNum) throws SQLException {
        return new BaseTableSummary(
            rs.getObject("id", UUID.class),
            rs.getObject("base_id", UUID.class),
            rs.getString("name"),
            rs.getObject("primary_field_id", UUID.class),
            rs.getInt("field_count"),
            rs.getInt("record_count"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private BaseField mapField(ResultSet rs, int rowNum) throws SQLException {
        return new BaseField(
            rs.getObject("id", UUID.class),
            rs.getObject("table_id", UUID.class),
            rs.getString("field_key"),
            rs.getString("name"),
            rs.getString("field_type"),
            readMap(rs.getString("config")),
            rs.getBoolean("required"),
            rs.getInt("sort_order"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private RecordShell mapRecordShell(ResultSet rs, int rowNum) throws SQLException {
        return new RecordShell(
            rs.getObject("id", UUID.class),
            rs.getObject("table_id", UUID.class),
            rs.getInt("record_no"),
            rs.getObject("primary_field_id", UUID.class),
            rs.getObject("created_by", UUID.class),
            rs.getString("created_by_name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getObject("updated_by", UUID.class),
            rs.getString("updated_by_name"),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    private BaseRecordRelationRecord mapRelationRecord(ResultSet rs, int rowNum) throws SQLException {
        return new BaseRecordRelationRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("record_id", UUID.class),
            rs.getString("target_type"),
            rs.getObject("target_id", UUID.class),
            rs.getString("relation_type"),
            rs.getObject("created_by", UUID.class),
            rs.getString("created_by_name"),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private BaseSummary withPermission(BaseSummary base, String permissionLevel) {
        return new BaseSummary(
            base.id(),
            base.name(),
            base.description(),
            base.status(),
            permissionLevel,
            base.tableCount(),
            base.recordCount(),
            base.createdBy(),
            base.createdByName(),
            base.createdAt(),
            base.updatedBy(),
            base.updatedByName(),
            base.updatedAt()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON value", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Object readObject(String value) {
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private List<BaseFilter> readFilters(String value) {
        try {
            return objectMapper.readValue(value, FILTER_LIST_TYPE);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<BaseSort> readSorts(String value) {
        try {
            return objectMapper.readValue(value, SORT_LIST_TYPE);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<UUID> readUuidList(String value) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value, UUID_LIST_TYPE);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private record QueryParts(StringBuilder sql, List<Object> args) {
    }

    private record RecordShell(
        UUID id,
        UUID tableId,
        int recordNo,
        UUID primaryFieldId,
        UUID createdBy,
        String createdByName,
        java.time.Instant createdAt,
        UUID updatedBy,
        String updatedByName,
        java.time.Instant updatedAt
    ) {
    }
}
