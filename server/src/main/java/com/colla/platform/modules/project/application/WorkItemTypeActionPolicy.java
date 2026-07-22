package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemTypeModels.WorkItemTypeDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkItemTypeActionPolicy {
    public List<String> collectionActions(String role, String spaceStatus) {
        if (!isManager(role) || !"active".equals(spaceStatus)) {
            return List.of();
        }
        return List.of("create", "reorder");
    }

    public List<String> typeActions(String role, String spaceStatus, WorkItemTypeDefinition type) {
        if (!isManager(role) || !"active".equals(spaceStatus)) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        actions.add("copy");
        if (!"retired".equals(type.status())) {
            actions.add("reorder");
        }
        if (!type.system() && !"retired".equals(type.status())) {
            actions.add("edit");
            actions.add("retire");
        }
        if ("active".equals(type.status())) {
            actions.add("disable");
        } else if ("disabled".equals(type.status())) {
            actions.add("restore");
        }
        return List.copyOf(actions);
    }

    public boolean isManager(String role) {
        return "owner".equals(role) || "admin".equals(role);
    }
}
