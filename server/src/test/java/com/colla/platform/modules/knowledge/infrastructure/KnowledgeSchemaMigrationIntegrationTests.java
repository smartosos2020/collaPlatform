package com.colla.platform.modules.knowledge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class KnowledgeSchemaMigrationIntegrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;


    @Test
    void activeSchemaUsesKnowledgeItemAndContentNamesOnly() {
        Integer present = jdbcTemplate.queryForObject(
            """
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'knowledge_base_items',
                    'knowledge_content_blocks',
                    'knowledge_content_versions',
                    'knowledge_content_comments',
                    'knowledge_content_collaboration_states',
                    'knowledge_content_templates',
                    'knowledge_item_relations',
                    'knowledge_item_share_links',
                    'search_index_entries',
                    'knowledge_content_canonical_documents',
                    'knowledge_content_migration_batches',
                    'knowledge_content_migration_items'
                  )
                """,
            Integer.class
        );
        assertEquals(12, present);
        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema='public' and table_name='knowledge_item_permissions'",
            Integer.class
        ));

        Integer oldTables = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema='public' and (table_name='documents' or table_name like 'document_%' or table_name='search_index_documents')",
            Integer.class
        );
        Integer oldColumns = jdbcTemplate.queryForObject(
            """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name in (
                    'knowledge_base_items',
                    'knowledge_content_blocks',
                    'knowledge_content_versions',
                    'knowledge_content_comments',
                    'knowledge_content_collaboration_states',
                    'knowledge_content_templates',
                    'knowledge_item_relations',
                    'knowledge_item_share_links',
                    'search_index_entries'
                  )
                  and (column_name like '%document%' or column_name in ('doc_type', 'node_kind'))
                """,
            Integer.class
        );
        assertEquals(0, oldTables);
        assertEquals(0, oldColumns);
        Integer retiredSnapshotColumns = jdbcTemplate.queryForObject(
            """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and (
                    (table_name = 'knowledge_base_items' and column_name = 'content')
                    or (table_name = 'knowledge_content_versions' and column_name = 'content')
                    or (table_name = 'knowledge_content_collaboration_states' and column_name = 'snapshot_content')
                    or (table_name = 'knowledge_content_templates' and column_name = 'content')
                  )
                """,
            Integer.class
        );
        assertEquals(0, retiredSnapshotColumns);
        assertTrue(jdbcTemplate.queryForObject(
            "select exists(select 1 from object_type_rules where object_type='knowledge_content')",
            Boolean.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.columns where table_schema='public' and table_name='knowledge_content_versions' and column_name='schema_version'",
            Integer.class
        ));
        assertEquals(3, jdbcTemplate.queryForObject(
            "select count(*) from information_schema.columns where table_schema='public' and column_name='canonical_snapshot' and table_name in ('knowledge_content_versions', 'knowledge_content_templates', 'knowledge_content_collaboration_states')",
            Integer.class
        ));
    }

    @Test
    void v049SchemaCanUpgradeToCanonicalContractInAnIsolatedDatabase() {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16");
        container.start();
        try {
            Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .target("49")
                .load()
                .migrate();
            Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .load()
                .migrate();

            org.postgresql.ds.PGSimpleDataSource dataSource = new org.postgresql.ds.PGSimpleDataSource();
            dataSource.setURL(container.getJdbcUrl());
            dataSource.setUser(container.getUsername());
            dataSource.setPassword(container.getPassword());
            JdbcTemplate upgradeJdbcTemplate = new JdbcTemplate(dataSource);
            assertEquals("051", upgradeJdbcTemplate.queryForObject(
                "select max(version) from flyway_schema_history",
                String.class
            ));
            assertEquals(1, upgradeJdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'knowledge_content_versions' and column_name = 'canonical_snapshot'",
                Integer.class
            ));
        } finally {
            container.stop();
        }
    }
}
