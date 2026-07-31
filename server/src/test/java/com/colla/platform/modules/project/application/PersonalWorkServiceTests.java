package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.project.contract.PersonalWorkQuery.WorkBucket;
import com.colla.platform.modules.project.domain.PersonalWorkModels.PersonalCandidate;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItem;
import com.colla.platform.modules.project.infrastructure.PersonalWorkRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.JwtTokenProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalWorkServiceTests {
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID VERSION = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    @Test
    void aggregatesMultipleReasonsOnceAndSignsCursorToWorkspaceAndUser() {
        PersonalWorkRepository repository = mock(PersonalWorkRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        PublishedSnapshotAdapter snapshots = mock(PublishedSnapshotAdapter.class);
        RuntimeConfiguration configuration = configuration();
        when(snapshots.requireComplete(eq(WORKSPACE), eq(SPACE), any(), eq(VERSION)))
            .thenReturn(configuration);
        when(spaces.listVisible(WORKSPACE, USER)).thenReturn(List.of(space(USER)));

        PersonalCandidate first = candidate(
            UUID.fromString("50000000-0000-0000-0000-000000000002"),
            NOW.minusSeconds(10),
            Set.of("assignee", "watcher"),
            true
        );
        PersonalCandidate second = candidate(
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            NOW.minusSeconds(20),
            Set.of("collaborator"),
            false
        );
        when(repository.listCandidates(
            eq(WORKSPACE), eq(USER), isNull(), any(), any(), anyInt()
        ))
            .thenReturn(List.of(first, second));

        JwtTokenProperties tokenProperties = new JwtTokenProperties();
        tokenProperties.setAccessSecret("personal-work-test-key-with-sufficient-entropy");
        PersonalWorkService service = new PersonalWorkService(
            repository,
            spaces,
            snapshots,
            new WorkItemPermissionDecisionService(
                new WorkItemPermissionRuntimeAdapter(),
                Clock.fixed(NOW, ZoneOffset.UTC)
            ),
            tokenProperties,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var page = service.list(user(USER), null, 1);

        assertThat(page.nextCursor()).isNotBlank();
        assertThat(page.buckets())
            .filteredOn(bucket -> bucket.bucket() == WorkBucket.responsible)
            .singleElement()
            .extracting(bucket -> bucket.items().getFirst().workItemId())
            .isEqualTo(first.item().id());
        assertThat(page.buckets())
            .filteredOn(bucket -> bucket.bucket() == WorkBucket.watching)
            .singleElement()
            .extracting(bucket -> bucket.items().getFirst().workItemId())
            .isEqualTo(first.item().id());
        var personalItem = page.buckets().stream()
            .filter(bucket -> bucket.bucket() == WorkBucket.responsible)
            .findFirst()
            .orElseThrow()
            .items()
            .getFirst();
        assertThat(personalItem.availableActions()).isEqualTo(personalItem.capabilities());
        verify(repository).synchronizeProjection(eq(WORKSPACE), eq(USER), any(), eq(NOW));

        UUID otherUser = UUID.fromString("30000000-0000-0000-0000-000000000002");
        assertThatThrownBy(() -> service.list(user(otherUser), page.nextCursor(), 1))
            .hasMessageContaining("cursor is invalid");
        assertThatThrownBy(() -> service.list(user(USER), SPACE, page.nextCursor(), 1))
            .hasMessageContaining("cursor is invalid");
    }

    @Test
    void appliesSpaceScopeBeforeScanningAndBindsCursorToThatScope() {
        PersonalWorkRepository repository = mock(PersonalWorkRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        PublishedSnapshotAdapter snapshots = mock(PublishedSnapshotAdapter.class);
        RuntimeConfiguration configuration = configuration();
        when(snapshots.requireComplete(eq(WORKSPACE), eq(SPACE), any(), eq(VERSION)))
            .thenReturn(configuration);
        when(spaces.listVisible(WORKSPACE, USER)).thenReturn(List.of(space(USER)));

        PersonalCandidate first = candidate(
            UUID.fromString("50000000-0000-0000-0000-000000000002"),
            NOW.minusSeconds(10),
            Set.of("assignee"),
            false
        );
        PersonalCandidate second = candidate(
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            NOW.minusSeconds(20),
            Set.of("collaborator"),
            false
        );
        when(repository.listCandidates(
            eq(WORKSPACE), eq(USER), eq(SPACE), any(), any(), anyInt()
        )).thenReturn(List.of(first, second));

        JwtTokenProperties tokenProperties = new JwtTokenProperties();
        tokenProperties.setAccessSecret("personal-work-test-key-with-sufficient-entropy");
        PersonalWorkService service = new PersonalWorkService(
            repository,
            spaces,
            snapshots,
            new WorkItemPermissionDecisionService(
                new WorkItemPermissionRuntimeAdapter(),
                Clock.fixed(NOW, ZoneOffset.UTC)
            ),
            tokenProperties,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var page = service.list(user(USER), SPACE, null, 1);

        assertThat(page.nextCursor()).isNotBlank();
        verify(repository).listCandidates(
            WORKSPACE,
            USER,
            SPACE,
            null,
            null,
            6
        );
        assertThat(service.list(user(USER), SPACE, page.nextCursor(), 1)).isNotNull();
        UUID otherSpace = UUID.fromString("20000000-0000-0000-0000-000000000002");
        assertThatThrownBy(() -> service.list(user(USER), otherSpace, page.nextCursor(), 1))
            .hasMessageContaining("cursor is invalid");
        assertThatThrownBy(() -> service.list(user(USER), page.nextCursor(), 1))
            .hasMessageContaining("cursor is invalid");
    }

    @Test
    void nonMemberCandidateIsRemovedBeforeDecisionAndNeverAffectsCounts() {
        PersonalWorkRepository repository = mock(PersonalWorkRepository.class);
        ProjectSpaceRepository spaces = mock(ProjectSpaceRepository.class);
        PublishedSnapshotAdapter snapshots = mock(PublishedSnapshotAdapter.class);
        PersonalCandidate candidate = candidate(UUID.randomUUID(), NOW, Set.of("assignee"), false);
        when(repository.listCandidates(
            eq(WORKSPACE), eq(USER), isNull(), any(), any(), anyInt()
        ))
            .thenReturn(List.of(candidate));
        when(spaces.listVisible(WORKSPACE, USER)).thenReturn(List.of());
        JwtTokenProperties tokenProperties = new JwtTokenProperties();
        tokenProperties.setAccessSecret("personal-work-test-key-with-sufficient-entropy");
        PersonalWorkService service = new PersonalWorkService(
            repository,
            spaces,
            snapshots,
            new WorkItemPermissionDecisionService(
                new WorkItemPermissionRuntimeAdapter(),
                Clock.fixed(NOW, ZoneOffset.UTC)
            ),
            tokenProperties,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var page = service.list(user(USER), null, 10);

        assertThat(page.buckets()).allMatch(bucket -> bucket.visibleCount() == 0);
        verify(repository).synchronizeProjection(WORKSPACE, USER, List.of(), NOW);
    }

    private RuntimeConfiguration configuration() {
        ObjectMapper mapper = new ObjectMapper();
        var snapshot = mapper.createObjectNode();
        snapshot.put("snapshotSchemaVersion", 5);
        snapshot.set("permissionModel", new WorkItemPermissionPresetCatalog(mapper).modelFor("task"));
        return new RuntimeConfiguration(
            VERSION,
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            1,
            5,
            "a".repeat(64),
            snapshot
        );
    }

    private PersonalCandidate candidate(
        UUID itemId,
        Instant updatedAt,
        Set<String> roles,
        boolean task
    ) {
        WorkItem item = new WorkItem(
            itemId,
            WORKSPACE,
            SPACE,
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            VERSION,
            "task",
            "Task",
            "a".repeat(64),
            1,
            "TASK-1",
            "Personal work item",
            new ObjectMapper().createObjectNode(),
            "active",
            3,
            USER,
            NOW.minusSeconds(100),
            USER,
            updatedAt,
            null
        );
        return new PersonalCandidate(item, roles, task, task ? "pending" : null, 2, task ? NOW : null);
    }

    private ProjectSpaceSummary space(UUID userId) {
        return new ProjectSpaceSummary(
            SPACE,
            WORKSPACE,
            "OPS",
            "Operations",
            "",
            "active",
            "private",
            1,
            "member",
            1,
            userId,
            NOW,
            userId,
            NOW,
            null,
            null
        );
    }

    private CurrentUser user(UUID userId) {
        return new CurrentUser(
            userId,
            WORKSPACE,
            UUID.randomUUID(),
            "member",
            "Member",
            Set.of("member"),
            Set.of()
        );
    }
}
