package com.colla.platform.modules.project.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class PersonalWorkModels {
    private PersonalWorkModels() {
    }

    public record PersonalCandidate(
        WorkItemModels.WorkItem item,
        Set<String> participantRoles,
        boolean pendingNodeTask,
        String nodeTaskState,
        long nodeTaskVersion,
        Instant nodeTaskDueAt
    ) {
        public PersonalCandidate {
            participantRoles = Set.copyOf(participantRoles);
        }
    }
}
