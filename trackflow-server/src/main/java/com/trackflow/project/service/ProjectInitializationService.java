package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.entity.BoardColumnConfig;
import com.trackflow.board.mapper.BoardColumnConfigMapper;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.query.entity.SavedQuery;
import com.trackflow.query.mapper.SavedQueryMapper;
import com.trackflow.workflow.entity.ProjectWorkflow;
import com.trackflow.workflow.entity.WorkflowDefinition;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.mapper.ProjectWorkflowMapper;
import com.trackflow.workflow.mapper.WorkflowDefinitionMapper;
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
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final ProjectWorkflowMapper projectWorkflowMapper;
    private final BoardColumnConfigMapper boardColumnConfigMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final SavedQueryMapper savedQueryMapper;

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
    @Transactional(rollbackFor = Exception.class)
    public void initialize(Long projectId, String template) {
        initialize(projectId, template, null, null);
    }

    /**
     * 根据模板类型初始化项目（含默认 Saved Query 创建）
     *
     * @param projectId  新创建的项目 ID
     * @param template   模板类型：default / scrum / kanban（null 等同于 default）
     * @param projectKey 项目标识（用于生成默认 Saved Query 名称）
     * @param creatorId  项目创建者 ID（Saved Query 归属人）
     */
    @Transactional(rollbackFor = Exception.class)
    public void initialize(Long projectId, String template, String projectKey, Long creatorId) {
        String tpl = (template == null || template.isBlank()) ? "default" : template;

        log.info("Initializing project {} with template: {}", projectId, tpl);

        // 1. 复制全局工作流到该项目（为项目创建独立的工作流定义）
        copyGlobalWorkflowToProject(projectId, projectKey, creatorId);

        // 2. 初始化看板列配置
        initializeBoardColumns(projectId, tpl);

        // 3. 为创建者生成默认 Saved Query（"未分配工单"）
        if (projectKey != null && creatorId != null) {
            createDefaultSavedQueries(projectId, projectKey, creatorId);
        }

        log.info("Project {} initialization completed (template={})", projectId, tpl);
    }

    /**
     * 复制全局默认工作流到指定项目。
     * <p>
     * 为新项目创建独立的 WorkflowDefinition，复制全局规则并绑定到该定义，
     * 确保每个项目有独立的工作流配置，可以后续自定义而不影响全局或其他项目。
     *
     * @param projectId  新创建的项目 ID
     * @param projectKey 项目标识（用于工作流定义命名，可为 null）
     * @param creatorId  项目创建者 ID（作为工作流定义创建者，可为 null）
     */
    private void copyGlobalWorkflowToProject(Long projectId, String projectKey, Long creatorId) {
        // 1. 查找系统默认工作流定义
        WorkflowDefinition defaultDef = workflowDefinitionMapper.selectOne(
                new LambdaQueryWrapper<WorkflowDefinition>()
                        .eq(WorkflowDefinition::getIsDefault, true)
                        .last("LIMIT 1"));

        if (defaultDef == null) {
            log.warn("No default workflow definition found, skipping workflow init for project {}", projectId);
            return;
        }

        // 2. 查询默认工作流定义下的全部转换规则
        List<WorkflowTransition> globalTransitions = workflowTransitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowTransition>()
                        .eq(WorkflowTransition::getWorkflowDefinitionId, defaultDef.getId())
        );

        if (globalTransitions.isEmpty()) {
            log.warn("Default workflow definition (id={}) has no transitions, skipping workflow init for project {}",
                    defaultDef.getId(), projectId);
            return;
        }

        // 3. 为新项目创建独立的 WorkflowDefinition
        LocalDateTime now = LocalDateTime.now();
        String defName = (projectKey != null ? projectKey : "Project-" + projectId) + " 工作流";

        WorkflowDefinition projectDef = new WorkflowDefinition();
        projectDef.setName(defName);
        projectDef.setDescription("从系统默认工作流复制（项目初始化自动创建）");
        projectDef.setIsDefault(false);
        projectDef.setCreatedBy(creatorId);
        projectDef.setUpdatedBy(creatorId);
        projectDef.setCreatedAt(now);
        projectDef.setUpdatedAt(now);
        workflowDefinitionMapper.insert(projectDef);

        Long newDefId = projectDef.getId();

        // 4. 创建项目与工作流定义的绑定关系
        ProjectWorkflow binding = new ProjectWorkflow();
        binding.setProjectId(projectId);
        binding.setWorkflowDefinitionId(newDefId);
        binding.setCreatedAt(now);
        projectWorkflowMapper.insert(binding);

        // 5. 按唯一键去重后复制转换规则（设置新的 workflowDefinitionId）
        Set<String> seen = new java.util.HashSet<>();
        int copied = 0;
        for (WorkflowTransition global : globalTransitions) {
            String key = buildTransitionKey(global);
            if (!seen.add(key)) {
                continue;
            }

            WorkflowTransition projectTransition = new WorkflowTransition();
            projectTransition.setProjectId(projectId);
            projectTransition.setWorkflowDefinitionId(newDefId);
            projectTransition.setIssueType(global.getIssueType());
            projectTransition.setRoleId(global.getRoleId());
            projectTransition.setOldStatusId(global.getOldStatusId());
            projectTransition.setNewStatusId(global.getNewStatusId());
            projectTransition.setAuthor(global.getAuthor());
            projectTransition.setAssignee(global.getAssignee());
            projectTransition.setConditions(global.getConditions());
            projectTransition.setRequireComment(global.getRequireComment());
            workflowTransitionMapper.insert(projectTransition);
            copied++;
        }

        if (copied < globalTransitions.size()) {
            log.warn("Deduplicated global workflow transitions: total={}, copied={} (for project {})",
                    globalTransitions.size(), copied, projectId);
        }

        log.info("Copied {} workflow transitions to project {} (workflowDefinitionId={})",
                copied, projectId, newDefId);
    }

    /**
     * 构造工作流转换的唯一键（用于去重）。
     * 匹配 DB 唯一约束：(workflow_definition_id, issue_type, role_id, old_status_id, new_status_id, author, assignee)
     * 注意：workflow_definition_id 在同一批复制中相同，不需要包含在去重 key 中。
     */
    private String buildTransitionKey(WorkflowTransition t) {
        return t.getIssueType() + "|" + t.getRoleId() + "|" + t.getOldStatusId() + "|" + t.getNewStatusId()
                + "|" + t.getAuthor() + "|" + t.getAssignee();
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

    /**
     * 为项目创建者生成默认 Saved Query。
     * 参考 YouTrack 行为：创建项目时自动生成 "Unassigned in <projectID>" 查询。
     * TrackFlow 适配为中文名称："未分配工单 (<projectKey>)"。
     *
     * @param projectId  项目 ID
     * @param projectKey 项目标识（如 "DE4"）
     * @param creatorId  创建者用户 ID
     */
    private void createDefaultSavedQueries(Long projectId, String projectKey, Long creatorId) {
        LocalDateTime now = LocalDateTime.now();

        // 创建"未分配工单"查询：project = X AND assignee 为空 AND 状态为 open
        String filters = "[" +
                "{\"field\":\"project\",\"operator\":\"eq\",\"value\":[\"" + projectId + "\"]}," +
                "{\"field\":\"assignee\",\"operator\":\"is_empty\",\"value\":[]}," +
                "{\"field\":\"status\",\"operator\":\"open\",\"value\":[]}" +
                "]";

        SavedQuery unassignedQuery = new SavedQuery();
        unassignedQuery.setName("未分配工单 (" + projectKey + ")");
        unassignedQuery.setProjectId(projectId);
        unassignedQuery.setUserId(creatorId);
        unassignedQuery.setShared(false);
        unassignedQuery.setPinned(false);
        unassignedQuery.setFolder(null);
        unassignedQuery.setFilters(filters);
        unassignedQuery.setColumns("[]");
        unassignedQuery.setSortCriteria("[]");
        unassignedQuery.setGroupBy(null);
        unassignedQuery.setIcon("icon-unassigned");
        unassignedQuery.setSortOrder(0);
        unassignedQuery.setCreatedAt(now);
        unassignedQuery.setUpdatedAt(now);
        savedQueryMapper.insert(unassignedQuery);

        log.info("Created default 'Unassigned' saved query for project {} (key={}), owner={}",
                projectId, projectKey, creatorId);
    }
}
