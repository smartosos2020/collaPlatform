package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemRelationRuntimeModels.RelationView;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class WorkItemRelationServiceIntegrationTests {
    private static final UUID WORKSPACE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WorkItemRelationService relationService;

    @Autowired
    private WorkItemService workItemService;

    @Autowired
    private WorkItemConfigurationSnapshotCanonicalizer snapshotCanonicalizer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void canonicalEdgeCommandsAreReplaySafeAndAppendOneCompleteFactChain() throws Exception {
        Fixture fixture = fixture("fact-chain");
        WorkItemView left = item(fixture, "Left", "fact-left");
        WorkItemView right = item(fixture, "Right", "fact-right");

        RelationView created = relationService.create(
            fixture.owner(), fixture.spaceId(), "relates_to",
            right.item().id(), left.item().id(), 0, 0, "relation-create"
        );
        RelationView replayed = relationService.create(
            fixture.owner(), fixture.spaceId(), "relates_to",
            right.item().id(), left.item().id(), 0, 0, "relation-create"
        );

        assertThat(replayed).isEqualTo(created);
        assertThat(created.source().id().toString())
            .isLessThan(created.target().id().toString());
        assertThat(relationService.list(
            fixture.owner(), fixture.spaceId(), left.item().id(), null, null, 50
        ).items()).singleElement().satisfies(view -> {
            assertThat(view.displayName()).isEqualTo("Related");
            assertThat(view.reverse()).isFalse();
        });
        assertThat(relationService.list(
            fixture.owner(), fixture.spaceId(), right.item().id(), null, null, 50
        ).items()).singleElement();
        assertThat(count(
            "project_work_item_relation_commands",
            "space_id=? and operation='create'",
            fixture.spaceId()
        )).isEqualTo(1);
        assertThat(count(
            "project_work_item_relation_history",
            "space_id=? and relation_id=?",
            fixture.spaceId(), created.id()
        )).isEqualTo(1);
        assertThat(count(
            "project_work_item_activities",
            "space_id=? and activity_type='relation_created'",
            fixture.spaceId()
        )).isEqualTo(2);
        assertThat(count(
            "audit_logs",
            "workspace_id=? and target_type='work_item_relation' and target_id=?",
            WORKSPACE_ID, created.id()
        )).isEqualTo(1);
        assertThat(count(
            "domain_events",
            "workspace_id=? and aggregate_id=? and event_type='work_item_relation.changed'",
            WORKSPACE_ID, created.id()
        )).isEqualTo(1);

        assertCode("IDEMPOTENCY_KEY_REUSED", () -> relationService.create(
            fixture.owner(), fixture.spaceId(), "relates_to",
            left.item().id(), right.item().id(), 0, 0, "relation-create"
        ));

        RelationView withdrawn = relationService.withdraw(
            fixture.owner(), fixture.spaceId(), created.id(),
            0, 0, 0, "no longer needed", "relation-withdraw"
        );
        RelationView withdrawReplay = relationService.withdraw(
            fixture.owner(), fixture.spaceId(), created.id(),
            0, 0, 0, "no longer needed", "relation-withdraw"
        );
        assertThat(withdrawReplay).isEqualTo(withdrawn);
        assertThat(withdrawn.status()).isEqualTo("withdrawn");
        assertThat(relationService.list(
            fixture.owner(), fixture.spaceId(), left.item().id(), null, null, 50
        ).items()).isEmpty();

        RelationView restored = relationService.restore(
            fixture.owner(), fixture.spaceId(), created.id(),
            1, 0, 0, "relation-restore"
        );
        assertThat(restored.status()).isEqualTo("active");
        assertThat(restored.version()).isEqualTo(2);
        assertThat(count(
            "project_work_item_relation_history",
            "space_id=? and relation_id=?",
            fixture.spaceId(), created.id()
        )).isEqualTo(3);
        assertThat(count(
            "domain_events",
            "workspace_id=? and aggregate_id=? and event_type='work_item_relation.changed'",
            WORKSPACE_ID, created.id()
        )).isEqualTo(3);

        RelationView directed = relationService.create(
            fixture.owner(), fixture.spaceId(), "references",
            left.item().id(), right.item().id(), 0, 0, "directed-normal"
        );
        assertThat(relationService.list(
            fixture.owner(), fixture.spaceId(), left.item().id(), "references", null, 50
        ).items()).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(directed.id());
            assertThat(view.displayName()).isEqualTo("References");
            assertThat(view.reverse()).isFalse();
        });
        assertThat(relationService.list(
            fixture.owner(), fixture.spaceId(), right.item().id(), "references", null, 50
        ).items()).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(directed.id());
            assertThat(view.displayName()).isEqualTo("Referenced by");
            assertThat(view.reverse()).isTrue();
        });
    }

    @Test
    void cardinalityAndConcurrentCycleChecksFailClosedWithoutPartialFacts() throws Exception {
        Fixture fixture = fixture("graph");
        WorkItemView first = item(fixture, "First", "graph-first");
        WorkItemView second = item(fixture, "Second", "graph-second");
        WorkItemView child = item(fixture, "Child", "graph-child");

        relationService.create(
            fixture.owner(), fixture.spaceId(), "parent_child",
            first.item().id(), child.item().id(), 0, 0, "parent-first"
        );
        assertCode("RELATION_TARGET_CARDINALITY_EXCEEDED", () -> relationService.create(
            fixture.owner(), fixture.spaceId(), "parent_child",
            second.item().id(), child.item().id(), 0, 0, "parent-second"
        ));
        assertThat(count(
            "project_work_item_relation_commands",
            "space_id=? and request_id='parent-second'",
            fixture.spaceId()
        )).isZero();

        WorkItemView cycleLeft = item(fixture, "Cycle left", "cycle-left");
        WorkItemView cycleRight = item(fixture, "Cycle right", "cycle-right");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> forward = executor.submit(() -> createAfterBarrier(
                fixture, cycleLeft, cycleRight, "cycle-forward", ready, start
            ));
            Future<Object> reverse = executor.submit(() -> createAfterBarrier(
                fixture, cycleRight, cycleLeft, "cycle-reverse", ready, start
            ));
            ready.await();
            start.countDown();
            Object leftResult = forward.get();
            Object rightResult = reverse.get();
            assertThat(List.of(leftResult, rightResult).stream()
                .filter(RelationView.class::isInstance).count()).isEqualTo(1);
            assertThat(List.of(leftResult, rightResult).stream()
                .filter(WorkItemRuntimeException.class::isInstance)
                .map(WorkItemRuntimeException.class::cast)
                .map(WorkItemRuntimeException::code))
                .containsExactly("RELATION_CYCLE_DETECTED");
        }
        assertThat(count(
            "project_work_item_relations",
            "space_id=? and relation_key='depends_on' and status='active'",
            fixture.spaceId()
        )).isEqualTo(1);
        assertThat(count(
            "project_work_item_relation_history",
            "space_id=? and relation_key='depends_on'",
            fixture.spaceId()
        )).isEqualTo(1);

        relationService.create(
            fixture.owner(), fixture.spaceId(), "blocks",
            first.item().id(), second.item().id(), 0, 0, "blocks-forward"
        );
        assertCode("RELATION_CYCLE_DETECTED", () -> relationService.create(
            fixture.owner(), fixture.spaceId(), "blocks",
            second.item().id(), first.item().id(), 0, 0, "blocks-reverse"
        ));
    }

    @Test
    void sixIdentityDecisionAndLifecyclePoliciesShareTheSameServerAuthority() throws Exception {
        Fixture fixture = fixture("access");
        CurrentUser admin = addMember(fixture, "admin");
        CurrentUser member = addMember(fixture, "member");
        CurrentUser guest = addMember(fixture, "guest");
        Fixture outsiderFixture = fixture("outsider");
        CurrentUser outsider = outsiderFixture.owner();
        CurrentUser enterpriseAdmin = new CurrentUser(
            outsider.id(),
            WORKSPACE_ID,
            UUID.randomUUID(),
            outsider.username(),
            outsider.displayName(),
            Set.of("admin"),
            Set.of("project.manage")
        );
        WorkItemView left = item(fixture, "Left", "access-left");
        WorkItemView right = item(fixture, "Right", "access-right");

        assertThat(relationService.capabilities(
            fixture.owner(), fixture.spaceId(), "relates_to",
            left.item().id(), right.item().id()
        ).canCreate()).isTrue();
        assertThat(relationService.capabilities(
            admin, fixture.spaceId(), "relates_to",
            left.item().id(), right.item().id()
        ).canCreate()).isTrue();
        assertThat(relationService.capabilities(
            member, fixture.spaceId(), "relates_to",
            left.item().id(), right.item().id()
        ).canCreate()).isTrue();
        assertThat(relationService.capabilities(
            guest, fixture.spaceId(), "relates_to",
            left.item().id(), right.item().id()
        ).canCreate()).isFalse();
        assertHidden(() -> relationService.capabilities(
            outsider, fixture.spaceId(), "relates_to",
            left.item().id(), right.item().id()
        ));
        assertHidden(() -> relationService.capabilities(
            enterpriseAdmin, fixture.spaceId(), "relates_to",
            left.item().id(), right.item().id()
        ));

        RelationView memberCreated = relationService.create(
            member, fixture.spaceId(), "relates_to",
            left.item().id(), right.item().id(), 0, 0, "member-create"
        );
        assertCode("FORBIDDEN", () -> relationService.withdraw(
            guest, fixture.spaceId(), memberCreated.id(),
            0, 0, 0, "guest attempt", "guest-withdraw"
        ));

        workItemService.transition(
            fixture.owner(), fixture.spaceId(), left.item().id(),
            "archived", 0, "archive-detach"
        );
        assertThat(relationService.get(
            fixture.owner(), fixture.spaceId(), memberCreated.id(), left.item().id()
        ).status()).isEqualTo("withdrawn");
        workItemService.transition(
            fixture.owner(), fixture.spaceId(), left.item().id(),
            "active", 1, "restore-detached-item"
        );
        assertThat(relationService.get(
            fixture.owner(), fixture.spaceId(), memberCreated.id(), left.item().id()
        ).status()).isEqualTo("withdrawn");

        WorkItemView parent = item(fixture, "Parent", "restrict-parent");
        WorkItemView child = item(fixture, "Child", "restrict-child");
        relationService.create(
            fixture.owner(), fixture.spaceId(), "parent_child",
            parent.item().id(), child.item().id(), 0, 0, "restrict-edge"
        );
        assertCode("RELATION_LIFECYCLE_RESTRICTED", () -> workItemService.transition(
            fixture.owner(), fixture.spaceId(), child.item().id(),
            "archived", 0, "restrict-archive"
        ));
        assertThat(count(
            "project_work_item_commands",
            "space_id=? and request_id='restrict-archive'",
            fixture.spaceId()
        )).isZero();

        WorkItemView dependencySource = item(fixture, "Dependency source", "retain-source");
        WorkItemView dependencyTarget = item(fixture, "Dependency target", "retain-target");
        RelationView retained = relationService.create(
            fixture.owner(), fixture.spaceId(), "depends_on",
            dependencySource.item().id(), dependencyTarget.item().id(),
            0, 0, "retain-edge"
        );
        workItemService.transition(
            fixture.owner(), fixture.spaceId(), dependencySource.item().id(),
            "archived", 0, "retain-archive"
        );
        assertThat(relationService.get(
            fixture.owner(), fixture.spaceId(), retained.id(), dependencySource.item().id()
        ).status()).isEqualTo("active");

        WorkItemView external = item(outsiderFixture, "External", "external-item");
        assertHidden(() -> relationService.create(
            fixture.owner(), fixture.spaceId(), "relates_to",
            right.item().id(), external.item().id(), 0, 0, "cross-space"
        ));
    }

    @Test
    void boundedListUsesTheEndpointIndexPlan() throws Exception {
        Fixture fixture = fixture("budget");
        WorkItemView left = item(fixture, "Left", "budget-left");
        WorkItemView right = item(fixture, "Right", "budget-right");
        relationService.create(
            fixture.owner(), fixture.spaceId(), "relates_to",
            left.item().id(), right.item().id(), 0, 0, "budget-edge"
        );

        assertThat(relationService.list(
            fixture.owner(), fixture.spaceId(), left.item().id(), null, null, 10_000
        ).items()).hasSizeLessThanOrEqualTo(200);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("set local enable_seqscan=off");
            String plan = String.join("\n", jdbcTemplate.queryForList(
                """
                    explain select id
                      from project_work_item_relations
                     where workspace_id=? and space_id=? and source_work_item_id=?
                       and status='active'
                     order by id limit 201
                    """,
                String.class,
                WORKSPACE_ID,
                fixture.spaceId(),
                left.item().id()
            ));
            assertThat(plan).contains("Index Scan");
            assertThat(jdbcTemplate.queryForList(
                """
                    select indexname from pg_indexes
                     where schemaname='public'
                       and indexname in (
                         'idx_project_work_item_relations_source',
                         'idx_project_work_item_relations_target'
                       )
                     order by indexname
                    """,
                String.class
            )).containsExactly(
                "idx_project_work_item_relations_source",
                "idx_project_work_item_relations_target"
            );
        });
    }

    private Object createAfterBarrier(
        Fixture fixture,
        WorkItemView source,
        WorkItemView target,
        String requestId,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        try {
            ready.countDown();
            start.await();
            return relationService.create(
                fixture.owner(), fixture.spaceId(), "depends_on",
                source.item().id(), target.item().id(), 0, 0, requestId
            );
        } catch (WorkItemRuntimeException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private WorkItemView item(Fixture fixture, String title, String requestId) {
        return workItemService.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            title,
            objectMapper.createObjectNode(),
            requestId
        );
    }

    private Fixture fixture(String label) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JsonNode snapshot = objectMapper.readTree("""
            {
              "snapshotSchemaVersion":4,
              "typeDefinition":{
                "id":"%s","workspaceId":"%s","spaceId":"%s","typeKey":"task",
                "name":"Task","icon":"","description":"","sortOrder":0,
                "status":"active","system":false
              },
              "fields":[],
              "layouts":[],
              "relationDefinitions":[
                {
                  "relationKey":"relates_to","kind":"normal","direction":"undirected",
                  "forwardName":"Related","reverseName":"Related",
                  "sourceTypeKeys":["task"],"targetTypeKeys":["task"],
                  "sourceCardinality":"many","targetCardinality":"many",
                  "deletionPolicy":"detach","allowSelf":false,"maxDepth":64,
                  "sortOrder":100,"system":false
                },
                {
                  "relationKey":"parent_child","kind":"parent_child","direction":"directed",
                  "forwardName":"Parent","reverseName":"Child",
                  "sourceTypeKeys":["task"],"targetTypeKeys":["task"],
                  "sourceCardinality":"many","targetCardinality":"one",
                  "deletionPolicy":"restrict","allowSelf":false,"maxDepth":64,
                  "sortOrder":200,"system":false
                },
                {
                  "relationKey":"references","kind":"normal","direction":"directed",
                  "forwardName":"References","reverseName":"Referenced by",
                  "sourceTypeKeys":["task"],"targetTypeKeys":["task"],
                  "sourceCardinality":"many","targetCardinality":"many",
                  "deletionPolicy":"retain_history","allowSelf":false,"maxDepth":64,
                  "sortOrder":250,"system":false
                },
                {
                  "relationKey":"depends_on","kind":"dependency","direction":"directed",
                  "forwardName":"Depends on","reverseName":"Required by",
                  "sourceTypeKeys":["task"],"targetTypeKeys":["task"],
                  "sourceCardinality":"many","targetCardinality":"many",
                  "deletionPolicy":"retain_history","allowSelf":false,"maxDepth":64,
                  "sortOrder":300,"system":false
                },
                {
                  "relationKey":"blocks","kind":"blocking","direction":"directed",
                  "forwardName":"Blocks","reverseName":"Blocked by",
                  "sourceTypeKeys":["task"],"targetTypeKeys":["task"],
                  "sourceCardinality":"many","targetCardinality":"many",
                  "deletionPolicy":"retain_history","allowSelf":false,"maxDepth":64,
                  "sortOrder":400,"system":false
                }
              ]
            }
            """.formatted(typeId, WORKSPACE_ID, spaceId));
        var canonical = snapshotCanonicalizer.canonicalize(snapshot);

        jdbcTemplate.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, ?, 'not-used', ?, 'active', now(), now())
                """,
            userId, WORKSPACE_ID, "s10_" + label + "_" + suffix, "S10 " + label
        );
        jdbcTemplate.update(
            """
                insert into project_spaces (
                    id, workspace_id, space_key, name, status, visibility, version,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', 'private', 0, ?, now(), ?, now())
                """,
            spaceId, WORKSPACE_ID, "s10_" + label + "_" + suffix, "S10 " + label,
            userId, userId
        );
        jdbcTemplate.update(
            """
                insert into project_space_members (
                    id, workspace_id, space_id, user_id, status, joined_at,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId, WORKSPACE_ID, spaceId, userId, userId, userId
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments (
                    id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at
                ) values (?, ?, ?, ?, 'owner', ?, now())
                """,
            UUID.randomUUID(), WORKSPACE_ID, spaceId, memberId, userId
        );
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                """
                    insert into project_work_item_types (
                        id, workspace_id, space_id, type_key, name, icon, description,
                        sort_order, status, is_system, current_version_id, created_by,
                        created_at, updated_by, updated_at, aggregate_version
                    ) values (?, ?, ?, 'task', 'Task', '', '', 0, 'active', false, ?, ?, now(), ?, now(), 0)
                    """,
                typeId, WORKSPACE_ID, spaceId, versionId, userId, userId
            );
            jdbcTemplate.update(
                """
                    insert into project_work_item_type_versions (
                        id, workspace_id, space_id, type_definition_id, version_number,
                        config_hash, status, config, created_by, created_at, published_by,
                        published_at, snapshot_schema_version
                    ) values (?, ?, ?, ?, 1, ?, 'published', ?::jsonb, ?, now(), ?, now(), 4)
                    """,
                versionId, WORKSPACE_ID, spaceId, typeId, canonical.configHash(),
                canonical.payload().toString(), userId, userId
            );
        });
        return new Fixture(
            new CurrentUser(
                userId, WORKSPACE_ID, UUID.randomUUID(),
                "s10_" + label + "_" + suffix, "S10 " + label,
                Set.of("member"), Set.of()
            ),
            spaceId,
            typeId
        );
    }

    private CurrentUser addMember(Fixture fixture, String role) {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, ?, 'not-used', ?, 'active', now(), now())
                """,
            userId, WORKSPACE_ID, "s10_" + role + "_" + suffix, "S10 " + role
        );
        jdbcTemplate.update(
            """
                insert into project_space_members (
                    id, workspace_id, space_id, user_id, status, joined_at,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', now(), ?, now(), ?, now())
                """,
            memberId, WORKSPACE_ID, fixture.spaceId(), userId,
            fixture.owner().id(), fixture.owner().id()
        );
        jdbcTemplate.update(
            """
                insert into project_space_role_assignments (
                    id, workspace_id, space_id, member_id, role_key, assigned_by, assigned_at
                ) values (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(), WORKSPACE_ID, fixture.spaceId(), memberId, role,
            fixture.owner().id()
        );
        return new CurrentUser(
            userId, WORKSPACE_ID, UUID.randomUUID(),
            "s10_" + role + "_" + suffix, "S10 " + role,
            Set.of("member"), Set.of()
        );
    }

    private int count(String table, String where, Object... parameters) {
        Integer value = jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where " + where,
            Integer.class,
            parameters
        );
        return value == null ? 0 : value;
    }

    private void assertHidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertCode("NOT_FOUND_OR_HIDDEN", operation);
    }

    private void assertCode(
        String code,
        org.assertj.core.api.ThrowableAssert.ThrowingCallable operation
    ) {
        assertThatThrownBy(operation)
            .isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo(code);
    }

    private record Fixture(CurrentUser owner, UUID spaceId, UUID typeId) {
    }
}
