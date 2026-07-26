package com.colla.platform.modules.project.application;

import com.colla.platform.modules.project.domain.WorkItemTypePresetCatalog.PresetTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class WorkItemStateFlowPresetCatalog {
    private static final List<String> EXECUTION_ROLES = List.of("admin", "member", "owner");
    private final ObjectMapper objectMapper;

    public WorkItemStateFlowPresetCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<JsonNode> stateFlowFor(PresetTemplate preset) {
        return stateFlowFor(preset.typeKey());
    }

    public Optional<JsonNode> stateFlowFor(String typeKey) {
        if (!SetHolder.PRESET_KEYS.contains(typeKey)) {
            return Optional.empty();
        }
        return Optional.of("bug".equals(typeKey) ? bugFlow() : standardFlow());
    }

    private JsonNode standardFlow() {
        ObjectNode flow = emptyFlow();
        addState(flow, "open", "待处理", "initial", 100);
        addState(flow, "in_progress", "处理中", "active", 200);
        addState(flow, "done", "已完成", "terminal", 300);
        addState(flow, "canceled", "已取消", "canceled", 400);
        addAction(flow, "start_progress", "开始处理", "forward", 100);
        addAction(flow, "complete", "完成", "forward", 200);
        addAction(flow, "reopen", "重新打开", "reopen", 300);
        addAction(flow, "terminate", "终止", "terminate", 400);
        addAction(flow, "restore", "恢复", "restore", 500);
        addTransition(flow, "open_start_progress", "start_progress", "open", "in_progress", 100);
        addTransition(flow, "in_progress_complete", "complete", "in_progress", "done", 200);
        addTransition(flow, "done_reopen", "reopen", "done", "open", 300);
        addTransition(flow, "open_terminate", "terminate", "open", "canceled", 400);
        addTransition(flow, "in_progress_terminate", "terminate", "in_progress", "canceled", 500);
        addTransition(flow, "canceled_restore", "restore", "canceled", "open", 600);
        return flow;
    }

    private JsonNode bugFlow() {
        ObjectNode flow = emptyFlow();
        addState(flow, "open", "待处理", "initial", 100);
        addState(flow, "in_progress", "处理中", "active", 200);
        addState(flow, "resolved", "待验证", "active", 300);
        addState(flow, "closed", "已关闭", "terminal", 400);
        addState(flow, "canceled", "已取消", "canceled", 500);
        addAction(flow, "start_progress", "开始处理", "forward", 100);
        addAction(flow, "mark_fixed", "提交修复", "forward", 200);
        addAction(flow, "verify_passed", "验证通过", "forward", 300);
        addAction(flow, "verify_failed", "验证失败", "return", 400);
        addAction(flow, "reopen", "重新打开", "reopen", 500);
        addAction(flow, "terminate", "终止", "terminate", 600);
        addAction(flow, "restore", "恢复", "restore", 700);
        addTransition(flow, "open_start_progress", "start_progress", "open", "in_progress", 100);
        addTransition(flow, "in_progress_mark_fixed", "mark_fixed", "in_progress", "resolved", 200);
        addTransition(flow, "resolved_verify_passed", "verify_passed", "resolved", "closed", 300);
        addTransition(flow, "resolved_verify_failed", "verify_failed", "resolved", "in_progress", 400);
        addTransition(flow, "closed_reopen", "reopen", "closed", "open", 500);
        addTransition(flow, "open_terminate", "terminate", "open", "canceled", 600);
        addTransition(flow, "in_progress_terminate", "terminate", "in_progress", "canceled", 700);
        addTransition(flow, "resolved_terminate", "terminate", "resolved", "canceled", 800);
        addTransition(flow, "canceled_restore", "restore", "canceled", "open", 900);
        return flow;
    }

    private ObjectNode emptyFlow() {
        ObjectNode flow = objectMapper.createObjectNode();
        flow.putArray("states");
        flow.putArray("actions");
        flow.putArray("transitions");
        flow.putArray("guards");
        return flow;
    }

    private void addState(ObjectNode flow, String key, String label, String category, int sortOrder) {
        ObjectNode state = flow.withArray("states").addObject();
        state.put("stateKey", key);
        state.put("label", label);
        state.put("description", "");
        state.put("color", "");
        state.put("category", category);
        state.put("sortOrder", sortOrder);
    }

    private void addAction(ObjectNode flow, String key, String label, String kind, int sortOrder) {
        ObjectNode action = flow.withArray("actions").addObject();
        action.put("actionKey", key);
        action.put("label", label);
        action.put("description", "");
        action.put("kind", kind);
        ArrayNode roles = action.putArray("authorizedRoles");
        EXECUTION_ROLES.forEach(roles::add);
        action.putArray("requiredFieldKeys");
        action.set("fieldPatch", objectMapper.createObjectNode());
        action.putArray("sideEffectKeys");
        action.put("sortOrder", sortOrder);
    }

    private void addTransition(
        ObjectNode flow,
        String key,
        String actionKey,
        String fromStateKey,
        String toStateKey,
        int sortOrder
    ) {
        ObjectNode transition = flow.withArray("transitions").addObject();
        transition.put("transitionKey", key);
        transition.put("actionKey", actionKey);
        transition.put("fromStateKey", fromStateKey);
        transition.put("toStateKey", toStateKey);
        transition.putNull("guardKey");
        transition.put("sortOrder", sortOrder);
    }

    private static final class SetHolder {
        private static final java.util.Set<String> PRESET_KEYS = java.util.Set.of(
            "project", "requirement", "task", "bug", "iteration", "release"
        );
    }
}
