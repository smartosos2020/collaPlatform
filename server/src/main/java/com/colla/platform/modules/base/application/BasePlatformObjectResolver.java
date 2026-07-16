package com.colla.platform.modules.base.application;

import com.colla.platform.modules.base.domain.BaseModels.BaseSummary;
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
public class BasePlatformObjectResolver implements PlatformObjectResolver {
    private final BaseService baseService;

    public BasePlatformObjectResolver(BaseService baseService) {
        this.baseService = baseService;
    }

    @Override
    public String objectType() {
        return "base";
    }

    @Override
    public Optional<PlatformObjectSummary> resolve(CurrentUser currentUser, UUID objectId) {
        try {
            BaseSummary base = baseService.requireView(currentUser, objectId);
            if (!"active".equals(base.status())) {
                return Optional.of(PlatformObjectSummary.unavailable(objectType(), objectId, ObjectAccessState.disabled));
            }
            return Optional.of(new PlatformObjectSummary(
                objectType(),
                objectId,
                ObjectAccessState.available,
                base.name(),
                "多维表格 / " + base.tableCount() + " 个数据表",
                base.status(),
                "/bases/" + objectId,
                "colla://base/" + objectId,
                Map.of("permissionLevel", base.permissionLevel(), "recordCount", base.recordCount())
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
