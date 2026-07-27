package com.colla.platform.modules.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.FilterNode;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.SortSpec;
import com.colla.platform.shared.auth.JwtTokenProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkItemQueryCanonicalizerTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID WORKSPACE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SPACE = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ANCHOR = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Test
    void canonicalizesCommutativeFiltersAndAddsStableIdSort() {
        WorkItemQueryCanonicalizer canonicalizer = new WorkItemQueryCanonicalizer();
        FilterNode title = predicate("title", "contains", "roadmap");
        FilterNode status = predicate("status", "eq", "active");
        QueryDefinition first = definition(new FilterNode("and", null, null, null, List.of(title, status)));
        QueryDefinition second = definition(new FilterNode("and", null, null, null, List.of(status, title)));

        var firstCanonical = canonicalizer.canonicalize(first);
        var secondCanonical = canonicalizer.canonicalize(second);

        assertThat(firstCanonical.hash()).isEqualTo(secondCanonical.hash());
        assertThat(firstCanonical.definition().sorts())
            .extracting(SortSpec::field)
            .containsExactly("updatedAt", "id");
        assertThat(firstCanonical.definition().limit()).isEqualTo(50);
    }

    @Test
    void rejectsUnregisteredFieldsAndComplexityOverflow() {
        WorkItemQueryCanonicalizer canonicalizer = new WorkItemQueryCanonicalizer();
        assertThatThrownBy(() -> canonicalizer.canonicalize(definition(
            predicate("fieldValues.secret", "eq", "x")
        )))
            .isInstanceOf(WorkItemRuntimeException.class)
            .extracting(error -> ((WorkItemRuntimeException) error).code())
            .isEqualTo("INVALID_QUERY_FIELD");

        List<FilterNode> children = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            children.add(predicate("status", "eq", "active"));
        }
        assertThatThrownBy(() -> canonicalizer.canonicalize(definition(
            new FilterNode("and", null, null, null, children)
        )))
            .isInstanceOf(WorkItemRuntimeException.class)
            .extracting(error -> ((WorkItemRuntimeException) error).code())
            .isEqualTo("QUERY_TOO_COMPLEX");
    }

    @Test
    void signedCursorIsBoundToIdentityScopeAndQueryHash() {
        JwtTokenProperties properties = new JwtTokenProperties();
        properties.setAccessSecret("work-item-query-test-secret-with-entropy");
        WorkItemQueryCursorCodec codec = new WorkItemQueryCursorCodec(properties);
        String cursor = codec.encode(WORKSPACE, USER, SPACE, "a".repeat(64), ANCHOR);

        assertThat(codec.decode(cursor, WORKSPACE, USER, SPACE, "a".repeat(64)).anchorId())
            .isEqualTo(ANCHOR);
        assertThatThrownBy(() -> codec.decode(
            cursor,
            WORKSPACE,
            UUID.randomUUID(),
            SPACE,
            "a".repeat(64)
        ))
            .isInstanceOf(WorkItemRuntimeException.class)
            .extracting(error -> ((WorkItemRuntimeException) error).code())
            .isEqualTo("INVALID_QUERY_CURSOR");
        String tampered = ("A".equals(cursor.substring(0, 1)) ? "B" : "A") + cursor.substring(1);
        assertThatThrownBy(() -> codec.decode(
            tampered,
            WORKSPACE,
            USER,
            SPACE,
            "a".repeat(64)
        )).isInstanceOf(WorkItemRuntimeException.class);
    }

    private static QueryDefinition definition(FilterNode filter) {
        return new QueryDefinition(
            1,
            null,
            filter,
            List.of(new SortSpec("updatedAt", "desc", "last")),
            null,
            List.of(),
            50,
            null
        );
    }

    private static FilterNode predicate(String field, String operator, String value) {
        return new FilterNode("predicate", field, operator, JSON.valueToTree(value), List.of());
    }
}
