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
        assertEquals(0, jdbcTemplate.queryForObject(
            """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = 'knowledge_content_collaboration_states'
                  and column_name in (
                    'state_vector', 'snapshot_payload', 'server_clock',
                    'last_client_id', 'updated_by', 'last_saved_at'
                  )
                """,
            Integer.class
        ));
        assertEquals(3, jdbcTemplate.queryForObject(
            """
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and (
                    (table_name = 'knowledge_base_items' and column_name = 'collaboration_generation')
                    or (table_name = 'knowledge_content_collaboration_states' and column_name = 'generation')
                    or (table_name = 'knowledge_content_collaboration_updates' and column_name = 'generation')
                  )
            """,
            Integer.class
        ));
        assertTrue(jdbcTemplate.queryForObject(
            """
                select index_row.indisvalid
                   and index_row.indisready
                   and index_row.indpred is null
                   and pg_get_indexdef(index_row.indexrelid) like '%USING btree (parent_id)'
                from pg_index index_row
                join pg_class index_class on index_class.oid = index_row.indexrelid
                join pg_class table_class on table_class.oid = index_row.indrelid
                join pg_namespace table_namespace on table_namespace.oid = table_class.relnamespace
                where table_namespace.nspname = 'public'
                  and table_class.relname = 'knowledge_content_blocks'
                  and index_class.relname = 'idx_knowledge_content_blocks_parent_id'
            """,
            Boolean.class
        ));
        assertTrue(jdbcTemplate.queryForObject(
            """
                select index_row.indisvalid
                   and index_row.indisready
                   and index_row.indpred is null
                   and pg_get_indexdef(index_row.indexrelid) like '%USING btree (parent_id)'
                from pg_index index_row
                join pg_class index_class on index_class.oid = index_row.indexrelid
                join pg_class table_class on table_class.oid = index_row.indrelid
                join pg_namespace table_namespace on table_namespace.oid = table_class.relnamespace
                where table_namespace.nspname = 'public'
                  and table_class.relname = 'knowledge_base_items'
                  and index_class.relname = 'idx_knowledge_base_items_parent_id_fk'
                """,
            Boolean.class
        ));
        assertEquals(
            "UNIQUE (workspace_id, item_id, generation, update_id)",
            jdbcTemplate.queryForObject(
                """
                    select pg_get_constraintdef(constraint_row.oid)
                    from pg_constraint constraint_row
                    join pg_class table_row on table_row.oid = constraint_row.conrelid
                    where table_row.relname = 'knowledge_content_collaboration_updates'
                      and constraint_row.conname = 'uq_knowledge_collaboration_update_generation'
                    """,
                String.class
            )
        );
        assertEquals(0, jdbcTemplate.queryForObject(
            """
                select count(*)
                from pg_constraint constraint_row
                join pg_class table_row on table_row.oid = constraint_row.conrelid
                where table_row.relname = 'knowledge_content_collaboration_updates'
                  and constraint_row.contype = 'u'
                  and pg_get_constraintdef(constraint_row.oid) =
                      'UNIQUE (workspace_id, item_id, update_id)'
                """,
            Integer.class
        ));
    }

    @Test
    void v049SchemaCanUpgradeToCanonicalContractInAnIsolatedDatabase() throws Exception {
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
            String latestMigrationVersion;
            try (java.util.stream.Stream<java.nio.file.Path> migrationFiles = java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/db/migration"))) {
                latestMigrationVersion = migrationFiles
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("V\\d{3}__.+\\.sql"))
                    .map(name -> name.substring(1, 4))
                    .max(java.util.Comparator.naturalOrder())
                    .orElseThrow();
            }
            assertEquals(latestMigrationVersion, upgradeJdbcTemplate.queryForObject(
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
