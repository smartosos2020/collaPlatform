package com.colla.platform.modules.project.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectRepository implements ProjectRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean legacyProjectExists(UUID workspaceId, UUID projectId) {
        Boolean exists = jdbcTemplate.queryForObject(
            "select exists(select 1 from projects where workspace_id=? and id=?)",
            Boolean.class,
            workspaceId,
            projectId
        );
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean isProjectMember(UUID workspaceId, UUID projectId, UUID userId) {
        Boolean exists = jdbcTemplate.queryForObject(
            """
                select exists(
                    select 1
                    from project_members
                    where workspace_id=? and project_id=? and user_id=?
                )
                """,
            Boolean.class,
            workspaceId,
            projectId,
            userId
        );
        return Boolean.TRUE.equals(exists);
    }
}
