package com.colla.platform.modules.base.infrastructure;

import com.colla.platform.modules.base.domain.BaseModels.BaseDetail;
import com.colla.platform.modules.base.domain.BaseModels.BaseField;
import com.colla.platform.modules.base.domain.BaseModels.BaseFilter;
import com.colla.platform.modules.base.domain.BaseModels.BaseMember;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecord;
import com.colla.platform.modules.base.domain.BaseModels.BaseSort;
import com.colla.platform.modules.base.domain.BaseModels.BaseSummary;
import com.colla.platform.modules.base.domain.BaseModels.BaseTableSummary;
import com.colla.platform.modules.base.domain.BaseModels.BaseView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BaseRepository {
    UUID createBase(UUID workspaceId, String name, String description, UUID createdBy);

    void updateBase(UUID workspaceId, UUID baseId, String name, String description, UUID updatedBy);

    void upsertMember(UUID workspaceId, UUID baseId, UUID userId, String permissionLevel, UUID actorId);

    Optional<String> findPermissionLevel(UUID workspaceId, UUID baseId, UUID userId);

    List<BaseSummary> listBases(UUID workspaceId, UUID userId);

    Optional<BaseSummary> findBase(UUID workspaceId, UUID baseId);

    Optional<BaseDetail> findBaseDetail(UUID workspaceId, UUID baseId, UUID userId);

    List<BaseMember> listMembers(UUID workspaceId, UUID baseId);

    UUID createTable(UUID workspaceId, UUID baseId, String name, UUID createdBy);

    void updateTable(UUID workspaceId, UUID tableId, String name, UUID updatedBy);

    Optional<BaseTableSummary> findTable(UUID workspaceId, UUID tableId);

    List<BaseTableSummary> listTables(UUID workspaceId, UUID baseId);

    UUID createField(UUID workspaceId, UUID tableId, String fieldKey, String name, String fieldType, Map<String, Object> config, boolean required, int sortOrder, UUID createdBy);

    void setPrimaryField(UUID workspaceId, UUID tableId, UUID fieldId);

    Optional<BaseField> findField(UUID workspaceId, UUID fieldId);

    List<BaseField> listFields(UUID workspaceId, UUID tableId);

    int nextRecordNumber(UUID tableId);

    UUID createRecord(UUID workspaceId, UUID tableId, int recordNo, UUID createdBy);

    void updateRecordTouched(UUID workspaceId, UUID recordId, UUID updatedBy);

    void deleteRecord(UUID workspaceId, UUID recordId, UUID deletedBy);

    Optional<BaseRecord> findRecord(UUID workspaceId, UUID recordId);

    List<BaseRecord> listRecords(UUID workspaceId, UUID tableId, List<BaseFilter> filters, List<BaseSort> sorts, int limit, int offset);

    int countRecords(UUID workspaceId, UUID tableId, List<BaseFilter> filters);

    void upsertValue(UUID workspaceId, UUID recordId, UUID fieldId, Object value, String valueText, Number valueNumber, String valueDate, UUID updatedBy);

    UUID createView(UUID workspaceId, UUID tableId, String name, List<BaseFilter> filters, List<BaseSort> sorts, UUID createdBy);

    List<BaseView> listViews(UUID workspaceId, UUID tableId);
}
