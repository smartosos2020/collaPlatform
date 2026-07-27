package com.colla.platform.modules.project.application;

import static com.colla.platform.modules.project.domain.WorkItemModels.failure;

import com.colla.platform.modules.project.domain.WorkItemModels.WorkItemRuntimeException;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryDefinition;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryItem;
import com.colla.platform.modules.project.domain.WorkItemQueryModels.QueryResult;
import com.colla.platform.modules.project.domain.WorkItemViewModels.BulkCommand;
import com.colla.platform.modules.project.domain.WorkItemViewModels.BulkItemResult;
import com.colla.platform.modules.project.domain.WorkItemViewModels.BulkResult;
import com.colla.platform.modules.project.domain.WorkItemViewModels.CellProjection;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ColumnSpec;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ExportCommand;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ExportDownload;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ExportJob;
import com.colla.platform.modules.project.domain.WorkItemViewModels.PreferenceCommand;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewMode;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewPreference;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewRequest;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewResult;
import com.colla.platform.modules.project.domain.WorkItemViewModels.ViewRow;
import com.colla.platform.modules.project.infrastructure.WorkItemViewRepository;
import com.colla.platform.shared.auth.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public final class WorkItemViewService {
    private static final Pattern VIEW_KEY = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Pattern COLUMN_KEY = Pattern.compile(
        "(id|typeId|displayKey|title|status|version|createdBy|createdAt|updatedAt"
            + "|participantRole|state|nodeState|relation|ancestor|descendant"
            + "|field\\.[a-z][a-z0-9_-]{0,63})"
    );
    private static final Set<String> DENSITIES = Set.of("compact", "comfortable");
    private static final Set<String> FORMATS = Set.of("text", "tag", "date", "datetime", "number", "boolean");

    private final WorkItemQueryService queries;
    private final WorkItemService workItems;
    private final WorkItemViewRepository repository;
    private final ObjectMapper objectMapper;

    public WorkItemViewService(
        WorkItemQueryService queries,
        WorkItemService workItems,
        WorkItemViewRepository repository,
        ObjectMapper objectMapper
    ) {
        this.queries = queries;
        this.workItems = workItems;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public ViewResult render(CurrentUser user, UUID spaceId, ViewRequest request) {
        if (request == null || request.schemaVersion() != 1 || request.mode() == null) {
            throw failure("INVALID_VIEW_SCHEMA", "WorkItem view schema version must be 1");
        }
        String density = density(request.density());
        List<ColumnSpec> columns = columns(request.columns());
        QueryDefinition query = withSelection(request.query(), columns, request.query().limit());
        QueryResult result = queries.execute(user, spaceId, query);
        return new ViewResult(
            1,
            request.mode(),
            density,
            columns,
            result.items().stream().map(item -> row(item, columns)).toList(),
            result.nextCursor(),
            result.queryHash()
        );
    }

    public ViewPreference preference(
        CurrentUser user,
        UUID spaceId,
        String viewKey
    ) {
        workItems.requireQueryScope(user, spaceId);
        String key = viewKey(viewKey);
        return repository.findPreference(user.workspaceId(), spaceId, user.id(), key)
            .orElse(new ViewPreference(
                key,
                ViewMode.table,
                "comfortable",
                defaults(),
                0,
                Instant.EPOCH
            ));
    }

    public ViewPreference savePreference(
        CurrentUser user,
        UUID spaceId,
        String viewKey,
        PreferenceCommand command
    ) {
        workItems.requireQueryScope(user, spaceId);
        if (command == null || command.mode() == null || requestId(command.requestId()).isBlank()) {
            throw failure("INVALID_VIEW_COMMAND", "View preference command is invalid");
        }
        PreferenceCommand normalized = new PreferenceCommand(
            requestId(command.requestId()),
            command.expectedVersion(),
            command.mode(),
            density(command.density()),
            columns(command.columns())
        );
        return repository.savePreference(
            user.workspaceId(),
            spaceId,
            user.id(),
            viewKey(viewKey),
            normalized
        );
    }

    public BulkResult bulk(CurrentUser user, UUID spaceId, BulkCommand command) {
        workItems.requireQueryScope(user, spaceId);
        if (command == null || !Set.of("archive", "restore").contains(command.action())) {
            throw failure("INVALID_BULK_ACTION", "Bulk action is not registered");
        }
        String requestId = requestId(command.requestId());
        if (command.selections() == null
            || command.selections().isEmpty()
            || command.selections().size() > 100
            || command.selections().stream().map(value -> value.workItemId()).distinct().count()
                != command.selections().size()) {
            throw failure("INVALID_BULK_SELECTION", "Bulk selection must contain 1 to 100 unique WorkItems");
        }
        List<BulkItemResult> items = new ArrayList<>();
        for (var selection : command.selections()) {
            try {
                var result = workItems.transition(
                    user,
                    spaceId,
                    selection.workItemId(),
                    "archive".equals(command.action()) ? "archived" : "active",
                    selection.expectedVersion(),
                    bulkItemRequestId(requestId, command.action(), selection.workItemId())
                );
                items.add(new BulkItemResult(
                    selection.workItemId(), "succeeded", null, result.item().version()
                ));
            } catch (WorkItemRuntimeException exception) {
                items.add(new BulkItemResult(
                    selection.workItemId(), "failed", safeReason(exception.code()), null
                ));
            }
        }
        int succeeded = (int) items.stream().filter(value -> "succeeded".equals(value.status())).count();
        return new BulkResult(requestId, succeeded, items.size() - succeeded, List.copyOf(items));
    }

    public ExportJob createExport(CurrentUser user, UUID spaceId, ExportCommand command) {
        workItems.requireQueryScope(user, spaceId);
        if (command == null || command.query() == null) {
            throw failure("INVALID_EXPORT_COMMAND", "Export command is invalid");
        }
        String requestId = requestId(command.requestId());
        List<ColumnSpec> columns = columns(command.columns());
        QueryDefinition query = withSelection(command.query(), columns, 200);
        JsonNode queryJson = objectMapper.valueToTree(query);
        JsonNode columnsJson = objectMapper.valueToTree(columns);
        String hash = sha256(queryJson.toString() + "|" + columnsJson);
        return repository.createOrFindExport(
            UUID.randomUUID(),
            user.workspaceId(),
            spaceId,
            user.id(),
            requestId,
            hash,
            queryJson,
            columnsJson,
            Instant.now().plus(1, ChronoUnit.DAYS)
        ).job();
    }

    public ExportDownload download(CurrentUser user, UUID spaceId, UUID exportId) {
        workItems.requireQueryScope(user, spaceId);
        var record = repository.findExport(
            user.workspaceId(), spaceId, user.id(), exportId
        ).orElseThrow(() -> failure("NOT_FOUND_OR_HIDDEN", "Export is not available"));
        if (!"ready".equals(record.job().status())
            || !record.job().expiresAt().isAfter(Instant.now())) {
            throw failure("EXPORT_EXPIRED", "Export is no longer available");
        }
        QueryDefinition query = read(record.query(), QueryDefinition.class);
        List<ColumnSpec> columns = read(
            record.columns(),
            new TypeReference<List<ColumnSpec>>() {}
        );
        ViewResult current = render(
            user,
            spaceId,
            new ViewRequest(1, ViewMode.table, "compact", columns, query)
        );
        StringBuilder csv = new StringBuilder();
        csv.append(columns.stream().map(column -> csv(column.label())).collect(
            java.util.stream.Collectors.joining(",")
        )).append('\n');
        for (ViewRow row : current.rows()) {
            Map<String, CellProjection> cells = row.cells().stream().collect(
                java.util.stream.Collectors.toMap(CellProjection::columnKey, value -> value)
            );
            csv.append(columns.stream()
                .map(column -> csv(cells.containsKey(column.key())
                    ? cells.get(column.key()).displayValue()
                    : ""))
                .collect(java.util.stream.Collectors.joining(",")))
                .append('\n');
        }
        return new ExportDownload(
            "work-items-" + exportId + ".csv",
            "text/csv; charset=utf-8",
            "\uFEFF" + csv
        );
    }

    private ViewRow row(QueryItem item, List<ColumnSpec> columns) {
        List<CellProjection> cells = new ArrayList<>();
        for (ColumnSpec column : columns) {
            Object value = item.selected().get(column.key());
            if (value == null && column.key().startsWith("field.")) continue;
            cells.add(new CellProjection(
                column.key(),
                value,
                display(value, column.format()),
                "user_safe"
            ));
        }
        return new ViewRow(
            item.id(),
            item.displayKey(),
            item.title(),
            item.version(),
            List.copyOf(cells),
            item.availableActions()
        );
    }

    private QueryDefinition withSelection(
        QueryDefinition source,
        List<ColumnSpec> columns,
        int limit
    ) {
        if (source == null) throw failure("INVALID_QUERY_DEFINITION", "View query is required");
        return new QueryDefinition(
            source.schemaVersion(),
            source.typeId(),
            source.filter(),
            source.sorts(),
            source.group(),
            columns.stream().map(ColumnSpec::key).toList(),
            Math.min(limit <= 0 ? 50 : limit, 200),
            source.cursor()
        );
    }

    private List<ColumnSpec> columns(List<ColumnSpec> requested) {
        List<ColumnSpec> source = requested == null || requested.isEmpty() ? defaults() : requested;
        if (source.size() > 20
            || source.stream().map(ColumnSpec::key).distinct().count() != source.size()) {
            throw failure("INVALID_VIEW_COLUMNS", "View must contain at most 20 unique columns");
        }
        return source.stream().map(column -> {
            String key = column.key() == null ? "" : column.key().trim();
            String label = column.label() == null ? "" : column.label().trim();
            String format = column.format() == null ? "text" : column.format().trim();
            if (!COLUMN_KEY.matcher(key).matches()
                || label.isBlank()
                || label.length() > 80
                || !FORMATS.contains(format)) {
                throw failure("INVALID_VIEW_COLUMNS", "View column is invalid");
            }
            return new ColumnSpec(
                key,
                label,
                Math.max(80, Math.min(column.width() <= 0 ? 160 : column.width(), 600)),
                column.frozen(),
                format
            );
        }).toList();
    }

    private static List<ColumnSpec> defaults() {
        return List.of(
            new ColumnSpec("displayKey", "编号", 120, true, "text"),
            new ColumnSpec("title", "标题", 320, true, "text"),
            new ColumnSpec("status", "状态", 120, false, "tag"),
            new ColumnSpec("updatedAt", "更新于", 190, false, "datetime")
        );
    }

    private static String density(String value) {
        String density = value == null ? "comfortable" : value.trim().toLowerCase(Locale.ROOT);
        if (!DENSITIES.contains(density)) {
            throw failure("INVALID_VIEW_DENSITY", "View density is invalid");
        }
        return density;
    }

    private static String viewKey(String value) {
        String key = value == null ? "" : value.trim();
        if (!VIEW_KEY.matcher(key).matches()) {
            throw failure("INVALID_VIEW_KEY", "View key is invalid");
        }
        return key;
    }

    private static String requestId(String value) {
        String requestId = value == null ? "" : value.trim();
        if (requestId.isBlank() || requestId.length() > 120) {
            throw failure("INVALID_REQUEST_ID", "Caller-stable request ID is required");
        }
        return requestId;
    }

    private static String bulkItemRequestId(String requestId, String action, UUID workItemId) {
        String source = requestId + "|" + action + "|" + workItemId;
        return "bulk-" + sha256(source).substring(0, 48);
    }

    private static String display(Object value, String format) {
        if (value == null) return "";
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        }
        return String.valueOf(value);
    }

    private static String safeReason(String code) {
        return Set.of(
            "FORBIDDEN",
            "NOT_FOUND_OR_HIDDEN",
            "WORK_ITEM_VERSION_CONFLICT",
            "INVALID_WORK_ITEM_TRANSITION"
        ).contains(code) ? code : "ACTION_FAILED";
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private <T> T read(JsonNode value, Class<T> type) {
        try {
            return objectMapper.treeToValue(value, type);
        } catch (JsonProcessingException exception) {
            throw failure("INVALID_STORED_EXPORT", "Stored export input is invalid", exception);
        }
    }

    private <T> T read(JsonNode value, TypeReference<T> type) {
        try {
            return objectMapper.readerFor(type).readValue(value);
        } catch (java.io.IOException exception) {
            throw failure("INVALID_STORED_EXPORT", "Stored export input is invalid", exception);
        }
    }
}
