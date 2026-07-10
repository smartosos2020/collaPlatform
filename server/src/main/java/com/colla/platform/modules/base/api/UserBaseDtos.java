package com.colla.platform.modules.base.api;

import com.colla.platform.modules.base.domain.BaseModels.BaseDetail;
import com.colla.platform.modules.base.domain.BaseModels.BaseMember;
import com.colla.platform.modules.base.domain.BaseModels.BaseRecordPage;
import com.colla.platform.modules.base.domain.BaseModels.BaseSummary;
import com.colla.platform.modules.base.domain.BaseModels.BaseTableSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class UserBaseDtos {
    private UserBaseDtos() {
    }

    static UserBaseView base(BaseSummary base) {
        return new UserBaseView(
            base.id(),
            base.name(),
            base.description(),
            base.status(),
            base.permissionLevel(),
            base.tableCount(),
            base.recordCount(),
            base.createdBy(),
            base.createdByName(),
            base.createdAt(),
            base.updatedBy(),
            base.updatedByName(),
            base.updatedAt(),
            new UserBaseCollaboration(base.permissionLevel(), permissionText(base.permissionLevel()), canEdit(base.permissionLevel())),
            List.of("open", "query_records", canEdit(base.permissionLevel()) ? "edit_records" : "request_permission")
        );
    }

    static UserBaseDetailView detail(BaseDetail detail) {
        return new UserBaseDetailView(
            base(detail.base()),
            detail.tables().stream().map(UserBaseDtos::table).toList(),
            detail.members().stream().map(UserBaseDtos::member).toList()
        );
    }

    static UserBaseTableView table(BaseTableSummary table) {
        return new UserBaseTableView(
            table.id(),
            table.baseId(),
            table.name(),
            table.primaryFieldId(),
            table.fieldCount(),
            table.recordCount(),
            table.createdAt(),
            table.updatedAt(),
            List.of("open", "query", "create_view")
        );
    }

    static UserBaseRecordPageView recordPage(BaseRecordPage page) {
        return new UserBaseRecordPageView(page.items(), page.total(), page.limit(), page.offset(), "可查看和筛选记录");
    }

    private static UserBaseMemberView member(BaseMember member) {
        return new UserBaseMemberView(
            member.id(),
            member.userId(),
            member.username(),
            member.displayName(),
            member.permissionLevel(),
            member.createdAt(),
            new UserBaseCollaboration(member.permissionLevel(), permissionText(member.permissionLevel()), canEdit(member.permissionLevel()))
        );
    }

    private static boolean canEdit(String permissionLevel) {
        return List.of("edit", "manage", "owner").contains(permissionLevel);
    }

    private static String permissionText(String permissionLevel) {
        return switch (permissionLevel == null ? "" : permissionLevel) {
            case "owner", "manage" -> "可管理";
            case "edit" -> "可编辑记录";
            case "comment" -> "可评论";
            case "view" -> "可查看";
            default -> "可申请权限";
        };
    }

    record UserBaseView(
        UUID id,
        String name,
        String description,
        String status,
        String permissionLevel,
        int tableCount,
        int recordCount,
        UUID createdBy,
        String createdByName,
        Instant createdAt,
        UUID updatedBy,
        String updatedByName,
        Instant updatedAt,
        UserBaseCollaboration collaboration,
        List<String> availableActions
    ) {
    }

    record UserBaseDetailView(UserBaseView base, List<UserBaseTableView> tables, List<UserBaseMemberView> members) {
    }

    record UserBaseTableView(
        UUID id,
        UUID baseId,
        String name,
        UUID primaryFieldId,
        int fieldCount,
        int recordCount,
        Instant createdAt,
        Instant updatedAt,
        List<String> availableActions
    ) {
    }

    record UserBaseMemberView(
        UUID id,
        UUID userId,
        String username,
        String displayName,
        String permissionLevel,
        Instant createdAt,
        UserBaseCollaboration collaboration
    ) {
    }

    record UserBaseRecordPageView(List<?> items, int total, int limit, int offset, String collaborationHint) {
    }

    record UserBaseCollaboration(String permissionLevel, String displayText, boolean canEdit) {
    }
}
