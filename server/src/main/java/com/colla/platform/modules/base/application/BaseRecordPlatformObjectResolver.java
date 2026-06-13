package com.colla.platform.modules.base.application;

import com.colla.platform.modules.base.domain.BaseModels.BaseRecord;
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
public class BaseRecordPlatformObjectResolver implements PlatformObjectResolver {
    private final BaseService baseService;

    public BaseRecordPlatformObjectResolver(BaseService baseService) {
        this.baseService = baseService;
    }

    @Override
    public String objectType() {
        return "base_record";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        try {
            BaseRecord record = baseService.getRecord(currentUser, objectId);
            BaseTableSummary table = baseService.requireTableView(currentUser, record.tableId());
            return Optional.of(new PlatformObjectSummary(
                objectType(),
                objectId,
                ObjectAccessState.available,
                record.primaryText(),
                table.name() + " / #" + record.recordNo(),
                "active",
                "/bases/" + table.baseId() + "/tables/" + table.id() + "/records/" + objectId,
                "colla://base_record/" + objectId,
                Map.of("baseId", table.baseId().toString(), "tableId", table.id().toString(), "recordNo", record.recordNo())
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
