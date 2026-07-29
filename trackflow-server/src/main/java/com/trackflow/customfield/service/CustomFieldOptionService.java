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
     * 仅在 manual 排序模式下允许手动重排。
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

        // 自动排序模式下不允许手动重排
        String sortMode = field.getSortMode() != null ? field.getSortMode() : "manual";
        if (!"manual".equals(sortMode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前排序模式为 " + sortMode + "，不支持手动重排，请先切换为手动排序模式");
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
        String sortMode = field.getSortMode() != null ? field.getSortMode() : "manual";
        int insertPosition = computeInsertPosition(fieldId, value.trim(), sortMode);
        CustomFieldOption option = new CustomFieldOption();
        option.setCustomFieldId(fieldId);
        option.setValue(value.trim());
        option.setPosition(insertPosition);
        option.setIsDefault(false);
        option.setColor(color);
        option.setIsArchived(false);
        option.setCreatedAt(LocalDateTime.now());
        option.setUpdatedAt(LocalDateTime.now());
        try {
            optionMapper.insert(option);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // 并发创建同名选项 → 数据库唯一约束拦截，返回已有选项
            log.warn("并发插入选项触发唯一约束: fieldId={}, value='{}', 尝试返回已有选项", fieldId, value.trim());
            CustomFieldOption existing = optionMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .eq(CustomFieldOption::getCustomFieldId, fieldId)
                            .eq(CustomFieldOption::getValue, value.trim())
                            .eq(CustomFieldOption::getIsArchived, false)
                            .last("LIMIT 1"));
            if (existing != null) {
                return existing;
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "选项值\"" + value.trim() + "\"已存在");
        }
        log.info("Inline add option: created new option {} (value='{}'), fieldId={}, projectId={}, position={}",
                option.getId(), value.trim(), fieldId, projectId, insertPosition);
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
        // 0. DTO 列表按 value 去重（保留首次出现的，忽略后续重复项）
        List<UpdateCustomFieldDTO.OptionItem> dedupedOptions = deduplicateOptionItems(dtoOptions);

        // 1. 加载当前数据库中所有选项
        List<CustomFieldOption> existingOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, customFieldId));
        Map<Long, CustomFieldOption> existingMap = existingOptions.stream()
                .collect(Collectors.toMap(CustomFieldOption::getId, o -> o));

        Set<Long> dtoIdsSeen = new HashSet<>();
        Set<String> activeValues = new HashSet<>();

        // 2. 遍历 DTO：更新已有 / 新增
        for (int i = 0; i < dedupedOptions.size(); i++) {
            UpdateCustomFieldDTO.OptionItem opt = dedupedOptions.get(i);
            if (opt.getId() != null && existingMap.containsKey(opt.getId())) {
                // UPDATE
                CustomFieldOption existing = existingMap.get(opt.getId());
                // 检查值是否与已处理的活跃选项冲突（同一批次内的去重保护）
                if (activeValues.contains(opt.getValue()) && !opt.getValue().equals(existing.getValue())) {
                    // 值已被此批次中另一个选项占用 → 跳过更新值，保留原值
                    log.warn("批量更新选项时检测到值冲突: fieldId={}, optionId={}, conflictValue='{}', 保留原值'{}'",
                            customFieldId, opt.getId(), opt.getValue(), existing.getValue());
                } else {
                    existing.setValue(opt.getValue());
                }
                existing.setPosition(i);
                existing.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
                if (opt.getColor() != null) {
                    existing.setColor(opt.getColor());
                }
                // 更新描述字段（允许清空）
                existing.setDescription(opt.getDescription());
                // 如果之前是归档状态，恢复为活跃
                if (Boolean.TRUE.equals(existing.getIsArchived())) {
                    existing.setIsArchived(false);
                }
                existing.setUpdatedAt(LocalDateTime.now());
                optionMapper.updateById(existing);
                dtoIdsSeen.add(opt.getId());
                activeValues.add(existing.getValue());
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
            copy.setDescription(src.getDescription());
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
     * 插入新选项或恢复同名归档选项。
     * 如果已存在同名活跃选项，返回其 ID（幂等行为，与 YouTrack 一致）。
     */
    private Long insertOrReactivateOption(Long customFieldId, UpdateCustomFieldDTO.OptionItem opt, int position) {
        // 先检查是否已存在同名的活跃选项（防止重复创建）
        CustomFieldOption activeSameName = optionMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, customFieldId)
                        .eq(CustomFieldOption::getValue, opt.getValue())
                        .eq(CustomFieldOption::getIsArchived, false)
                        .last("LIMIT 1"));

        if (activeSameName != null) {
            // 已存在同名活跃选项 → 更新位置和颜色，返回已有 ID（幂等）
            activeSameName.setPosition(position);
            activeSameName.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
            if (opt.getColor() != null) {
                activeSameName.setColor(opt.getColor());
            }
            activeSameName.setDescription(opt.getDescription());
            activeSameName.setUpdatedAt(LocalDateTime.now());
            optionMapper.updateById(activeSameName);
            return activeSameName.getId();
        }

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
            archivedSameName.setDescription(opt.getDescription());
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
        newOption.setDescription(opt.getDescription());
        newOption.setIsArchived(false);
        newOption.setCreatedAt(LocalDateTime.now());
        newOption.setUpdatedAt(LocalDateTime.now());
        try {
            optionMapper.insert(newOption);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // 并发插入触发唯一约束 → 返回已有选项 ID
            log.warn("insertOrReactivateOption 并发插入触发唯一约束: fieldId={}, value='{}'", customFieldId, opt.getValue());
            CustomFieldOption existing = optionMapper.selectOne(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .eq(CustomFieldOption::getCustomFieldId, customFieldId)
                            .eq(CustomFieldOption::getValue, opt.getValue())
                            .eq(CustomFieldOption::getIsArchived, false)
                            .last("LIMIT 1"));
            if (existing != null) {
                return existing.getId();
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "选项值\"" + opt.getValue() + "\"已存在");
        }
        return newOption.getId();
    }

    /**
     * 对选项列表按 value 去重（保留首次出现的，忽略后续重复项）。
     * 带 id 的选项优先保留（它们是已存在的选项更新），无 id 的同名选项被丢弃。
     */
    private List<UpdateCustomFieldDTO.OptionItem> deduplicateOptionItems(List<UpdateCustomFieldDTO.OptionItem> options) {
        if (options == null || options.isEmpty()) return List.of();
        Set<String> seenValues = new LinkedHashSet<>();
        List<UpdateCustomFieldDTO.OptionItem> result = new ArrayList<>();
        for (UpdateCustomFieldDTO.OptionItem opt : options) {
            String value = opt.getValue() != null ? opt.getValue().trim() : "";
            if (value.isEmpty()) continue;
            if (!seenValues.contains(value)) {
                seenValues.add(value);
                result.add(opt);
            } else {
                log.debug("选项去重: 跳过重复值 '{}'", value);
            }
        }
        return result;
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

    // ========== 排序模式管理 ==========

    /**
     * 设置字段选项的排序模式并重新计算所有选项的 position。
     * 切换到自动排序模式时，按照指定规则重新排列所有非归档选项的 position。
     *
     * @param fieldId  字段 ID
     * @param sortMode 排序模式（manual / name_asc / name_desc / name_ci_asc / name_ci_desc）
     */
    @Transactional(rollbackFor = Exception.class)
    public void setSortMode(Long fieldId, String sortMode) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!"list".equals(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅列表类型字段支持排序模式");
        }

        field.setSortMode(sortMode);
        definitionMapper.updateById(field);

        // 如果不是手动排序，重新计算所有选项的 position
        if (!"manual".equals(sortMode)) {
            recomputePositions(fieldId, sortMode);
        }

        log.info("Custom field {} sort mode changed to '{}'", fieldId, sortMode);
    }

    /**
     * 按当前 sort_mode 重新计算所有非归档选项的 position 值。
     */
    public void recomputePositions(Long fieldId, String sortMode) {
        List<CustomFieldOption> options = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .eq(CustomFieldOption::getIsArchived, false));

        // 根据排序模式排序
        sortOptionsByMode(options, sortMode);

        // 更新 position
        for (int i = 0; i < options.size(); i++) {
            CustomFieldOption option = options.get(i);
            if (option.getPosition() != i) {
                option.setPosition(i);
                optionMapper.updateById(option);
            }
        }
    }

    /**
     * 根据排序模式对选项列表进行排序。
     */
    private void sortOptionsByMode(List<CustomFieldOption> options, String sortMode) {
        switch (sortMode) {
            case "name_asc":
                options.sort((a, b) -> a.getValue().compareTo(b.getValue()));
                break;
            case "name_desc":
                options.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                break;
            case "name_ci_asc":
                options.sort((a, b) -> a.getValue().compareToIgnoreCase(b.getValue()));
                break;
            case "name_ci_desc":
                options.sort((a, b) -> b.getValue().compareToIgnoreCase(a.getValue()));
                break;
            default:
                // manual 模式不排序
                break;
        }
    }

    /**
     * 计算新选项在自动排序模式下应该插入的 position。
     * 如果是手动模式，返回 maxPosition + 1（追加到末尾）。
     */
    public int computeInsertPosition(Long fieldId, String value, String sortMode) {
        if ("manual".equals(sortMode)) {
            return getMaxOptionPosition(fieldId) + 1;
        }

        // 获取所有非归档选项
        List<CustomFieldOption> options = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .eq(CustomFieldOption::getIsArchived, false)
                        .orderByAsc(CustomFieldOption::getPosition));

        // 找到正确的插入位置
        for (int i = 0; i < options.size(); i++) {
            int cmp;
            switch (sortMode) {
                case "name_asc":
                    cmp = value.compareTo(options.get(i).getValue());
                    break;
                case "name_desc":
                    cmp = options.get(i).getValue().compareTo(value);
                    break;
                case "name_ci_asc":
                    cmp = value.compareToIgnoreCase(options.get(i).getValue());
                    break;
                case "name_ci_desc":
                    cmp = options.get(i).getValue().compareToIgnoreCase(value);
                    break;
                default:
                    cmp = 1; // 追加到末尾
                    break;
            }
            if (cmp <= 0) {
                // 新值应该在当前位置之前，需要将后续选项的 position 全部 +1
                shiftPositionsFrom(fieldId, i);
                return i;
            }
        }
        // 新值排在最后
        return options.isEmpty() ? 0 : options.get(options.size() - 1).getPosition() + 1;
    }

    /**
     * 将指定 position 及之后的所有选项的 position 值 +1。
     */
    private void shiftPositionsFrom(Long fieldId, int fromPosition) {
        List<CustomFieldOption> toShift = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .ge(CustomFieldOption::getPosition, fromPosition)
                        .eq(CustomFieldOption::getIsArchived, false)
                        .orderByDesc(CustomFieldOption::getPosition));

        for (CustomFieldOption opt : toShift) {
            opt.setPosition(opt.getPosition() + 1);
            optionMapper.updateById(opt);
        }
    }
}
