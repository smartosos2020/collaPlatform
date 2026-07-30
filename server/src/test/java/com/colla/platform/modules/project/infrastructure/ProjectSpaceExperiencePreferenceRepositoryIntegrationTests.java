package com.colla.platform.modules.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.ProjectSpaceExperienceModels.ExperiencePreferenceConflictException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ProjectSpaceExperiencePreferenceRepositoryIntegrationTests {
    @Autowired
    private ProjectSpaceExperiencePreferenceRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v140PreferenceIsCasVersionedResettableAndIsolatedByUser() {
        Fixture fixture = fixture();

        var owner = repository.save(
            fixture.workspaceId(), fixture.spaceId(), fixture.ownerId(), 1, "advanced", 0
        );
        var member = repository.save(
            fixture.workspaceId(), fixture.spaceId(), fixture.memberId(), 1, "simple", 0
        );
        assertThat(owner.version()).isEqualTo(1);
        assertThat(member.mode()).isEqualTo("simple");

        var revised = repository.save(
            fixture.workspaceId(), fixture.spaceId(), fixture.ownerId(), 1, "simple", 1
        );
        assertThat(revised.version()).isEqualTo(2);
        assertThat(repository.find(
            fixture.workspaceId(), fixture.spaceId(), fixture.memberId()
        )).get().extracting(value -> value.mode()).isEqualTo("simple");

        assertThatThrownBy(() -> repository.save(
            fixture.workspaceId(), fixture.spaceId(), fixture.ownerId(), 1, "advanced", 1
        )).isInstanceOf(ExperiencePreferenceConflictException.class);

        repository.reset(
            fixture.workspaceId(), fixture.spaceId(), fixture.ownerId(), 2
        );
        assertThat(repository.find(
            fixture.workspaceId(), fixture.spaceId(), fixture.ownerId()
        )).isEmpty();
        assertThat(repository.find(
            fixture.workspaceId(), fixture.spaceId(), fixture.memberId()
        )).isPresent();
    }

    private Fixture fixture() {
        UUID workspaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        String suffix = suffix();
        jdbc.update("""
            insert into workspaces(id, name, slug, status, created_at, updated_at)
            values (?, ?, ?, 'active', now(), now())
            """, workspaceId, "Experience " + suffix, "experience-" + suffix);
        jdbc.update("""
            insert into users(
                id, workspace_id, username, password_hash, display_name, status, created_at, updated_at
            ) values (?, ?, ?, 'unused', 'Owner', 'active', now(), now())
            """, ownerId, workspaceId, "experience_owner_" + suffix);
        jdbc.update("""
            insert into users(
                id, workspace_id, username, password_hash, display_name, status, created_at, updated_at
            ) values (?, ?, ?, 'unused', 'Member', 'active', now(), now())
            """, memberId, workspaceId, "experience_member_" + suffix);
        jdbc.update("""
            insert into project_spaces(
                id, workspace_id, space_key, name, description, status, visibility,
                version, created_by, created_at, updated_by, updated_at
            ) values (?, ?, ?, 'Experience Space', '', 'active', 'discoverable',
                0, ?, now(), ?, now())
            """, spaceId, workspaceId, "experience-" + suffix, ownerId, ownerId);
        return new Fixture(workspaceId, spaceId, ownerId, memberId);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private record Fixture(
        UUID workspaceId,
        UUID spaceId,
        UUID ownerId,
        UUID memberId
    ) {
    }
}
