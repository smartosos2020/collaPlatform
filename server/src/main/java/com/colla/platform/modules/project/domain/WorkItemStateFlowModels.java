package com.colla.platform.modules.project.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class WorkItemStateFlowModels {
    public static final int MAX_STATES = 64;
    public static final int MAX_ACTIONS = 128;
    public static final int MAX_TRANSITIONS = 256;
    public static final int MAX_GUARDS = 128;
    public static final int MAX_REQUIRED_FIELDS = 32;
    public static final int MAX_SIDE_EFFECTS = 16;
    public static final Pattern SEMANTIC_KEY = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    public static final Set<String> AUTHORIZATION_ROLES = Set.of(
        "owner", "admin", "member", "guest", "assignee", "collaborator", "watcher"
    );

    private WorkItemStateFlowModels() {
    }

    public enum StateCategory {
        initial,
        active,
        terminal,
        canceled;

        public static StateCategory parse(String value) {
            try {
                return valueOf(normalize(value));
            } catch (IllegalArgumentException exception) {
                throw failure("INVALID_STATE_CATEGORY", "Unknown state category");
            }
        }
    }

    public enum ActionKind {
        forward,
        return_action,
        reopen,
        terminate,
        restore,
        correction;

        public static ActionKind parse(String value) {
            String normalized = normalize(value);
            if ("return".equals(normalized)) {
                normalized = "return_action";
            }
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException exception) {
                throw failure("INVALID_ACTION_KIND", "Unknown workflow action kind");
            }
        }
    }

    public enum GuardKind {
        field,
        participant,
        space_role,
        all,
        any,
        not;

        public static GuardKind parse(String value) {
            try {
                return valueOf(normalize(value));
            } catch (IllegalArgumentException exception) {
                throw failure("INVALID_GUARD_KIND", "Unknown guard kind");
            }
        }
    }

    public record StateDefinition(
        String stateKey,
        String label,
        String description,
        String color,
        StateCategory category,
        int sortOrder
    ) {
    }

    public record ActionDefinition(
        String actionKey,
        String label,
        String description,
        ActionKind kind,
        List<String> authorizedRoles,
        List<String> requiredFieldKeys,
        JsonNode fieldPatch,
        List<String> sideEffectKeys,
        int sortOrder
    ) {
    }

    public record TransitionDefinition(
        String transitionKey,
        String actionKey,
        String fromStateKey,
        String toStateKey,
        String guardKey,
        int sortOrder
    ) {
    }

    public record GuardDefinition(
        String guardKey,
        GuardKind kind,
        String operator,
        String fieldKey,
        String participantRole,
        List<String> spaceRoles,
        JsonNode value,
        List<String> guardKeys
    ) {
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException failure(String code, String message) {
        return new IllegalArgumentException(code + ": " + message);
    }
}
