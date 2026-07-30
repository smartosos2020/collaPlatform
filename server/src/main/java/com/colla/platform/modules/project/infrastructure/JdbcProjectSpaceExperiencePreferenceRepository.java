package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreferenceConflictException;
import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProjectSpaceExperiencePreferenceRepository
    implements ProjectSpaceExperiencePreferenceRepository {
    private final JdbcTemplate jdbc;

    public JdbcProjectSpaceExperiencePreferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ExperiencePreference> find(UUID workspaceId, UUID spaceId, UUID userId) {
        return jdbc.query("""
            select schema_version, mode, version, updated_at
              from project_space_experience_preferences
             where workspace_id=? and space_id=? and user_id=?
            """, (rs, row) -> new ExperiencePreference(
                rs.getInt("schema_version"),
                rs.getString("mode"),
                rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant()
            ), workspaceId, spaceId, userId).stream().findFirst();
    }

    @Override
    public ExperiencePreference save(
        UUID workspaceId,
        UUID spaceId,
        UUID userId,
        int schemaVersion,
        String mode,
        long expectedVersion
    ) {
        int changed;
        if (expectedVersion == 0) {
            changed = jdbc.update("""
                insert into project_space_experience_preferences(
                    id, workspace_id, space_id, user_id, schema_version, mode, version, updated_at
                ) values (?, ?, ?, ?, ?, ?, 1, now())
                on conflict (workspace_id, space_id, user_id) do nothing
                """, UUID.randomUUID(), workspaceId, spaceId, userId, schemaVersion, mode);
        } else {
            changed = jdbc.update("""
                update project_space_experience_preferences
                   set schema_version=?, mode=?, version=version+1, updated_at=now()
                 where workspace_id=? and space_id=? and user_id=? and version=?
                """, schemaVersion, mode, workspaceId, spaceId, userId, expectedVersion);
        }
        if (changed != 1) {
            throw new ExperiencePreferenceConflictException();
        }
        return find(workspaceId, spaceId, userId).orElseThrow();
    }

    @Override
    public void reset(UUID workspaceId, UUID spaceId, UUID userId, long expectedVersion) {
        int changed = jdbc.update("""
            delete from project_space_experience_preferences
             where workspace_id=? and space_id=? and user_id=? and version=?
            """, workspaceId, spaceId, userId, expectedVersion);
        if (changed != 1) {
            throw new ExperiencePreferenceConflictException();
        }
    }
}
