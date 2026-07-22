package com.colla.platform.modules.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.colla.platform.modules.project.domain.WorkItemTypeModels.CreateWorkItemType;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeException;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository.NewDefinition;
import com.colla.platform.modules.project.infrastructure.WorkItemTypeRepository.NewVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class WorkItemTypeDefinitionServiceIntegrationTests {
    @Autowired
    private WorkItemTypeDefinitionService service;

    @Autowired
    private WorkItemTypeRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsDefinitionAndPublishedV1Atomically() {
        Fixture fixture = fixture("atomic");
        WorkItemTypeDefinition created = create(fixture, "task", "Task", 20);

        assertEquals("task", created.typeKey());
        assertEquals("active", created.status());
        assertEquals(1, created.currentVersionNumber());
        assertEquals(64, created.currentConfigHash().length());
        assertEquals("published", repository.listVersions(fixture.workspaceId(), fixture.spaceId(), created.id()).getFirst().status());
        assertEquals(created.currentVersionId(), repository.listVersions(fixture.workspaceId(), fixture.spaceId(), created.id()).getFirst().id());
        assertEquals(1, count("project_work_item_types", fixture.spaceId()));
        assertEquals(1, count("project_work_item_type_versions", fixture.spaceId()));
    }

    @Test
    void duplicateKeyDoesNotLeaveASecondVersionOrDefinition() {
        Fixture fixture = fixture("duplicate");
        create(fixture, "requirement", "Requirement", 10);

        WorkItemTypeException exception = assertThrows(
            WorkItemTypeException.class,
            () -> create(fixture, "requirement", "Duplicate", 20)
        );
        assertEquals("TYPE_KEY_CONFLICT", exception.code());
        assertEquals(1, count("project_work_item_types", fixture.spaceId()));
        assertEquals(1, count("project_work_item_type_versions", fixture.spaceId()));
    }

    @Test
    void failedVersionInsertRollsBackDeferredDefinitionInsert() throws Exception {
        Fixture fixture = fixture("rollback");
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        var config = objectMapper.readTree("{\"schemaVersion\":1,\"typeKey\":\"bug\"}");

        assertThrows(DataIntegrityViolationException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            repository.insertDefinition(new NewDefinition(
                typeId, fixture.workspaceId(), fixture.spaceId(), "bug", "Bug", "", "", 0,
                "active", false, versionId, fixture.actorId()
            ));
            repository.insertVersion(new NewVersion(
                versionId, fixture.workspaceId(), fixture.spaceId(), typeId, 1,
                "invalid-hash", "published", config, fixture.actorId()
            ));
        }));

        assertEquals(0, jdbcTemplate.queryForObject(
            "select count(*) from project_work_item_types where id = ?",
            Integer.class,
            typeId
        ));
    }

    @Test
    void compositeScopeRejectsCrossWorkspaceTypeRowsAndQueriesStayIsolated() {
        Fixture first = fixture("scope-a");
        Fixture second = fixture("scope-b");
        WorkItemTypeDefinition created = create(first, "release", "Release", 0);

        assertTrue(repository.findById(first.workspaceId(), first.spaceId(), created.id()).isPresent());
        assertTrue(repository.findById(second.workspaceId(), first.spaceId(), created.id()).isEmpty());
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            """
                insert into project_work_item_types
                    (id, workspace_id, space_id, type_key, name, icon, description, sort_order,
                     status, is_system, current_version_id, created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, 'cross_scope', 'Cross scope', '', '', 0, 'active', false, ?, ?, now(), ?, now())
                """,
            UUID.randomUUID(),
            first.workspaceId(),
            second.spaceId(),
            UUID.randomUUID(),
            first.actorId(),
            first.actorId()
        ));
    }

    @Test
    void databaseProtectsPermanentIdentityAndPublishedPayload() {
        Fixture fixture = fixture("immutable");
        WorkItemTypeDefinition created = create(fixture, "iteration", "Iteration", 0);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "update project_work_item_types set type_key='changed' where id=?",
            created.id()
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "delete from project_work_item_types where id=?",
            created.id()
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "update project_work_item_type_versions set config='{\"changed\":true}'::jsonb where id=?",
            created.currentVersionId()
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "delete from project_work_item_type_versions where id=?",
            created.currentVersionId()
        ));
    }

    @Test
    void lifecycleIsGuardedAndRetiredCannotReturn() {
        Fixture fixture = fixture("lifecycle");
        WorkItemTypeDefinition created = create(fixture, "project", "Project", 0);
        WorkItemTypeDefinition disabled = service.transition(
            fixture.workspaceId(), fixture.spaceId(), created.id(), "disabled", fixture.actorId(), 0
        );
        WorkItemTypeDefinition restored = service.transition(
            fixture.workspaceId(), fixture.spaceId(), created.id(), "active", fixture.actorId(), 1
        );
        WorkItemTypeDefinition retired = service.transition(
            fixture.workspaceId(), fixture.spaceId(), created.id(), "retired", fixture.actorId(), 2
        );

        assertEquals("disabled", disabled.status());
        assertEquals("active", restored.status());
        assertEquals("retired", retired.status());
        WorkItemTypeException exception = assertThrows(
            WorkItemTypeException.class,
            () -> service.transition(fixture.workspaceId(), fixture.spaceId(), created.id(), "active", fixture.actorId(), 3)
        );
        assertEquals("INVALID_LIFECYCLE_TRANSITION", exception.code());
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
            "update project_work_item_types set status='active' where id=?",
            created.id()
        ));
    }

    @Test
    void aggregateVersionAllowsOnlyOneConcurrentWriter() throws Exception {
        Fixture fixture = fixture("concurrent");
        WorkItemTypeDefinition created = create(fixture, "task", "Task", 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> transitionAfterBarrier(fixture, created.id(), ready, start));
            Future<Integer> second = executor.submit(() -> transitionAfterBarrier(fixture, created.id(), ready, start));
            ready.await();
            start.countDown();
            assertEquals(1, first.get() + second.get());
        }
        WorkItemTypeException exception = assertThrows(
            WorkItemTypeException.class,
            () -> service.reorder(fixture.workspaceId(), fixture.spaceId(), created.id(), 99, fixture.actorId(), 0)
        );
        assertEquals("VERSION_CONFLICT", exception.code());
        assertEquals(1, service.get(fixture.workspaceId(), fixture.spaceId(), created.id()).aggregateVersion());
    }

    @Test
    void concurrentCreationWithTheSameKeyReturnsOneStableConflict() throws Exception {
        Fixture fixture = fixture("create-race");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<WorkItemTypeDefinition> first = executor.submit(() -> createAfterBarrier(fixture, ready, start));
            Future<WorkItemTypeDefinition> second = executor.submit(() -> createAfterBarrier(fixture, ready, start));
            ready.await();
            start.countDown();
            int successes = 0;
            int conflicts = 0;
            for (Future<WorkItemTypeDefinition> future : List.of(first, second)) {
                try {
                    future.get();
                    successes++;
                } catch (ExecutionException exception) {
                    WorkItemTypeException cause = (WorkItemTypeException) exception.getCause();
                    assertEquals("TYPE_KEY_CONFLICT", cause.code());
                    conflicts++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, conflicts);
        }
        assertEquals(1, count("project_work_item_types", fixture.spaceId()));
        assertEquals(1, count("project_work_item_type_versions", fixture.spaceId()));
    }

    @Test
    void listUsesStatusAndStableSortProjection() {
        Fixture fixture = fixture("list");
        WorkItemTypeDefinition later = create(fixture, "task", "Task", 20);
        WorkItemTypeDefinition earlier = create(fixture, "bug", "Bug", 10);
        service.transition(fixture.workspaceId(), fixture.spaceId(), later.id(), "disabled", fixture.actorId(), 0);

        List<WorkItemTypeDefinition> all = service.list(fixture.workspaceId(), fixture.spaceId(), null);
        assertEquals(List.of(earlier.id(), later.id()), all.stream().map(WorkItemTypeDefinition::id).toList());
        assertEquals(List.of(later.id()), service.list(fixture.workspaceId(), fixture.spaceId(), "disabled")
            .stream().map(WorkItemTypeDefinition::id).toList());
    }

    @Test
    void inactiveOrMissingSpacesCannotReceiveTypes() {
        Fixture fixture = fixture("inactive");
        jdbcTemplate.update(
            "update project_spaces set status='disabled', disabled_at=now() where id=?",
            fixture.spaceId()
        );
        WorkItemTypeException disabled = assertThrows(
            WorkItemTypeException.class,
            () -> create(fixture, "task", "Task", 0)
        );
        assertEquals("SPACE_UNAVAILABLE", disabled.code());

        WorkItemTypeException missing = assertThrows(
            WorkItemTypeException.class,
            () -> service.create(new CreateWorkItemType(
                fixture.workspaceId(), UUID.randomUUID(), fixture.actorId(), "task", "Task", "", "", 0, false
            ))
        );
        assertEquals("SPACE_NOT_FOUND", missing.code());
        assertFalse(repository.findByKey(fixture.workspaceId(), fixture.spaceId(), "task").isPresent());
    }

    private int transitionAfterBarrier(Fixture fixture, UUID typeId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return repository.transitionStatus(
            fixture.workspaceId(), fixture.spaceId(), typeId, "disabled", fixture.actorId(), 0
        );
    }

    private WorkItemTypeDefinition createAfterBarrier(Fixture fixture, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return create(fixture, "race_key", "Race", 0);
    }

    private WorkItemTypeDefinition create(Fixture fixture, String key, String name, int sortOrder) {
        return service.create(new CreateWorkItemType(
            fixture.workspaceId(), fixture.spaceId(), fixture.actorId(), key, name, "", "", sortOrder, false
        ));
    }

    private int count(String table, UUID spaceId) {
        if (!List.of("project_work_item_types", "project_work_item_type_versions").contains(table)) {
            throw new IllegalArgumentException("Unsupported table");
        }
        return jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where space_id = ?",
            Integer.class,
            spaceId
        );
    }

    private Fixture fixture(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UUID workspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into workspaces (id, name, slug, status, created_at, updated_at) values (?, ?, ?, 'active', now(), now())",
            workspaceId,
            "S03 " + label,
            "s03-" + label + "-" + suffix
        );
        jdbcTemplate.update(
            """
                insert into users
                    (id, workspace_id, username, password_hash, display_name, status, created_at, updated_at)
                values (?, ?, ?, 'not-used', ?, 'active', now(), now())
                """,
            actorId,
            workspaceId,
            "s03" + suffix,
            "S03 Actor"
        );
        jdbcTemplate.update(
            """
                insert into project_spaces
                    (id, workspace_id, space_key, name, description, status, visibility,
                     created_by, created_at, updated_by, updated_at)
                values (?, ?, ?, ?, '', 'active', 'private', ?, now(), ?, now())
                """,
            spaceId,
            workspaceId,
            "space-" + suffix,
            "S03 Space " + label,
            actorId,
            actorId
        );
        return new Fixture(workspaceId, actorId, spaceId);
    }

    private record Fixture(UUID workspaceId, UUID actorId, UUID spaceId) {
    }
}
