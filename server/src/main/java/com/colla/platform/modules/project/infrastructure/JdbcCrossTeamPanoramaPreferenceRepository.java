package com.colla.platform.modules.project.infrastructure;

import com.colla.platform.modules.project.domain.CrossTeamPanoramaModels.PanoramaPreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCrossTeamPanoramaPreferenceRepository
    implements CrossTeamPanoramaPreferenceRepository {
    private final JdbcTemplate jdbc;

    public JdbcCrossTeamPanoramaPreferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PanoramaPreference> find(
        UUID workspaceId, UUID spaceId, UUID userId
    ) {
        return jdbc.query("""
            select compact,window_days,version
              from project_cross_team_panorama_preferences
             where workspace_id=? and space_id=? and user_id=?
            """, (rs, row) -> new PanoramaPreference(
                rs.getBoolean("compact"), rs.getInt("window_days"),
                rs.getLong("version")
            ), workspaceId, spaceId, userId).stream().findFirst();
    }

    @Override
    public PanoramaPreference save(
        UUID workspaceId, UUID spaceId, UUID userId,
        boolean compact, int windowDays, long expectedVersion
    ) {
        int updated;
        if (expectedVersion == 0) {
            updated = jdbc.update("""
                insert into project_cross_team_panorama_preferences(
                  id,workspace_id,space_id,user_id,compact,window_days,version
                ) values (?,?,?,?,?,?,1)
                on conflict (workspace_id,space_id,user_id) do nothing
                """, UUID.randomUUID(), workspaceId, spaceId, userId, compact, windowDays);
        } else {
            updated = jdbc.update("""
                update project_cross_team_panorama_preferences
                   set compact=?,window_days=?,version=version+1,updated_at=now()
                 where workspace_id=? and space_id=? and user_id=? and version=?
                """, compact, windowDays, workspaceId, spaceId, userId, expectedVersion);
        }
        if (updated != 1) throw new IllegalStateException("panorama preference conflict");
        return find(workspaceId, spaceId, userId).orElseThrow();
    }
}
