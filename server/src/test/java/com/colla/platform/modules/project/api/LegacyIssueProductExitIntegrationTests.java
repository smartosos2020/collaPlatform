package com.colla.platform.modules.project.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.project.infrastructure.ProjectRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
class LegacyIssueProductExitIntegrationTests {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void v139RetiresLegacyProductRegistrationsWithoutDeletingHistoricalEvidence() {
        assertThat(jdbc.queryForObject(
            "select max(version) from flyway_schema_history", String.class
        )).isEqualTo("141");
        assertThat(jdbc.queryForObject(
            "select count(*) from permissions where code like 'issue.%'", Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from object_type_rules where object_type='issue'", Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
            "select count(*) from search_index_entries where object_type='issue'", Integer.class
        )).isZero();

        for (String table : Set.of(
            "projects",
            "issues",
            "project_work_item_migration_batches",
            "project_work_item_migration_units",
            "project_legacy_work_item_maps",
            "project_legacy_audit_snapshots",
            "project_legacy_removal_decisions"
        )) {
            assertThat(jdbc.queryForObject(
                "select to_regclass(?) is not null", Boolean.class, table
            )).as("historical evidence table %s", table).isTrue();
        }
    }

    @Test
    void runtimeExposesOnlyCanonicalProjectProductRoutesAndHistoryOnlyRepositoryMethods() {
        Set<String> paths = applicationContext
            .getBeansOfType(RequestMappingHandlerMapping.class)
            .values()
            .stream()
            .flatMap(mapping -> mapping.getHandlerMethods().keySet().stream())
            .flatMap(mapping -> mapping.getPatternValues().stream())
            .collect(Collectors.toSet());

        assertThat(paths).noneMatch(path ->
            path.startsWith("/api/projects")
                || path.startsWith("/api/issues")
                || path.equals("/api/my/issues")
                || path.endsWith("/convert-to-issue")
                || path.endsWith("/issues/from-selection")
        );
        assertThat(paths).anyMatch(path -> path.startsWith("/api/project-spaces"));
        assertThat(paths).contains(
            "/api/compat/work-items/legacy/issues/{issueId}/location"
        );

        assertThat(ProjectRepository.class.getDeclaredMethods())
            .extracting(method -> method.getName())
            .containsExactlyInAnyOrder("legacyProjectExists", "isProjectMember");
    }
}
