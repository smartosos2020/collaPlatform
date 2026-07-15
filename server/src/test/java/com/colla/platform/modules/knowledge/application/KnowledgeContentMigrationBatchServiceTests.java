package com.colla.platform.modules.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentCanonicalDocument;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentMigrationPlan;
import com.colla.platform.modules.knowledge.domain.KnowledgeContentMigrationModels.KnowledgeContentMigrationBatchPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeContentMigrationBatchServiceTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ITEM_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void repeatedDryRunWithSameKeyIsIdempotentAndDoesNotClaimToApply() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        KnowledgeContentCanonicalDocument document = new KnowledgeContentSchemaService(mapper)
            .fromLegacy(ITEM_ID, List.of(), "hello");
        KnowledgeContentMigrationPlan item = new KnowledgeContentMigrationPlan(
            ITEM_ID, 2, 3, "source", document.checksum(), 1, 1, true, true, "dry-run", List.of(), document
        );
        KnowledgeContentMigrationBatchService service = new KnowledgeContentMigrationBatchService();

        KnowledgeContentMigrationBatchPlan first = service.plan(WORKSPACE_ID, "kb-product-m2-test", List.of(item));
        KnowledgeContentMigrationBatchPlan second = service.plan(WORKSPACE_ID, "kb-product-m2-test", List.of(item));

        assertEquals(first.batchId(), second.batchId());
        assertEquals(first.sourceChecksum(), second.sourceChecksum());
        assertEquals("dry_run", first.mode());
        assertEquals("planned", first.status());
        assertEquals(1, first.plannedCount());
    }

    @Test
    void failedItemIsListedAndBatchDoesNotPretendAllItemsAreSafe() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        KnowledgeContentCanonicalDocument document = new KnowledgeContentSchemaService(mapper)
            .fromLegacy(ITEM_ID, List.of(), "hello");
        KnowledgeContentMigrationPlan item = new KnowledgeContentMigrationPlan(
            ITEM_ID, 2, 3, "source", document.checksum(), 1, 1, true, false, "dry-run",
            List.of(new com.colla.platform.modules.knowledge.domain.KnowledgeContentCanonicalModels.KnowledgeContentSchemaIssue(
                "SCHEMA_INVALID", "$.content", "bad content", "error"
            )),
            document
        );

        KnowledgeContentMigrationBatchPlan batch = new KnowledgeContentMigrationBatchService()
            .plan(WORKSPACE_ID, "kb-product-m2-failure", List.of(item));

        assertEquals("failed", batch.status());
        assertEquals(0, batch.plannedCount());
        assertEquals(1, batch.failureCount());
        assertEquals("failed", batch.items().get(0).status());
        assertEquals("SCHEMA_INVALID", batch.items().get(0).failureCode());
    }
}
