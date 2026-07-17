package com.trackflow.workflow.service;

import com.trackflow.workflow.entity.TransitionAction;
import com.trackflow.workflow.mapper.TransitionActionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 动作解析器 —— 解析当前转换路径匹配的最终动作列表。
 * <p>
 * 优先级（从高到低）：
 * <ol>
 *   <li>project_id = X AND issue_type = 精确类型</li>
 *   <li>project_id = X AND issue_type = '*'</li>
 *   <li>project_id IS NULL AND issue_type = 精确类型</li>
 *   <li>project_id IS NULL AND issue_type = '*'</li>
 * </ol>
 * 返回最高优先级非空层级中的所有动作（已按 sort_order 升序）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionResolver {

    private final TransitionActionMapper actionMapper;

    /**
     * 解析最终的动作执行列表。
     *
     * @param projectId   项目 ID
     * @param issueType   Issue 类型（如 Bug, Task, Feature）
     * @param oldStatusId 转换前状态 ID
     * @param newStatusId 转换后状态 ID
     * @return 最高优先级非空层级的动作列表（已按 sort_order 排序，仅启用的动作）
     */
    public List<TransitionAction> resolve(Long projectId, String issueType,
                                          Long oldStatusId, Long newStatusId) {
        // 查询所有匹配的启用动作（包含 project+type, project+wildcard, global+type, global+wildcard）
        List<TransitionAction> allActions = actionMapper.selectByTransitionPath(
                projectId, issueType, oldStatusId, newStatusId);

        if (allActions == null || allActions.isEmpty()) {
            return Collections.emptyList();
        }

        return filterByPriority(allActions, projectId, issueType);
    }

    /**
     * 解析创建时的动作执行列表（old_status_id IS NULL）。
     *
     * @param projectId   项目 ID
     * @param issueType   Issue 类型
     * @param newStatusId 创建后的初始状态 ID
     * @return 最高优先级非空层级的动作列表
     */
    public List<TransitionAction> resolveOnCreate(Long projectId, String issueType, Long newStatusId) {
        List<TransitionAction> allActions = actionMapper.selectByCreationPath(
                projectId, issueType, newStatusId);

        if (allActions == null || allActions.isEmpty()) {
            return Collections.emptyList();
        }

        return filterByPriority(allActions, projectId, issueType);
    }

    /**
     * 按优先级从动作列表中筛选最高优先级层级的动作。
     */
    private List<TransitionAction> filterByPriority(List<TransitionAction> allActions,
                                                     Long projectId, String issueType) {

        // Level 1: project_id = projectId AND issue_type = issueType（精确匹配）
        List<TransitionAction> level1 = allActions.stream()
                .filter(a -> Objects.equals(a.getProjectId(), projectId) && issueType.equals(a.getIssueType()))
                .toList();
        if (!level1.isEmpty()) {
            log.debug("ActionResolver: matched Level 1 (project+exactType), {} actions", level1.size());
            return level1;
        }

        // Level 2: project_id = projectId AND issue_type = '*'（项目通配符）
        List<TransitionAction> level2 = allActions.stream()
                .filter(a -> Objects.equals(a.getProjectId(), projectId) && "*".equals(a.getIssueType()))
                .toList();
        if (!level2.isEmpty()) {
            log.debug("ActionResolver: matched Level 2 (project+wildcard), {} actions", level2.size());
            return level2;
        }

        // Level 3: project_id IS NULL AND issue_type = issueType（全局精确）
        List<TransitionAction> level3 = allActions.stream()
                .filter(a -> a.getProjectId() == null && issueType.equals(a.getIssueType()))
                .toList();
        if (!level3.isEmpty()) {
            log.debug("ActionResolver: matched Level 3 (global+exactType), {} actions", level3.size());
            return level3;
        }

        // Level 4: project_id IS NULL AND issue_type = '*'（全局通配符）
        List<TransitionAction> level4 = allActions.stream()
                .filter(a -> a.getProjectId() == null && "*".equals(a.getIssueType()))
                .toList();
        if (!level4.isEmpty()) {
            log.debug("ActionResolver: matched Level 4 (global+wildcard), {} actions", level4.size());
            return level4;
        }

        return Collections.emptyList();
    }
}
