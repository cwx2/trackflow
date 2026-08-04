package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import com.trackflow.customfield.service.CustomFieldOptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 优先级字段服务 - 将优先级作为自定义字段系统中的枚举字段管理
 * <p>
 * 优先级字段使用固定 ID = 1000000000000000001（由 V249 迁移脚本种子化）。
 * 本服务对外提供简化接口，屏蔽自定义字段系统的复杂性。
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
        List<CustomFieldOption> options = getPriorityOptions(projectId);
        return options.stream().anyMatch(opt -> opt.getValue().equals(priority));
    }

    /**
     * 获取项目的默认优先级值。
     *
     * @param projectId 项目 ID
     * @return 默认优先级值，如果没有配置默认值则返回 "Normal"
     */
    public String getDefaultPriority(Long projectId) {
        List<CustomFieldOption> options = getPriorityOptions(projectId);
        return options.stream()
                .filter(opt -> Boolean.TRUE.equals(opt.getIsDefault()))
                .map(CustomFieldOption::getValue)
                .findFirst()
                .orElse("Normal");
    }
}
