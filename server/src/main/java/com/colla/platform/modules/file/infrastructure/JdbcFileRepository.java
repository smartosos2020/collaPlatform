package com.colla.platform.modules.file.infrastructure;

import com.colla.platform.modules.file.domain.FileModels.FileMetadata;
import com.colla.platform.modules.file.domain.FileModels.FileUsage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFileRepository implements FileRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public FileMetadata createPending(UUID workspaceId, String objectKey, String originalName, String contentType, long sizeBytes, UUID uploadedBy) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into files
                    (id, workspace_id, object_key, original_name, content_type, size_bytes, status, uploaded_by, created_at)
                values (?, ?, ?, ?, ?, ?, 'pending', ?, now())
                """,
            id,
            workspaceId,
            objectKey,
            originalName,
            contentType,
            sizeBytes,
            uploadedBy
        );
        return find(workspaceId, id).orElseThrow();
    }

    @Override
    public Optional<FileMetadata> complete(UUID workspaceId, UUID fileId, UUID uploadedBy) {
        jdbcTemplate.update(
            """
                update files
                set status = 'completed', completed_at = coalesce(completed_at, now())
                where workspace_id = ? and id = ? and uploaded_by = ? and deleted_at is null
                """,
            workspaceId,
            fileId,
            uploadedBy
        );
        return find(workspaceId, fileId);
    }

    @Override
    public Optional<FileMetadata> find(UUID workspaceId, UUID fileId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    select id, object_key, original_name, content_type, size_bytes, status, uploaded_by, created_at, completed_at
                    from files
                    where workspace_id = ? and id = ? and deleted_at is null
                    """,
                this::mapFile,
                workspaceId,
                fileId
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void addUsage(UUID workspaceId, UUID fileId, String targetType, UUID targetId, UUID createdBy) {
        jdbcTemplate.update(
            """
                insert into file_usages (id, workspace_id, file_id, target_type, target_id, created_by, created_at)
                values (?, ?, ?, ?, ?, ?, now())
                on conflict (workspace_id, file_id, target_type, target_id) do nothing
                """,
            UUID.randomUUID(),
            workspaceId,
            fileId,
            targetType,
            targetId,
            createdBy
        );
    }

    @Override
    public List<FileUsage> listUsages(UUID workspaceId, UUID fileId) {
        return jdbcTemplate.query(
            """
                select target_type, target_id
                from file_usages
                where workspace_id = ? and file_id = ?
                """,
            (rs, rowNum) -> new FileUsage(rs.getString("target_type"), rs.getObject("target_id", UUID.class)),
            workspaceId,
            fileId
        );
    }

    private FileMetadata mapFile(ResultSet rs, int rowNum) throws SQLException {
        return new FileMetadata(
            rs.getObject("id", UUID.class),
            rs.getString("object_key"),
            rs.getString("original_name"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            rs.getString("status"),
            rs.getObject("uploaded_by", UUID.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()
        );
    }
}
