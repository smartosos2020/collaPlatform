package com.colla.platform.modules.project.contract;

import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PersonalWorkQuery {
    PersonalWorkPage list(CurrentUser user, UUID spaceId, String cursor, int limit);

    default PersonalWorkPage list(CurrentUser user, String cursor, int limit) {
        return list(user, null, cursor, limit);
    }

    PersonalWorkPage dashboard(CurrentUser user);

    enum WorkBucket {
        todo,
        responsible,
        participating,
        watching
    }

    record BucketReason(
        WorkBucket bucket,
        String source,
        String sourceState,
        long sourceVersion,
        Instant dueAt
    ) {
    }

    record PersonalWorkItem(
        UUID workItemId,
        UUID spaceId,
        String spaceName,
        String typeKey,
        String typeName,
        String displayKey,
        String title,
        String lifecycle,
        long version,
        Instant updatedAt,
        List<BucketReason> reasons,
        List<String> capabilities,
        List<String> availableActions,
        String deepLink
    ) {
        public PersonalWorkItem {
            reasons = List.copyOf(reasons == null ? List.of() : reasons);
            capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
            availableActions = capabilities;
        }

        public PersonalWorkItem(
            UUID workItemId,
            UUID spaceId,
            String spaceName,
            String typeKey,
            String typeName,
            String displayKey,
            String title,
            String lifecycle,
            long version,
            Instant updatedAt,
            List<BucketReason> reasons,
            List<String> capabilities,
            String deepLink
        ) {
            this(
                workItemId,
                spaceId,
                spaceName,
                typeKey,
                typeName,
                displayKey,
                title,
                lifecycle,
                version,
                updatedAt,
                reasons,
                capabilities,
                capabilities,
                deepLink
            );
        }
    }

    record WorkBucketView(WorkBucket bucket, int visibleCount, List<PersonalWorkItem> items) {
        public WorkBucketView {
            items = List.copyOf(items);
        }
    }

    record PersonalWorkPage(
        List<WorkBucketView> buckets,
        String nextCursor,
        boolean truncated,
        Instant generatedAt
    ) {
        public PersonalWorkPage {
            buckets = List.copyOf(buckets);
        }
    }
}
