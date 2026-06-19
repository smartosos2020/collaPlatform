package com.colla.platform.modules.audit.infrastructure;

import com.colla.platform.modules.audit.domain.AuditModels.AuditLogEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditRepository implements AuditRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAuditRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(
        UUID workspaceId,
        UUID actorId,
        String action,
        String targetType,
        UUID targetId,
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata
    ) {
        jdbcTemplate.update(
            """
                insert into audit_logs
                    (id, workspace_id, actor_id, action, target_type, target_id, ip_address, user_agent, metadata, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """,
            UUID.randomUUID(),
            workspaceId,
            actorId,
            action,
            targetType,
            targetId,
            ipAddress,
            userAgent,
            writeJson(metadata)
        );
    }

    @Override
    public List<AuditLogEntry> list(UUID workspaceId, String action, String targetType, UUID targetId, UUID actorId, int limit) {
        StringBuilder sql = new StringBuilder(
            """
                select a.id, a.workspace_id, a.actor_id, u.display_name actor_name, a.action, a.target_type,
                       a.target_id, a.ip_address, a.user_agent, a.metadata, a.created_at
                from audit_logs a
                left join users u on u.id = a.actor_id
                where a.workspace_id = ?
                """
        );
        List<Object> args = new ArrayList<>();
        args.add(workspaceId);
        if (action != null && !action.isBlank()) {
            sql.append(" and a.action = ?");
            args.add(action);
        }
        if (targetType != null && !targetType.isBlank()) {
            sql.append(" and a.target_type = ?");
            args.add(targetType);
        }
        if (targetId != null) {
            sql.append(" and a.target_id = ?");
            args.add(targetId);
        }
        if (actorId != null) {
            sql.append(" and a.actor_id = ?");
            args.add(actorId);
        }
        sql.append(" order by a.created_at desc limit ?");
        args.add(Math.min(Math.max(limit, 1), 200));
        return jdbcTemplate.query(sql.toString(), this::mapEntry, args.toArray());
    }

    private AuditLogEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogEntry(
            rs.getObject("id", UUID.class),
            rs.getObject("workspace_id", UUID.class),
            rs.getObject("actor_id", UUID.class),
            rs.getString("actor_name"),
            rs.getString("action"),
            rs.getString("target_type"),
            rs.getObject("target_id", UUID.class),
            rs.getString("ip_address"),
            rs.getString("user_agent"),
            readJson(rs.getString("metadata")),
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private String writeJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid audit metadata", exception);
        }
    }

    private Map<String, Object> readJson(String metadata) {
        try {
            return objectMapper.readValue(metadata == null ? "{}" : metadata, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid audit metadata", exception);
        }
    }
}
