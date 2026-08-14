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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工单类型字段服务 - 将工单类型作为自定义字段系统中的枚举字段管理
 * <p>
 * 工单类型字段使用固定 ID = 1000000000000000002（由 V251 迁移脚本种子化）。
 * 本服务对外提供简化接口，屏蔽自定义字段系统的复杂性。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueTypeFieldService {

    /** 工单类型自定义字段的固定 ID（V251 迁移脚本中定义） */
    public static final long ISSUE_TYPE_FIELD_ID = 1000000000000000002L;

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldOptionService optionService;

    /** 大小写无关的规范化缓存（懒加载，按项目 ID 隔离） */
    private final Map<Long, Map<String, String>> normalizeCache = new ConcurrentHashMap<>();

    /**
     * 获取指定项目的工单类型选项列表（排除已归档的选项）。
     * <p>
     * 支持项目独立选项集（如果项目已 Make Independent Copy）。
     *
     * @param projectId 项目 ID
     * @return 有效的工单类型选项列表，按 position 排序
     */
    public List<CustomFieldOption> getIssueTypeOptions(Long projectId) {
        return optionService.getEffectiveOptions(ISSUE_TYPE_FIELD_ID, projectId);
    }

    /**
     * 获取全局工单类型选项列表（不区分项目）。
     */
    public List<CustomFieldOption> getGlobalIssueTypeOptions() {
        return optionService.getGlobalOptions(ISSUE_TYPE_FIELD_ID);
    }

    /**
     * 获取工单类型自定义字段定义。
     */
    public CustomFieldDefinition getIssueTypeFieldDefinition() {
        CustomFieldDefinition field = definitionMapper.selectById(ISSUE_TYPE_FIELD_ID);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "工单类型自定义字段定义不存在，请确认 V251 迁移脚本已执行");
        }
        return field;
    }

    /**
     * 获取所有有效的工单类型值列表（用于工作流等全局场景）。
     * 返回全局选项集中未归档的值。
     *
     * @return 工单类型值列表
     */
    public List<String> listIssueTypes() {
        return getGlobalIssueTypeOptions().stream()
                .map(CustomFieldOption::getValue)
                .toList();
    }

    /**
     * 获取项目有效的工单类型值列表。
     *
     * @param projectId 项目 ID
     * @return 项目有效的工单类型值列表
     */
    public List<String> listIssueTypes(Long projectId) {
        return getIssueTypeOptions(projectId).stream()
                .map(CustomFieldOption::getValue)
                .toList();
    }

    /**
     * 校验并归一化 issueType 值。
     * 支持大小写无关匹配（如 "bug" → "Bug"），不在有效列表中则抛异常。
     *
     * @param issueType 用户输入的类型值
     * @param projectId 项目 ID（用于获取项目有效选项集）
     * @return 归一化后的标准类型值
     * @throws BusinessException 当类型不合法时
     */
    public String normalizeIssueType(String issueType, Long projectId) {
        if (issueType == null || issueType.isBlank()) {
            return getDefaultIssueType(projectId);
        }

        List<CustomFieldOption> options = getIssueTypeOptions(projectId);
        // 精确匹配优先
        for (CustomFieldOption option : options) {
            if (option.getValue().equals(issueType)) {
                return option.getValue();
            }
        }
        // 大小写无关匹配
        String lowerInput = issueType.toLowerCase().trim();
        for (CustomFieldOption option : options) {
            if (option.getValue().toLowerCase().equals(lowerInput)) {
                return option.getValue();
            }
        }

        List<String> validTypes = options.stream().map(CustomFieldOption::getValue).toList();
        throw new BusinessException(ErrorCode.BAD_REQUEST,
                "不支持的 Issue 类型: " + issueType + "，允许的值: " + validTypes);
    }

    /**
     * 校验并归一化 issueType 值（全局场景，不区分项目）。
     * 用于兼容不传 projectId 的旧接口。
     *
     * @param issueType 用户输入的类型值
     * @return 归一化后的标准类型值
     * @throws BusinessException 当类型不合法时
     */
    public String normalizeIssueType(String issueType) {
        if (issueType == null || issueType.isBlank()) {
            return getDefaultIssueType();
        }

        List<CustomFieldOption> options = getGlobalIssueTypeOptions();
        // 精确匹配优先
        for (CustomFieldOption option : options) {
            if (option.getValue().equals(issueType)) {
                return option.getValue();
            }
        }
        // 大小写无关匹配
        String lowerInput = issueType.toLowerCase().trim();
        for (CustomFieldOption option : options) {
            if (option.getValue().toLowerCase().equals(lowerInput)) {
                return option.getValue();
            }
        }

        List<String> validTypes = options.stream().map(CustomFieldOption::getValue).toList();
        throw new BusinessException(ErrorCode.BAD_REQUEST,
                "不支持的 Issue 类型: " + issueType + "，允许的值: " + validTypes);
    }

    /**
     * 根据工单类型值获取对应的选项 ID（用于 EAV 存储）。
     * <p>
     * 自定义字段 EAV 表中列表类型字段存储的是 option ID（而非 value 文本）。
     * 本方法将归一化后的类型值（如 "需求"）映射为对应的 option ID。
     *
     * @param issueType 归一化后的工单类型值
     * @param projectId 项目 ID
     * @return option ID 的字符串表示，如果找不到则返回 null
     */
    public String getOptionIdByValue(String issueType, Long projectId) {
        if (issueType == null || issueType.isBlank()) {
            return null;
        }
        List<CustomFieldOption> options = getIssueTypeOptions(projectId);
        for (CustomFieldOption option : options) {
            if (option.getValue().equalsIgnoreCase(issueType)) {
                return String.valueOf(option.getId());
            }
        }
        return null;
    }

    /**
     * 验证指定的工单类型值在项目中是否有效。
     *
     * @param issueType 工单类型值（如 "Bug", "Task"）
     * @param projectId 项目 ID
     * @return true 表示有效，false 表示无效
     */
    public boolean isValidIssueType(String issueType, Long projectId) {
        if (issueType == null || issueType.isBlank()) {
            return false; // 类型是必填字段
        }
        List<CustomFieldOption> options = getIssueTypeOptions(projectId);
        return options.stream().anyMatch(opt -> opt.getValue().equalsIgnoreCase(issueType));
    }

    /**
     * 获取项目的默认工单类型值。
     *
     * @param projectId 项目 ID
     * @return 默认工单类型值，如果没有配置默认值则返回列表中第一个选项的值
     */
    public String getDefaultIssueType(Long projectId) {
        List<CustomFieldOption> options = getIssueTypeOptions(projectId);
        return options.stream()
                .filter(opt -> Boolean.TRUE.equals(opt.getIsDefault()))
                .map(CustomFieldOption::getValue)
                .findFirst()
                .orElseGet(() -> options.isEmpty() ? "任务" : options.get(0).getValue());
    }

    /**
     * 获取全局默认工单类型值。
     *
     * @return 默认工单类型值
     */
    public String getDefaultIssueType() {
        List<CustomFieldOption> options = getGlobalIssueTypeOptions();
        return options.stream()
                .filter(opt -> Boolean.TRUE.equals(opt.getIsDefault()))
                .map(CustomFieldOption::getValue)
                .findFirst()
                .orElseGet(() -> options.isEmpty() ? "任务" : options.get(0).getValue());
    }
}
