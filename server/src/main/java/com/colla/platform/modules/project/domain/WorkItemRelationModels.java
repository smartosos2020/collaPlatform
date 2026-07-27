package com.colla.platform.modules.project.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class WorkItemRelationModels {
    public static final int MAX_RELATION_DEFINITIONS = 32;
    public static final int MAX_ENDPOINT_TYPES = 32;
    public static final int MAX_HIERARCHY_DEPTH = 64;
    public static final Pattern SEMANTIC_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    public static final Set<String> SYSTEM_TYPE_KEYS = Set.of(
        "project", "requirement", "task", "bug", "iteration", "release"
    );

    private WorkItemRelationModels() {
    }

    public enum RelationKind {
        normal,
        parent_child,
        dependency,
        blocking;

        public static RelationKind parse(String value) {
            return parseEnum(RelationKind.class, value, "INVALID_RELATION_KIND");
        }
    }

    public enum Direction {
        directed,
        undirected;

        public static Direction parse(String value) {
            return parseEnum(Direction.class, value, "INVALID_RELATION_DIRECTION");
        }
    }

    public enum Cardinality {
        one,
        many;

        public static Cardinality parse(String value) {
            return parseEnum(Cardinality.class, value, "INVALID_RELATION_CARDINALITY");
        }
    }

    public enum DeletionPolicy {
        restrict,
        detach,
        retain_history;

        public static DeletionPolicy parse(String value) {
            return parseEnum(DeletionPolicy.class, value, "INVALID_RELATION_DELETION_POLICY");
        }
    }

    public enum LegacyClassification {
        canonical_work_item,
        platform_object_reference,
        unresolved,
        unsupported
    }

    public record RelationDefinition(
        String relationKey,
        RelationKind kind,
        Direction direction,
        String forwardName,
        String reverseName,
        List<String> sourceTypeKeys,
        List<String> targetTypeKeys,
        Cardinality sourceCardinality,
        Cardinality targetCardinality,
        DeletionPolicy deletionPolicy,
        boolean allowSelf,
        int maxDepth,
        int sortOrder,
        boolean system
    ) {
    }

    public record LegacyMigrationManifestEntry(
        UUID legacyRelationId,
        LegacyClassification classification,
        UUID sourceWorkItemId,
        UUID targetWorkItemId,
        String targetObjectType,
        UUID targetObjectId,
        String reasonCode
    ) {
    }

    private static <T extends Enum<T>> T parseEnum(
        Class<T> enumType,
        String value,
        String code
    ) {
        try {
            return Enum.valueOf(enumType, value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw WorkItemConfigurationModels.failure(code, "Unknown work item relation contract value");
        }
    }
}
