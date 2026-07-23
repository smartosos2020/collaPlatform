package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemFieldModels.FieldDefinition;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkItemFieldActionPolicy {
    public List<String> collectionActions(String role, String spaceStatus, String typeStatus) {
        if (!isManager(role) || !"active".equals(spaceStatus) || "retired".equals(typeStatus)) {
            return List.of();
        }
        return List.of("create", "reorder");
    }

    public List<String> fieldActions(String role, String spaceStatus, String typeStatus, FieldDefinition field) {
        if (!isManager(role) || !"active".equals(spaceStatus) || "retired".equals(typeStatus)) {
            return List.of();
        }
        List<String> actions = new ArrayList<>();
        if (!"retired".equals(field.status())) {
            actions.add("reorder");
        }
        if (!field.system() && !"retired".equals(field.status())) {
            actions.add("edit");
            actions.add("configure");
            actions.add("retire");
        }
        if ("active".equals(field.status())) {
            actions.add("disable");
        } else if ("disabled".equals(field.status())) {
            actions.add("restore");
        }
        return List.copyOf(actions);
    }

    public boolean isManager(String role) {
        return "owner".equals(role) || "admin".equals(role);
    }
}
