package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.entity.BoardColumnConfig;
import com.trackflow.board.mapper.BoardColumnConfigMapper;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.mapper.WorkflowTransitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 项目初始化服务。
 * 根据模板类型为新创建的项目初始化工作流、看板列配置等基础数据。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectInitializationService {

    private final WorkflowTransitionMapper workflowTransitionMapper;
    private final BoardColumnConfigMapper boardColumnConfigMapper;
    private final IssueStatusMapper issueStatusMapper;

    /**
     * 默认模板使用的核心状态 ID（标准软件开发流程）
     * Open(1), In Progress(2), Code Review(3), Testing(4), Done(5), Cancelled(6), Reopened(7)
     */
    private static final Set<Long> DEFAULT_STATUS_IDS = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);

    /**
     * Scrum 模板使用的状态 ID（敏捷开发）
     * Todo(8), In Progress(2), Code Review(3), Testing(4), Done(5), Cancelled(6), Reopened(7)
     */
    private static final Set<Long> SCRUM_STATUS_IDS = Set.of(8L, 2L, 3L, 4L, 5L, 6L, 7L);

    /**
     * 看板模板使用的状态 ID（精益管理，按阶段流转）
     * Open(1), In Progress(2), Code Review(3), Testing(4), Done(5), Cancelled(6)
     */
    private static final Set<Long> KANBAN_STATUS_IDS = Set.of(1L, 2L, 3L, 4L, 5L, 6L);

    /**
     * 根据模板类型初始化项目
     *
     * @param projectId 新创建的项目 ID
     * @param template  模板类型：default / scrum / kanban（null 等同于 default）
     */
    @Transactional
    public void initialize(Long projectId, String template) {
        String tpl = (template == null || template.isBlank()) ? "default" : template;

        log.info("Initializing project {} with template: {}", projectId, tpl);

        // 1. 复制全局工作流到该项目
        copyGlobalWorkflowToProject(projectId);

        // 2. 初始化看板列配置
        initializeBoardColumns(projectId, tpl);

        log.info("Project {} initialization completed (template={})", projectId, tpl);
    }

    /**
     * 复制全局默认工作流（project_id IS NULL）到指定项目。
     * 这样每个项目有独立的工作流配置，可以后续自定义而不影响全局。
     * 复制前先对全局规则按唯一键 (issue_type, role_id, old_status_id, new_status_id) 去重，
     * 防止全局数据中存在重复记录导致唯一约束冲突。
     */
    private void copyGlobalWorkflowToProject(Long projectId) {
        // 查询全局工作流规则
        List<WorkflowTransition> globalTransitions = workflowTransitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowTransition>()
                        .isNull(WorkflowTransition::getProjectId)
        );

        if (globalTransitions.isEmpty()) {
            log.warn("No global workflow transitions found, skipping workflow init for project {}", projectId);
            return;
        }

        // 按唯一键去重（防止全局数据中存在重复记录）
        Set<String> seen = new java.util.HashSet<>();
        List<WorkflowTransition> uniqueTransitions = globalTransitions.stream()
                .filter(t -> seen.add(buildTransitionKey(t)))
                .toList();

        if (uniqueTransitions.size() < globalTransitions.size()) {
            log.warn("Found {} duplicate global workflow transitions (total={}, unique={})",
                    globalTransitions.size() - uniqueTransitions.size(),
                    globalTransitions.size(), uniqueTransitions.size());
        }

        // 复制到该项目
        for (WorkflowTransition global : uniqueTransitions) {
            WorkflowTransition projectTransition = new WorkflowTransition();
            projectTransition.setProjectId(projectId);
            projectTransition.setIssueType(global.getIssueType());
            projectTransition.setRoleId(global.getRoleId());
            projectTransition.setOldStatusId(global.getOldStatusId());
            projectTransition.setNewStatusId(global.getNewStatusId());
            projectTransition.setConditions(global.getConditions());
            workflowTransitionMapper.insert(projectTransition);
        }

        log.info("Copied {} workflow transitions to project {}", uniqueTransitions.size(), projectId);
    }

    /**
     * 构造工作流转换的唯一键（用于去重）
     */
    private String buildTransitionKey(WorkflowTransition t) {
        return t.getIssueType() + "|" + t.getRoleId() + "|" + t.getOldStatusId() + "|" + t.getNewStatusId();
    }

    /**
     * 初始化看板列配置。
     * 根据模板类型决定哪些状态可见、排序、是否默认折叠。
     */
    private void initializeBoardColumns(Long projectId, String template) {
        Set<Long> visibleStatusIds = getVisibleStatusIds(template);

        // 查询所有需要展示的状态，按 sort_order 排序
        List<IssueStatus> allStatuses = issueStatusMapper.selectList(
                new LambdaQueryWrapper<IssueStatus>()
                        .in(IssueStatus::getId, visibleStatusIds)
                        .orderByAsc(IssueStatus::getSortOrder)
        );

        LocalDateTime now = LocalDateTime.now();
        int sortOrder = 0;

        for (IssueStatus status : allStatuses) {
            BoardColumnConfig config = new BoardColumnConfig();
            config.setProjectId(projectId);
            config.setStatusId(status.getId());
            config.setVisible(true);
            config.setSortOrder(sortOrder++);
            config.setCollapsed(shouldCollapse(template, status));
            config.setCreatedAt(now);
            config.setUpdatedAt(now);
            boardColumnConfigMapper.insert(config);
        }

        log.info("Initialized {} board columns for project {} (template={})",
                allStatuses.size(), projectId, template);
    }

    /**
     * 根据模板获取可见的状态 ID 集合
     */
    private Set<Long> getVisibleStatusIds(String template) {
        return switch (template) {
            case "scrum" -> SCRUM_STATUS_IDS;
            case "kanban" -> KANBAN_STATUS_IDS;
            default -> DEFAULT_STATUS_IDS;
        };
    }

    /**
     * 判断看板列是否默认折叠。
     * - 看板模板：Cancelled 列默认折叠
     * - 其他模板：Cancelled 列默认折叠
     */
    private boolean shouldCollapse(String template, IssueStatus status) {
        // Cancelled 和 Done 在看板模式下可考虑折叠（减少视觉噪音）
        if ("kanban".equals(template)) {
            return "cancelled".equals(status.getCode());
        }
        // 默认/Scrum 模板：Cancelled 折叠
        return "cancelled".equals(status.getCode());
    }
}
