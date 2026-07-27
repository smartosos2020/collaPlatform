package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.PermissionDecision;
import com.colla.platform.modules.project.contract.WorkItemPermissionContracts.SubjectContext;
import com.colla.platform.modules.project.domain.WorkItemPermissionModels;
import com.colla.platform.modules.project.runtime.PublishedSnapshotAdapter.RuntimeConfiguration;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter;
import com.colla.platform.modules.project.runtime.WorkItemPermissionRuntimeAdapter.EvaluationContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Single and batch WorkItem permission decisions share the same evaluator and ordering.
 */
@Service
public final class WorkItemPermissionDecisionService {
    public static final int MAX_BATCH_SIZE = 200;

    private final WorkItemPermissionRuntimeAdapter runtimeAdapter;
    private final Clock clock;

    @Autowired
    public WorkItemPermissionDecisionService(WorkItemPermissionRuntimeAdapter runtimeAdapter) {
        this(runtimeAdapter, Clock.systemUTC());
    }

    WorkItemPermissionDecisionService(WorkItemPermissionRuntimeAdapter runtimeAdapter, Clock clock) {
        this.runtimeAdapter = runtimeAdapter;
        this.clock = clock;
    }

    public PermissionDecision decide(
        RuntimeConfiguration configuration,
        SubjectContext subject,
        UUID spaceId,
        UUID workItemId,
        String action
    ) {
        return decide(configuration, subject, spaceId, workItemId, action, EvaluationContext.empty());
    }

    public PermissionDecision decide(
        RuntimeConfiguration configuration,
        SubjectContext subject,
        UUID spaceId,
        UUID workItemId,
        String action,
        EvaluationContext context
    ) {
        if (!WorkItemPermissionModels.ACTION_KEYS.contains(action)) {
            throw failure("INVALID_PERMISSION_ACTION", "Permission action is not supported");
        }
        var evaluated = runtimeAdapter.evaluate(configuration, subject, action, context);
        Instant now = clock.instant();
        String identity = subject.workspaceId() + ":" + spaceId + ":" + workItemId + ":"
            + action + ":" + configuration.versionId() + ":" + subject.subjectVersion()
            + ":" + evaluated.allowed() + ":" + evaluated.safePolicySources();
        return new PermissionDecision(
            UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)),
            subject.workspaceId(),
            spaceId,
            workItemId,
            action,
            evaluated.allowed(),
            evaluated.reasonCode(),
            evaluated.allowed() ? "user_safe" : "minimal",
            configuration.versionId(),
            configuration.configHash(),
            subject.subjectVersion(),
            evaluated.safePolicySources(),
            now
        );
    }

    public List<PermissionDecision> decideBatch(List<DecisionInput> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > MAX_BATCH_SIZE) {
            throw failure("INVALID_PERMISSION_BATCH", "Permission batch must contain 1 to 200 decisions");
        }
        List<PermissionDecision> results = new ArrayList<>(inputs.size());
        for (DecisionInput input : inputs) {
            results.add(decide(
                input.configuration(),
                input.subject(),
                input.spaceId(),
                input.workItemId(),
                input.action()
            ));
        }
        return List.copyOf(results);
    }

    public List<PermissionDecision> decideContextBatch(List<ContextDecisionInput> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > MAX_BATCH_SIZE) {
            throw failure("INVALID_PERMISSION_BATCH", "Permission batch must contain 1 to 200 decisions");
        }
        List<PermissionDecision> results = new ArrayList<>(inputs.size());
        for (ContextDecisionInput input : inputs) {
            results.add(decide(
                input.configuration(),
                input.subject(),
                input.spaceId(),
                input.workItemId(),
                input.action(),
                input.context()
            ));
        }
        return List.copyOf(results);
    }

    public void require(PermissionDecision decision) {
        if (!decision.allowed()) {
            throw failure(
                "FORBIDDEN",
                "The requested WorkItem action is not allowed by the bound permission policy"
            );
        }
    }

    public record DecisionInput(
        RuntimeConfiguration configuration,
        SubjectContext subject,
        UUID spaceId,
        UUID workItemId,
        String action
    ) {
    }

    public record ContextDecisionInput(
        RuntimeConfiguration configuration,
        SubjectContext subject,
        UUID spaceId,
        UUID workItemId,
        String action,
        EvaluationContext context
    ) {
    }
}
