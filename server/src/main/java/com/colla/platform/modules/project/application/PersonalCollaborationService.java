package com.colla.platform.modules.project.application;

import com.colla.platform.modules.audit.contract.AuditLog;
import com.colla.platform.modules.event.contract.TransactionalOutbox;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ActivityItem;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ActivityPage;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ConsistencyResult;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.NudgeReceipt;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReadState;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderDispatchResult;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderItem;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderPreference;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderState;
import com.colla.platform.modules.project.contract.PersonalCollaborationQuery.ReminderView;
import com.colla.platform.modules.project.contract.PersonalWorkQuery;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkItem;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkPage;
import com.colla.platform.modules.project.infrastructure.PersonalCollaborationRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalCollaborationService implements PersonalCollaborationQuery {
    private static final Duration NUDGE_COOLDOWN = Duration.ofMinutes(30);
    private static final Duration DUE_WINDOW = Duration.ofMinutes(5);

    private final PersonalWorkQuery personalWork;
    private final WorkItemSearchProjectionProvider workItems;
    private final PersonalCollaborationRepository repository;
    private final TransactionalOutbox outbox;
    private final AuditLog auditLog;
    private final Clock clock;

    @Autowired
    public PersonalCollaborationService(
        PersonalWorkQuery personalWork,
        WorkItemSearchProjectionProvider workItems,
        PersonalCollaborationRepository repository,
        TransactionalOutbox outbox,
        AuditLog auditLog
    ) {
        this(personalWork, workItems, repository, outbox, auditLog, Clock.systemUTC());
    }

    PersonalCollaborationService(
        PersonalWorkQuery personalWork,
        WorkItemSearchProjectionProvider workItems,
        PersonalCollaborationRepository repository,
        TransactionalOutbox outbox,
        AuditLog auditLog,
        Clock clock
    ) {
        this.personalWork = personalWork;
        this.workItems = workItems;
        this.repository = repository;
        this.outbox = outbox;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public ActivityPage activities(
        CurrentUser user,
        UUID spaceId,
        Long beforeSequence,
        int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<PersonalWorkItem> visible = visibleWork(user, spaceId);
        Set<UUID> visibleIds = visible.stream()
            .map(PersonalWorkItem::workItemId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<ActivityItem> candidates = repository.listActivities(
            user.workspaceId(),
            visibleIds,
            beforeSequence,
            safeLimit + 1
        );
        boolean truncated = candidates.size() > safeLimit;
        List<ActivityItem> items = truncated ? candidates.subList(0, safeLimit) : candidates;
        long readThrough = repository.readThroughSequence(user.workspaceId(), user.id());
        long unread = items.stream().filter(item -> item.sequence() > readThrough).count();
        Long next = truncated && !items.isEmpty() ? items.get(items.size() - 1).sequence() : null;
        return new ActivityPage(items, next, readThrough, unread, truncated, clock.instant());
    }

    @Override
    @Transactional
    public ReadState markActivitiesRead(CurrentUser user, long throughSequence) {
        if (throughSequence < 0 || throughSequence > clock.instant().plus(Duration.ofMinutes(1)).toEpochMilli()) {
            throw badRequest("Invalid activity read watermark");
        }
        Instant now = clock.instant();
        repository.markRead(user.workspaceId(), user.id(), throughSequence, now);
        return new ReadState(repository.readThroughSequence(user.workspaceId(), user.id()), now);
    }

    @Override
    public ReminderView reminders(CurrentUser user, String timezone) {
        ReminderPreference preference = repository.preference(user.workspaceId(), user.id());
        String effectiveTimezone = timezone == null || timezone.isBlank()
            ? preference.timezone()
            : canonicalTimezone(timezone);
        Instant now = clock.instant();
        if (!preference.enabled()) {
            return new ReminderView(List.of(), effectiveTimezone, now, false);
        }
        List<ReminderItem> reminders = new ArrayList<>();
        for (PersonalWorkItem item : visibleWork(user)) {
            item.reasons().stream()
                .map(PersonalWorkQuery.BucketReason::dueAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .filter(dueAt -> !dueAt.isAfter(now.plus(Duration.ofMinutes(preference.approachingMinutes()))))
                .ifPresent(dueAt -> reminders.add(new ReminderItem(
                    item.workItemId(),
                    item.spaceId(),
                    item.displayKey(),
                    item.title(),
                    dueAt,
                    reminderState(now, dueAt),
                    item.deepLink()
                )));
        }
        reminders.sort(java.util.Comparator.comparing(ReminderItem::dueAt));
        return new ReminderView(reminders, effectiveTimezone, now, true);
    }

    @Override
    @Transactional
    public ReminderDispatchResult dispatchReminders(
        CurrentUser user,
        String timezone,
        String requestId
    ) {
        String safeRequestId = requireRequestId(requestId);
        ReminderView view = reminders(user, timezone);
        int emitted = 0;
        for (ReminderItem item : view.items()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("recipientId", user.id().toString());
            payload.put("notificationType", "project_work_item_reminder");
            payload.put("title", reminderTitle(item));
            payload.put("body", item.displayKey() + " · " + item.state().name());
            payload.put("targetType", "work_item");
            payload.put("targetId", item.workItemId().toString());
            payload.put("webPath", item.deepLink());
            payload.put(
                "dedupeKey",
                "work-item-reminder:" + user.id() + ":" + item.workItemId() + ":"
                    + item.dueAt().toEpochMilli() + ":" + item.state()
            );
            outbox.append(
                user.workspaceId(),
                "notification.created",
                "work_item",
                item.workItemId(),
                user.id(),
                payload,
                "personal-reminder:" + safeRequestId + ":" + item.workItemId() + ":" + item.state()
            );
            emitted++;
        }
        return new ReminderDispatchResult(view.items().size(), emitted, clock.instant());
    }

    @Override
    public ReminderPreference preference(CurrentUser user) {
        return repository.preference(user.workspaceId(), user.id());
    }

    @Override
    @Transactional
    public ReminderPreference updatePreference(
        CurrentUser user,
        String timezone,
        int approachingMinutes,
        boolean enabled
    ) {
        if (approachingMinutes < 5 || approachingMinutes > 10080) {
            throw badRequest("Reminder lead time must be between 5 and 10080 minutes");
        }
        ReminderPreference result = repository.updatePreference(
            user.workspaceId(),
            user.id(),
            canonicalTimezone(timezone),
            approachingMinutes,
            enabled,
            clock.instant()
        );
        auditLog.log(
            user,
            "personal.reminder.preference.updated",
            "user",
            user.id(),
            Map.of(
                "timezone", result.timezone(),
                "approachingMinutes", result.approachingMinutes(),
                "enabled", result.enabled()
            )
        );
        return result;
    }

    @Override
    @Transactional
    public NudgeReceipt nudge(
        CurrentUser user,
        UUID spaceId,
        UUID workItemId,
        UUID recipientId,
        String requestId
    ) {
        String safeRequestId = requireRequestId(requestId);
        if (recipientId == null || recipientId.equals(user.id())) {
            throw badRequest("A distinct nudge recipient is required");
        }
        String requestHash = sha256(spaceId + ":" + workItemId + ":" + recipientId);
        var replay = repository.findNudge(user.workspaceId(), user.id(), safeRequestId);
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw conflict("Request id was already used for a different nudge");
            }
            return replay.get().receipt();
        }
        if (!workItems.allowed(user, List.of(workItemId), Set.of()).contains(workItemId)) {
            throw notFound();
        }
        var document = workItems.findDocument(user.workspaceId(), workItemId)
            .filter(value -> spaceId.equals(value.spaceId()))
            .orElseThrow(PersonalCollaborationService::notFound);
        Set<UUID> recipients = repository.nudgeRecipients(user.workspaceId(), spaceId, workItemId);
        if (!recipients.contains(recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nudge is unavailable");
        }
        Instant now = clock.instant();
        if (repository.recentlyNudged(
            user.workspaceId(),
            workItemId,
            user.id(),
            recipientId,
            now.minus(NUDGE_COOLDOWN)
        )) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Nudge cooldown is active");
        }
        UUID receiptId = UUID.randomUUID();
        if (!repository.createNudge(
            receiptId,
            user.workspaceId(),
            spaceId,
            workItemId,
            user.id(),
            recipientId,
            safeRequestId,
            requestHash,
            now
        )) {
            return repository.findNudge(user.workspaceId(), user.id(), safeRequestId)
                .filter(value -> value.requestHash().equals(requestHash))
                .map(PersonalCollaborationRepository.NudgeCommand::receipt)
                .orElseThrow(() -> conflict("Concurrent nudge request conflict"));
        }
        outbox.append(
            user.workspaceId(),
            "notification.created",
            "work_item",
            workItemId,
            user.id(),
            Map.of(
                "recipientId", recipientId.toString(),
                "notificationType", "project_work_item_nudge",
                "title", document.title(),
                "body", "You have been nudged about this work item",
                "targetType", "work_item",
                "targetId", workItemId.toString(),
                "webPath", document.webPath(),
                "dedupeKey", "work-item-nudge:" + receiptId
            ),
            "personal-nudge:" + receiptId
        );
        auditLog.log(
            user,
            "work_item.nudged",
            "work_item",
            workItemId,
            Map.of("recipientId", recipientId.toString(), "receiptId", receiptId.toString())
        );
        return new NudgeReceipt(receiptId, workItemId, recipientId, "accepted", now, false);
    }

    @Override
    @Transactional
    public ConsistencyResult consistency(CurrentUser user, boolean dryRun, boolean rebuild) {
        if (dryRun && rebuild) {
            throw badRequest("dryRun and rebuild cannot both be true");
        }
        long activeBefore = repository.activeProjectionRows(user.workspaceId(), user.id());
        long invalidBefore = repository.invalidProjectionRows(user.workspaceId(), user.id());
        int refreshed = 0;
        if (rebuild) {
            repository.clearDiscardableProjection(user.workspaceId(), user.id());
            refreshed = visibleWork(user).size();
        }
        return new ConsistencyResult(
            dryRun,
            rebuild,
            rebuild ? repository.activeProjectionRows(user.workspaceId(), user.id()) : activeBefore,
            rebuild ? repository.invalidProjectionRows(user.workspaceId(), user.id()) : invalidBefore,
            refreshed,
            List.of(),
            clock.instant()
        );
    }

    private List<PersonalWorkItem> visibleWork(CurrentUser user) {
        return visibleWork(user, null);
    }

    private List<PersonalWorkItem> visibleWork(CurrentUser user, UUID spaceId) {
        PersonalWorkPage page = personalWork.list(user, spaceId, null, 100);
        LinkedHashMap<UUID, PersonalWorkItem> values = new LinkedHashMap<>();
        page.buckets().forEach(bucket -> bucket.items().stream()
            .filter(item -> spaceId == null || spaceId.equals(item.spaceId()))
            .forEach(item -> values.putIfAbsent(item.workItemId(), item)));
        return List.copyOf(values.values());
    }

    private static ReminderState reminderState(Instant now, Instant dueAt) {
        if (now.isAfter(dueAt.plus(DUE_WINDOW))) {
            return ReminderState.overdue;
        }
        if (!now.isBefore(dueAt.minus(DUE_WINDOW))) {
            return ReminderState.due;
        }
        return ReminderState.approaching;
    }

    private static String reminderTitle(ReminderItem item) {
        return switch (item.state()) {
            case approaching -> item.displayKey() + " is approaching its due time";
            case due -> item.displayKey() + " is due";
            case overdue -> item.displayKey() + " is overdue";
        };
    }

    private static String canonicalTimezone(String value) {
        String safe = value == null || value.isBlank() ? "UTC" : value.trim();
        try {
            return ZoneId.of(safe).getId();
        } catch (ZoneRulesException exception) {
            throw badRequest("Invalid timezone");
        }
    }

    private static String requireRequestId(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 120) {
            throw badRequest("A request id of at most 120 characters is required");
        }
        return value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Work item not found");
    }
}
