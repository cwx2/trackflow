package com.trackflow.customfield.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.customfield.dto.UpdateCustomFieldDTO;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.entity.CustomFieldOption;
import com.trackflow.customfield.entity.CustomFieldProject;
import com.trackflow.customfield.entity.CustomFieldValue;
import com.trackflow.customfield.mapper.CustomFieldDefinitionMapper;
import com.trackflow.customfield.mapper.CustomFieldOptionMapper;
import com.trackflow.customfield.mapper.CustomFieldProjectMapper;
import com.trackflow.customfield.mapper.CustomFieldValueMapper;
import com.trackflow.customfield.vo.OptionUsageItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义字段选项集管理服务 — 处理枚举类型字段的选项 CRUD、排序、归档、用量统计
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldOptionService {

    private final CustomFieldDefinitionMapper definitionMapper;
    private final CustomFieldOptionMapper optionMapper;
    private final CustomFieldProjectMapper projectMapper;
    private final CustomFieldValueMapper valueMapper;

    /**
     * 重新排序枚举字段的选项值。
     * 仅更新 position 字段，不改变选项内容。
     *
     * @param fieldId   字段 ID
     * @param optionIds 按新顺序排列的选项 ID 列表
     */
    public void reorderOptions(Long fieldId, List<Long> optionIds) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!"list".equals(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅列表类型字段支持选项排序");
        }

        for (int i = 0; i < optionIds.size(); i++) {
            CustomFieldOption option = new CustomFieldOption();
            option.setId(optionIds.get(i));
            option.setPosition(i);
            optionMapper.updateById(option);
        }
    }

    /**
     * 设置选项的归档状态。
     * 归档后选项不出现在工单编辑时的下拉列表中，但已有工单的值仍保留。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setOptionArchived(Long fieldId, Long optionId, boolean archived) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!"list".equals(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅列表类型字段支持选项归档");
        }

        CustomFieldOption option = optionMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getId, optionId)
                        .eq(CustomFieldOption::getCustomFieldId, fieldId));
        if (option == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "选项不存在");
        }

        option.setIsArchived(archived);
        if (archived) {
            option.setPosition(Integer.MAX_VALUE);
        }
        option.setUpdatedAt(LocalDateTime.now());
        optionMapper.updateById(option);

        log.info("Custom field option {} {} (fieldId={})", optionId, archived ? "archived" : "unarchived", fieldId);
    }

    /**
     * 获取枚举字段逐选项使用统计。
     * 返回每个选项被多少个工单引用（DISTINCT issue_id 计数）。
     */
    public List<OptionUsageItemVO> getOptionUsage(Long id) {
        CustomFieldDefinition def = definitionMapper.selectById(id);
        if (def == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "自定义字段不存在");
        }
        if (!"list".equals(def.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅枚举(list)类型字段支持逐选项统计");
        }

        List<CustomFieldOption> allOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, id)
                        .orderByAsc(CustomFieldOption::getPosition));

        if (allOptions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> usageCounts = valueMapper.countIssuesByOption(id);
        Map<String, Long> optionCountMap = new HashMap<>();
        for (Map<String, Object> row : usageCounts) {
            String optionId = String.valueOf(row.get("option_id"));
            long count = ((Number) row.get("issue_count")).longValue();
            optionCountMap.put(optionId, count);
        }

        List<OptionUsageItemVO> result = new ArrayList<>();
        for (CustomFieldOption option : allOptions) {
            String optionIdStr = String.valueOf(option.getId());
            long count = optionCountMap.getOrDefault(optionIdStr, 0L);
            result.add(new OptionUsageItemVO(optionIdStr, option.getValue(), option.getColor(), count));
        }
        return result;
    }

    /**
     * 内联添加枚举字段选项值（工单详情页/创建表单快捷入口）。
     * 参考 YouTrack: 在工单编辑时直接添加新值到枚举字段。
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomFieldOption addOptionInline(Long projectId, Long fieldId, String value, String color) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!"list".equals(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有枚举类型字段支持添加选项值");
        }

        // 校验字段在该项目中可用（全局字段或已关联到项目）
        if (!field.getIsForAll()) {
            CustomFieldProject mapping = projectMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getCustomFieldId, fieldId)
                            .eq(CustomFieldProject::getProjectId, projectId)
                            .eq(CustomFieldProject::getIsExcluded, false));
            if (mapping == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该字段未关联到此项目");
            }
        } else {
            // 全局字段也可能被从项目中排除
            boolean excluded = projectMapper.exists(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getCustomFieldId, fieldId)
                            .eq(CustomFieldProject::getProjectId, projectId)
                            .eq(CustomFieldProject::getIsExcluded, true));
            if (excluded) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该字段已从此项目中移除");
            }
        }

        // 检查是否有同名选项
        List<CustomFieldOption> existingOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .eq(CustomFieldOption::getValue, value.trim()));

        for (CustomFieldOption existing : existingOptions) {
            if (!Boolean.TRUE.equals(existing.getIsArchived())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "选项值\"" + value.trim() + "\"已存在");
            }
        }

        // 如有同名归档选项 → 恢复
        CustomFieldOption archivedSameName = existingOptions.stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsArchived()))
                .findFirst()
                .orElse(null);

        if (archivedSameName != null) {
            int maxPosition = getMaxOptionPosition(fieldId);
            archivedSameName.setPosition(maxPosition + 1);
            archivedSameName.setIsArchived(false);
            archivedSameName.setIsDefault(false);
            if (color != null) {
                archivedSameName.setColor(color);
            }
            archivedSameName.setUpdatedAt(LocalDateTime.now());
            optionMapper.updateById(archivedSameName);
            log.info("Inline add option: reactivated archived option {} (value='{}'), fieldId={}, projectId={}",
                    archivedSameName.getId(), value.trim(), fieldId, projectId);
            return archivedSameName;
        }

        // 新建选项
        int maxPosition = getMaxOptionPosition(fieldId);
        CustomFieldOption option = new CustomFieldOption();
        option.setCustomFieldId(fieldId);
        option.setValue(value.trim());
        option.setPosition(maxPosition + 1);
        option.setIsDefault(false);
        option.setColor(color);
        option.setIsArchived(false);
        option.setCreatedAt(LocalDateTime.now());
        option.setUpdatedAt(LocalDateTime.now());
        optionMapper.insert(option);
        log.info("Inline add option: created new option {} (value='{}'), fieldId={}, projectId={}",
                option.getId(), value.trim(), fieldId, projectId);
        return option;
    }

    /**
     * 获取字段选项列表（含被替代的归档过滤）
     */
    public List<CustomFieldOption> getOptions(Long fieldId) {
        List<CustomFieldOption> allOptions = optionMapper.selectList(new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                .orderByAsc(CustomFieldOption::getPosition));
        return filterSupersededArchivedOptions(allOptions);
    }

    /**
     * 批量获取多个字段的选项列表。
     * @return Map<fieldId, List<CustomFieldOption>>（按 position 排序）
     */
    public Map<Long, List<CustomFieldOption>> getBatchOptions(List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) return Map.of();
        List<CustomFieldOption> allOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .in(CustomFieldOption::getCustomFieldId, fieldIds)
                        .orderByAsc(CustomFieldOption::getPosition));
        return allOptions.stream()
                .collect(Collectors.groupingBy(CustomFieldOption::getCustomFieldId))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> filterSupersededArchivedOptions(e.getValue())));
    }

    /**
     * 更新列表类型字段的选项（保持选项 ID 稳定性）。
     * <p>
     * 策略：
     * - DTO 中有 id 的选项 → UPDATE（更新 value/position/isDefault）
     * - DTO 中无 id 的选项 → INSERT（新增）
     * - DB 中存在但 DTO 中不存在的选项 → 检查是否被引用：有引用则归档，无引用则物理删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateListOptions(Long customFieldId, List<UpdateCustomFieldDTO.OptionItem> dtoOptions) {
        // 1. 加载当前数据库中所有选项
        List<CustomFieldOption> existingOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, customFieldId));
        Map<Long, CustomFieldOption> existingMap = existingOptions.stream()
                .collect(Collectors.toMap(CustomFieldOption::getId, o -> o));

        Set<Long> dtoIdsSeen = new HashSet<>();
        Set<String> activeValues = new HashSet<>();

        // 2. 遍历 DTO：更新已有 / 新增
        for (int i = 0; i < dtoOptions.size(); i++) {
            UpdateCustomFieldDTO.OptionItem opt = dtoOptions.get(i);
            if (opt.getId() != null && existingMap.containsKey(opt.getId())) {
                // UPDATE
                CustomFieldOption existing = existingMap.get(opt.getId());
                existing.setValue(opt.getValue());
                existing.setPosition(i);
                existing.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
                if (opt.getColor() != null) {
                    existing.setColor(opt.getColor());
                }
                // 如果之前是归档状态，恢复为活跃
                if (Boolean.TRUE.equals(existing.getIsArchived())) {
                    existing.setIsArchived(false);
                }
                existing.setUpdatedAt(LocalDateTime.now());
                optionMapper.updateById(existing);
                dtoIdsSeen.add(opt.getId());
                activeValues.add(opt.getValue());
            } else {
                // INSERT or reactivate archived
                Long newId = insertOrReactivateOption(customFieldId, opt, i);
                dtoIdsSeen.add(newId);
                activeValues.add(opt.getValue());
            }
        }

        // 3. 处理 DB 中有但 DTO 中不存在的选项
        for (CustomFieldOption existing : existingOptions) {
            if (!dtoIdsSeen.contains(existing.getId())) {
                if (isOptionReferenced(customFieldId, existing.getId())) {
                    // 被引用 → 归档
                    existing.setIsArchived(true);
                    existing.setPosition(Integer.MAX_VALUE);
                    existing.setUpdatedAt(LocalDateTime.now());
                    optionMapper.updateById(existing);
                } else {
                    // 未被引用 → 物理删除
                    optionMapper.deleteById(existing.getId());
                }
            }
        }

        // 4. 清理被活跃选项替代的旧归档条目
        cleanupSupersededArchivedOptions(customFieldId, activeValues);
    }

    /**
     * 复制选项集：从源字段复制到目标字段
     */
    public void copyOptionsFromField(Long targetFieldId, Long sourceFieldId) {
        List<CustomFieldOption> sourceOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, sourceFieldId)
                        .eq(CustomFieldOption::getIsArchived, false)
                        .orderByAsc(CustomFieldOption::getPosition));

        for (int i = 0; i < sourceOptions.size(); i++) {
            CustomFieldOption src = sourceOptions.get(i);
            CustomFieldOption copy = new CustomFieldOption();
            copy.setCustomFieldId(targetFieldId);
            copy.setValue(src.getValue());
            copy.setPosition(i);
            copy.setIsDefault(src.getIsDefault());
            copy.setColor(src.getColor());
            copy.setIsArchived(false);
            copy.setCreatedAt(LocalDateTime.now());
            copy.setUpdatedAt(LocalDateTime.now());
            optionMapper.insert(copy);
        }
    }

    /**
     * 追加选项集：从源字段追加到目标字段（不替换现有选项，仅追加不存在的）
     */
    public void appendOptionsFromField(Long targetFieldId, Long sourceFieldId) {
        List<CustomFieldOption> sourceOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, sourceFieldId)
                        .eq(CustomFieldOption::getIsArchived, false)
                        .orderByAsc(CustomFieldOption::getPosition));

        if (sourceOptions.isEmpty()) return;

        // 获取目标字段已有选项的 value 集合（防止重复）
        Set<String> existingValues = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, targetFieldId))
                .stream()
                .map(CustomFieldOption::getValue)
                .collect(Collectors.toSet());

        // 获取目标字段当前最大 position
        int maxPosition = getMaxOptionPosition(targetFieldId);

        int appendCount = 0;
        for (CustomFieldOption src : sourceOptions) {
            if (existingValues.contains(src.getValue())) continue;
            maxPosition++;
            CustomFieldOption copy = new CustomFieldOption();
            copy.setCustomFieldId(targetFieldId);
            copy.setValue(src.getValue());
            copy.setPosition(maxPosition);
            copy.setIsDefault(false);
            copy.setColor(src.getColor());
            copy.setIsArchived(false);
            copy.setCreatedAt(LocalDateTime.now());
            copy.setUpdatedAt(LocalDateTime.now());
            optionMapper.insert(copy);
            appendCount++;
        }

        if (appendCount > 0) {
            log.info("Appended {} options from field {} to field {} (skipped {} duplicates)",
                    appendCount, sourceFieldId, targetFieldId, sourceOptions.size() - appendCount);
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 获取字段选项列表中的最大 position 值
     */
    int getMaxOptionPosition(Long fieldId) {
        List<CustomFieldOption> allOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .eq(CustomFieldOption::getIsArchived, false)
                        .orderByDesc(CustomFieldOption::getPosition)
                        .last("LIMIT 1"));
        return allOptions.isEmpty() ? -1 : allOptions.get(0).getPosition();
    }

    /**
     * 过滤被同名活跃选项替代的归档残留条目。
     */
    List<CustomFieldOption> filterSupersededArchivedOptions(List<CustomFieldOption> options) {
        Set<String> activeValues = options.stream()
                .filter(o -> !o.getIsArchived())
                .map(CustomFieldOption::getValue)
                .collect(Collectors.toSet());

        return options.stream()
                .filter(o -> !o.getIsArchived() || !activeValues.contains(o.getValue()))
                .toList();
    }

    /**
     * 清理被活跃选项替代的旧归档条目（物理删除）
     */
    private void cleanupSupersededArchivedOptions(Long customFieldId, Set<String> activeValues) {
        List<CustomFieldOption> archivedOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, customFieldId)
                        .eq(CustomFieldOption::getIsArchived, true));

        for (CustomFieldOption archived : archivedOptions) {
            if (activeValues.contains(archived.getValue())) {
                if (!isOptionReferenced(customFieldId, archived.getId())) {
                    optionMapper.deleteById(archived.getId());
                }
            }
        }
    }

    /**
     * 插入新选项或恢复同名归档选项
     */
    private Long insertOrReactivateOption(Long customFieldId, UpdateCustomFieldDTO.OptionItem opt, int position) {
        // 检查是否有同 value 的归档选项可以复用
        CustomFieldOption archivedSameName = optionMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, customFieldId)
                        .eq(CustomFieldOption::getValue, opt.getValue())
                        .eq(CustomFieldOption::getIsArchived, true)
                        .last("LIMIT 1"));

        if (archivedSameName != null) {
            archivedSameName.setPosition(position);
            archivedSameName.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
            archivedSameName.setIsArchived(false);
            if (opt.getColor() != null) {
                archivedSameName.setColor(opt.getColor());
            }
            archivedSameName.setUpdatedAt(LocalDateTime.now());
            optionMapper.updateById(archivedSameName);
            return archivedSameName.getId();
        }

        // 全新插入
        CustomFieldOption newOption = new CustomFieldOption();
        newOption.setCustomFieldId(customFieldId);
        newOption.setValue(opt.getValue());
        newOption.setPosition(position);
        newOption.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
        newOption.setColor(opt.getColor());
        newOption.setIsArchived(false);
        newOption.setCreatedAt(LocalDateTime.now());
        newOption.setUpdatedAt(LocalDateTime.now());
        optionMapper.insert(newOption);
        return newOption.getId();
    }

    /**
     * 检查选项是否被工单值引用
     */
    private boolean isOptionReferenced(Long customFieldId, Long optionId) {
        return valueMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getCustomFieldId, customFieldId)
                        .eq(CustomFieldValue::getValue, String.valueOf(optionId))) > 0;
    }
}
