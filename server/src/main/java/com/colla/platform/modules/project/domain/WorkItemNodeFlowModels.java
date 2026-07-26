package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class WorkItemNodeFlowModels {
    public static final int MAX_STAGES = 32;
    public static final int MAX_NODES = 256;
    public static final int MAX_EDGES = 512;
    public static final int MAX_BRANCHES = 128;
    public static final int MAX_JOINS = 128;
    public static final int MAX_CANDIDATE_ROLES = 32;
    public static final Pattern SEMANTIC_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    public static final Set<String> CANDIDATE_ROLES = Set.of(
        "owner", "admin", "member", "guest", "creator", "assignee", "collaborator", "watcher"
    );

    private WorkItemNodeFlowModels() {
    }

    public enum NodeKind {
        start,
        manual,
        automatic,
        branch,
        join,
        end;

        public static NodeKind parse(String value) {
            return parseEnum(NodeKind.class, value, "INVALID_NODE_KIND", "Unknown node kind");
        }
    }

    public enum ProcessingStrategy {
        automatic,
        single,
        any,
        all,
        quorum;

        public static ProcessingStrategy parse(String value) {
            return parseEnum(
                ProcessingStrategy.class,
                value,
                "INVALID_PROCESSING_STRATEGY",
                "Unknown processing strategy"
            );
        }
    }

    public enum BranchMode {
        exclusive,
        parallel;

        public static BranchMode parse(String value) {
            return parseEnum(BranchMode.class, value, "INVALID_BRANCH_MODE", "Unknown branch mode");
        }
    }

    public enum JoinPolicy {
        all,
        any,
        quorum;

        public static JoinPolicy parse(String value) {
            return parseEnum(JoinPolicy.class, value, "INVALID_JOIN_POLICY", "Unknown join policy");
        }
    }

    public enum RecoveryCommandKind {
        return_to,
        jump,
        terminate,
        correct;

        public static RecoveryCommandKind parse(String value) {
            return parseEnum(
                RecoveryCommandKind.class,
                value,
                "INVALID_RECOVERY_COMMAND_KIND",
                "Unknown recovery command kind"
            );
        }
    }

    public record StageDefinition(String stageKey, String label, String description, int sortOrder) {
    }

    public record NodeDefinition(
        String nodeKey,
        String stageKey,
        String label,
        String description,
        NodeKind kind,
        ProcessingStrategy processingStrategy,
        List<String> candidateRoles,
        Integer quorumCount,
        JsonNode configuration,
        int sortOrder
    ) {
    }

    public record EdgeDefinition(
        String edgeKey,
        String fromNodeKey,
        String toNodeKey,
        int priority,
        JsonNode condition
    ) {
    }

    public record BranchDefinition(
        String branchKey,
        String nodeKey,
        BranchMode mode,
        List<String> edgeKeys
    ) {
    }

    public record JoinDefinition(
        String joinKey,
        String nodeKey,
        JoinPolicy policy,
        List<String> inboundEdgeKeys,
        Integer quorumCount
    ) {
    }

    public record RecoveryCommandDefinition(
        String commandKey,
        RecoveryCommandKind kind,
        List<String> fromNodeKeys,
        String targetNodeKey,
        List<String> authorizedRoles,
        String closeMode,
        String confirmation
    ) {
    }

    public record CompensationDefinition(
        String compensationKey,
        String commandKey,
        String actionKey,
        int sortOrder
    ) {
    }

    private static <T extends Enum<T>> T parseEnum(
        Class<T> type,
        String value,
        String code,
        String message
    ) {
        try {
            return Enum.valueOf(type, normalize(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(code + ": " + message);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
