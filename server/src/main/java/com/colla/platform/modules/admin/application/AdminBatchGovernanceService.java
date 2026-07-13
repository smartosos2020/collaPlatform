package com.colla.platform.modules.admin.application;

import com.colla.platform.modules.audit.application.AuditService;
import com.colla.platform.modules.permission.application.PermissionService;
import com.colla.platform.shared.auth.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AdminBatchGovernanceService {
    private final JdbcTemplate jdbcTemplate;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public AdminBatchGovernanceService(JdbcTemplate jdbcTemplate, PermissionService permissionService, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public List<com.colla.platform.modules.admin.api.AdminBatchGovernanceController.BatchCapability> capabilities() {
        return List.of(
            new com.colla.platform.modules.admin.api.AdminBatchGovernanceController.BatchCapability("users", "disable", "停用成员"),
            new com.colla.platform.modules.admin.api.AdminBatchGovernanceController.BatchCapability("departments", "disable", "停用部门"),
            new com.colla.platform.modules.admin.api.AdminBatchGovernanceController.BatchCapability("user_groups", "disable", "停用用户组"),
            new com.colla.platform.modules.admin.api.AdminBatchGovernanceController.BatchCapability("roles", "disable", "停用角色")
        );
    }

    public BatchReport preview(CurrentUser user, BatchCommand command) {
        String resourceType = normalizeResource(command.resourceType());
        String action = normalizeAction(command.action());
        requirePermission(user, resourceType);
        validateAction(action);
        List<BatchItem> items = inspect(user, resourceType, command.targetIds());
        long readyCount = items.stream().filter(item -> "ready".equals(item.status())).count();
        auditService.log(user, "admin.batch.previewed", resourceType, null, Map.of("action", action, "targetCount", command.targetIds().size(), "readyCount", readyCount));
        return new BatchReport(UUID.randomUUID(), resourceType, action, command.targetIds().size(), readyCount, false, items);
    }

    @Transactional
    public BatchReport execute(CurrentUser user, BatchCommand command, boolean confirm) {
        BatchReport report = preview(user, command);
        if (!confirm) {
            return report;
        }
        if (report.readyCount() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可执行的批量目标");
        }
        for (BatchItem item : report.items()) {
            if ("ready".equals(item.status())) {
                disable(user, report.resourceType(), item.targetId());
            }
        }
        auditService.log(user, "admin.batch.executed", report.resourceType(), null, Map.of("action", report.action(), "targetCount", report.targetCount(), "readyCount", report.readyCount(), "confirmed", true));
        List<BatchItem> refreshed = inspect(user, report.resourceType(), command.targetIds());
        return new BatchReport(report.operationId(), report.resourceType(), report.action(), report.targetCount(), report.readyCount(), true, refreshed);
    }

    private List<BatchItem> inspect(CurrentUser user, String resourceType, List<UUID> targetIds) {
        String table = table(resourceType);
        List<BatchItem> items = new ArrayList<>();
        for (UUID targetId : targetIds) {
            Integer count = jdbcTemplate.queryForObject("select count(*) from " + table + " where id = ? and workspace_id = ?" + activePredicate(resourceType), Integer.class, targetId, user.workspaceId());
            items.add(new BatchItem(targetId, count != null && count > 0 ? "ready" : "not_found", count != null && count > 0 ? "权限检查通过" : "目标不存在或不属于当前工作区"));
        }
        return items;
    }

    private void disable(CurrentUser user, String resourceType, UUID targetId) {
        String table = table(resourceType);
        if ("roles".equals(resourceType)) {
            jdbcTemplate.update("update roles set status = 'disabled', updated_at = now() where id = ? and workspace_id = ? and status = 'active'", targetId, user.workspaceId());
            return;
        }
        jdbcTemplate.update("update " + table + " set status = 'disabled', updated_by = ?, updated_at = now() where id = ? and workspace_id = ? and deleted_at is null", user.id(), targetId, user.workspaceId());
    }

    private void requirePermission(CurrentUser user, String resourceType) {
        switch (resourceType) {
            case "users" -> permissionService.requireManageUsers(user);
            case "departments" -> permissionService.requireManageOrganization(user);
            case "user_groups" -> permissionService.requireManageUserGroups(user);
            case "roles" -> permissionService.requireManageRoles(user);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的批量资源类型");
        }
    }

    private String table(String resourceType) {
        return switch (resourceType) {
            case "users", "departments", "user_groups", "roles" -> resourceType;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的批量资源类型");
        };
    }

    private String activePredicate(String resourceType) {
        return "roles".equals(resourceType) ? " and status = 'active'" : " and deleted_at is null";
    }

    private String normalizeResource(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String normalizeAction(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private void validateAction(String action) {
        if (!"disable".equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量动作仅支持 disable");
        }
    }

    public record BatchCommand(@NotBlank String resourceType, @NotBlank String action, @NotEmpty List<UUID> targetIds) {}
    public record BatchItem(UUID targetId, String status, String message) {}
    public record BatchReport(UUID operationId, String resourceType, String action, int targetCount, long readyCount, boolean executed, List<BatchItem> items) {}
}
