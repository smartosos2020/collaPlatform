package com.colla.platform.modules.project.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkItemLayoutActionPolicy {
    public List<String> availableActions(String role, String spaceStatus, String typeStatus) {
        if (!isManager(role) || !"active".equals(spaceStatus) || "retired".equals(typeStatus)) {
            return List.of();
        }
        return List.of("save", "save_policies", "synthetic_preview");
    }

    public boolean isManager(String role) {
        return "owner".equals(role) || "admin".equals(role);
    }
}
