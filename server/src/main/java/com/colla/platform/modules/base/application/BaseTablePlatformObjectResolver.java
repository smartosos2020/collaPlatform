package com.colla.platform.modules.base.application;

import com.colla.platform.modules.base.domain.BaseModels.BaseTableSummary;
import com.colla.platform.modules.platform.application.PlatformObjectResolver;
import com.colla.platform.modules.platform.domain.PlatformModels.ObjectAccessState;
import com.colla.platform.modules.platform.domain.PlatformModels.PlatformObjectSummary;
import com.colla.platform.shared.auth.CurrentUser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BaseTablePlatformObjectResolver implements PlatformObjectResolver {
    private final BaseService baseService;

    public BaseTablePlatformObjectResolver(BaseService baseService) {
        this.baseService = baseService;
    }

    @Override
    public String objectType() {
        return "base_table";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        try {
            BaseTableSummary table = baseService.requireTableView(currentUser, objectId);
            return Optional.of(new PlatformObjectSummary(
                objectType(),
                objectId,
                ObjectAccessState.available,
                table.name(),
                "数据表 / " + table.recordCount() + " 条记录",
                "active",
                "/bases/" + table.baseId() + "/tables/" + objectId,
                "colla://base_table/" + objectId,
                Map.of("baseId", table.baseId().toString(), "fieldCount", table.fieldCount())
            ));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
                return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.forbidden));
            }
            if (exception.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw exception;
        }
    }
}
