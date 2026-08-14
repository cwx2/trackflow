package com.trackflow.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.dto.CreateWorkflowDefinitionDTO;
import com.trackflow.workflow.dto.UpdateWorkflowDefinitionDTO;
import com.trackflow.workflow.entity.ProjectWorkflow;
import com.trackflow.workflow.entity.WorkflowDefinition;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.mapper.ProjectWorkflowMapper;
import com.trackflow.workflow.mapper.WorkflowDefinitionMapper;
import com.trackflow.workflow.mapper.WorkflowTransitionMapper;
import com.trackflow.workflow.vo.WorkflowDefinitionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工作流定义管理服务。
 * <p>
 * 提供工作流定义的 CRUD、克隆、附加/分离功能。
 * 对标 YouTrack 的命名工作流 + attach/detach 机制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionService {

    private final WorkflowDefinitionMapper definitionMapper;
    private final ProjectWorkflowMapper projectWorkflowMapper;
    private final WorkflowTransitionMapper transitionMapper;
    private final ProjectMapper projectMapper;
    private final SysUserMapper userMapper;

    /**
     * 查询所有工作流定义列表（含绑定项目数和规则数统计）
     */
    public List<WorkflowDefinitionVO> listDefinitions() {
        List<WorkflowDefinition> definitions = definitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowDefinition>().orderByDesc(WorkflowDefinition::getIsDefault)
                        .orderByAsc(WorkflowDefinition::getCreatedAt));

        if (definitions.isEmpty()) {
            return List.of();
        }

        // 批量查询绑定项目信息
        List<Long> defIds = definitions.stream().map(WorkflowDefinition::getId).toList();

        // 查询每个定义的转换规则数
        Map<Long, Long> transitionCounts = countTransitionsByDefinitionIds(defIds);

        // 查询每个定义绑定的项目
        Map<Long, List<ProjectWorkflow>> projectBindings = getProjectBindingsByDefinitionIds(defIds);

        // 查询创建者名称
        List<Long> creatorIds = definitions.stream()
                .map(WorkflowDefinition::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> userNameMap = getUserNameMap(creatorIds);

        // 查询所有相关项目的简要信息
        List<Long> allProjectIds = projectBindings.values().stream()
                .flatMap(List::stream)
                .map(ProjectWorkflow::getProjectId)
                .distinct().toList();
        Map<Long, Project> projectMap = getProjectMap(allProjectIds);

        return definitions.stream().map(def -> {
            WorkflowDefinitionVO vo = new WorkflowDefinitionVO();
            vo.setId(String.valueOf(def.getId()));
            vo.setName(def.getName());
            vo.setDescription(def.getDescription());
            vo.setIsDefault(def.getIsDefault());
            vo.setTransitionCount(transitionCounts.getOrDefault(def.getId(), 0L).intValue());
            vo.setCreatedBy(def.getCreatedBy() != null ? String.valueOf(def.getCreatedBy()) : null);
            vo.setCreatedByName(def.getCreatedBy() != null ? userNameMap.get(def.getCreatedBy()) : null);
            vo.setCreatedAt(def.getCreatedAt());
            vo.setUpdatedAt(def.getUpdatedAt());

            // 绑定的项目
            List<ProjectWorkflow> bindings = projectBindings.getOrDefault(def.getId(), List.of());
            vo.setProjectCount(bindings.size());
            List<WorkflowDefinitionVO.BoundProject> boundProjects = bindings.stream()
                    .map(pw -> {
                        Project p = projectMap.get(pw.getProjectId());
                        if (p == null) return null;
                        WorkflowDefinitionVO.BoundProject bp = new WorkflowDefinitionVO.BoundProject();
                        bp.setId(String.valueOf(p.getId()));
                        bp.setName(p.getName());
                        bp.setKey(p.getKey());
                        return bp;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            vo.setProjects(boundProjects);

            return vo;
        }).toList();
    }

    /**
     * 获取单个工作流定义详情
     */
    public WorkflowDefinitionVO getDefinition(Long id) {
        WorkflowDefinition def = definitionMapper.selectById(id);
        if (def == null) {
            throw BusinessException.notFound("工作流定义不存在");
        }

        // 复用 list 逻辑（单元素）
        List<WorkflowDefinitionVO> list = listDefinitions();
        return list.stream()
                .filter(vo -> vo.getId().equals(String.valueOf(id)))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("工作流定义"));
    }

    /**
     * 创建工作流定义
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinition createDefinition(CreateWorkflowDefinitionDTO dto) {
        // 名称唯一性校验
        Long existCount = definitionMapper.selectCount(
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getName, dto.getName()));
        if (existCount > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "工作流名称已存在：" + dto.getName());
        }

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName(dto.getName());
        definition.setDescription(dto.getDescription());
        definition.setIsDefault(Boolean.TRUE.equals(dto.getIsDefault()));
        definition.setCreatedBy(SecurityUtils.getCurrentUserId());
        definition.setUpdatedBy(SecurityUtils.getCurrentUserId());
        definition.setCreatedAt(LocalDateTime.now());
        definition.setUpdatedAt(LocalDateTime.now());

        // 如果设为默认，取消其他默认
        if (definition.getIsDefault()) {
            clearDefaultFlag();
        }

        definitionMapper.insert(definition);
        log.info("Created workflow definition: id={}, name={}", definition.getId(), definition.getName());
        return definition;
    }

    /**
     * 更新工作流定义
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDefinition(Long id, UpdateWorkflowDefinitionDTO dto) {
        WorkflowDefinition existing = definitionMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("工作流定义不存在");
        }

        if (dto.getName() != null) {
            // 名称唯一性校验（排除自身）
            Long existCount = definitionMapper.selectCount(
                    new LambdaQueryWrapper<WorkflowDefinition>()
                            .eq(WorkflowDefinition::getName, dto.getName())
                            .ne(WorkflowDefinition::getId, id));
            if (existCount > 0) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "工作流名称已存在：" + dto.getName());
            }
            existing.setName(dto.getName());
        }

        if (dto.getDescription() != null) {
            existing.setDescription(dto.getDescription());
        }

        if (dto.getIsDefault() != null) {
            if (Boolean.TRUE.equals(dto.getIsDefault())) {
                clearDefaultFlag();
            }
            existing.setIsDefault(dto.getIsDefault());
        }

        existing.setUpdatedBy(SecurityUtils.getCurrentUserId());
        existing.setUpdatedAt(LocalDateTime.now());
        definitionMapper.updateById(existing);
        log.info("Updated workflow definition: id={}", id);
    }

    /**
     * 删除工作流定义
     * <p>
     * 不允许删除系统默认工作流。
     * 删除时级联删除关联的 project_workflow 和 workflow_transition。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDefinition(Long id) {
        WorkflowDefinition existing = definitionMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("工作流定义不存在");
        }

        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能删除系统默认工作流");
        }

        // 检查是否有项目绑定
        Long bindingCount = projectWorkflowMapper.selectCount(
                new LambdaQueryWrapper<ProjectWorkflow>()
                        .eq(ProjectWorkflow::getWorkflowDefinitionId, id));
        if (bindingCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该工作流仍被 " + bindingCount + " 个项目使用，请先解除绑定");
        }

        // 删除关联的转换规则
        transitionMapper.delete(
                new LambdaQueryWrapper<WorkflowTransition>()
                        .eq(WorkflowTransition::getWorkflowDefinitionId, id));

        definitionMapper.deleteById(id);
        log.info("Deleted workflow definition: id={}, name={}", id, existing.getName());
    }

    /**
     * 克隆工作流定义（含所有转换规则）
     *
     * @param sourceId 源工作流定义 ID
     * @param newName  新名称
     * @return 克隆后的工作流定义
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDefinition cloneDefinition(Long sourceId, String newName) {
        WorkflowDefinition source = definitionMapper.selectById(sourceId);
        if (source == null) {
            throw BusinessException.notFound("源工作流定义不存在");
        }

        // 名称唯一性校验
        Long existCount = definitionMapper.selectCount(
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getName, newName));
        if (existCount > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "工作流名称已存在：" + newName);
        }

        // 创建新定义
        WorkflowDefinition target = new WorkflowDefinition();
        target.setName(newName);
        target.setDescription("从「" + source.getName() + "」克隆");
        target.setIsDefault(false);
        target.setCreatedBy(SecurityUtils.getCurrentUserId());
        target.setUpdatedBy(SecurityUtils.getCurrentUserId());
        target.setCreatedAt(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        definitionMapper.insert(target);

        // 复制转换规则
        List<WorkflowTransition> sourceTransitions = transitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowTransition>()
                        .eq(WorkflowTransition::getWorkflowDefinitionId, sourceId));

        for (WorkflowTransition src : sourceTransitions) {
            WorkflowTransition copy = new WorkflowTransition();
            copy.setWorkflowDefinitionId(target.getId());
            copy.setProjectId(null); // 克隆出来的工作流不绑定具体项目（通过 project_workflow 关联）
            copy.setIssueType(src.getIssueType());
            copy.setRoleId(src.getRoleId());
            copy.setOldStatusId(src.getOldStatusId());
            copy.setNewStatusId(src.getNewStatusId());
            copy.setAuthor(src.getAuthor());
            copy.setAssignee(src.getAssignee());
            copy.setConditions(src.getConditions());
            transitionMapper.insert(copy);
        }

        log.info("Cloned workflow definition: source={}, target={}, transitions={}",
                sourceId, target.getId(), sourceTransitions.size());
        return target;
    }

    /**
     * 将工作流定义附加到项目。
     * <p>
     * 对标 YouTrack 的行为性 attach 语义：附加后该工作流的转换规则立即对项目生效。
     * 实现方式：将定义中的全局模板规则（project_id IS NULL）按项目 ID 复制到 workflow_transition 表。
     */
    @Transactional(rollbackFor = Exception.class)
    public void attachToProject(Long projectId, Long definitionId) {
        // 验证工作流定义存在
        WorkflowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw BusinessException.notFound("工作流定义不存在");
        }

        // 验证项目存在
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BusinessException.notFound("项目不存在");
        }

        // 检查是否已绑定
        Long existing = projectWorkflowMapper.selectCount(
                new LambdaQueryWrapper<ProjectWorkflow>()
                        .eq(ProjectWorkflow::getProjectId, projectId)
                        .eq(ProjectWorkflow::getWorkflowDefinitionId, definitionId));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "项目已绑定该工作流");
        }

        // 1. 插入 project_workflow 映射记录
        ProjectWorkflow binding = new ProjectWorkflow();
        binding.setProjectId(projectId);
        binding.setWorkflowDefinitionId(definitionId);
        binding.setCreatedAt(LocalDateTime.now());
        projectWorkflowMapper.insert(binding);

        // 2. 将定义中的全局模板规则复制为项目级规则，使其立即生效
        List<WorkflowTransition> templateRules = transitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowTransition>()
                        .eq(WorkflowTransition::getWorkflowDefinitionId, definitionId)
                        .isNull(WorkflowTransition::getProjectId));

        int copiedCount = 0;
        for (WorkflowTransition template : templateRules) {
            WorkflowTransition copy = new WorkflowTransition();
            copy.setWorkflowDefinitionId(definitionId);
            copy.setProjectId(projectId);
            copy.setIssueType(template.getIssueType());
            copy.setRoleId(template.getRoleId());
            copy.setOldStatusId(template.getOldStatusId());
            copy.setNewStatusId(template.getNewStatusId());
            copy.setAuthor(template.getAuthor());
            copy.setAssignee(template.getAssignee());
            copy.setConditions(template.getConditions());
            copy.setRequireComment(template.getRequireComment());
            transitionMapper.insert(copy);
            copiedCount++;
        }

        log.info("Attached workflow '{}' to project '{}', copied {} transition rules",
                def.getName(), project.getName(), copiedCount);
    }

    /**
     * 从项目分离工作流定义。
     * <p>
     * 对标 YouTrack 的行为性 detach 语义：分离后该工作流的转换规则立即对项目停止生效。
     * 实现方式：删除 workflow_transition 中属于该项目和定义的所有项目级规则。
     */
    @Transactional(rollbackFor = Exception.class)
    public void detachFromProject(Long projectId, Long definitionId) {
        // 1. 删除 project_workflow 映射记录
        int deleted = projectWorkflowMapper.delete(
                new LambdaQueryWrapper<ProjectWorkflow>()
                        .eq(ProjectWorkflow::getProjectId, projectId)
                        .eq(ProjectWorkflow::getWorkflowDefinitionId, definitionId));

        if (deleted == 0) {
            throw BusinessException.notFound("项目未绑定该工作流");
        }

        // 2. 删除 workflow_transition 中该项目和该定义关联的所有项目级规则，使其立即停止生效
        int rulesDeleted = transitionMapper.delete(
                new LambdaQueryWrapper<WorkflowTransition>()
                        .eq(WorkflowTransition::getProjectId, projectId)
                        .eq(WorkflowTransition::getWorkflowDefinitionId, definitionId));

        log.info("Detached workflow definition {} from project {}, removed {} transition rules",
                definitionId, projectId, rulesDeleted);
    }

    /**
     * 获取项目绑定的工作流定义列表
     */
    public List<WorkflowDefinitionVO> getProjectWorkflows(Long projectId) {
        List<Long> defIds = projectWorkflowMapper.selectDefinitionIdsByProjectId(projectId);
        if (defIds.isEmpty()) {
            return List.of();
        }

        List<WorkflowDefinition> definitions = definitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowDefinition>().in(WorkflowDefinition::getId, defIds));

        // 简单转换（不需要完整的统计信息）
        return definitions.stream().map(def -> {
            WorkflowDefinitionVO vo = new WorkflowDefinitionVO();
            vo.setId(String.valueOf(def.getId()));
            vo.setName(def.getName());
            vo.setDescription(def.getDescription());
            vo.setIsDefault(def.getIsDefault());
            vo.setCreatedAt(def.getCreatedAt());
            vo.setUpdatedAt(def.getUpdatedAt());
            return vo;
        }).toList();
    }

    // ========== 私有辅助方法 ==========

    private void clearDefaultFlag() {
        List<WorkflowDefinition> defaults = definitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowDefinition>().eq(WorkflowDefinition::getIsDefault, true));
        for (WorkflowDefinition d : defaults) {
            d.setIsDefault(false);
            d.setUpdatedAt(LocalDateTime.now());
            definitionMapper.updateById(d);
        }
    }

    private Map<Long, Long> countTransitionsByDefinitionIds(List<Long> defIds) {
        if (defIds.isEmpty()) return Map.of();
        List<WorkflowTransition> all = transitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowTransition>()
                        .in(WorkflowTransition::getWorkflowDefinitionId, defIds)
                        .select(WorkflowTransition::getWorkflowDefinitionId));
        return all.stream().collect(Collectors.groupingBy(
                WorkflowTransition::getWorkflowDefinitionId, Collectors.counting()));
    }

    private Map<Long, List<ProjectWorkflow>> getProjectBindingsByDefinitionIds(List<Long> defIds) {
        if (defIds.isEmpty()) return Map.of();
        List<ProjectWorkflow> bindings = projectWorkflowMapper.selectList(
                new LambdaQueryWrapper<ProjectWorkflow>()
                        .in(ProjectWorkflow::getWorkflowDefinitionId, defIds));
        return bindings.stream().collect(Collectors.groupingBy(ProjectWorkflow::getWorkflowDefinitionId));
    }

    private Map<Long, String> getUserNameMap(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().in(SysUser::getId, userIds));
        return users.stream().collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName));
    }

    private Map<Long, Project> getProjectMap(List<Long> projectIds) {
        if (projectIds.isEmpty()) return Map.of();
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().in(Project::getId, projectIds));
        return projects.stream().collect(Collectors.toMap(Project::getId, p -> p));
    }
}
