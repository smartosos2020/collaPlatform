package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.WorkItemHierarchyModels.HierarchyMutation;
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
class WorkItemHierarchyServiceIntegrationTests {
    private static final UUID WORKSPACE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private WorkItemHierarchyService hierarchyService;

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
    void closureNavigationReparentAndChildSplitRemainAtomicAndReplaySafe() throws Exception {
        Fixture fixture = fixture("navigation", 8);
        WorkItemView root = workItemService.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.typeId(),
            "Root",
            objectMapper.createObjectNode().put("priority", "high"),
            "root"
        );
        WorkItemView middle = item(fixture, "Middle", "middle");
        WorkItemView leaf = item(fixture, "Leaf", "leaf");
        WorkItemView newRoot = item(fixture, "New root", "new-root");
        WorkItemView crossTypeChild = workItemService.create(
            fixture.owner(),
            fixture.spaceId(),
            fixture.bugTypeId(),
            "Cross type child",
            objectMapper.createObjectNode(),
            "cross-type-child"
        );

        RelationView rootEdge = hierarchyService.attach(
            fixture.owner(), fixture.spaceId(), "parent_child",
            root.item().id(), middle.item().id(), 0, 0, "attach-root"
        ).relation();
        hierarchyService.attach(
            fixture.owner(), fixture.spaceId(), "parent_child",
            middle.item().id(), leaf.item().id(), 0, 0, "attach-leaf"
        );
        hierarchyService.attach(
            fixture.owner(), fixture.spaceId(), "parent_child",
            leaf.item().id(), crossTypeChild.item().id(), 0, 0, "attach-cross-type"
        );

        assertThat(hierarchyService.query(
            fixture.owner(), fixture.spaceId(), root.item().id(), "parent_child",
            "descendants", null, 8, 100
        ).items()).extracting(node -> node.depth()).containsExactly(1, 2, 3);
        assertThat(hierarchyService.navigation(
            fixture.owner(), fixture.spaceId(), crossTypeChild.item().id(),
            "parent_child", 8, 100
        ).focus().typeKey()).isEqualTo("bug");
        assertThat(hierarchyService.navigation(
            fixture.owner(), fixture.spaceId(), leaf.item().id(), "parent_child", 8, 100
        ).breadcrumbs()).extracting(node -> node.id())
            .containsExactly(root.item().id(), middle.item().id());

        HierarchyMutation split = hierarchyService.splitChild(
            fixture.owner(), fixture.spaceId(), root.item().id(), "parent_child",
            fixture.typeId(), "Split child", objectMapper.createObjectNode(),
            List.of("priority"), 0, "split-child"
        );
        HierarchyMutation replay = hierarchyService.splitChild(
            fixture.owner(), fixture.spaceId(), root.item().id(), "parent_child",
            fixture.typeId(), "Split child", objectMapper.createObjectNode(),
            List.of("priority"), 0, "split-child"
        );
        assertThat(replay.child().item().id()).isEqualTo(split.child().item().id());
        assertThat(split.child().fieldValues().path("priority").asText()).isEqualTo("high");
        assertThat(count(
            "project_work_items",
            "space_id=? and title='Split child'",
            fixture.spaceId()
        )).isEqualTo(1);

        RelationView reparented = hierarchyService.reparent(
            fixture.owner(), fixture.spaceId(), rootEdge.id(), newRoot.item().id(),
            rootEdge.version(), 0, 0, 0,
            "move branch", "REPARENT", "reparent-middle"
        ).relation();
        assertThat(hierarchyService.navigation(
            fixture.owner(), fixture.spaceId(), middle.item().id(),
            "parent_child", 8, 100
        ).parent().id()).isEqualTo(newRoot.item().id());
        assertThat(relationService.get(
            fixture.owner(), fixture.spaceId(), rootEdge.id(), middle.item().id()
        ).status()).isEqualTo("withdrawn");

