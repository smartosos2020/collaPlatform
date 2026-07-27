package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;
import static com.colla.platform.modules.project.domain.WorkItemBoardModels.MAX_CARDS;
import static com.colla.platform.modules.project.domain.WorkItemBoardModels.MAX_COLUMNS;
import static com.colla.platform.modules.project.domain.WorkItemBoardModels.MAX_SWIMLANES;
import static com.colla.platform.modules.project.domain.WorkItemBoardModels.MAX_WIP_LIMIT;

import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardAction;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardCard;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardColumn;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardColumnResult;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardLane;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardOrder;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardPreference;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardPreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardRequest;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.BoardResult;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.MoveIntent;
import com.colla.platform.modules.project.domain.WorkItemBoardModels.MoveResult;
import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemView;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeAvailableAction;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeCommandResult;
import com.colla.platform.modules.project.domain.WorkItemNodeRuntimeModels.NodeWorkflowPresentation;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.AvailableAction;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowCommandResult;
import com.colla.platform.modules.project.domain.WorkItemStateRuntimeModels.WorkflowPresentation;
import com.colla.platform.modules.project.infrastructure.WorkItemBoardRepository;
import com.colla.platform.modules.project.infrastructure.WorkItemBoardRepository.CommandRecord;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkItemBoardService {
    private static final Pattern VIEW_KEY = Pattern.compile("^[a-z][a-z0-9._-]{0,79}$");
    private static final Pattern COLUMN_KEY = Pattern.compile("^[a-z0-9][a-z0-9._:-]{0,119}$");
    private static final Set<String> BOARD_FIELDS = Set.of(
        "status", "state", "nodeState", "participantRole"
    );
    private static final String UNASSIGNED = "unassigned";

    private final WorkItemBoardRepository repository;
    private final WorkItemQueryService queries;
    private final WorkItemQueryCanonicalizer canonicalizer;
    private final WorkItemService workItems;
    private final ObjectMapper objectMapper;
    private final Timer renderLatency;
    private final Counter renders;
    private final Counter moves;
    private final Counter conflicts;

    public WorkItemBoardService(
        WorkItemBoardRepository repository,
        WorkItemQueryService queries,
        WorkItemQueryCanonicalizer canonicalizer,
        WorkItemService workItems,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.queries = queries;
        this.canonicalizer = canonicalizer;
        this.workItems = workItems;
        this.objectMapper = objectMapper;
        this.renderLatency = meterRegistry.timer("colla.project.work_item_board.render_latency");
        this.renders = meterRegistry.counter("colla.project.work_item_board.renders");
        this.moves = meterRegistry.counter("colla.project.work_item_board.moves");
        this.conflicts = meterRegistry.counter("colla.project.work_item_board.conflicts");
    }

    public Optional<BoardPreference> preference(CurrentUser user, UUID spaceId, String viewKey) {
        workItems.requireQueryScope(user, spaceId);
        return repository.findPreference(
            user.workspaceId(), spaceId, user.id(), requireViewKey(viewKey)
        );
    }

    public BoardPreference savePreference(
        CurrentUser user,
        UUID spaceId,
        String viewKey,
        BoardPreferenceCommand command
    ) {
        workItems.requireQueryScope(user, spaceId);
        validatePreference(command);
        validateBoardFieldShape(command.columnField());
        if (command.swimlaneField() != null) validateBoardFieldShape(command.swimlaneField());
        return repository.savePreference(
            user.workspaceId(), spaceId, user.id(), requireViewKey(viewKey), command
        );
    }

    public BoardResult render(CurrentUser user, UUID spaceId, BoardRequest request) {
        return renderLatency.record(() -> renderTimed(user, spaceId, request));
    }

    private BoardResult renderTimed(CurrentUser user, UUID spaceId, BoardRequest request) {
        validateRequest(request);
        QueryDefinition query = withBoardFields(request.query(), request.columnField(), request.swimlaneField());
        validateBoardFields(
            user, spaceId, request.columnField(), request.swimlaneField(), query.typeId()
        );
        QueryResult result = queries.execute(user, spaceId, query);
        Map<UUID, BoardOrder> orders = repository.listOrders(
            user.workspaceId(),
            spaceId,
            user.id(),
            request.viewKey(),
            result.items().stream().map(QueryItem::id).toList()
        ).stream().collect(java.util.stream.Collectors.toMap(BoardOrder::workItemId, value -> value));
        Map<String, BoardColumn> configured = new LinkedHashMap<>();
        request.columns().forEach(column -> configured.put(column.key(), column));
        Map<String, Map<String, List<BoardCard>>> grouped = new LinkedHashMap<>();
        configured.keySet().forEach(column -> grouped.put(column, new LinkedHashMap<>()));
        LinkedHashSet<String> laneKeys = new LinkedHashSet<>();
        long fallbackRank = 0;
        for (QueryItem item : result.items()) {
            WorkflowPresentation workflow = workItems.workflow(user, spaceId, item.id());
            NodeWorkflowPresentation node = workItems.nodeWorkflow(user, spaceId, item.id());
            String columnKey = groupingValue(
                boardValue(request.columnField(), item, workflow, node)
            );
            if (!configured.containsKey(columnKey)) {
                throw failure(
                    "BOARD_COLUMN_UNMAPPED",
                    "Visible value '" + columnKey + "' has no configured board column"
                );
            }
            String laneKey = request.swimlaneField() == null
                ? UNASSIGNED
                : groupingValue(boardValue(request.swimlaneField(), item, workflow, node));
            if (laneKeys.add(laneKey) && laneKeys.size() > MAX_SWIMLANES) {
                throw failure("BOARD_LANE_BUDGET_EXCEEDED", "Board has too many visible swimlanes");
            }
            BoardOrder order = orders.get(item.id());
            boolean usableOrder = order != null
                && order.sourceWorkItemVersion() == item.version()
                && order.columnKey().equals(columnKey)
                && order.swimlaneKey().equals(laneKey);
            List<BoardAction> actions = actions(workflow, node);
            BoardCard card = new BoardCard(
                item.id(),
                item.displayKey(),
                item.title(),
                item.status(),
                item.version(),
                columnKey,
                laneKey,
                usableOrder ? order.rank() : fallbackRank,
                usableOrder ? order.version() : 0,
                item.availableActions(),
                actions
            );
            grouped.get(columnKey).computeIfAbsent(laneKey, ignored -> new ArrayList<>()).add(card);
            fallbackRank += 1024;
        }
        if (laneKeys.isEmpty()) laneKeys.add(UNASSIGNED);
        List<BoardColumnResult> columns = new ArrayList<>();
        for (BoardColumn column : request.columns()) {
            Map<String, List<BoardCard>> byLane = grouped.get(column.key());
            List<BoardLane> lanes = laneKeys.stream().map(lane -> {
                List<BoardCard> cards = new ArrayList<>(byLane.getOrDefault(lane, List.of()));
                cards.sort(Comparator.comparingLong(BoardCard::rank)
                    .thenComparing(card -> card.workItemId().toString()));
                return new BoardLane(lane, laneLabel(lane), List.copyOf(cards));
            }).toList();
            int visible = lanes.stream().mapToInt(lane -> lane.cards().size()).sum();
            columns.add(new BoardColumnResult(
                column,
                visible,
                column.wipLimit() > 0 && visible > column.wipLimit(),
                lanes
            ));
        }
        repository.recordRender(
            user.workspaceId(), spaceId, request.viewKey(),
            columns.size(), laneKeys.size(), result.items().size()
        );
        renders.increment();
        return new BoardResult(
            1,
            request.viewKey(),
            result.queryHash(),
            request.columnField(),
            request.swimlaneField(),
            List.copyOf(columns),
            result.nextCursor(),
            result.items().size(),
            false
        );
    }

    @Transactional
    public MoveResult move(
        CurrentUser user,
        UUID spaceId,
        String viewKey,
        UUID workItemId,
        MoveIntent intent
    ) {
        String normalizedViewKey = requireViewKey(viewKey);
        validateMove(intent);
        workItems.requireQueryScope(user, spaceId);
        BoardPreference preference = repository.findPreference(
            user.workspaceId(), spaceId, user.id(), normalizedViewKey
        ).orElseThrow(() -> failure(
            "BOARD_PREFERENCE_REQUIRED",
            "Save the board preference before moving cards"
        ));
        BoardColumn target = preference.columns().stream()
            .filter(column -> column.key().equals(intent.targetColumnKey()))
            .findFirst()
            .orElseThrow(() -> failure("INVALID_BOARD_MOVE", "Target column is not configured"));
        boolean reorderRequested = "reorder".equals(normalize(intent.kind()))
            && "reorder".equals(normalize(intent.actionKey()));
        String kind = reorderRequested ? "reorder" : normalize(target.moveKind());
        String actionKey = reorderRequested ? "reorder" : normalize(target.moveActionKey());
        if (!reorderRequested
            && (!kind.equals(normalize(intent.kind())) || !actionKey.equals(normalize(intent.actionKey())))) {
            throw failure("INVALID_BOARD_MOVE", "Move action does not match the target column");
        }
        String operation = switch (kind) {
            case "state" -> "move_state";
            case "node" -> "move_node";
            case "reorder" -> "reorder";
            default -> throw failure("INVALID_BOARD_MOVE", "Target column has no registered move action");
        };
        String requestHash = sha256(json(List.of(
            normalizedViewKey,
            workItemId,
            intent.expectedWorkItemVersion(),
            intent.expectedOrderVersion(),
            intent.targetColumnKey(),
            intent.targetSwimlaneKey(),
            intent.rank(),
            kind,
            actionKey,
            intent.fromStateKey() == null ? "" : intent.fromStateKey(),
            intent.taskId() == null ? "" : intent.taskId(),
            intent.nodeOperation() == null ? "" : intent.nodeOperation(),
            intent.expectedInstanceVersion(),
            intent.decision() == null ? "" : intent.decision(),
            intent.fieldPatch() == null ? "" : intent.fieldPatch()
        )));
        Optional<CommandRecord> existing = repository.findCommand(
            user.workspaceId(), spaceId, user.id(), operation, intent.requestId()
        );
        if (existing.isPresent()) return replay(existing.get(), requestHash);
        WorkItemView current = workItems.get(user, spaceId, workItemId);
        if (current.item().version() != intent.expectedWorkItemVersion()) {
            conflicts.increment();
            throw failure("BOARD_WORK_ITEM_VERSION_CONFLICT", "Work item changed; refresh and retry");
        }
        if (reorderRequested && !isCurrentGroup(
            user, spaceId, current, preference.columnField(), intent.targetColumnKey()
        )) {
            throw failure(
                "INVALID_BOARD_MOVE",
                "Reorder cannot move a card across authoritative groups"
            );
        }
        CommandRecord command = repository.beginCommand(
            user.workspaceId(), spaceId, user.id(), normalizedViewKey, workItemId,
            operation, intent.requestId(), requestHash, intent.expectedWorkItemVersion()
        );
        if (!command.requestHash().equals(requestHash)) {
            throw failure("BOARD_REQUEST_CONFLICT", "Board request ID was reused with different input");
        }
        if ("completed".equals(command.status())) return replay(command, requestHash);
        BoardOrder reserved = repository.reserveOrder(
            user.workspaceId(), spaceId, user.id(), normalizedViewKey, workItemId,
            intent.targetColumnKey(), requireLaneKey(intent.targetSwimlaneKey()), intent.rank(),
            intent.expectedOrderVersion(), intent.expectedWorkItemVersion()
        );
        long workItemVersion = intent.expectedWorkItemVersion();
        long workflowVersion = 0;
        if ("state".equals(kind)) {
            WorkflowCommandResult result = workItems.executeWorkflowAction(
                user, spaceId, workItemId, actionKey, intent.fromStateKey(),
                intent.expectedWorkItemVersion(), intent.fieldPatch(), "board:" + intent.requestId()
            );
            if ("state".equals(preference.columnField())
                && !intent.targetColumnKey().equals(result.toStateKey())) {
                throw failure(
                    "INVALID_BOARD_MOVE",
                    "Workflow action does not resolve to the target column"
                );
            }
            workItemVersion = result.workItemVersion();
            workflowVersion = result.aggregateVersion();
        } else if ("node".equals(kind)) {
            if (intent.taskId() == null) {
                throw failure("INVALID_BOARD_MOVE", "Node board moves require a visible task");
            }
            NodeCommandResult result = workItems.executeNodeTask(
                user, spaceId, workItemId, intent.taskId(),
                normalize(intent.nodeOperation()).isEmpty() ? actionKey : normalize(intent.nodeOperation()),
                intent.decision(), null, intent.fieldPatch(), List.of(),
                intent.expectedWorkItemVersion(), intent.expectedInstanceVersion(),
                "board:" + intent.requestId()
            );
            workItemVersion = result.workItemVersion();
            workflowVersion = result.aggregateVersion();
        }
        BoardOrder aligned = repository.alignOrderSourceVersion(
            user.workspaceId(), spaceId, user.id(), normalizedViewKey, workItemId,
            reserved.version(), workItemVersion
        );
        MoveResult result = new MoveResult(
            workItemId, normalizedViewKey, aligned.columnKey(), aligned.swimlaneKey(),
            aligned.rank(), workItemVersion, workflowVersion, aligned.version(), false
        );
        repository.completeCommand(command.id(), json(result));
        moves.increment();
        return result;
    }

    private List<BoardAction> actions(
        WorkflowPresentation workflow,
        NodeWorkflowPresentation node
    ) {
        List<BoardAction> result = new ArrayList<>();
        for (AvailableAction action : workflow.availableActions()) {
            result.add(new BoardAction(
                "state", action.actionKey(), action.label(), workflow.currentStateKey(), null, 0
            ));
        }
        for (NodeAvailableAction action : node.availableActions()) {
            if (action.taskId() != null) {
                result.add(new BoardAction(
                    "node", action.actionKey(), action.actionKey(), null,
                    action.taskId(), action.expectedInstanceVersion()
                ));
            }
        }
        return List.copyOf(result);
    }

    private Object boardValue(
        String field,
        QueryItem item,
        WorkflowPresentation workflow,
        NodeWorkflowPresentation node
    ) {
        Object selected = item.selected().get(field);
        if (selected != null && !(selected instanceof Collection<?> values && values.isEmpty())) {
            return selected;
        }
        if ("state".equals(field)) return workflow.currentStateKey();
        if ("nodeState".equals(field)) {
            return node.activeTokens().stream()
                .map(token -> token.nodeKey())
                .sorted()
                .findFirst()
                .orElse(null);
        }
        return selected;
    }

    private boolean isCurrentGroup(
        CurrentUser user,
        UUID spaceId,
        WorkItemView current,
        String columnField,
        String targetColumnKey
    ) {
        if ("status".equals(columnField)) {
            return targetColumnKey.equals(current.item().status());
        }
        if ("state".equals(columnField)) {
            return targetColumnKey.equals(
                workItems.workflow(user, spaceId, current.item().id()).currentStateKey()
            );
        }
        throw failure(
            "INVALID_BOARD_MOVE",
            "Keyboard reorder is only registered for state or status boards"
        );
    }

    private QueryDefinition withBoardFields(
        QueryDefinition source,
        String columnField,
        String swimlaneField
    ) {
        if (source == null) throw failure("INVALID_BOARD_CONFIGURATION", "Board query is required");
        LinkedHashSet<String> selected = new LinkedHashSet<>(
            source.select() == null ? List.of() : source.select()
        );
        selected.add(columnField);
        if (swimlaneField != null) selected.add(swimlaneField);
        if (selected.size() > 32) {
            throw failure("QUERY_TOO_COMPLEX", "Board query selects too many fields");
        }
        QueryDefinition augmented = new QueryDefinition(
            source.schemaVersion(),
            source.typeId(),
            source.filter(),
            source.sorts(),
            source.group(),
            List.copyOf(selected),
            Math.min(
                source.limit() <= 0
                    ? MAX_CARDS
                    : source.limit(),
                MAX_CARDS
            ),
            source.cursor()
        );
        return canonicalizer.canonicalize(augmented).definition();
    }

    private void validateBoardFields(
        CurrentUser user,
        UUID spaceId,
        String columnField,
        String swimlaneField,
        UUID typeId
    ) {
        validateBoardField(user, spaceId, columnField, typeId);
        if (swimlaneField != null) validateBoardField(user, spaceId, swimlaneField, typeId);
    }

    private void validateBoardField(
        CurrentUser user,
        UUID spaceId,
        String field,
        UUID typeId
    ) {
        if (BOARD_FIELDS.contains(field)) return;
        if (!canonicalizer.isDynamicField(field)) {
            throw failure("INVALID_BOARD_FIELD", "Board grouping field is not registered");
        }
        if (typeId == null) {
            throw failure("QUERY_TYPE_REQUIRED", "A WorkItem type is required for dynamic grouping");
        }
        workItems.requireQueryCapability(
            user, spaceId, typeId, field.substring("field.".length()), "eq", "none"
        );
    }

    private void validateBoardFieldShape(String field) {
        if (!BOARD_FIELDS.contains(field) && !canonicalizer.isDynamicField(field)) {
            throw failure("INVALID_BOARD_FIELD", "Board grouping field is not registered");
        }
    }

    private void validateRequest(BoardRequest request) {
        if (request == null || request.schemaVersion() != 1) {
            throw failure("INVALID_BOARD_SCHEMA", "WorkItem board schema version must be 1");
        }
        requireViewKey(request.viewKey());
        validateColumns(request.columns());
        if (request.columnField() == null || request.columnField().isBlank()) {
            throw failure("INVALID_BOARD_CONFIGURATION", "Board column field is required");
        }
        if (request.swimlaneField() != null && request.swimlaneField().isBlank()) {
            throw failure("INVALID_BOARD_CONFIGURATION", "Board swimlane field cannot be blank");
        }
    }

    private void validatePreference(BoardPreferenceCommand command) {
        if (command == null) throw failure("INVALID_BOARD_CONFIGURATION", "Board preference is required");
        requireRequestId(command.requestId());
        if (command.expectedVersion() < 0) {
            throw failure("INVALID_BOARD_CONFIGURATION", "Board preference version is invalid");
        }
        if (command.columnField() == null || command.columnField().isBlank()) {
            throw failure("INVALID_BOARD_CONFIGURATION", "Board column field is required");
        }
        validateColumns(command.columns());
    }

    private void validateColumns(List<BoardColumn> columns) {
        if (columns == null || columns.isEmpty()
            || columns.size() > MAX_COLUMNS) {
            throw failure("INVALID_BOARD_CONFIGURATION", "Board must define 1 to 12 columns");
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (BoardColumn column : columns) {
            if (column == null || column.key() == null || !COLUMN_KEY.matcher(column.key()).matches()
                || !keys.add(column.key())) {
                throw failure("INVALID_BOARD_CONFIGURATION", "Board column keys must be unique and registered");
            }
            if (column.label() == null || column.label().isBlank() || column.label().length() > 80) {
                throw failure("INVALID_BOARD_CONFIGURATION", "Board column label is invalid");
            }
            if (column.wipLimit() < 0
                || column.wipLimit() > MAX_WIP_LIMIT) {
                throw failure("INVALID_BOARD_CONFIGURATION", "Board WIP limit is invalid");
            }
            String kind = normalize(column.moveKind());
            String action = normalize(column.moveActionKey());
            if (!Set.of("", "state", "node", "reorder").contains(kind)
                || kind.isEmpty() != action.isEmpty()) {
                throw failure("INVALID_BOARD_CONFIGURATION", "Board move mapping is invalid");
            }
        }
    }

    private void validateMove(MoveIntent intent) {
        if (intent == null) throw failure("INVALID_BOARD_MOVE", "Move intent is required");
        requireRequestId(intent.requestId());
        if (intent.expectedWorkItemVersion() < 0 || intent.expectedOrderVersion() < 0
            || intent.rank() < 0 || intent.rank() > Long.MAX_VALUE / 2) {
            throw failure("INVALID_BOARD_MOVE", "Move versions or rank are invalid");
        }
        if (intent.targetColumnKey() == null || !COLUMN_KEY.matcher(intent.targetColumnKey()).matches()) {
            throw failure("INVALID_BOARD_MOVE", "Target column is invalid");
        }
        requireLaneKey(intent.targetSwimlaneKey());
    }

    private MoveResult replay(CommandRecord record, String requestHash) {
        if (!record.requestHash().equals(requestHash)) {
            throw failure("BOARD_REQUEST_CONFLICT", "Board request ID was reused with different input");
        }
        if (!"completed".equals(record.status())) {
            throw failure("BOARD_REQUEST_IN_PROGRESS", "Board request is already in progress");
        }
        try {
            MoveResult value = objectMapper.readValue(record.responseJson(), MoveResult.class);
            return new MoveResult(
                value.workItemId(), value.viewKey(), value.targetColumnKey(),
                value.targetSwimlaneKey(), value.rank(), value.workItemVersion(),
                value.workflowVersion(), value.orderVersion(), true
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored board response is invalid", exception);
        }
    }

    private String requireViewKey(String viewKey) {
        String normalized = normalize(viewKey);
        if (!VIEW_KEY.matcher(normalized).matches()) {
            throw failure("INVALID_BOARD_CONFIGURATION", "Board view key is invalid");
        }
        return normalized;
    }

    private String requireRequestId(String requestId) {
        String normalized = requestId == null ? "" : requestId.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw failure("INVALID_BOARD_CONFIGURATION", "Board request ID is invalid");
        }
        return normalized;
    }

    private String requireLaneKey(String laneKey) {
        String normalized = laneKey == null || laneKey.isBlank() ? UNASSIGNED : laneKey.trim();
        if (normalized.length() > 120) {
            throw failure("INVALID_BOARD_MOVE", "Target swimlane is invalid");
        }
        return normalized;
    }

    private static String groupingValue(Object raw) {
        if (raw == null) return UNASSIGNED;
        if (raw instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).sorted().findFirst().orElse(UNASSIGNED);
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? UNASSIGNED : value;
    }

    private static String laneLabel(String key) {
        return UNASSIGNED.equals(key) ? "未分组" : key;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw failure("INVALID_BOARD_CONFIGURATION", "Board configuration is invalid", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
