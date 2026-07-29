package com.colla.platform.modules.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.colla.platform.modules.search.infrastructure.SearchRepository.ProjectionOperation;
import com.colla.platform.modules.search.application.SearchIndexService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SearchProjectionPersistenceIntegrationTests {
    private static final UUID DEFAULT_WORKSPACE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private SearchRepository searchRepository;

    @Autowired
    private SearchIndexService searchIndexService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void staleProjectionCannotOverwriteNewerDeleteWaterline() {
        UUID objectId = UUID.randomUUID();

        assertThat(searchRepository.projectObject(
            DEFAULT_WORKSPACE_ID,
            "work_item",
            objectId,
            5,
            ProjectionOperation.DELETE
        )).isTrue();
        assertThat(searchRepository.projectObject(
            DEFAULT_WORKSPACE_ID,
            "work_item",
            objectId,
            4,
            ProjectionOperation.UPSERT
        )).isFalse();

        Map<String, Object> waterline = jdbcTemplate.queryForMap(
            """
                select source_version, operation
                from search_projection_versions
                where workspace_id = ? and object_type = ? and object_id = ?
                """,
            DEFAULT_WORKSPACE_ID,
            "work_item",
            objectId
        );
        Integer indexed = jdbcTemplate.queryForObject(
            """
                select count(*)
                from search_index_entries
                where workspace_id = ? and object_type = ? and object_id = ?
                """,
            Integer.class,
            DEFAULT_WORKSPACE_ID,
            "work_item",
            objectId
        );

        assertThat(waterline.get("source_version")).isEqualTo(5L);
        assertThat(waterline.get("operation")).isEqualTo("delete");
        assertThat(indexed).isZero();
    }

    @Test
    void canonicalWorkItemProjectionSchemaAndResumableRebuildAreAvailable() {
        Integer projectionColumns = jdbcTemplate.queryForObject(
            """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'search_index_entries'
                  and column_name in ('space_id', 'object_subtype', 'object_status', 'source_version')
                """,
            Integer.class
        );

        var page = searchIndexService.rebuildBatch(DEFAULT_WORKSPACE_ID, "work_item", null, 10);

        assertThat(projectionColumns).isEqualTo(4);
        assertThat(page.objectType()).isEqualTo("work_item");
        assertThat(page.processed()).isBetween(0, 10);
    }
}
