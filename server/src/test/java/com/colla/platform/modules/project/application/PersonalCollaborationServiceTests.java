package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.platform.contract.PlatformSearchProjectionProvider.SearchDocument;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ActivityItem;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.NudgeReceipt;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderPreference;
import com.colla.platform.modules.project.contract.PersonalWorkQuery;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.BucketReason;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkItem;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkPage;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.WorkBucket;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.WorkBucketView;
import com.colla.platform.modules.project.infrastructure.PersonalCollaborationRepository;
import com.colla.platform.modules.project.infrastructure.PersonalCollaborationRepository.NudgeCommand;
import com.colla.platform.shared.auth.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PersonalCollaborationServiceTests {
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RECIPIENT = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID SPACE = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ITEM = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-27T08:00:00Z");

    @Test
    void activityAndReminderOnlyUseCurrentlyVisiblePersonalWork() {
        Fixture fixture = fixture();
        when(fixture.personalWork.list(any(), eq(null), eq(100))).thenReturn(page());
        when(fixture.repository.readThroughSequence(WORKSPACE, USER)).thenReturn(0L);
        when(fixture.repository.listActivities(eq(WORKSPACE), eq(Set.of(ITEM)), eq(null), eq(31)))
            .thenReturn(List.of(
                new ActivityItem(
                    NOW.toEpochMilli(),
                    ITEM,
                    SPACE,
                    "TASK-1",
                    "Visible item",
                    "updated",
                    3,
                    NOW,
                    "/project-spaces/" + SPACE + "/work-items/" + ITEM
                )
            ));
        when(fixture.repository.preference(WORKSPACE, USER))
            .thenReturn(new ReminderPreference("UTC", 1440, true, NOW));

        var activities = fixture.service.activities(user(), null, 30);
        var reminders = fixture.service.reminders(user(), "Asia/Shanghai");

        assertThat(activities.items()).singleElement()
            .extracting(ActivityItem::title)
            .isEqualTo("Visible item");
        assertThat(activities.unreadCount()).isEqualTo(1);
        assertThat(reminders.items()).singleElement()
            .satisfies(value -> {
                assertThat(value.workItemId()).isEqualTo(ITEM);
                assertThat(value.state()).isEqualTo(
                    com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderState.approaching
                );
            });
    }

    @Test
    void nudgeIsIdempotentAndRejectsRequestIdReuse() {
        Fixture fixture = fixture();
        when(fixture.workItems.allowed(any(), eq(List.of(ITEM)), eq(Set.of())))
            .thenReturn(Set.of(ITEM));
        when(fixture.workItems.findDocument(WORKSPACE, ITEM)).thenReturn(Optional.of(
            new SearchDocument(
                "work_item",
                ITEM,
                "TASK-1 Visible item",
                "Visible item",
                "/project-spaces/" + SPACE + "/work-items/" + ITEM,
                "colla://work-item/" + ITEM,
                "Visible item",
                NOW,
                SPACE,
                "task",
                "active",
                3
            )
        ));
        when(fixture.repository.nudgeRecipients(WORKSPACE, SPACE, ITEM))
            .thenReturn(Set.of(RECIPIENT));
        when(fixture.repository.createNudge(
            any(), eq(WORKSPACE), eq(SPACE), eq(ITEM), eq(USER), eq(RECIPIENT),
            eq("request-1"), any(), eq(NOW)
        )).thenReturn(true);

        NudgeReceipt created = fixture.service.nudge(user(), SPACE, ITEM, RECIPIENT, "request-1");
        when(fixture.repository.findNudge(WORKSPACE, USER, "request-1"))
            .thenReturn(Optional.of(new NudgeCommand(
                new NudgeReceipt(
                    created.receiptId(),
                    ITEM,
                    RECIPIENT,
                    "accepted",
                    NOW,
                    true
                ),
                sha256(SPACE + ":" + ITEM + ":" + RECIPIENT)
            )));

        NudgeReceipt replay = fixture.service.nudge(user(), SPACE, ITEM, RECIPIENT, "request-1");

        assertThat(created.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        verify(fixture.outbox).append(
            eq(WORKSPACE),
            eq("notification.created"),
            eq("work_item"),
            eq(ITEM),
            eq(USER),
            any(),
            eq("personal-nudge:" + created.receiptId())
        );

        assertThatThrownBy(() -> fixture.service.nudge(
            user(),
            SPACE,
            UUID.randomUUID(),
            RECIPIENT,
            "request-1"
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("different nudge");
    }

    @Test
    void hiddenWorkItemCannotBeNudgedAndDoesNotResolveRecipients() {
        Fixture fixture = fixture();
        when(fixture.workItems.allowed(any(), eq(List.of(ITEM)), eq(Set.of())))
            .thenReturn(Set.of());

        assertThatThrownBy(() -> fixture.service.nudge(
            user(),
            SPACE,
            ITEM,
            RECIPIENT,
            "request-hidden"
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");

        verify(fixture.repository, never()).nudgeRecipients(any(), any(), any());
    }

    private Fixture fixture() {
        PersonalWorkQuery personalWork = mock(PersonalWorkQuery.class);
        WorkItemSearchProjectionProvider workItems = mock(WorkItemSearchProjectionProvider.class);
        PersonalCollaborationRepository repository = mock(PersonalCollaborationRepository.class);
        TransactionalOutbox outbox = mock(TransactionalOutbox.class);
        AuditLog auditLog = mock(AuditLog.class);
        return new Fixture(
            personalWork,
            workItems,
            repository,
            outbox,
            new PersonalCollaborationService(
                personalWork,
                workItems,
                repository,
                outbox,
                auditLog,
                Clock.fixed(NOW, ZoneOffset.UTC)
            )
        );
    }

    private PersonalWorkPage page() {
        PersonalWorkItem item = new PersonalWorkItem(
            ITEM,
            SPACE,
            "Operations",
            "task",
            "Task",
            "TASK-1",
            "Visible item",
            "active",
            3,
            NOW,
            List.of(new BucketReason(
                WorkBucket.todo,
                "node_task",
                "pending",
                2,
                NOW.plusSeconds(3600)
            )),
            List.of("view", "edit"),
            "/project-spaces/" + SPACE + "/work-items/" + ITEM
        );
        return new PersonalWorkPage(
            List.of(
                new WorkBucketView(WorkBucket.todo, 1, List.of(item)),
                new WorkBucketView(WorkBucket.responsible, 0, List.of()),
                new WorkBucketView(WorkBucket.participating, 0, List.of()),
                new WorkBucketView(WorkBucket.watching, 0, List.of())
            ),
            null,
            false,
            NOW
        );
    }

    private CurrentUser user() {
        return new CurrentUser(
            USER,
            WORKSPACE,
            UUID.randomUUID(),
            "member",
            "Member",
            Set.of("member"),
            Set.of()
        );
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Fixture(
        PersonalWorkQuery personalWork,
        WorkItemSearchProjectionProvider workItems,
        PersonalCollaborationRepository repository,
        TransactionalOutbox outbox,
        PersonalCollaborationService service
    ) {
    }
}
