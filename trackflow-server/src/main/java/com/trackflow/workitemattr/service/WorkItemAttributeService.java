package com.trackflow.workitemattr.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.workitemattr.BuiltinAttributeCode;
import com.trackflow.workitemattr.dto.CreateWorkItemAttributeDTO;
import com.trackflow.workitemattr.dto.ManageAttributeProjectsDTO;
import com.trackflow.workitemattr.dto.UpdateWorkItemAttributeDTO;
import com.trackflow.workitemattr.entity.TimeEntryAttributeValue;
import com.trackflow.workitemattr.entity.WorkItemAttribute;
import com.trackflow.workitemattr.entity.WorkItemAttributeProject;
import com.trackflow.workitemattr.entity.WorkItemAttributeValue;
import com.trackflow.workitemattr.mapper.TimeEntryAttributeValueMapper;
import com.trackflow.workitemattr.mapper.WorkItemAttributeMapper;
import com.trackflow.workitemattr.mapper.WorkItemAttributeProjectMapper;
import com.trackflow.workitemattr.mapper.WorkItemAttributeValueMapper;
import com.trackflow.workitemattr.vo.AttributeProjectVO;
import com.trackflow.workitemattr.vo.AttributeValueVO;
import com.trackflow.workitemattr.vo.WorkItemAttributeVO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工作项属性管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemAttributeService {

    private final WorkItemAttributeMapper attributeMapper;
    private final WorkItemAttributeValueMapper valueMapper;
    private final WorkItemAttributeProjectMapper projectMapper;
    private final TimeEntryAttributeValueMapper entryValueMapper;
    private final ProjectMapper projMapper;

    /**
     * 内建属性 code → id 缓存（应用级缓存，应用启动后首次查询时填充）
     */
    private final ConcurrentHashMap<String, Long> builtinAttributeIdCache = new ConcurrentHashMap<>();

    /**
     * 根据系统代码获取内建属性的 ID。
     * 使用应用级缓存，首次调用时从数据库查询并缓存。
     *
     * @param code 内建属性代码（如 {@link BuiltinAttributeCode#WORK_TYPE}）
     * @return 属性 ID
     * @throws BusinessException 如果指定 code 的属性不存在
     */
    public Long getBuiltinAttributeId(String code) {
        return builtinAttributeIdCache.computeIfAbsent(code, c -> {
            WorkItemAttribute attr = attributeMapper.selectOne(
                    new QueryWrapper<WorkItemAttribute>()
                            .eq("code", c)
                            .eq("is_builtin", true));
            if (attr == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "内建属性未找到: code=" + c);
            }
            return attr.getId();
        });
    }

    /**
     * 获取 Work Type 内建属性的 ID（快捷方法）
     */
    public Long getWorkTypeAttributeId() {
        return getBuiltinAttributeId(BuiltinAttributeCode.WORK_TYPE);
    }

    /**
     * 列出所有工作项属性（含值列表和项目分配）
     */
    public List<WorkItemAttributeVO> listAll() {
        List<WorkItemAttribute> attrs = attributeMapper.selectList(
                new QueryWrapper<WorkItemAttribute>().orderByAsc("position", "id"));

        if (attrs.isEmpty()) return List.of();

        List<Long> attrIds = attrs.stream().map(WorkItemAttribute::getId).toList();

        // 批量查询值
        Map<Long, List<WorkItemAttributeValue>> valuesMap = valueMapper.selectList(
                new QueryWrapper<WorkItemAttributeValue>()
                        .in("attribute_id", attrIds)
                        .orderByAsc("position", "id")
        ).stream().collect(Collectors.groupingBy(WorkItemAttributeValue::getAttributeId));

        // 批量查询项目分配
        Map<Long, List<Long>> projectsMap = projectMapper.selectList(
                new QueryWrapper<WorkItemAttributeProject>().in("attribute_id", attrIds)
        ).stream().collect(Collectors.groupingBy(
                WorkItemAttributeProject::getAttributeId,
                Collectors.mapping(WorkItemAttributeProject::getProjectId, Collectors.toList())
        ));

        // 批量查询关联的项目信息
        Set<Long> allProjectIds = projectsMap.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, Project> projectInfoMap = new HashMap<>();
        if (!allProjectIds.isEmpty()) {
            List<Project> projects = projMapper.selectBatchIds(allProjectIds);
            projects.forEach(p -> projectInfoMap.put(p.getId(), p));
        }

        // 批量统计使用量
        Map<Long, Integer> usageMap = new HashMap<>();
        for (Long attrId : attrIds) {
            Long count = entryValueMapper.selectCount(
                    new QueryWrapper<TimeEntryAttributeValue>().eq("attribute_id", attrId));
            usageMap.put(attrId, count.intValue());
        }

        return attrs.stream().map(attr -> {
            WorkItemAttributeVO vo = new WorkItemAttributeVO();
            vo.setId(String.valueOf(attr.getId()));
            vo.setName(attr.getName());
            vo.setIsBuiltin(attr.getIsBuiltin());
            vo.setPosition(attr.getPosition());
            if (attr.getCreatedAt() != null) vo.setCreatedAt(attr.getCreatedAt().toString());
            if (attr.getUpdatedAt() != null) vo.setUpdatedAt(attr.getUpdatedAt().toString());

            // 值列表
            List<WorkItemAttributeValue> values = valuesMap.getOrDefault(attr.getId(), List.of());
            vo.setValues(values.stream().map(this::toValueVO).toList());

            // 项目分配
            List<Long> pids = projectsMap.getOrDefault(attr.getId(), List.of());
            vo.setProjectIds(pids.stream().map(String::valueOf).toList());
            vo.setProjects(pids.stream().map(pid -> toProjectVO(pid, projectInfoMap)).toList());

            // 使用量
            vo.setUsageCount(usageMap.getOrDefault(attr.getId(), 0));

            return vo;
        }).toList();
    }

    /**
     * 获取单个属性详情
     */
    public WorkItemAttributeVO getById(Long id) {
        WorkItemAttribute attr = attributeMapper.selectById(id);
        if (attr == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作项属性不存在");
        }

        WorkItemAttributeVO vo = new WorkItemAttributeVO();
        vo.setId(String.valueOf(attr.getId()));
        vo.setName(attr.getName());
        vo.setIsBuiltin(attr.getIsBuiltin());
        vo.setPosition(attr.getPosition());
        if (attr.getCreatedAt() != null) vo.setCreatedAt(attr.getCreatedAt().toString());
        if (attr.getUpdatedAt() != null) vo.setUpdatedAt(attr.getUpdatedAt().toString());

        // 值列表
        List<WorkItemAttributeValue> values = valueMapper.selectList(
                new QueryWrapper<WorkItemAttributeValue>()
                        .eq("attribute_id", id)
                        .orderByAsc("position", "id"));
        vo.setValues(values.stream().map(this::toValueVO).toList());

        // 项目分配
        List<WorkItemAttributeProject> projects = projectMapper.selectList(
                new QueryWrapper<WorkItemAttributeProject>().eq("attribute_id", id));
        List<Long> pids = projects.stream().map(WorkItemAttributeProject::getProjectId).toList();
        vo.setProjectIds(pids.stream().map(String::valueOf).toList());

        // 获取项目详细信息
        Map<Long, Project> projectInfoMap = new HashMap<>();
        if (!pids.isEmpty()) {
            List<Project> projList = projMapper.selectBatchIds(pids);
            projList.forEach(p -> projectInfoMap.put(p.getId(), p));
        }
        vo.setProjects(pids.stream().map(pid -> toProjectVO(pid, projectInfoMap)).toList());

        // 使用量
        Long usage = entryValueMapper.selectCount(
                new QueryWrapper<TimeEntryAttributeValue>().eq("attribute_id", id));
        vo.setUsageCount(usage.intValue());

        return vo;
    }

    /**
     * 创建工作项属性
     */
    @Transactional
    public WorkItemAttributeVO create(CreateWorkItemAttributeDTO dto) {
        // 检查名称唯一性
        Long existCount = attributeMapper.selectCount(
                new QueryWrapper<WorkItemAttribute>().apply("LOWER(name) = LOWER({0})", dto.getName()));
        if (existCount > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "属性名称已存在: " + dto.getName());
        }

        // 获取最大位置
        WorkItemAttribute maxPos = attributeMapper.selectOne(
                new QueryWrapper<WorkItemAttribute>().orderByDesc("position").last("LIMIT 1"));
        int nextPos = (maxPos != null) ? maxPos.getPosition() + 1 : 0;

        WorkItemAttribute attr = new WorkItemAttribute();
        attr.setName(dto.getName().trim());
        attr.setIsBuiltin(false);
        attr.setPosition(nextPos);
        attr.setCreatedAt(LocalDateTime.now());
        attr.setUpdatedAt(LocalDateTime.now());
        attributeMapper.insert(attr);

        // 创建初始值列表
        if (dto.getValues() != null && !dto.getValues().isEmpty()) {
            for (int i = 0; i < dto.getValues().size(); i++) {
                CreateWorkItemAttributeDTO.ValueItem item = dto.getValues().get(i);
                WorkItemAttributeValue value = new WorkItemAttributeValue();
                value.setAttributeId(attr.getId());
                value.setName(item.getName().trim());
                value.setColor(item.getColor());
                value.setPosition(i);
                value.setCreatedAt(LocalDateTime.now());
                valueMapper.insert(value);
            }
        }

        log.info("创建工作项属性: id={}, name={}", attr.getId(), attr.getName());
        return getById(attr.getId());
    }

    /**
     * 更新工作项属性（名称和值列表）
     */
    @Transactional
    public WorkItemAttributeVO update(Long id, UpdateWorkItemAttributeDTO dto) {
        WorkItemAttribute attr = attributeMapper.selectById(id);
        if (attr == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作项属性不存在");
        }

        // 更新名称
        if (dto.getName() != null && !dto.getName().isBlank()) {
            String newName = dto.getName().trim();
            // 检查名称唯一性（排除自身）
            Long existCount = attributeMapper.selectCount(
                    new QueryWrapper<WorkItemAttribute>()
                            .apply("LOWER(name) = LOWER({0})", newName)
                            .ne("id", id));
            if (existCount > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "属性名称已存在: " + newName);
            }
            attr.setName(newName);
        }

        attr.setUpdatedAt(LocalDateTime.now());
        attributeMapper.updateById(attr);

        // 更新值列表（全量替换策略：传入完整列表）
        if (dto.getValues() != null) {
            updateValues(id, dto.getValues());
        }

        log.info("更新工作项属性: id={}, name={}", id, attr.getName());
        return getById(id);
    }

    /**
     * 删除工作项属性
     * FK 已改为 RESTRICT，需先清理所有引用才能删除
     */
    @Transactional
    public void delete(Long id) {
        WorkItemAttribute attr = attributeMapper.selectById(id);
        if (attr == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作项属性不存在");
        }
        if (Boolean.TRUE.equals(attr.getIsBuiltin())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "内置属性不可删除");
        }

        // 检查是否有工时记录引用此属性
        Long usageCount = entryValueMapper.selectCount(
                new QueryWrapper<TimeEntryAttributeValue>().eq("attribute_id", id));
        if (usageCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该属性已被 " + usageCount + " 条工时记录使用，请先将工时转移到其他属性或逐个删除属性值的引用");
        }

        // 先删除属性值（FK RESTRICT 要求先清理子表）
        valueMapper.delete(new QueryWrapper<WorkItemAttributeValue>().eq("attribute_id", id));
        // 删除项目关联
        projectMapper.delete(new QueryWrapper<WorkItemAttributeProject>().eq("attribute_id", id));
        // 最后删除属性本体
        attributeMapper.deleteById(id);
        log.info("删除工作项属性: id={}, name={}", id, attr.getName());
    }

    /**
     * 管理属性的项目分配（全量覆盖）
     */
    @Transactional
    public WorkItemAttributeVO manageProjects(Long id, ManageAttributeProjectsDTO dto) {
        WorkItemAttribute attr = attributeMapper.selectById(id);
        if (attr == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作项属性不存在");
        }

        // 删除旧的项目分配
        projectMapper.delete(new QueryWrapper<WorkItemAttributeProject>().eq("attribute_id", id));

        // 建立新的项目分配
        for (Long projectId : dto.getProjectIds()) {
            WorkItemAttributeProject rel = new WorkItemAttributeProject();
            rel.setAttributeId(id);
            rel.setProjectId(projectId);
            rel.setCreatedAt(LocalDateTime.now());
            projectMapper.insert(rel);
        }

        log.info("更新属性项目分配: attributeId={}, projectCount={}", id, dto.getProjectIds().size());
        return getById(id);
    }

    /**
     * 获取指定项目可用的工作项属性（含值列表）
     * 用于前端工时弹窗动态加载
     */
    public List<WorkItemAttributeVO> listByProject(Long projectId) {
        // 查询分配到该项目的属性 ID
        List<WorkItemAttributeProject> rels = projectMapper.selectList(
                new QueryWrapper<WorkItemAttributeProject>().eq("project_id", projectId));
        if (rels.isEmpty()) return List.of();

        List<Long> attrIds = rels.stream().map(WorkItemAttributeProject::getAttributeId).toList();

        // 查询属性
        List<WorkItemAttribute> attrs = attributeMapper.selectList(
                new QueryWrapper<WorkItemAttribute>().in("id", attrIds).orderByAsc("position", "id"));

        // 批量查询值
        Map<Long, List<WorkItemAttributeValue>> valuesMap = valueMapper.selectList(
                new QueryWrapper<WorkItemAttributeValue>()
                        .in("attribute_id", attrIds)
                        .orderByAsc("position", "id")
        ).stream().collect(Collectors.groupingBy(WorkItemAttributeValue::getAttributeId));

        return attrs.stream().map(attr -> {
            WorkItemAttributeVO vo = new WorkItemAttributeVO();
            vo.setId(String.valueOf(attr.getId()));
            vo.setName(attr.getName());
            vo.setIsBuiltin(attr.getIsBuiltin());
            vo.setPosition(attr.getPosition());

            List<WorkItemAttributeValue> values = valuesMap.getOrDefault(attr.getId(), List.of());
            vo.setValues(values.stream().map(this::toValueVO).toList());

            return vo;
        }).toList();
    }

    /**
     * 保存工时记录的属性值
     */
    @Transactional
    public void saveTimeEntryAttributeValues(Long timeEntryId, Map<Long, Long> attributeValueMap) {
        // 删除旧值
        entryValueMapper.delete(
                new QueryWrapper<TimeEntryAttributeValue>().eq("time_entry_id", timeEntryId));

        // 插入新值
        if (attributeValueMap != null && !attributeValueMap.isEmpty()) {
            for (Map.Entry<Long, Long> entry : attributeValueMap.entrySet()) {
                TimeEntryAttributeValue teav = new TimeEntryAttributeValue();
                teav.setTimeEntryId(timeEntryId);
                teav.setAttributeId(entry.getKey());
                teav.setValueId(entry.getValue());
                teav.setCreatedAt(LocalDateTime.now());
                entryValueMapper.insert(teav);
            }
        }
    }

    /**
     * 获取工时记录的属性值
     */
    public Map<String, Map<String, String>> getTimeEntryAttributeValues(Long timeEntryId) {
        List<TimeEntryAttributeValue> entries = entryValueMapper.selectList(
                new QueryWrapper<TimeEntryAttributeValue>().eq("time_entry_id", timeEntryId));

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (TimeEntryAttributeValue teav : entries) {
            WorkItemAttribute attr = attributeMapper.selectById(teav.getAttributeId());
            WorkItemAttributeValue val = valueMapper.selectById(teav.getValueId());
            if (attr != null && val != null) {
                Map<String, String> valInfo = new HashMap<>();
                valInfo.put("valueId", String.valueOf(val.getId()));
                valInfo.put("valueName", val.getName());
                valInfo.put("valueColor", val.getColor());
                result.put(String.valueOf(attr.getId()), valInfo);
            }
        }
        return result;
    }

    /**
     * 获取属性的使用统计（删除前预检）
     */
    public int getUsageCount(Long attributeId) {
        return entryValueMapper.selectCount(
                new QueryWrapper<TimeEntryAttributeValue>().eq("attribute_id", attributeId)).intValue();
    }

    /**
     * 获取单个属性值的使用统计
     */
    public int getValueUsageCount(Long valueId) {
        return entryValueMapper.selectCount(
                new QueryWrapper<TimeEntryAttributeValue>().eq("value_id", valueId)).intValue();
    }

    /**
     * 转移属性值引用：将所有引用 fromValueId 的工时记录迁移到 targetValueId
     * 参考 OpenProject TimeEntryActivity#transfer_relations(to)
     *
     * @param attributeId 属性 ID（用于校验值归属）
     * @param fromValueId 源值 ID
     * @param targetValueId 目标值 ID
     * @return 转移的工时记录数量
     */
    @Transactional
    public int transferValueReferences(Long attributeId, Long fromValueId, Long targetValueId) {
        // 校验源值存在且属于该属性
        WorkItemAttributeValue fromValue = valueMapper.selectById(fromValueId);
        if (fromValue == null || !fromValue.getAttributeId().equals(attributeId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "源属性值不存在或不属于该属性");
        }

        // 校验目标值存在且属于同一属性
        WorkItemAttributeValue targetValue = valueMapper.selectById(targetValueId);
        if (targetValue == null || !targetValue.getAttributeId().equals(attributeId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "目标属性值不存在或不属于同一属性");
        }

        // 源和目标不能相同
        if (fromValueId.equals(targetValueId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "源值和目标值不能相同");
        }

        // 批量迁移：UPDATE time_entry_attribute_value SET value_id = target WHERE value_id = from
        int transferred = entryValueMapper.transferValueReferences(fromValueId, targetValueId);

        log.info("属性值引用转移完成: attributeId={}, fromValueId={} ({}), targetValueId={} ({}), transferred={}",
                attributeId, fromValueId, fromValue.getName(),
                targetValueId, targetValue.getName(), transferred);

        return transferred;
    }

    // ========== 内部方法 ==========

    private void updateValues(Long attributeId, List<UpdateWorkItemAttributeDTO.ValueItem> items) {
        // 获取现有值
        List<WorkItemAttributeValue> existing = valueMapper.selectList(
                new QueryWrapper<WorkItemAttributeValue>().eq("attribute_id", attributeId));
        Map<Long, WorkItemAttributeValue> existingMap = existing.stream()
                .collect(Collectors.toMap(WorkItemAttributeValue::getId, v -> v));

        Set<Long> newIds = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {
            UpdateWorkItemAttributeDTO.ValueItem item = items.get(i);
            if (item.getId() != null && !item.getId().isBlank()) {
                // 更新已有值
                Long valueId = Long.parseLong(item.getId());
                newIds.add(valueId);
                WorkItemAttributeValue val = existingMap.get(valueId);
                if (val != null) {
                    val.setName(item.getName().trim());
                    val.setColor(item.getColor());
                    val.setPosition(i);
                    valueMapper.updateById(val);
                }
            } else {
                // 新增值
                WorkItemAttributeValue val = new WorkItemAttributeValue();
                val.setAttributeId(attributeId);
                val.setName(item.getName().trim());
                val.setColor(item.getColor());
                val.setPosition(i);
                val.setCreatedAt(LocalDateTime.now());
                valueMapper.insert(val);
                newIds.add(val.getId());
            }
        }

        // 删除不在新列表中的旧值——但必须先检查引用
        for (WorkItemAttributeValue old : existing) {
            if (!newIds.contains(old.getId())) {
                Long usageCount = entryValueMapper.selectCount(
                        new QueryWrapper<TimeEntryAttributeValue>().eq("value_id", old.getId()));
                if (usageCount > 0) {
                    throw new BusinessException(ErrorCode.CONFLICT,
                            "值\"" + old.getName() + "\"已被 " + usageCount + " 条工时记录使用，请先转移到其他值再删除");
                }
                valueMapper.deleteById(old.getId());
            }
        }
    }

    private AttributeValueVO toValueVO(WorkItemAttributeValue val) {
        AttributeValueVO vo = new AttributeValueVO();
        vo.setId(String.valueOf(val.getId()));
        vo.setName(val.getName());
        vo.setColor(val.getColor());
        vo.setPosition(val.getPosition());
        return vo;
    }

    /**
     * 将项目 ID 转为 AttributeProjectVO，处理项目不存在（已删除）的情况
     */
    private AttributeProjectVO toProjectVO(Long projectId, Map<Long, Project> projectInfoMap) {
        AttributeProjectVO vo = new AttributeProjectVO();
        vo.setId(String.valueOf(projectId));
        Project project = projectInfoMap.get(projectId);
        if (project != null) {
            vo.setKey(project.getKey());
            vo.setName(project.getName());
            vo.setDeleted(false);
        } else {
            vo.setKey(null);
            vo.setName("已删除的项目");
            vo.setDeleted(true);
        }
        return vo;
    }

    /**
     * 获取属性值的名称（通过 ID）
     */
    public String getAttributeValueName(Long valueId) {
        WorkItemAttributeValue val = valueMapper.selectById(valueId);
        return val != null ? val.getName() : null;
    }

    /**
     * 按名称查找内建 Work type 属性值的 ID
     * 用于向下兼容前端传递名称字符串的场景
     */
    public Long findWorkTypeValueIdByName(String name) {
        if (name == null || name.isBlank()) return null;
        Long workTypeAttrId = getWorkTypeAttributeId();
        WorkItemAttributeValue val = valueMapper.selectOne(
                new QueryWrapper<WorkItemAttributeValue>()
                        .eq("attribute_id", workTypeAttrId)
                        .eq("name", name.trim()));
        return val != null ? val.getId() : null;
    }

    /**
     * 批量查询工时记录的 Work type 属性值
     * 返回行：(time_entry_id, value_id, value_name, value_color)
     */
    public List<Map<String, Object>> getWorkTypeForEntries(String timeEntryIds) {
        if (timeEntryIds == null || timeEntryIds.isBlank()) return List.of();
        Long workTypeAttrId = getWorkTypeAttributeId();
        return entryValueMapper.selectWorkTypeForEntries(workTypeAttrId, timeEntryIds);
    }

    /**
     * 删除工时记录的所有属性值关联
     */
    @Transactional
    public void deleteTimeEntryAttributeValues(Long timeEntryId) {
        entryValueMapper.delete(
                new QueryWrapper<TimeEntryAttributeValue>().eq("time_entry_id", timeEntryId));
    }

    /**
     * 根据属性 ID 获取属性名称。
     * 用于活动日志中的人类可读变更描述。
     *
     * @param attributeId 属性 ID
     * @return 属性名称，如"工作类型"
     */
    public String getAttributeNameById(Long attributeId) {
        WorkItemAttribute attr = attributeMapper.selectById(attributeId);
        return attr != null ? attr.getName() : "未知属性";
    }
}
