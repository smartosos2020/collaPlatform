package com.colla.platform.modules.project.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WorkItemTypePresetCatalog {
    public static final String CATALOG_VERSION = "development-v1";

    private static final List<PresetTemplate> DEVELOPMENT_PRESETS = List.of(
        new PresetTemplate("project", "项目", "project", "用于承载项目目标、范围与交付结果。", 100),
        new PresetTemplate("requirement", "需求", "requirement", "用于记录业务需求、用户诉求与验收目标。", 200),
        new PresetTemplate("task", "任务", "task", "用于拆解和跟踪可执行工作。", 300),
        new PresetTemplate("bug", "缺陷", "bug", "用于记录、定位和修复产品缺陷。", 400),
        new PresetTemplate("iteration", "迭代", "iteration", "用于组织固定周期内的计划与交付。", 500),
        new PresetTemplate("release", "版本", "release", "用于管理版本范围、发布计划与结果。", 600)
    );

    private static final Map<String, PresetTemplate> BY_KEY = DEVELOPMENT_PRESETS.stream()
        .collect(Collectors.toUnmodifiableMap(PresetTemplate::typeKey, Function.identity()));

    public String version() {
        return CATALOG_VERSION;
    }

    public List<PresetTemplate> developmentPresets() {
        return DEVELOPMENT_PRESETS;
    }

    public boolean isPresetKey(String typeKey) {
        return BY_KEY.containsKey(typeKey);
    }

    public record PresetTemplate(
        String typeKey,
        String name,
        String icon,
        String description,
        int sortOrder
    ) {
    }
}