        hierarchyService.detach(
            fixture.owner(), fixture.spaceId(), reparented.id(),
            reparented.version(), 0, 0, "detach branch", "detach-middle"
        );
        assertThat(hierarchyService.query(
            fixture.owner(), fixture.spaceId(), newRoot.item().id(),
            "parent_child", "descendants", null, 8, 100
        ).items()).isEmpty();
        assertThat(count(
            "project_work_item_hierarchy_paths",
            "space_id=? and relation_key='parent_child'",
            fixture.spaceId()
        )).isGreaterThan(0);
    }

    @Test
    void depthBudgetAndConcurrentReparentHaveOneWinnerWithoutOrphans() throws Exception {
        Fixture fixture = fixture("concurrency", 2);
        WorkItemView first = item(fixture, "First", "first");
        WorkItemView second = item(fixture, "Second", "second");
        WorkItemView third = item(fixture, "Third", "third");
        WorkItemView fourth = item(fixture, "Fourth", "fourth");
        hierarchyService.attach(
            fixture.owner(), fixture.spaceId(), "parent_child",
            first.item().id(), second.item().id(), 0, 0, "depth-one"
        );
        hierarchyService.attach(
            fixture.owner(), fixture.spaceId(), "parent_child",
            second.item().id(), third.item().id(), 0, 0, "depth-two"
        );
        assertCode("HIERARCHY_CANONICAL_GRAPH_INVALID", () ->
            hierarchyService.attach(
                fixture.owner(), fixture.spaceId(), "parent_child",
                third.item().id(), fourth.item().id(), 0, 0, "depth-three"
            )
        );
        assertThat(count(
            "project_work_item_relations",
            "space_id=? and source_work_item_id=? and target_work_item_id=? and status='active'",
            fixture.spaceId(), third.item().id(), fourth.item().id()
        )).isZero();

        WorkItemView child = item(fixture, "Concurrent child", "concurrent-child");
        WorkItemView candidateA = item(fixture, "Candidate A", "candidate-a");
        WorkItemView candidateB = item(fixture, "Candidate B", "candidate-b");
        RelationView current = hierarchyService.attach(
            fixture.owner(), fixture.spaceId(), "parent_child",
            fourth.item().id(), child.item().id(), 0, 0, "current-parent"
        ).relation();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> left = executor.submit(() -> reparentAfterBarrier(
                fixture, current, candidateA, child, "concurrent-a", ready, start
            ));
            Future<Object> right = executor.submit(() -> reparentAfterBarrier(
                fixture, current, candidateB, child, "concurrent-b", ready, start
            ));
            ready.await();
            start.countDown();
            List<Object> results = List.of(left.get(), right.get());
            assertThat(results.stream().filter(HierarchyMutation.class::isInstance).count())
                .isEqualTo(1);
            assertThat(results.stream().filter(WorkItemRuntimeException.class::isInstance).count())
                .isEqualTo(1);
        }
        assertThat(hierarchyService.query(
            fixture.owner(), fixture.spaceId(), child.item().id(),
            "parent_child", "ancestors", null, 1, 100
        ).items()).hasSize(1);
        assertThat(count(
            "project_work_item_relations",
            "space_id=? and relation_key='parent_child' and target_work_item_id=? and status='active'",
            fixture.spaceId(), child.item().id()
        )).isEqualTo(1);
    }

    @Test
    void scanDryRunRebuildFailureAndResumeNeverRewriteCanonicalEdges() throws Exception {
        Fixture fixture = fixture("recovery", 8);
        CurrentUser member = addMember(fixture, "member");
        WorkItemView root = item(fixture, "Recovery root", "recovery-root");
        WorkItemView child = item(fixture, "Recovery child", "recovery-child");
        WorkItemView leaf = item(fixture, "Recovery leaf", "recovery-leaf");
        RelationView rootEdge = hierarchyService.attach(
            fixture.owner(), fixture.spaceId(), "parent_child",
            root.item().id(), child.item().id(), 0, 0, "recovery-edge-one"
        ).relation();
        hierarchyService.attach(
            fixture.owner(), fixture.spaceId(), "parent_child",
            child.item().id(), leaf.item().id(), 0, 0, "recovery-edge-two"
        );
        assertCode("FORBIDDEN", () -> hierarchyService.scan(
            member, fixture.spaceId(), "parent_child"
        ));

        jdbcTemplate.update(
            """
                delete from project_work_item_hierarchy_paths
                 where workspace_id=? and space_id=? and relation_key='parent_child'
                   and ancestor_work_item_id=? and descendant_work_item_id=?
                """,
            WORKSPACE_ID, fixture.spaceId(), root.item().id(), leaf.item().id()
        );
        assertThat(hierarchyService.scan(
            fixture.owner(), fixture.spaceId(), "parent_child"
        ).issues()).extracting(issue -> issue.code()).contains("MISSING_PATH");

        var dryRun = hierarchyService.rebuild(
            fixture.owner(), fixture.spaceId(), "parent_child",
            true, null, "rebuild-dry-run"
        );
        assertThat(dryRun.status()).isEqualTo("completed");
        assertThat(dryRun.issueCount()).isPositive();
        var repaired = hierarchyService.rebuild(
            fixture.owner(), fixture.spaceId(), "parent_child",
            false, "REBUILD_HIERARCHY", "rebuild-repair"
        );
        assertThat(repaired.status()).isEqualTo("completed");
        assertThat(hierarchyService.scan(
            fixture.owner(), fixture.spaceId(), "parent_child"
        ).issues()).isEmpty();
        assertThat(count(
            "project_work_item_relations",
            "space_id=? and status='active'",
            fixture.spaceId()
        )).isEqualTo(2);

        UUID corruptRelationId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                insert into project_work_item_relations (
                    id, workspace_id, space_id, relation_key, relation_kind, direction,
                    definition_type_id, definition_version_id, definition_config_hash,
                    source_work_item_id, target_work_item_id, status, version,
                    created_by, created_at, updated_by, updated_at
                )
                select ?, workspace_id, space_id, relation_key, relation_kind, direction,
                       definition_type_id, definition_version_id, definition_config_hash,
                       ?, ?, 'active', 0, ?, now(), ?, now()
                  from project_work_item_relations
                 where id=?
                """,
            corruptRelationId,
            leaf.item().id(),
            root.item().id(),
            fixture.owner().id(),
            fixture.owner().id(),
            rootEdge.id()
        );
        var failed = hierarchyService.rebuild(
            fixture.owner(), fixture.spaceId(), "parent_child",
            false, "REBUILD_HIERARCHY", "rebuild-cycle"
        );
        assertThat(failed.status()).isEqualTo("failed");
        assertThat(failed.failures()).extracting(issue -> issue.code())
            .contains("CANONICAL_EDGE_CYCLE");
        jdbcTemplate.update(
            "delete from project_work_item_relations where id=?",
            corruptRelationId
        );
        var resumed = hierarchyService.resume(
            fixture.owner(), fixture.spaceId(), failed.id(), "REBUILD_HIERARCHY"
        );
        assertThat(resumed.status()).isEqualTo("completed");
        assertThat(resumed.attempt()).isEqualTo(2);

        assertThat(hierarchyService.query(
            fixture.owner(), fixture.spaceId(), root.item().id(),
            "parent_child", "descendants", null, 64, 10_000
        ).items()).hasSizeLessThanOrEqualTo(200);
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("set local enable_seqscan=off");
            String plan = String.join("\n", jdbcTemplate.queryForList(
                """
                    explain select descendant_work_item_id
                      from project_work_item_hierarchy_paths
                     where workspace_id=? and space_id=? and relation_key='parent_child'
                       and ancestor_work_item_id=? and depth between 1 and 8
                     order by depth, descendant_work_item_id limit 201
                    """,
                String.class,
                WORKSPACE_ID,
                fixture.spaceId(),
                root.item().id()
            ));
            assertThat(plan)
                .contains("idx_project_work_item_hierarchy_ancestors")
                .contains("Index");
        });
    }

    private Object reparentAfterBarrier(
        Fixture fixture,
        RelationView current,
        WorkItemView candidate,
        WorkItemView child,
        String requestId,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        try {
            ready.countDown();
            start.await();
            return hierarchyService.reparent(
                fixture.owner(), fixture.spaceId(), current.id(), candidate.item().id(),
                current.version(), 0, 0, child.item().version(),
                "concurrent move", "REPARENT", requestId
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

    private Fixture fixture(String label, int maxDepth) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID bugTypeId = UUID.randomUUID();
        UUID bugVersionId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JsonNode snapshot = objectMapper.readTree("""
            {
              "snapshotSchemaVersion":4,
              "typeDefinition":{
                "id":"%s","workspaceId":"%s","spaceId":"%s","typeKey":"task",
                "name":"Task","icon":"","description":"","sortOrder":0,
                "status":"active","system":false
              },
              "fields":[
                {
                  "id":"%s","fieldKey":"priority","name":"Priority","fieldType":"text",
                  "config":{"schemaVersion":1,"required":false,"validationRules":[]},
                  "sortOrder":0,"status":"active","system":false,"options":[]
                }
              ],
              "layouts":[],
              "relationDefinitions":[
                {
                  "relationKey":"parent_child","kind":"parent_child","direction":"directed",
                  "forwardName":"Parent","reverseName":"Child",
                  "sourceTypeKeys":["task"],"targetTypeKeys":["task","bug"],
                  "sourceCardinality":"many","targetCardinality":"one",
                  "deletionPolicy":"restrict","allowSelf":false,"maxDepth":%d,
                  "sortOrder":100,"system":false
                }
              ]
            }
            """.formatted(
                typeId, WORKSPACE_ID, spaceId, UUID.randomUUID(), maxDepth
            ));
        var canonical = snapshotCanonicalizer.canonicalize(snapshot);
        JsonNode bugSnapshot = objectMapper.readTree("""
            {
              "snapshotSchemaVersion":4,
              "typeDefinition":{
                "id":"%s","workspaceId":"%s","spaceId":"%s","typeKey":"bug",
                "name":"Bug","icon":"","description":"","sortOrder":1,
                "status":"active","system":false
              },
              "fields":[
                {
                  "id":"%s","fieldKey":"priority","name":"Priority","fieldType":"text",
                  "config":{"schemaVersion":1,"required":false,"validationRules":[]},
                  "sortOrder":0,"status":"active","system":false,"options":[]
                }
              ],
              "layouts":[],
              "relationDefinitions":[
                {
                  "relationKey":"parent_child","kind":"parent_child","direction":"directed",
                  "forwardName":"Parent","reverseName":"Child",
                  "sourceTypeKeys":["bug"],"targetTypeKeys":["task","bug"],
                  "sourceCardinality":"many","targetCardinality":"one",
                  "deletionPolicy":"restrict","allowSelf":false,"maxDepth":%d,
                  "sortOrder":100,"system":false
                }
              ]
            }
            """.formatted(
                bugTypeId, WORKSPACE_ID, spaceId, UUID.randomUUID(), maxDepth
            ));
        var bugCanonical = snapshotCanonicalizer.canonicalize(bugSnapshot);

        jdbcTemplate.update(
            """
                insert into users (
                    id, workspace_id, username, password_hash, display_name, status,
                    created_at, updated_at
                ) values (?, ?, ?, 'not-used', ?, 'active', now(), now())
                """,
            userId, WORKSPACE_ID, "s10_m3_" + label + "_" + suffix, "S10 M3 " + label
        );
        jdbcTemplate.update(
            """
                insert into project_spaces (
                    id, workspace_id, space_key, name, status, visibility, version,
                    created_by, created_at, updated_by, updated_at
                ) values (?, ?, ?, ?, 'active', 'private', 0, ?, now(), ?, now())
                """,
            spaceId, WORKSPACE_ID, "s10_m3_" + label + "_" + suffix, "S10 M3 " + label,
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
            jdbcTemplate.update(
                """
                    insert into project_work_item_types (
                        id, workspace_id, space_id, type_key, name, icon, description,
                        sort_order, status, is_system, current_version_id, created_by,
                        created_at, updated_by, updated_at, aggregate_version
                    ) values (?, ?, ?, 'bug', 'Bug', '', '', 1, 'active', false, ?, ?, now(), ?, now(), 0)
                    """,
                bugTypeId, WORKSPACE_ID, spaceId, bugVersionId, userId, userId
            );
            jdbcTemplate.update(
                """
                    insert into project_work_item_type_versions (
                        id, workspace_id, space_id, type_definition_id, version_number,
                        config_hash, status, config, created_by, created_at, published_by,
                        published_at, snapshot_schema_version
                    ) values (?, ?, ?, ?, 1, ?, 'published', ?::jsonb, ?, now(), ?, now(), 4)
                    """,
                bugVersionId, WORKSPACE_ID, spaceId, bugTypeId, bugCanonical.configHash(),
                bugCanonical.payload().toString(), userId, userId
            );
        });
        return new Fixture(
            new CurrentUser(
                userId, WORKSPACE_ID, UUID.randomUUID(),
                "s10_m3_" + label + "_" + suffix, "S10 M3 " + label,
                Set.of("member"), Set.of()
            ),
            spaceId,
            typeId,
            bugTypeId
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
            userId, WORKSPACE_ID, "s10_m3_" + role + "_" + suffix, "S10 M3 " + role
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
            "s10_m3_" + role + "_" + suffix, "S10 M3 " + role,
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

    private void assertCode(
        String code,
        org.assertj.core.api.ThrowableAssert.ThrowingCallable operation
    ) {
        assertThatThrownBy(operation)
            .isInstanceOf(WorkItemRuntimeException.class)
            .extracting(exception -> ((WorkItemRuntimeException) exception).code())
            .isEqualTo(code);
    }

    private record Fixture(
        CurrentUser owner,
        UUID spaceId,
        UUID typeId,
        UUID bugTypeId
    ) {
    }
}
