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
import com.trackflow.customfield.vo.OptionSetStatusVO;
import com.trackflow.customfield.vo.OptionUsageItemVO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自定义字段选项集管理服务 — 处理枚举类型字段的选项 CRUD、排序、归档、用量统计
 * <p>
 * 支持两种选项集作用域：
 * <ul>
 *   <li>全局共享选项集（project_id = NULL）：所有使用该字段的项目共享</li>
 *   <li>项目独立选项集（project_id = 指定值）：仅对该项目生效，Make Independent Copy 后创建</li>
 * </ul>
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
    private final ProjectMapper projMapper;

    /**
     * 判断字段类型是否支持选项集（list、state 和 ownedField 类型都有选项）。
     */
    public static boolean isEnumLikeFormat(String fieldFormat) {
        return "list".equals(fieldFormat) || "state".equals(fieldFormat) || "ownedField".equals(fieldFormat);
    }

    // ========== 项目级选项集管理（Make Independent Copy）==========

    /**
     * 获取字段在指定项目中的选项集状态。
     */
    public OptionSetStatusVO getOptionSetStatus(Long projectId, Long fieldId) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!isEnumLikeFormat(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅列表类型字段支持选项集");
        }

        CustomFieldProject mapping = getFieldProjectMapping(projectId, fieldId);
        boolean isIndependent = mapping != null && Boolean.TRUE.equals(mapping.getHasIndependentOptions());

        OptionSetStatusVO status = new OptionSetStatusVO();
        status.setIsIndependent(isIndependent);
        status.setOptionSetType(isIndependent ? "independent" : "shared");

        if (!isIndependent) {
            // 计算共享该选项集的项目数量
            List<String> sharedProjects = getProjectsSharingOptions(fieldId, field.getIsForAll());
            status.setSharedProjectCount(sharedProjects.size());
            status.setSharedProjectNames(sharedProjects);
            status.setCanMakeIndependent(true);
            status.setCannotMakeIndependentReason(null);
        } else {
            status.setSharedProjectCount(0);
            status.setSharedProjectNames(List.of());
            status.setCanMakeIndependent(false);
            status.setCannotMakeIndependentReason("此项目已使用独立选项集");
        }

        return status;
    }

    /**
     * 创建项目级独立选项副本（Make Independent Copy）。
     * <p>
     * 将全局共享选项集复制到项目级，后续该项目的选项变更不影响其他项目。
     *
     * @param projectId    项目 ID
     * @param fieldId      字段 ID
     * @param emptyOptions 是否创建空选项集（true=不复制现有选项，false=复制）
     * @return 新创建的独立选项列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<CustomFieldOption> makeIndependentCopy(Long projectId, Long fieldId, boolean emptyOptions) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!isEnumLikeFormat(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅列表类型字段支持独立选项集");
        }

        // 获取或创建字段-项目关联
        CustomFieldProject mapping = getOrCreateFieldProjectMapping(projectId, fieldId, field.getIsForAll());

        if (Boolean.TRUE.equals(mapping.getHasIndependentOptions())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "此项目已使用独立选项集，无需重复创建");
        }

        List<CustomFieldOption> createdOptions = new ArrayList<>();

        if (!emptyOptions) {
            // 复制全局选项到项目级
            List<CustomFieldOption> globalOptions = optionMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .eq(CustomFieldOption::getCustomFieldId, fieldId)
                            .isNull(CustomFieldOption::getProjectId)
                            .eq(CustomFieldOption::getIsArchived, false)
                            .orderByAsc(CustomFieldOption::getPosition));

            for (CustomFieldOption src : globalOptions) {
                CustomFieldOption copy = new CustomFieldOption();
                copy.setCustomFieldId(fieldId);
                copy.setProjectId(projectId);
                copy.setValue(src.getValue());
                copy.setPosition(src.getPosition());
                copy.setIsDefault(src.getIsDefault());
                copy.setColor(src.getColor());
                copy.setDescription(src.getDescription());
                copy.setIsArchived(false);
                copy.setCreatedAt(LocalDateTime.now());
                copy.setUpdatedAt(LocalDateTime.now());
                optionMapper.insert(copy);
                createdOptions.add(copy);
            }
            log.info("Made independent copy for field {} in project {}: copied {} options",
                    fieldId, projectId, createdOptions.size());
        } else {
            log.info("Made empty independent option set for field {} in project {}", fieldId, projectId);
        }

        // 标记为独立选项集
        mapping.setHasIndependentOptions(true);
        projectMapper.updateById(mapping);

        return createdOptions;
    }

    /**
     * 恢复为全局共享选项集。
     * 删除项目独立选项，恢复使用全局共享选项集。
     *
     * @param projectId 项目 ID
     * @param fieldId   字段 ID
     * @param confirm   确认删除（因为会丢失独立配置）
     */
    @Transactional(rollbackFor = Exception.class)
    public void revertToShared(Long projectId, Long fieldId, boolean confirm) {
        if (!confirm) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, 
                    "恢复共享选项集将删除当前项目的所有独立选项配置，请设置 confirm=true 确认");
        }

        CustomFieldProject mapping = getFieldProjectMapping(projectId, fieldId);
        if (mapping == null || !Boolean.TRUE.equals(mapping.getHasIndependentOptions())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "此项目未使用独立选项集");
        }

        // 删除项目级选项
        optionMapper.delete(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .eq(CustomFieldOption::getProjectId, projectId));

        // 清除标记
        mapping.setHasIndependentOptions(false);
        projectMapper.updateById(mapping);

        log.info("Reverted field {} in project {} to shared option set", fieldId, projectId);
    }

    // ========== 选项查询（感知项目上下文）==========

    /**
     * 获取项目中有效的选项列表。
     * <ul>
     *   <li>如果项目有独立选项集 → 返回独立选项</li>
     *   <li>否则 → 返回全局共享选项</li>
     * </ul>
     *
     * @param fieldId   字段 ID
     * @param projectId 项目 ID（可选，null 时返回全局选项）
     * @return 有效选项列表（按 position 排序）
     */
    public List<CustomFieldOption> getEffectiveOptions(Long fieldId, Long projectId) {
        if (projectId != null) {
            CustomFieldProject mapping = getFieldProjectMapping(projectId, fieldId);
            if (mapping != null && Boolean.TRUE.equals(mapping.getHasIndependentOptions())) {
                // 使用项目独立选项集
                List<CustomFieldOption> options = optionMapper.selectList(
                        new LambdaQueryWrapper<CustomFieldOption>()
                                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                                .eq(CustomFieldOption::getProjectId, projectId)
                                .orderByAsc(CustomFieldOption::getPosition));
                return filterSupersededArchivedOptions(options);
            }
        }
        // 使用全局共享选项集
        return getGlobalOptions(fieldId);
    }

    /**
     * 获取全局共享选项（project_id IS NULL）
     */
    public List<CustomFieldOption> getGlobalOptions(Long fieldId) {
        List<CustomFieldOption> allOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, fieldId)
                        .isNull(CustomFieldOption::getProjectId)
                        .orderByAsc(CustomFieldOption::getPosition));
        return filterSupersededArchivedOptions(allOptions);
    }

    /**
     * 批量获取多个字段在指定项目中的有效选项列表。
     *
     * @param fieldIds  字段 ID 列表
     * @param projectId 项目 ID
     * @return Map<fieldId, List<CustomFieldOption>>
     */
    public Map<Long, List<CustomFieldOption>> getBatchEffectiveOptions(List<Long> fieldIds, Long projectId) {
        if (fieldIds == null || fieldIds.isEmpty()) return Map.of();

        // 获取哪些字段在该项目中有独立选项集
        List<CustomFieldProject> mappings = projectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .in(CustomFieldProject::getCustomFieldId, fieldIds)
                        .eq(CustomFieldProject::getProjectId, projectId)
                        .eq(CustomFieldProject::getHasIndependentOptions, true));
        Set<Long> independentFieldIds = mappings.stream()
                .map(CustomFieldProject::getCustomFieldId)
                .collect(Collectors.toSet());

        Map<Long, List<CustomFieldOption>> result = new HashMap<>();

        // 查询独立选项集
        if (!independentFieldIds.isEmpty()) {
            List<CustomFieldOption> independentOptions = optionMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .in(CustomFieldOption::getCustomFieldId, independentFieldIds)
                            .eq(CustomFieldOption::getProjectId, projectId)
                            .orderByAsc(CustomFieldOption::getPosition));
            independentOptions.stream()
                    .collect(Collectors.groupingBy(CustomFieldOption::getCustomFieldId))
                    .forEach((fid, opts) -> result.put(fid, filterSupersededArchivedOptions(opts)));
        }

        // 查询共享选项集
        Set<Long> sharedFieldIds = fieldIds.stream()
                .filter(fid -> !independentFieldIds.contains(fid))
                .collect(Collectors.toSet());
        if (!sharedFieldIds.isEmpty()) {
            List<CustomFieldOption> sharedOptions = optionMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldOption>()
                            .in(CustomFieldOption::getCustomFieldId, sharedFieldIds)
                            .isNull(CustomFieldOption::getProjectId)
                            .orderByAsc(CustomFieldOption::getPosition));
            sharedOptions.stream()
                    .collect(Collectors.groupingBy(CustomFieldOption::getCustomFieldId))
                    .forEach((fid, opts) -> result.put(fid, filterSupersededArchivedOptions(opts)));
        }

        return result;
    }

    // ========== 选项管理（需感知项目上下文）==========

    // ========== 选项管理（需感知项目上下文）==========

    /**
     * 重新排序枚举字段的选项值。
     * 仅更新 position 字段，不改变选项内容。
     * 仅在 manual 排序模式下允许手动重排。
     *
     * @param fieldId   字段 ID
     * @param optionIds 按新顺序排列的选项 ID 列表
     * @param projectId 项目 ID（可选，指定时操作项目独立选项）
     */
    public void reorderOptions(Long fieldId, List<Long> optionIds, Long projectId) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!isEnumLikeFormat(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅列表类型字段支持选项排序");
        }

        // 自动排序模式下不允许手动重排
        String sortMode = field.getSortMode() != null ? field.getSortMode() : "manual";
        if (!"manual".equals(sortMode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前排序模式为 " + sortMode + "，不支持手动重排，请先切换为手动排序模式");
        }

        // 验证项目独立选项权限
        if (projectId != null) {
            validateIndependentOptionsAccess(projectId, fieldId, "排序");
        }

        for (int i = 0; i < optionIds.size(); i++) {
            CustomFieldOption option = new CustomFieldOption();
            option.setId(optionIds.get(i));
            option.setPosition(i);
            optionMapper.updateById(option);
        }
    }

    /**
     * 重新排序枚举字段的选项值（全局选项）
     */
    public void reorderOptions(Long fieldId, List<Long> optionIds) {
        reorderOptions(fieldId, optionIds, null);
    }

    /**
     * 设置选项的归档状态。
     * 归档后选项不出现在工单编辑时的下拉列表中，但已有工单的值仍保留。
     *
     * @param fieldId   字段 ID
     * @param optionId  选项 ID
     * @param archived  是否归档
     * @param projectId 项目 ID（可选，指定时操作项目独立选项）
     */
    @Transactional(rollbackFor = Exception.class)
    public void setOptionArchived(Long fieldId, Long optionId, boolean archived, Long projectId) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!isEnumLikeFormat(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅列表类型字段支持选项归档");
        }

        LambdaQueryWrapper<CustomFieldOption> wrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getId, optionId)
                .eq(CustomFieldOption::getCustomFieldId, fieldId);
        if (projectId != null) {
            validateIndependentOptionsAccess(projectId, fieldId, "归档");
            wrapper.eq(CustomFieldOption::getProjectId, projectId);
        } else {
            wrapper.isNull(CustomFieldOption::getProjectId);
        }

        CustomFieldOption option = optionMapper.selectOne(wrapper);
        if (option == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "选项不存在");
        }

        option.setIsArchived(archived);
        if (archived) {
            option.setPosition(Integer.MAX_VALUE);
        }
        option.setUpdatedAt(LocalDateTime.now());
        optionMapper.updateById(option);

        log.info("Custom field option {} {} (fieldId={}, projectId={})", 
                optionId, archived ? "archived" : "unarchived", fieldId, projectId);
    }

    /**
     * 设置选项的归档状态（全局选项）
     */
    @Transactional(rollbackFor = Exception.class)
    public void setOptionArchived(Long fieldId, Long optionId, boolean archived) {
        setOptionArchived(fieldId, optionId, archived, null);
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
        if (!isEnumLikeFormat(def.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅枚举(list)类型字段支持逐选项统计");
        }

        // 统计全局选项使用情况
        List<CustomFieldOption> allOptions = optionMapper.selectList(
                new LambdaQueryWrapper<CustomFieldOption>()
                        .eq(CustomFieldOption::getCustomFieldId, id)
                        .isNull(CustomFieldOption::getProjectId)
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
     * <p>
     * 如果项目有独立选项集，添加到独立选项集；否则添加到全局选项集。
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomFieldOption addOptionInline(Long projectId, Long fieldId, String value, String color, Long ownerUserId) {
        CustomFieldDefinition field = definitionMapper.selectById(fieldId);
        if (field == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "字段不存在");
        }
        if (!isEnumLikeFormat(field.getFieldFormat())) {
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

        // 确定目标选项集（独立 or 共享）
        CustomFieldProject mapping = getFieldProjectMapping(projectId, fieldId);
        Long targetProjectId = (mapping != null && Boolean.TRUE.equals(mapping.getHasIndependentOptions())) 
                ? projectId : null;

        // 检查是否有同名选项
        LambdaQueryWrapper<CustomFieldOption> existWrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                .eq(CustomFieldOption::getValue, value.trim());
        if (targetProjectId != null) {
            existWrapper.eq(CustomFieldOption::getProjectId, targetProjectId);
        } else {
            existWrapper.isNull(CustomFieldOption::getProjectId);
        }
        List<CustomFieldOption> existingOptions = optionMapper.selectList(existWrapper);

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
            int maxPosition = getMaxOptionPosition(fieldId, targetProjectId);
            archivedSameName.setPosition(maxPosition + 1);
            archivedSameName.setIsArchived(false);
            archivedSameName.setIsDefault(false);
            if (color != null) {
                archivedSameName.setColor(color);
            }
            archivedSameName.setOwnerUserId(ownerUserId);
            archivedSameName.setUpdatedAt(LocalDateTime.now());
            optionMapper.updateById(archivedSameName);
            log.info("Inline add option: reactivated archived option {} (value='{}'), fieldId={}, projectId={}, targetScope={}",
                    archivedSameName.getId(), value.trim(), fieldId, projectId, targetProjectId != null ? "independent" : "shared");
            return archivedSameName;
        }

        // 新建选项
        String sortMode = field.getSortMode() != null ? field.getSortMode() : "manual";
        int insertPosition = computeInsertPosition(fieldId, value.trim(), sortMode, targetProjectId);
        CustomFieldOption option = new CustomFieldOption();
        option.setCustomFieldId(fieldId);
        option.setProjectId(targetProjectId);
        option.setValue(value.trim());
        option.setPosition(insertPosition);
        option.setIsDefault(false);
        option.setColor(color);
        option.setOwnerUserId(ownerUserId);
        option.setIsArchived(false);
        option.setCreatedAt(LocalDateTime.now());
        option.setUpdatedAt(LocalDateTime.now());
        try {
            optionMapper.insert(option);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // 并发创建同名选项 → 数据库唯一约束拦截，返回已有选项
            log.warn("并发插入选项触发唯一约束: fieldId={}, value='{}', 尝试返回已有选项", fieldId, value.trim());
            LambdaQueryWrapper<CustomFieldOption> retryWrapper = new LambdaQueryWrapper<CustomFieldOption>()
                    .eq(CustomFieldOption::getCustomFieldId, fieldId)
                    .eq(CustomFieldOption::getValue, value.trim())
                    .eq(CustomFieldOption::getIsArchived, false);
            if (targetProjectId != null) {
                retryWrapper.eq(CustomFieldOption::getProjectId, targetProjectId);
            } else {
                retryWrapper.isNull(CustomFieldOption::getProjectId);
            }
            retryWrapper.last("LIMIT 1");
            CustomFieldOption existing = optionMapper.selectOne(retryWrapper);
            if (existing != null) {
                return existing;
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "选项值\"" + value.trim() + "\"已存在");
        }
        log.info("Inline add option: created new option {} (value='{}'), fieldId={}, projectId={}, position={}, targetScope={}",
                option.getId(), value.trim(), fieldId, projectId, insertPosition, targetProjectId != null ? "independent" : "shared");
        return option;
    }

    /**
     * 获取字段选项列表（含被替代的归档过滤）
     */
    public List<CustomFieldOption> getOptions(Long fieldId) {
        List<CustomFieldOption> allOptions = optionMapper.selectList(new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                .isNull(CustomFieldOption::getProjectId)
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
                        .isNull(CustomFieldOption::getProjectId)
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
     *
     * @param customFieldId 字段 ID
     * @param dtoOptions    选项列表
     * @param projectId     项目 ID（可选，指定时操作项目独立选项集）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateListOptions(Long customFieldId, List<UpdateCustomFieldDTO.OptionItem> dtoOptions, Long projectId) {
        // 0. DTO 列表按 value 去重（保留首次出现的，忽略后续重复项）
        List<UpdateCustomFieldDTO.OptionItem> dedupedOptions = deduplicateOptionItems(dtoOptions);

        // 1. 加载当前数据库中目标范围的选项
        LambdaQueryWrapper<CustomFieldOption> loadWrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, customFieldId);
        if (projectId != null) {
            loadWrapper.eq(CustomFieldOption::getProjectId, projectId);
        } else {
            loadWrapper.isNull(CustomFieldOption::getProjectId);
        }
        List<CustomFieldOption> existingOptions = optionMapper.selectList(loadWrapper);
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
                // 更新 isResolved（state 类型字段用）
                if (opt.getIsResolved() != null) {
                    existing.setIsResolved(opt.getIsResolved());
                }
                // 更新 ownerUserId（ownedField 类型字段用，允许清空）
                existing.setOwnerUserId(opt.getOwnerUserId());
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
                Long newId = insertOrReactivateOption(customFieldId, opt, i, projectId);
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
        cleanupSupersededArchivedOptions(customFieldId, activeValues, projectId);
    }

    /**
     * 更新列表类型字段的全局选项（兼容旧调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateListOptions(Long customFieldId, List<UpdateCustomFieldDTO.OptionItem> dtoOptions) {
        updateListOptions(customFieldId, dtoOptions, null);
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
     * 获取字段在项目中的关联记录
     */
    private CustomFieldProject getFieldProjectMapping(Long projectId, Long fieldId) {
        return projectMapper.selectOne(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getCustomFieldId, fieldId)
                        .eq(CustomFieldProject::getProjectId, projectId));
    }

    /**
     * 获取或创建字段-项目关联记录
     */
    private CustomFieldProject getOrCreateFieldProjectMapping(Long projectId, Long fieldId, Boolean isForAll) {
        CustomFieldProject mapping = getFieldProjectMapping(projectId, fieldId);
        if (mapping == null) {
            mapping = new CustomFieldProject();
            mapping.setCustomFieldId(fieldId);
            mapping.setProjectId(projectId);
            mapping.setPosition(0);
            mapping.setHasIndependentOptions(false);
            mapping.setIsExcluded(false);
            projectMapper.insert(mapping);
        }
        return mapping;
    }

    /**
     * 获取共享该字段选项集的项目名称列表
     */
    private List<String> getProjectsSharingOptions(Long fieldId, Boolean isForAll) {
        if (Boolean.TRUE.equals(isForAll)) {
            // 全局字段：所有项目都使用（除了被排除的和有独立选项的）
            List<Long> excludedProjectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getCustomFieldId, fieldId)
                            .and(w -> w.eq(CustomFieldProject::getIsExcluded, true)
                                    .or().eq(CustomFieldProject::getHasIndependentOptions, true)))
                    .stream()
                    .map(CustomFieldProject::getProjectId)
                    .toList();
            
            LambdaQueryWrapper<Project> projWrapper = new LambdaQueryWrapper<Project>();
            if (!excludedProjectIds.isEmpty()) {
                projWrapper.notIn(Project::getId, excludedProjectIds);
            }
            return projMapper.selectList(projWrapper).stream()
                    .map(Project::getName)
                    .limit(10)  // 限制显示数量
                    .toList();
        } else {
            // 非全局字段：只有显式关联且没有独立选项的项目
            List<Long> sharedProjectIds = projectMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getCustomFieldId, fieldId)
                            .eq(CustomFieldProject::getIsExcluded, false)
                            .and(w -> w.isNull(CustomFieldProject::getHasIndependentOptions)
                                    .or().eq(CustomFieldProject::getHasIndependentOptions, false)))
                    .stream()
                    .map(CustomFieldProject::getProjectId)
                    .toList();
            
            if (sharedProjectIds.isEmpty()) {
                return List.of();
            }
            return projMapper.selectBatchIds(sharedProjectIds).stream()
                    .map(Project::getName)
                    .limit(10)
                    .toList();
        }
    }

    /**
     * 验证项目独立选项集访问权限
     */
    private void validateIndependentOptionsAccess(Long projectId, Long fieldId, String operation) {
        CustomFieldProject mapping = getFieldProjectMapping(projectId, fieldId);
        if (mapping == null || !Boolean.TRUE.equals(mapping.getHasIndependentOptions())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, 
                    "此项目未使用独立选项集，" + operation + "操作应针对全局选项");
        }
    }

    /**
     * 获取字段选项列表中的最大 position 值
     */
    int getMaxOptionPosition(Long fieldId, Long projectId) {
        LambdaQueryWrapper<CustomFieldOption> wrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                .eq(CustomFieldOption::getIsArchived, false)
                .orderByDesc(CustomFieldOption::getPosition)
                .last("LIMIT 1");
        if (projectId != null) {
            wrapper.eq(CustomFieldOption::getProjectId, projectId);
        } else {
            wrapper.isNull(CustomFieldOption::getProjectId);
        }
        List<CustomFieldOption> allOptions = optionMapper.selectList(wrapper);
        return allOptions.isEmpty() ? -1 : allOptions.get(0).getPosition();
    }

    /**
     * 获取全局选项的最大 position（兼容旧调用）
     */
    int getMaxOptionPosition(Long fieldId) {
        return getMaxOptionPosition(fieldId, null);
    }

    /**
     * 过滤被同名活跃选项替代的归档残留条目。
     */
    List<CustomFieldOption> filterSupersededArchivedOptions(List<CustomFieldOption> options) {
        Set<String> activeValues = options.stream()
                .filter(o -> !Boolean.TRUE.equals(o.getIsArchived()))
                .map(CustomFieldOption::getValue)
                .collect(Collectors.toSet());

        return options.stream()
                .filter(o -> !Boolean.TRUE.equals(o.getIsArchived()) || !activeValues.contains(o.getValue()))
                .toList();
    }

    /**
     * 清理被活跃选项替代的旧归档条目（物理删除）
     */
    private void cleanupSupersededArchivedOptions(Long customFieldId, Set<String> activeValues, Long projectId) {
        LambdaQueryWrapper<CustomFieldOption> wrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, customFieldId)
                .eq(CustomFieldOption::getIsArchived, true);
        if (projectId != null) {
            wrapper.eq(CustomFieldOption::getProjectId, projectId);
        } else {
            wrapper.isNull(CustomFieldOption::getProjectId);
        }
        List<CustomFieldOption> archivedOptions = optionMapper.selectList(wrapper);

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
    private Long insertOrReactivateOption(Long customFieldId, UpdateCustomFieldDTO.OptionItem opt, int position, Long projectId) {
        // 先检查是否已存在同名的活跃选项（防止重复创建）
        LambdaQueryWrapper<CustomFieldOption> activeWrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, customFieldId)
                .eq(CustomFieldOption::getValue, opt.getValue())
                .eq(CustomFieldOption::getIsArchived, false);
        if (projectId != null) {
            activeWrapper.eq(CustomFieldOption::getProjectId, projectId);
        } else {
            activeWrapper.isNull(CustomFieldOption::getProjectId);
        }
        activeWrapper.last("LIMIT 1");
        CustomFieldOption activeSameName = optionMapper.selectOne(activeWrapper);

        if (activeSameName != null) {
            // 已存在同名活跃选项 → 更新位置和颜色，返回已有 ID（幂等）
            activeSameName.setPosition(position);
            activeSameName.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
            if (opt.getColor() != null) {
                activeSameName.setColor(opt.getColor());
            }
            activeSameName.setDescription(opt.getDescription());
            if (opt.getIsResolved() != null) {
                activeSameName.setIsResolved(opt.getIsResolved());
            }
            activeSameName.setOwnerUserId(opt.getOwnerUserId());
            activeSameName.setUpdatedAt(LocalDateTime.now());
            optionMapper.updateById(activeSameName);
            return activeSameName.getId();
        }

        // 检查是否有同 value 的归档选项可以复用
        LambdaQueryWrapper<CustomFieldOption> archivedWrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, customFieldId)
                .eq(CustomFieldOption::getValue, opt.getValue())
                .eq(CustomFieldOption::getIsArchived, true);
        if (projectId != null) {
            archivedWrapper.eq(CustomFieldOption::getProjectId, projectId);
        } else {
            archivedWrapper.isNull(CustomFieldOption::getProjectId);
        }
        archivedWrapper.last("LIMIT 1");
        CustomFieldOption archivedSameName = optionMapper.selectOne(archivedWrapper);

        if (archivedSameName != null) {
            archivedSameName.setPosition(position);
            archivedSameName.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
            archivedSameName.setIsArchived(false);
            if (opt.getColor() != null) {
                archivedSameName.setColor(opt.getColor());
            }
            archivedSameName.setDescription(opt.getDescription());
            if (opt.getIsResolved() != null) {
                archivedSameName.setIsResolved(opt.getIsResolved());
            }
            archivedSameName.setOwnerUserId(opt.getOwnerUserId());
            archivedSameName.setUpdatedAt(LocalDateTime.now());
            optionMapper.updateById(archivedSameName);
            return archivedSameName.getId();
        }

        // 全新插入
        CustomFieldOption newOption = new CustomFieldOption();
        newOption.setCustomFieldId(customFieldId);
        newOption.setProjectId(projectId);
        newOption.setValue(opt.getValue());
        newOption.setPosition(position);
        newOption.setIsDefault(Boolean.TRUE.equals(opt.getIsDefault()));
        newOption.setColor(opt.getColor());
        newOption.setDescription(opt.getDescription());
        newOption.setIsResolved(Boolean.TRUE.equals(opt.getIsResolved()));
        newOption.setOwnerUserId(opt.getOwnerUserId());
        newOption.setIsArchived(false);
        newOption.setCreatedAt(LocalDateTime.now());
        newOption.setUpdatedAt(LocalDateTime.now());
        try {
            optionMapper.insert(newOption);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // 并发插入触发唯一约束 → 返回已有选项 ID
            log.warn("insertOrReactivateOption 并发插入触发唯一约束: fieldId={}, value='{}'", customFieldId, opt.getValue());
            LambdaQueryWrapper<CustomFieldOption> retryWrapper = new LambdaQueryWrapper<CustomFieldOption>()
                    .eq(CustomFieldOption::getCustomFieldId, customFieldId)
                    .eq(CustomFieldOption::getValue, opt.getValue())
                    .eq(CustomFieldOption::getIsArchived, false);
            if (projectId != null) {
                retryWrapper.eq(CustomFieldOption::getProjectId, projectId);
            } else {
                retryWrapper.isNull(CustomFieldOption::getProjectId);
            }
            retryWrapper.last("LIMIT 1");
            CustomFieldOption existing = optionMapper.selectOne(retryWrapper);
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
        if (!isEnumLikeFormat(field.getFieldFormat())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅列表类型字段支持排序模式");
        }

        field.setSortMode(sortMode);
        definitionMapper.updateById(field);

        // 如果不是手动排序，重新计算所有选项的 position（包括全局和所有独立选项集）
        if (!"manual".equals(sortMode)) {
            // 重新计算全局选项
            recomputePositions(fieldId, sortMode, null);
            
            // 重新计算所有独立选项集
            List<CustomFieldProject> independentMappings = projectMapper.selectList(
                    new LambdaQueryWrapper<CustomFieldProject>()
                            .eq(CustomFieldProject::getCustomFieldId, fieldId)
                            .eq(CustomFieldProject::getHasIndependentOptions, true));
            for (CustomFieldProject mapping : independentMappings) {
                recomputePositions(fieldId, sortMode, mapping.getProjectId());
            }
        }

        log.info("Custom field {} sort mode changed to '{}'", fieldId, sortMode);
    }

    /**
     * 按当前 sort_mode 重新计算指定范围内所有非归档选项的 position 值。
     *
     * @param fieldId   字段 ID
     * @param sortMode  排序模式
     * @param projectId 项目 ID（null=全局选项）
     */
    public void recomputePositions(Long fieldId, String sortMode, Long projectId) {
        LambdaQueryWrapper<CustomFieldOption> wrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                .eq(CustomFieldOption::getIsArchived, false);
        if (projectId != null) {
            wrapper.eq(CustomFieldOption::getProjectId, projectId);
        } else {
            wrapper.isNull(CustomFieldOption::getProjectId);
        }
        List<CustomFieldOption> options = optionMapper.selectList(wrapper);

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
     * 按当前 sort_mode 重新计算全局选项的 position 值（兼容旧调用）
     */
    public void recomputePositions(Long fieldId, String sortMode) {
        recomputePositions(fieldId, sortMode, null);
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
     *
     * @param fieldId   字段 ID
     * @param value     选项值
     * @param sortMode  排序模式
     * @param projectId 项目 ID（null=全局选项）
     */
    public int computeInsertPosition(Long fieldId, String value, String sortMode, Long projectId) {
        if ("manual".equals(sortMode)) {
            return getMaxOptionPosition(fieldId, projectId) + 1;
        }

        // 获取所有非归档选项
        LambdaQueryWrapper<CustomFieldOption> wrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                .eq(CustomFieldOption::getIsArchived, false)
                .orderByAsc(CustomFieldOption::getPosition);
        if (projectId != null) {
            wrapper.eq(CustomFieldOption::getProjectId, projectId);
        } else {
            wrapper.isNull(CustomFieldOption::getProjectId);
        }
        List<CustomFieldOption> options = optionMapper.selectList(wrapper);

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
                shiftPositionsFrom(fieldId, i, projectId);
                return i;
            }
        }
        // 新值排在最后
        return options.isEmpty() ? 0 : options.get(options.size() - 1).getPosition() + 1;
    }

    /**
     * 计算新全局选项的插入位置（兼容旧调用）
     */
    public int computeInsertPosition(Long fieldId, String value, String sortMode) {
        return computeInsertPosition(fieldId, value, sortMode, null);
    }

    /**
     * 将指定 position 及之后的所有选项的 position 值 +1。
     */
    private void shiftPositionsFrom(Long fieldId, int fromPosition, Long projectId) {
        LambdaQueryWrapper<CustomFieldOption> wrapper = new LambdaQueryWrapper<CustomFieldOption>()
                .eq(CustomFieldOption::getCustomFieldId, fieldId)
                .ge(CustomFieldOption::getPosition, fromPosition)
                .eq(CustomFieldOption::getIsArchived, false)
                .orderByDesc(CustomFieldOption::getPosition);
        if (projectId != null) {
            wrapper.eq(CustomFieldOption::getProjectId, projectId);
        } else {
            wrapper.isNull(CustomFieldOption::getProjectId);
        }
        List<CustomFieldOption> toShift = optionMapper.selectList(wrapper);

        for (CustomFieldOption opt : toShift) {
            opt.setPosition(opt.getPosition() + 1);
            optionMapper.updateById(opt);
        }
    }
}
