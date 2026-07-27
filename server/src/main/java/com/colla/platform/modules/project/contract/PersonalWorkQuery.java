package com.colla.platform.modules.project.contract;

import com.colla.platform.shared.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PersonalWorkQuery {
    PersonalWorkPage list(CurrentUser user, String cursor, int limit);

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
        String deepLink
    ) {
        public PersonalWorkItem {
            reasons = List.copyOf(reasons);
            capabilities = List.copyOf(capabilities);
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
