package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.application.WorkItemPermissionDecisionService.ContextDecisionInput;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.PermissionDecision;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.SubjectContext;
import com.colla.platform.modules.project.contract.PersonalWorkQuery;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.BucketReason;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkItem;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.PersonalWorkPage;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.WorkBucket;
import com.colla.platform.modules.project.contract.PersonalWorkQuery.WorkBucketView;
import com.colla.platform.modules.project.domain.PersonalWorkModels.PersonalCandidate;
import com.colla.platform.modules.project.domain.ProjectSpaceModels.ProjectSpaceSummary;
import com.colla.platform.modules.project.infrastructure.PersonalWorkRepository;
import com.colla.platform.modules.project.infrastructure.ProjectSpaceRepository;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter.EvaluationContext;
import com.colla.platform.shared.auth.CurrentUser;
import com.colla.platform.shared.auth.JwtTokenProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalWorkService implements PersonalWorkQuery {
    public static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CANDIDATE_SCAN = 500;

    private final PersonalWorkRepository repository;
    private final ProjectSpaceRepository spaceRepository;
    private final PublishedSnapshotAdapter snapshotAdapter;
    private final WorkItemPermissionDecisionService permissionDecisionService;
    private final JwtTokenProperties tokenProperties;
    private final Clock clock;

    @Autowired
    public PersonalWorkService(
        PersonalWorkRepository repository,
        ProjectSpaceRepository spaceRepository,
        PublishedSnapshotAdapter snapshotAdapter,
        WorkItemPermissionDecisionService permissionDecisionService,
        JwtTokenProperties tokenProperties
    ) {
        this(
            repository,
            spaceRepository,
            snapshotAdapter,
            permissionDecisionService,
            tokenProperties,
            Clock.systemUTC()
        );
    }

    PersonalWorkService(
        PersonalWorkRepository repository,
        ProjectSpaceRepository spaceRepository,
        PublishedSnapshotAdapter snapshotAdapter,
        WorkItemPermissionDecisionService permissionDecisionService,
        JwtTokenProperties tokenProperties,
        Clock clock
    ) {
        this.repository = repository;
        this.spaceRepository = spaceRepository;
        this.snapshotAdapter = snapshotAdapter;
        this.permissionDecisionService = permissionDecisionService;
        this.tokenProperties = tokenProperties;
        this.clock = clock;
    }

    @Transactional
    @Override
    public PersonalWorkPage list(CurrentUser user, UUID spaceId, String cursor, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        CursorAnchor anchor = decodeCursor(user, spaceId, cursor);
        int scanLimit = Math.min(MAX_CANDIDATE_SCAN, safeLimit * 5);
        List<PersonalCandidate> candidates = repository.listCandidates(
            user.workspaceId(),
            user.id(),
            spaceId,
            anchor == null ? null : anchor.updatedAt(),
            anchor == null ? null : anchor.workItemId(),
            scanLimit + 1
        );
        boolean candidateOverflow = candidates.size() > scanLimit;
        if (candidateOverflow) {
            candidates = candidates.subList(0, scanLimit);
        }

        Map<UUID, ProjectSpaceSummary> spaces = new HashMap<>();
        for (ProjectSpaceSummary space : spaceRepository.listVisible(user.workspaceId(), user.id())) {
            if ((spaceId == null || space.id().equals(spaceId))
                && space.isMember()
                && !"archived".equals(space.status())) {
                spaces.put(space.id(), space);
            }
        }

        List<PreparedCandidate> prepared = new ArrayList<>();
        List<ContextDecisionInput> viewInputs = new ArrayList<>();
        Map<SnapshotKey, RuntimeConfiguration> configurations = new HashMap<>();
        for (PersonalCandidate candidate : candidates) {
            ProjectSpaceSummary space = spaces.get(candidate.item().spaceId());
            if (space == null) {
                continue;
            }
            SnapshotKey snapshotKey = new SnapshotKey(
                candidate.item().spaceId(),
                candidate.item().typeDefinitionId(),
                candidate.item().typeVersionId()
            );
            RuntimeConfiguration configuration = configurations.computeIfAbsent(
                snapshotKey,
                ignored -> snapshotAdapter.requireComplete(
                    user.workspaceId(),
                    candidate.item().spaceId(),
                    candidate.item().typeDefinitionId(),
                    candidate.item().typeVersionId()
                )
            );
            if (!configuration.configHash().equals(candidate.item().configHash())) {
                continue;
            }
            SubjectContext subject = subject(user, space, candidate);
            EvaluationContext context = context(user, candidate);
            prepared.add(new PreparedCandidate(candidate, space, configuration, subject, context));
            viewInputs.add(new ContextDecisionInput(
                configuration,
                subject,
                candidate.item().spaceId(),
                candidate.item().id(),
                "view",
                context
            ));
        }

        List<PermissionDecision> viewDecisions = decideViewBatches(viewInputs);
        List<PreparedCandidate> visible = new ArrayList<>();
        for (int index = 0; index < prepared.size(); index++) {
            if (viewDecisions.get(index).allowed()) {
                visible.add(prepared.get(index));
            }
        }

        boolean visibleOverflow = visible.size() > safeLimit;
        List<PreparedCandidate> page = visibleOverflow ? visible.subList(0, safeLimit) : visible;
        List<PersonalWorkItem> items = page.stream().map(value -> toItem(user, value)).toList();
        repository.synchronizeProjection(user.workspaceId(), user.id(), items, clock.instant());

        EnumMap<WorkBucket, List<PersonalWorkItem>> grouped = new EnumMap<>(WorkBucket.class);
        for (WorkBucket bucket : WorkBucket.values()) {
            grouped.put(bucket, new ArrayList<>());
        }
        for (PersonalWorkItem item : items) {
            item.reasons().stream().map(BucketReason::bucket).distinct()
                .forEach(bucket -> grouped.get(bucket).add(item));
        }
        List<WorkBucketView> buckets = new ArrayList<>();
        for (WorkBucket bucket : WorkBucket.values()) {
            buckets.add(new WorkBucketView(bucket, grouped.get(bucket).size(), grouped.get(bucket)));
        }

        PreparedCandidate last = page.isEmpty() ? null : page.get(page.size() - 1);
        String nextCursor = (candidateOverflow || visibleOverflow) && last != null
            ? encodeCursor(
                user,
                spaceId,
                last.candidate().item().updatedAt(),
                last.candidate().item().id()
            )
            : null;
        return new PersonalWorkPage(
            buckets,
            nextCursor,
            candidateOverflow,
            clock.instant()
        );
    }

    private List<PermissionDecision> decideViewBatches(List<ContextDecisionInput> inputs) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        List<PermissionDecision> results = new ArrayList<>(inputs.size());
        for (int offset = 0; offset < inputs.size(); offset += WorkItemPermissionDecisionService.MAX_BATCH_SIZE) {
            int end = Math.min(inputs.size(), offset + WorkItemPermissionDecisionService.MAX_BATCH_SIZE);
            results.addAll(permissionDecisionService.decideContextBatch(inputs.subList(offset, end)));
        }
        return List.copyOf(results);
    }

    @Override
    public PersonalWorkPage dashboard(CurrentUser user) {
        return list(user, null, null, 12);
    }

    public void invalidate(
        UUID workspaceId,
        UUID userId,
        String sourceKey,
        long sourceVersion
    ) {
        if (sourceKey == null || sourceKey.isBlank() || sourceKey.length() > 160 || sourceVersion < 0) {
            throw failure("INVALID_PERSONAL_WORK_INVALIDATION", "Personal work invalidation is invalid");
        }
        repository.markInvalidated(
            workspaceId,
            userId,
            sourceKey.trim().toLowerCase(Locale.ROOT),
            sourceVersion,
            clock.instant()
        );
    }

    private PersonalWorkItem toItem(CurrentUser user, PreparedCandidate value) {
        PersonalCandidate candidate = value.candidate();
        List<BucketReason> reasons = reasons(candidate);
        List<String> capabilities = new ArrayList<>();
        for (String action : List.of("view", "edit", "archive")) {
            PermissionDecision decision = permissionDecisionService.decide(
                value.configuration(),
                value.subject(),
                candidate.item().spaceId(),
                candidate.item().id(),
                action,
                value.context()
            );
            if (decision.allowed()) {
                capabilities.add(action);
            }
        }
        return new PersonalWorkItem(
            candidate.item().id(),
            candidate.item().spaceId(),
            value.space().name(),
            candidate.item().typeKey(),
            candidate.item().typeName(),
            candidate.item().displayKey(),
            candidate.item().title(),
            candidate.item().status(),
            candidate.item().version(),
            candidate.item().updatedAt(),
            reasons,
            capabilities,
            "/project-spaces/" + candidate.item().spaceId() + "/work-items/" + candidate.item().id()
        );
    }

    private List<BucketReason> reasons(PersonalCandidate candidate) {
        LinkedHashMap<WorkBucket, BucketReason> reasons = new LinkedHashMap<>();
        if (candidate.pendingNodeTask()) {
            reasons.put(WorkBucket.todo, new BucketReason(
                WorkBucket.todo,
                "node_task",
                candidate.nodeTaskState(),
                candidate.nodeTaskVersion(),
                candidate.nodeTaskDueAt()
            ));
        }
        if (candidate.participantRoles().contains("owner")
            || candidate.participantRoles().contains("assignee")) {
            reasons.put(WorkBucket.responsible, new BucketReason(
                WorkBucket.responsible,
                "participant",
                "active",
                candidate.item().version(),
                null
            ));
        }
        if (candidate.participantRoles().contains("collaborator")) {
            reasons.put(WorkBucket.participating, new BucketReason(
                WorkBucket.participating,
                "participant",
                "active",
                candidate.item().version(),
                null
            ));
        }
        if (candidate.participantRoles().contains("watcher")) {
            reasons.put(WorkBucket.watching, new BucketReason(
                WorkBucket.watching,
                "participant",
                "active",
                candidate.item().version(),
                null
            ));
        }
        return List.copyOf(reasons.values());
    }

    private SubjectContext subject(
        CurrentUser user,
        ProjectSpaceSummary space,
        PersonalCandidate candidate
    ) {
        Set<String> enterpriseRoles = user.roles().stream()
            .map(role -> role.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        LinkedHashSet<String> workItemRoles = new LinkedHashSet<>(candidate.participantRoles());
        if (candidate.item().createdBy().equals(user.id())) {
            workItemRoles.add("creator");
        }
        return new SubjectContext(
            user.workspaceId(),
            user.id(),
            candidate.item().version(),
            enterpriseRoles,
            Set.of(space.currentUserRole()),
            workItemRoles,
            candidate.participantRoles()
        );
    }

    private EvaluationContext context(CurrentUser user, PersonalCandidate candidate) {
        Set<UUID> participants = candidate.participantRoles().isEmpty()
            && !candidate.item().createdBy().equals(user.id())
                ? Set.of()
                : Set.of(user.id());
        LinkedHashSet<String> roles = new LinkedHashSet<>(candidate.participantRoles());
        if (candidate.item().createdBy().equals(user.id())) {
            roles.add("creator");
        }
        Map<String, String> values = new HashMap<>();
        candidate.item().fieldValues().fields().forEachRemaining(entry -> {
            if (entry.getValue().isValueNode()) {
                values.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return new EvaluationContext(
            candidate.item().id(),
            candidate.item().createdBy(),
            participants,
            roles,
            Map.copyOf(values),
            null,
            null,
            null
        );
    }

    private String encodeCursor(
        CurrentUser user,
        UUID spaceId,
        Instant updatedAt,
        UUID workItemId
    ) {
        String payload = spaceId == null
            ? user.workspaceId() + "|" + user.id() + "|" + updatedAt + "|" + workItemId
            : user.workspaceId() + "|" + user.id() + "|" + spaceId + "|" + updatedAt + "|" + workItemId;
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            (payload + "|" + signature).getBytes(StandardCharsets.UTF_8)
        );
    }

    private CursorAnchor decodeCursor(CurrentUser user, UUID spaceId, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            boolean scoped = parts.length == 6;
            if ((!scoped && parts.length != 5)
                || (scoped && spaceId == null)
                || (!scoped && spaceId != null)) {
                throw failure("INVALID_PERSONAL_CURSOR", "Personal work cursor is invalid");
            }
            int updatedAtIndex = scoped ? 3 : 2;
            int workItemIdIndex = scoped ? 4 : 3;
            int signatureIndex = scoped ? 5 : 4;
            String payload = String.join(
                "|",
                java.util.Arrays.copyOf(parts, signatureIndex)
            );
            if (!MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.US_ASCII),
                parts[signatureIndex].getBytes(StandardCharsets.US_ASCII)
            ) || !user.workspaceId().toString().equals(parts[0])
                || !user.id().toString().equals(parts[1])
                || (scoped && !spaceId.toString().equals(parts[2]))) {
                throw failure("INVALID_PERSONAL_CURSOR", "Personal work cursor is invalid");
            }
            return new CursorAnchor(
                Instant.parse(parts[updatedAtIndex]),
                UUID.fromString(parts[workItemIdIndex])
            );
        } catch (IllegalArgumentException exception) {
            throw failure("INVALID_PERSONAL_CURSOR", "Personal work cursor is invalid", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                tokenProperties.getAccessSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            ));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign personal work cursor", exception);
        }
    }

    private record CursorAnchor(Instant updatedAt, UUID workItemId) {
    }

    private record SnapshotKey(UUID spaceId, UUID typeDefinitionId, UUID typeVersionId) {
    }

    private record PreparedCandidate(
        PersonalCandidate candidate,
        ProjectSpaceSummary space,
        RuntimeConfiguration configuration,
        SubjectContext subject,
        EvaluationContext context
    ) {
    }
}
