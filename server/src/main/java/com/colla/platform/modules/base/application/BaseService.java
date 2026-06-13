package com.colla.platform.modules.base.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.base.domain.BaseModels.BaseDetail;
import com.colla.platform.modules.base.domain.BaseModels.BaseField;
import com.colla.platform.modules.base.domain.BaseModels.BaseFilter;
import com.colla.platform.modules.base.domain.BaseModels.BaseCalendarBucket;
import com.colla.platform.modules.base.domain.BaseModels.BaseCalendarView;
import com.colla.platform.modules.base.domain.BaseModels.BaseKanbanColumn;
import com.colla.platform.modules.base.domain.BaseModels.BaseKanbanView;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecord;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecordPage;
import com.colla.platform.modules.base.domain.BaseModels.BaseSort;
import com.colla.platform.modules.base.domain.BaseModels.BaseSummary;
import com.colla.platform.modules.base.domain.BaseModels.BaseTableDetail;
import com.colla.platform.modules.base.domain.BaseModels.BaseTableSummary;
import com.colla.platform.modules.base.domain.BaseModels.BaseView;
import com.colla.platform.modules.base.infrastructure.BaseRepository;
import com.colla.platform.modules.event.infrastructure.DomainEventRepository;
import com.colla.platform.modules.file.infrastructure.FileRepository;
import com.colla.platform.modules.identity.infrastructure.IdentityRepository;
import com.colla.platform.modules.platform.infrastructure.PlatformObjectRepository;
import com.colla.platform.shared.auth.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BaseService {
    private static final List<String> FIELD_TYPES = List.of("text", "number", "member", "date", "attachment", "single_select", "multi_select");

    private final BaseRepository baseRepository;
    private final IdentityRepository identityRepository;
    private final FileRepository fileRepository;
    private final PlatformObjectRepository objectRepository;
    private final DomainEventRepository eventRepository;
    private final AuditService auditService;

    public BaseService(
        BaseRepository baseRepository,
        IdentityRepository identityRepository,
        FileRepository fileRepository,
        PlatformObjectRepository objectRepository,
        DomainEventRepository eventRepository,
        AuditService auditService
    ) {
        this.baseRepository = baseRepository;
        this.identityRepository = identityRepository;
        this.fileRepository = fileRepository;
        this.objectRepository = objectRepository;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
    }

    public List<BaseSummary> listBases(CurrentUser currentUser) {
        return baseRepository.listBases(currentUser.workspaceId(), currentUser.id());
    }

    @Transactional
    public BaseDetail createBase(CurrentUser currentUser, String name, String description) {
        String normalizedName = required(name, "Base name is required");
        UUID baseId = baseRepository.createBase(currentUser.workspaceId(), normalizedName, description, currentUser.id());
        baseRepository.upsertMember(currentUser.workspaceId(), baseId, currentUser.id(), "manage", currentUser.id());
        registerBaseObject(currentUser.workspaceId(), baseId, normalizedName);
        eventRepository.append(
            currentUser.workspaceId(),
            "base.created",
            "base",
            baseId,
            currentUser.id(),
            Map.of("baseId", baseId.toString()),
            "base.created:" + baseId
        );
        return getBase(currentUser, baseId);
    }

    public BaseDetail getBase(CurrentUser currentUser, UUID baseId) {
        requireView(currentUser, baseId);
        return baseRepository.findBaseDetail(currentUser.workspaceId(), baseId, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Base not found"));
    }

    @Transactional
    public BaseDetail updateBase(CurrentUser currentUser, UUID baseId, String name, String description) {
        BaseSummary base = requireManage(currentUser, baseId);
        String normalizedName = name == null || name.isBlank() ? base.name() : name.trim();
        baseRepository.updateBase(currentUser.workspaceId(), baseId, normalizedName, description, currentUser.id());
        registerBaseObject(currentUser.workspaceId(), baseId, normalizedName);
        return getBase(currentUser, baseId);
    }

    @Transactional
    public BaseDetail grantPermission(CurrentUser currentUser, UUID baseId, UUID userId, String permissionLevel) {
        requireManage(currentUser, baseId);
        identityRepository.findUserById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String normalizedPermission = normalizePermission(permissionLevel);
        baseRepository.upsertMember(currentUser.workspaceId(), baseId, userId, normalizedPermission, currentUser.id());
        auditService.log(
            currentUser,
            "permission.granted",
            "base",
            baseId,
            Map.of("userId", userId.toString(), "permissionLevel", normalizedPermission)
        );
        return getBase(currentUser, baseId);
    }

    @Transactional
    public BaseTableDetail createTable(CurrentUser currentUser, UUID baseId, String name) {
        BaseSummary base = requireEdit(currentUser, baseId);
        String normalizedName = required(name, "Table name is required");
        UUID tableId = baseRepository.createTable(currentUser.workspaceId(), baseId, normalizedName, currentUser.id());
        registerTableObject(currentUser.workspaceId(), base.id(), tableId, normalizedName);
        eventRepository.append(
            currentUser.workspaceId(),
            "base.table.created",
            "base_table",
            tableId,
            currentUser.id(),
            Map.of("baseId", baseId.toString(), "tableId", tableId.toString()),
            "base.table.created:" + tableId
        );
        return getTable(currentUser, baseId, tableId);
    }

    public BaseTableDetail getTable(CurrentUser currentUser, UUID baseId, UUID tableId) {
        requireView(currentUser, baseId);
        BaseTableSummary table = requireTable(currentUser, baseId, tableId);
        return new BaseTableDetail(
            table,
            baseRepository.listFields(currentUser.workspaceId(), tableId),
            baseRepository.listViews(currentUser.workspaceId(), tableId)
        );
    }

    @Transactional
    public BaseTableDetail updateTable(CurrentUser currentUser, UUID baseId, UUID tableId, String name) {
        requireEdit(currentUser, baseId);
        BaseTableSummary table = requireTable(currentUser, baseId, tableId);
        String normalizedName = name == null || name.isBlank() ? table.name() : name.trim();
        baseRepository.updateTable(currentUser.workspaceId(), tableId, normalizedName, currentUser.id());
        registerTableObject(currentUser.workspaceId(), baseId, tableId, normalizedName);
        return getTable(currentUser, baseId, tableId);
    }

    @Transactional
    public BaseTableDetail createField(
        CurrentUser currentUser,
        UUID baseId,
        UUID tableId,
        String name,
        String fieldType,
        Map<String, Object> config,
        boolean required
    ) {
        requireEdit(currentUser, baseId);
        BaseTableSummary table = requireTable(currentUser, baseId, tableId);
        String normalizedType = normalizeFieldType(fieldType);
        Map<String, Object> normalizedConfig = validateFieldConfig(normalizedType, config == null ? Map.of() : config);
        int sortOrder = baseRepository.listFields(currentUser.workspaceId(), tableId).size() + 1;
        UUID fieldId = baseRepository.createField(
            currentUser.workspaceId(),
            tableId,
            "f_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
            required(name, "Field name is required"),
            normalizedType,
            normalizedConfig,
            required,
            sortOrder,
            currentUser.id()
        );
        if (table.primaryFieldId() == null) {
            baseRepository.setPrimaryField(currentUser.workspaceId(), tableId, fieldId);
        }
        return getTable(currentUser, baseId, tableId);
    }

    @Transactional
    public BaseRecord createRecord(CurrentUser currentUser, UUID baseId, UUID tableId, Map<String, Object> values) {
        requireEdit(currentUser, baseId);
        requireTable(currentUser, baseId, tableId);
        List<BaseField> fields = baseRepository.listFields(currentUser.workspaceId(), tableId);
        Map<UUID, NormalizedValue> normalizedValues = normalizeValues(currentUser, fields, values == null ? Map.of() : values, false);
        UUID recordId = baseRepository.createRecord(currentUser.workspaceId(), tableId, baseRepository.nextRecordNumber(tableId), currentUser.id());
        for (Map.Entry<UUID, NormalizedValue> entry : normalizedValues.entrySet()) {
            NormalizedValue value = entry.getValue();
            baseRepository.upsertValue(currentUser.workspaceId(), recordId, entry.getKey(), value.value(), value.valueText(), value.valueNumber(), value.valueDate(), currentUser.id());
        }
        baseRepository.updateRecordTouched(currentUser.workspaceId(), recordId, currentUser.id());
        BaseRecord record = baseRepository.findRecord(currentUser.workspaceId(), recordId).orElseThrow();
        registerRecordObject(currentUser.workspaceId(), baseId, tableId, record);
        return record;
    }

    @Transactional
    public BaseRecord updateRecord(CurrentUser currentUser, UUID baseId, UUID tableId, UUID recordId, Map<String, Object> values) {
        requireEdit(currentUser, baseId);
        requireTable(currentUser, baseId, tableId);
        BaseRecord before = baseRepository.findRecord(currentUser.workspaceId(), recordId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
        if (!before.tableId().equals(tableId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
        }
        Map<UUID, NormalizedValue> normalizedValues = normalizeValues(
            currentUser,
            baseRepository.listFields(currentUser.workspaceId(), tableId),
            values == null ? Map.of() : values,
            true
        );
        for (Map.Entry<UUID, NormalizedValue> entry : normalizedValues.entrySet()) {
            NormalizedValue value = entry.getValue();
            baseRepository.upsertValue(currentUser.workspaceId(), recordId, entry.getKey(), value.value(), value.valueText(), value.valueNumber(), value.valueDate(), currentUser.id());
        }
        baseRepository.updateRecordTouched(currentUser.workspaceId(), recordId, currentUser.id());
        BaseRecord record = baseRepository.findRecord(currentUser.workspaceId(), recordId).orElseThrow();
        registerRecordObject(currentUser.workspaceId(), baseId, tableId, record);
        return record;
    }

    @Transactional
    public void deleteRecord(CurrentUser currentUser, UUID baseId, UUID tableId, UUID recordId) {
        requireEdit(currentUser, baseId);
        requireTable(currentUser, baseId, tableId);
        baseRepository.deleteRecord(currentUser.workspaceId(), recordId, currentUser.id());
    }

    public BaseRecord getRecord(CurrentUser currentUser, UUID recordId) {
        BaseRecord record = baseRepository.findRecord(currentUser.workspaceId(), recordId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"));
        BaseTableSummary table = baseRepository.findTable(currentUser.workspaceId(), record.tableId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        requireView(currentUser, table.baseId());
        return record;
    }

    public BaseRecordPage listRecords(CurrentUser currentUser, UUID baseId, UUID tableId, List<BaseFilter> filters, List<BaseSort> sorts, int limit, int offset) {
        requireView(currentUser, baseId);
        requireTable(currentUser, baseId, tableId);
        List<BaseFilter> normalizedFilters = normalizeFilters(currentUser, tableId, filters == null ? List.of() : filters);
        List<BaseSort> normalizedSorts = normalizeSorts(currentUser, tableId, sorts == null ? List.of() : sorts);
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        int boundedOffset = Math.max(0, offset);
        return new BaseRecordPage(
            baseRepository.listRecords(currentUser.workspaceId(), tableId, normalizedFilters, normalizedSorts, boundedLimit, boundedOffset),
            baseRepository.countRecords(currentUser.workspaceId(), tableId, normalizedFilters),
            boundedLimit,
            boundedOffset
        );
    }

    public BaseKanbanView kanbanView(CurrentUser currentUser, UUID baseId, UUID tableId, UUID groupFieldId) {
        requireView(currentUser, baseId);
        requireTable(currentUser, baseId, tableId);
        BaseField groupField = requireField(currentUser, tableId, groupFieldId);
        if (!List.of("single_select", "member", "text").contains(groupField.fieldType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kanban group field must be text, member or single_select");
        }
        List<BaseRecord> records = baseRepository.listRecords(currentUser.workspaceId(), tableId, List.of(), List.of(), 100, 0);
        Map<String, List<BaseRecord>> grouped = new LinkedHashMap<>();
        for (BaseRecord record : records) {
            String key = valueText(record.values().get(groupField.id().toString()), "未分组");
            grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(record);
        }
        return new BaseKanbanView(
            tableId,
            groupFieldId,
            grouped.entrySet().stream()
                .map(entry -> new BaseKanbanColumn(entry.getKey(), entry.getKey(), entry.getValue()))
                .toList()
        );
    }

    public BaseCalendarView calendarView(CurrentUser currentUser, UUID baseId, UUID tableId, UUID dateFieldId) {
        requireView(currentUser, baseId);
        requireTable(currentUser, baseId, tableId);
        BaseField dateField = requireField(currentUser, tableId, dateFieldId);
        if (!"date".equals(dateField.fieldType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calendar field must be date");
        }
        List<BaseRecord> records = baseRepository.listRecords(currentUser.workspaceId(), tableId, List.of(), List.of(), 100, 0);
        Map<String, List<BaseRecord>> grouped = new TreeMap<>();
        for (BaseRecord record : records) {
            Object rawValue = record.values().get(dateField.id().toString());
            if (rawValue != null) {
                grouped.computeIfAbsent(rawValue.toString(), ignored -> new java.util.ArrayList<>()).add(record);
            }
        }
        return new BaseCalendarView(
            tableId,
            dateFieldId,
            grouped.entrySet().stream()
                .map(entry -> new BaseCalendarBucket(entry.getKey(), entry.getValue()))
                .toList()
        );
    }

    @Transactional
    public BaseView createView(CurrentUser currentUser, UUID baseId, UUID tableId, String name, List<BaseFilter> filters, List<BaseSort> sorts) {
        requireEdit(currentUser, baseId);
        requireTable(currentUser, baseId, tableId);
        List<BaseFilter> normalizedFilters = normalizeFilters(currentUser, tableId, filters == null ? List.of() : filters);
        List<BaseSort> normalizedSorts = normalizeSorts(currentUser, tableId, sorts == null ? List.of() : sorts);
        UUID viewId = baseRepository.createView(currentUser.workspaceId(), tableId, required(name, "View name is required"), normalizedFilters, normalizedSorts, currentUser.id());
        return baseRepository.listViews(currentUser.workspaceId(), tableId).stream()
            .filter(view -> view.id().equals(viewId))
            .findFirst()
            .orElseThrow();
    }

    public BaseSummary requireView(CurrentUser currentUser, UUID baseId) {
        return requirePermission(currentUser, baseId, "view");
    }

    public BaseTableSummary requireTableView(CurrentUser currentUser, UUID tableId) {
        BaseTableSummary table = baseRepository.findTable(currentUser.workspaceId(), tableId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        requireView(currentUser, table.baseId());
        return table;
    }

    private BaseSummary requireEdit(CurrentUser currentUser, UUID baseId) {
        return requirePermission(currentUser, baseId, "edit");
    }

    private BaseSummary requireManage(CurrentUser currentUser, UUID baseId) {
        return requirePermission(currentUser, baseId, "manage");
    }

    private BaseSummary requirePermission(CurrentUser currentUser, UUID baseId, String requiredLevel) {
        BaseSummary base = baseRepository.findBase(currentUser.workspaceId(), baseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Base not found"));
        String permission = baseRepository.findPermissionLevel(currentUser.workspaceId(), baseId, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Base access denied"));
        if (permissionRank(permission) < permissionRank(requiredLevel)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Base access denied");
        }
        return new BaseSummary(
            base.id(),
            base.name(),
            base.description(),
            base.status(),
            permission,
            base.tableCount(),
            base.recordCount(),
            base.createdBy(),
            base.createdByName(),
            base.createdAt(),
            base.updatedBy(),
            base.updatedByName(),
            base.updatedAt()
        );
    }

    private BaseTableSummary requireTable(CurrentUser currentUser, UUID baseId, UUID tableId) {
        BaseTableSummary table = baseRepository.findTable(currentUser.workspaceId(), tableId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        if (!table.baseId().equals(baseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found");
        }
        return table;
    }

    private BaseField requireField(CurrentUser currentUser, UUID tableId, UUID fieldId) {
        BaseField field = baseRepository.findField(currentUser.workspaceId(), fieldId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Field not found"));
        if (!field.tableId().equals(tableId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Field not found");
        }
        return field;
    }

    private Map<UUID, NormalizedValue> normalizeValues(CurrentUser currentUser, List<BaseField> fields, Map<String, Object> values, boolean partial) {
        Map<UUID, NormalizedValue> normalized = new LinkedHashMap<>();
        for (BaseField field : fields) {
            Object raw = values.containsKey(field.id().toString()) ? values.get(field.id().toString()) : values.get(field.name());
            if (raw == null) {
                if (field.required() && !partial) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field is required: " + field.name());
                }
                continue;
            }
            normalized.put(field.id(), normalizeFieldValue(currentUser, field, raw));
        }
        return normalized;
    }

    private NormalizedValue normalizeFieldValue(CurrentUser currentUser, BaseField field, Object raw) {
        return switch (field.fieldType()) {
            case "number" -> normalizeNumber(raw);
            case "member" -> normalizeMember(raw);
            case "date" -> normalizeDate(raw);
            case "attachment" -> normalizeAttachment(currentUser, raw);
            case "single_select" -> normalizeSingleSelect(field, raw);
            case "multi_select" -> normalizeMultiSelect(field, raw);
            default -> new NormalizedValue(raw.toString(), raw.toString(), null, null);
        };
    }

    private NormalizedValue normalizeNumber(Object raw) {
        BigDecimal number = raw instanceof Number numeric ? BigDecimal.valueOf(numeric.doubleValue()) : new BigDecimal(raw.toString());
        return new NormalizedValue(number, number.toPlainString(), number, null);
    }

    private NormalizedValue normalizeMember(Object raw) {
        UUID userId = UUID.fromString(raw.toString());
        identityRepository.findUserById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Member field user not found"));
        return new NormalizedValue(userId.toString(), userId.toString(), null, null);
    }

    private NormalizedValue normalizeDate(Object raw) {
        LocalDate date = LocalDate.parse(raw.toString());
        return new NormalizedValue(date.toString(), date.toString(), null, date.toString());
    }

    private NormalizedValue normalizeAttachment(CurrentUser currentUser, Object raw) {
        UUID fileId = UUID.fromString(raw.toString());
        fileRepository.find(currentUser.workspaceId(), fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment file not found"));
        return new NormalizedValue(fileId.toString(), fileId.toString(), null, null);
    }

    private NormalizedValue normalizeSingleSelect(BaseField field, Object raw) {
        String value = raw.toString();
        if (!options(field).contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid select option");
        }
        return new NormalizedValue(value, value, null, null);
    }

    private NormalizedValue normalizeMultiSelect(BaseField field, Object raw) {
        List<?> values = raw instanceof List<?> list ? list : List.of(raw.toString());
        List<String> normalized = values.stream().map(Object::toString).toList();
        if (!options(field).containsAll(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid multi select option");
        }
        return new NormalizedValue(normalized, String.join(",", normalized), null, null);
    }

    private List<BaseFilter> normalizeFilters(CurrentUser currentUser, UUID tableId, List<BaseFilter> filters) {
        Map<UUID, BaseField> fields = fieldMap(currentUser, tableId);
        return filters.stream()
            .map(filter -> {
                BaseField field = fields.get(filter.fieldId());
                if (field == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown filter field");
                }
                NormalizedValue value = normalizeFieldValue(currentUser, field, filter.value());
                return new BaseFilter(filter.fieldId(), normalizeOperator(filter.operator()), value.value());
            })
            .toList();
    }

    private List<BaseSort> normalizeSorts(CurrentUser currentUser, UUID tableId, List<BaseSort> sorts) {
        Map<UUID, BaseField> fields = fieldMap(currentUser, tableId);
        return sorts.stream()
            .map(sort -> {
                if (!fields.containsKey(sort.fieldId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown sort field");
                }
                return new BaseSort(sort.fieldId(), "desc".equalsIgnoreCase(sort.direction()) ? "desc" : "asc");
            })
            .toList();
    }

    private Map<UUID, BaseField> fieldMap(CurrentUser currentUser, UUID tableId) {
        Map<UUID, BaseField> fields = new LinkedHashMap<>();
        for (BaseField field : baseRepository.listFields(currentUser.workspaceId(), tableId)) {
            fields.put(field.id(), field);
        }
        return fields;
    }

    private Map<String, Object> validateFieldConfig(String fieldType, Map<String, Object> config) {
        if (List.of("single_select", "multi_select").contains(fieldType)) {
            Object options = config.get("options");
            if (!(options instanceof List<?> list) || list.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select field requires options");
            }
            return Map.of("options", list.stream().map(Object::toString).toList());
        }
        return config;
    }

    private List<String> options(BaseField field) {
        Object options = field.config().get("options");
        if (options instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private void registerBaseObject(UUID workspaceId, UUID baseId, String name) {
        objectRepository.upsertObjectLink(workspaceId, "base", baseId, "/bases/" + baseId, "colla://base/" + baseId, name);
    }

    private void registerTableObject(UUID workspaceId, UUID baseId, UUID tableId, String name) {
        objectRepository.upsertObjectLink(
            workspaceId,
            "base_table",
            tableId,
            "/bases/" + baseId + "/tables/" + tableId,
            "colla://base_table/" + tableId,
            name
        );
    }

    private void registerRecordObject(UUID workspaceId, UUID baseId, UUID tableId, BaseRecord record) {
        objectRepository.upsertObjectLink(
            workspaceId,
            "base_record",
            record.id(),
            "/bases/" + baseId + "/tables/" + tableId + "/records/" + record.id(),
            "colla://base_record/" + record.id(),
            record.primaryText()
        );
    }

    private String normalizeFieldType(String fieldType) {
        String type = fieldType == null ? "" : fieldType.toLowerCase(Locale.ROOT);
        if (!FIELD_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid field type");
        }
        return type;
    }

    private String normalizePermission(String permissionLevel) {
        String level = permissionLevel == null ? "" : permissionLevel.toLowerCase(Locale.ROOT);
        if (!List.of("view", "edit", "manage").contains(level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid base permission");
        }
        return level;
    }

    private String normalizeOperator(String operator) {
        String value = operator == null || operator.isBlank() ? "eq" : operator.toLowerCase(Locale.ROOT);
        if (!List.of("eq", "contains", "gt", "lt").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filter operator");
        }
        return value;
    }

    private int permissionRank(String permissionLevel) {
        return switch (permissionLevel) {
            case "manage" -> 3;
            case "edit" -> 2;
            case "view" -> 1;
            default -> 0;
        };
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String valueText(Object value, String fallback) {
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString();
    }

    private record NormalizedValue(Object value, String valueText, Number valueNumber, String valueDate) {
    }
}
