package com.trackflow.issue.service;

import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import com.trackflow.customfield.service.CustomFieldOptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 优先级字段服务 - 将优先级作为自定义字段系统中的枚举字段管理
 * <p>
 * 优先级字段使用固定 ID = 1000000000000000001（由 V249 迁移脚本种子化）。
 * 本服务对外提供简化接口，屏蔽自定义字段系统的复杂性。
 * <p>
 * <b>所有需要优先级值列表、颜色映射的代码必须通过本服务获取，禁止使用硬编码。</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriorityFieldService {

    /** 优先级自定义字段的固定 ID（V249 迁移脚本中定义） */
    public static final long PRIORITY_FIELD_ID = 1000000000000000001L;

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldOptionService optionService;

    /**
     * 获取指定项目的优先级选项列表（排除已归档的选项）。
     * <p>
     * 支持项目独立选项集（如果项目已 Make Independent Copy）。
     *
     * @param projectId 项目 ID
     * @return 有效的优先级选项列表，按 position 排序
     */
    public List<CustomFieldOption> getPriorityOptions(Long projectId) {
        return optionService.getEffectiveOptions(PRIORITY_FIELD_ID, projectId);
    }

    /**
     * 获取全局优先级选项列表（不区分项目）。
     */
    public List<CustomFieldOption> getGlobalPriorityOptions() {
        return optionService.getGlobalOptions(PRIORITY_FIELD_ID);
    }

    /**
     * 获取优先级自定义字段定义。
     */
    public CustomFieldDefinition getPriorityFieldDefinition() {
        CustomFieldDefinition field = definitionMapper.selectById(PRIORITY_FIELD_ID);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "优先级自定义字段定义不存在，请确认 V249 迁移脚本已执行");
        }
        return field;
    }

    /**
     * 获取指定项目的优先级值列表（有序，按 position 排序）。
     * <p>
     * 替代原 {@code IssuePriority.ALL_VALUES} 的使用场景。
     *
     * @param projectId 项目 ID（传 null 获取全局选项）
     * @return 有序的优先级值列表
     */
    public List<String> getPriorityValues(Long projectId) {
        List<CustomFieldOption> options = projectId != null
                ? getPriorityOptions(projectId)
                : getGlobalPriorityOptions();
        return options.stream().map(CustomFieldOption::getValue).toList();
    }

    /**
     * 获取指定项目的优先级颜色映射（value → color）。
     * <p>
     * 替代原 {@code IssuePriority.COLOR_MAP} 的使用场景。
     *
     * @param projectId 项目 ID（传 null 获取全局选项）
     * @return 优先级值 → 颜色映射（保持 position 顺序）
     */
    public Map<String, String> getPriorityColors(Long projectId) {
        List<CustomFieldOption> options = projectId != null
                ? getPriorityOptions(projectId)
                : getGlobalPriorityOptions();
        Map<String, String> colorMap = new LinkedHashMap<>();
        for (CustomFieldOption opt : options) {
            colorMap.put(opt.getValue(), opt.getColor() != null ? opt.getColor() : "#6b7280");
        }
        return colorMap;
    }

    /**
     * 根据优先级值获取对应的选项 ID（用于 EAV 存储）。
     * <p>
     * 自定义字段 EAV 表中列表类型字段存储的是 option ID（而非 value 文本）。
     * 本方法将归一化后的优先级值（如 "高"）映射为对应的 option ID。
     *
     * @param priority  归一化后的优先级值
     * @param projectId 项目 ID
     * @return option ID 的字符串表示，如果找不到则返回 null
     */
    public String getOptionIdByValue(String priority, Long projectId) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        String normalized = normalizePriority(priority);
        List<CustomFieldOption> options = getPriorityOptions(projectId);
        for (CustomFieldOption option : options) {
            if (option.getValue().equalsIgnoreCase(normalized)) {
                return String.valueOf(option.getId());
            }
        }
        return null;
    }

    /**
     * 验证指定的优先级值在项目中是否有效。
     *
     * @param priority  优先级值（如 "Normal", "Critical"）
     * @param projectId 项目 ID
     * @return true 表示有效，false 表示无效
     */
    public boolean isValidPriority(String priority, Long projectId) {
        if (priority == null || priority.isBlank()) {
            return true; // 允许为空（由字段配置的 canBeEmpty 控制）
        }
        // 兼容旧数据：medium 映射为 Normal
        String normalized = normalizePriority(priority);
        List<CustomFieldOption> options = getPriorityOptions(projectId);
        return options.stream().anyMatch(opt -> opt.getValue().equalsIgnoreCase(normalized));
    }

    /**
     * 规范化优先级值（兼容旧数据中的 medium 等脏数据）。
     *
     * @param priority 原始优先级值
     * @return 规范化后的优先级值
     */
    public String normalizePriority(String priority) {
        if (priority == null) return null;
        if ("medium".equalsIgnoreCase(priority)) {
            return "普通"; // 已知映射，不依赖枚举
        }
        return priority;
    }

    /**
     * 获取项目的默认优先级值。
     *
     * @param projectId 项目 ID
     * @return 默认优先级值，如果没有配置默认值则返回列表中第一个选项的值
     */
    public String getDefaultPriority(Long projectId) {
        List<CustomFieldOption> options = projectId != null
                ? getPriorityOptions(projectId)
                : getGlobalPriorityOptions();
        // 优先找标记为 isDefault 的选项
        return options.stream()
                .filter(opt -> Boolean.TRUE.equals(opt.getIsDefault()))
                .map(CustomFieldOption::getValue)
                .findFirst()
                // 没有标记 default 的，取列表中间位置的选项（通常是"普通"）
                .orElseGet(() -> {
                    if (options.isEmpty()) return "普通";
                    int mid = options.size() / 2;
                    return options.get(mid).getValue();
                });
    }
}
