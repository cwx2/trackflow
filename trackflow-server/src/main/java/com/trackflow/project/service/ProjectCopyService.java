package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.board.entity.*;
import com.trackflow.board.mapper.BoardCardConfigMapper;
import com.trackflow.board.mapper.BoardColumnConfigMapper;
import com.trackflow.board.mapper.BoardColumnMergeMapper;
import com.trackflow.board.mapper.BoardGeneralConfigMapper;
import com.trackflow.board.mapper.BoardSwimlaneConfigMapper;
import com.trackflow.common.constant.SystemRoleIds;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.customfield.entity.CustomFieldProject;
import com.trackflow.customfield.mapper.CustomFieldProjectMapper;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.mapper.IssueTagMapper;
import com.trackflow.project.dto.CopyProjectDTO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.entity.ProjectStatus;
import com.trackflow.project.entity.ProjectVisibility;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.project.vo.ProjectCopySummaryVO;
import com.trackflow.query.entity.SavedQuery;
import com.trackflow.query.mapper.SavedQueryMapper;
import com.trackflow.workflow.entity.TransitionAction;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.mapper.TransitionActionMapper;
import com.trackflow.workflow.mapper.WorkflowTransitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 项目复制服务。
 * 参考 OpenProject Projects::CopyService，实现模块化的选择性复制。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectCopyService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final WorkflowTransitionMapper workflowTransitionMapper;
    private final BoardColumnConfigMapper boardColumnConfigMapper;
    private final BoardColumnMergeMapper boardColumnMergeMapper;
    private final BoardCardConfigMapper boardCardConfigMapper;
    private final BoardGeneralConfigMapper boardGeneralConfigMapper;
    private final BoardSwimlaneConfigMapper boardSwimlaneConfigMapper;
    private final IssueTagMapper issueTagMapper;
    private final CustomFieldProjectMapper customFieldProjectMapper;
    private final TransitionActionMapper transitionActionMapper;
    private final SavedQueryMapper savedQueryMapper;
    private final ProjectModuleService projectModuleService;

    /** 有效的复制选项 */
    private static final Set<String> VALID_OPTIONS = Set.of(
            "workflow", "tags", "custom_fields", "board", "actions", "members", "queries"
    );

    /** project_admin 角色 ID */
    private static final Long PROJECT_ADMIN_ROLE_ID = SystemRoleIds.PROJECT_ADMIN;

    /**
     * 从源项目复制创建新项目。
     *
     * @param dto 复制请求
     * @return 新创建的项目
     */
    @Transactional(rollbackFor = Exception.class)
    public Project copyProject(CopyProjectDTO dto) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 验证源项目存在
        Project source = projectMapper.selectById(dto.getSourceProjectId());
        if (source == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "源项目不存在");
        }

        // 2. 验证 Key 唯一性
        Project existingKey = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>().eq(Project::getKey, dto.getKey())
        );
        if (existingKey != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目标识 " + dto.getKey() + " 已存在");
        }

        // 3. 验证 Name 唯一性（不区分大小写，对标 YouTrack: "The project name must be unique."）
        Long nameCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().apply("LOWER(name) = LOWER({0})", dto.getName().trim())
        );
        if (nameCount > 0) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_DUPLICATE);
        }

        // 4. 创建新项目
        Project newProject = new Project();
        newProject.setName(dto.getName());
        newProject.setKey(dto.getKey());
        newProject.setDescription(dto.getDescription() != null ? dto.getDescription() : source.getDescription());
        newProject.setOrgId(source.getOrgId());
        newProject.setLeadId(currentUserId);
        newProject.setStatus(ProjectStatus.ACTIVE);
        newProject.setVisibility(ProjectVisibility.PRIVATE);
        newProject.setIssueSequence(0);
        newProject.setSettings(source.getSettings());
        newProject.setCreatedBy(currentUserId);
        newProject.setUpdatedBy(currentUserId);
        LocalDateTime now = LocalDateTime.now();
        newProject.setCreatedAt(now);
        newProject.setUpdatedAt(now);
        projectMapper.insert(newProject);

        Long newProjectId = newProject.getId();
        log.info("Created new project {} (id={}) as copy of project {} (id={})",
                dto.getKey(), newProjectId, source.getKey(), source.getId());

        // 4. 添加当前用户为项目管理员
        addCreatorAsAdmin(newProjectId, currentUserId, now);

        // 5. 复制源项目的启用模块配置
        copyEnabledModules(source.getId(), newProjectId);

        // 6. 根据 copyOptions 选择性复制
        Set<String> options = normalizeCopyOptions(dto.getCopyOptions());

        if (options.contains("workflow")) {
            copyWorkflowTransitions(source.getId(), newProjectId);
        }
        if (options.contains("tags")) {
            copyTags(source.getId(), newProjectId, currentUserId, now);
        }
        if (options.contains("custom_fields")) {
            copyCustomFieldBindings(source.getId(), newProjectId);
        }
        if (options.contains("board")) {
            copyBoardConfig(source.getId(), newProjectId, now);
        }
        if (options.contains("actions")) {
            copyTransitionActions(source.getId(), newProjectId, currentUserId, now);
        }
        if (options.contains("members")) {
            copyMembers(source.getId(), newProjectId, currentUserId, now);
        }
        if (options.contains("queries")) {
            copySavedQueries(source.getId(), newProjectId, currentUserId, now);
        }

        log.info("Project copy completed: {} → {} (options={})", source.getKey(), dto.getKey(), options);
        return newProject;
    }

    /**
     * 获取源项目的可复制模块概要信息（用于前端展示）。
     */
    public ProjectCopySummaryVO getSourceProjectSummary(Long sourceProjectId) {
        ProjectCopySummaryVO vo = new ProjectCopySummaryVO();

        vo.setWorkflow(Math.toIntExact(workflowTransitionMapper.selectCount(
                new LambdaQueryWrapper<WorkflowTransition>().eq(WorkflowTransition::getProjectId, sourceProjectId))));
        vo.setTags(Math.toIntExact(issueTagMapper.selectCount(
                new LambdaQueryWrapper<IssueTag>().eq(IssueTag::getProjectId, sourceProjectId))));
        vo.setCustomFields(Math.toIntExact(customFieldProjectMapper.selectCount(
                new LambdaQueryWrapper<CustomFieldProject>()
                        .eq(CustomFieldProject::getProjectId, sourceProjectId)
                        .eq(CustomFieldProject::getIsExcluded, false))));
        vo.setBoard(Math.toIntExact(boardColumnConfigMapper.selectCount(
                new LambdaQueryWrapper<BoardColumnConfig>().eq(BoardColumnConfig::getProjectId, sourceProjectId))));
        vo.setActions(Math.toIntExact(transitionActionMapper.selectCount(
                new LambdaQueryWrapper<TransitionAction>().eq(TransitionAction::getProjectId, sourceProjectId))));
        vo.setMembers(projectMemberMapper.countDistinctUsers(sourceProjectId));
        vo.setQueries(Math.toIntExact(savedQueryMapper.selectCount(
                new LambdaQueryWrapper<SavedQuery>()
                        .eq(SavedQuery::getProjectId, sourceProjectId)
                        .eq(SavedQuery::getShared, true))));

        return vo;
    }

    // ======== 私有方法 ========

    private Set<String> normalizeCopyOptions(Set<String> options) {
        if (options == null || options.isEmpty()) {
            // 默认复制所有配置类模块（不含 members 和 queries）
            return Set.of("workflow", "tags", "custom_fields", "board", "actions");
        }
        Set<String> normalized = new HashSet<>();
        for (String opt : options) {
            if (VALID_OPTIONS.contains(opt)) {
                normalized.add(opt);
            }
        }
        return normalized;
    }

    private void addCreatorAsAdmin(Long projectId, Long userId, LocalDateTime now) {
        ProjectMember admin = new ProjectMember();
        admin.setProjectId(projectId);
        admin.setUserId(userId);
        admin.setRoleId(PROJECT_ADMIN_ROLE_ID);
        admin.setJoinedAt(now);
        projectMemberMapper.insert(admin);
    }

    /**
     * 复制源项目的启用模块配置到新项目
     */
    private void copyEnabledModules(Long sourceId, Long targetId) {
        projectModuleService.copyEnabledModules(sourceId, targetId);
        log.debug("Copied enabled modules from project {} to {}", sourceId, targetId);
    }

    private void copyWorkflowTransitions(Long sourceId, Long targetId) {
        List<WorkflowTransition> transitions = workflowTransitionMapper.selectList(
                new LambdaQueryWrapper<WorkflowTransition>().eq(WorkflowTransition::getProjectId, sourceId));

        // 去重（防止源数据中有重复）
        Set<String> seen = new HashSet<>();
        int copied = 0;
        for (WorkflowTransition src : transitions) {
            String key = src.getIssueType() + "|" + src.getRoleId() + "|" + src.getOldStatusId() + "|" + src.getNewStatusId()
                    + "|" + src.getAuthor() + "|" + src.getAssignee();
            if (!seen.add(key)) continue;

            WorkflowTransition t = new WorkflowTransition();
            t.setProjectId(targetId);
            t.setIssueType(src.getIssueType());
            t.setRoleId(src.getRoleId());
            t.setOldStatusId(src.getOldStatusId());
            t.setNewStatusId(src.getNewStatusId());
            t.setAuthor(src.getAuthor());
            t.setAssignee(src.getAssignee());
            t.setConditions(src.getConditions());
            t.setWorkflowDefinitionId(src.getWorkflowDefinitionId());
            workflowTransitionMapper.insert(t);
            copied++;
        }
        log.info("Copied {} workflow transitions from project {} to {}", copied, sourceId, targetId);
    }

    private void copyTags(Long sourceId, Long targetId, Long userId, LocalDateTime now) {
        List<IssueTag> tags = issueTagMapper.selectList(
                new LambdaQueryWrapper<IssueTag>().eq(IssueTag::getProjectId, sourceId));

        for (IssueTag src : tags) {
            IssueTag tag = new IssueTag();
            tag.setProjectId(targetId);
            tag.setName(src.getName());
            tag.setColor(src.getColor());
            tag.setCreatedBy(userId);
            tag.setCreatedAt(now);
            issueTagMapper.insert(tag);
        }
        log.info("Copied {} tags from project {} to {}", tags.size(), sourceId, targetId);
    }

    private void copyCustomFieldBindings(Long sourceId, Long targetId) {
        List<CustomFieldProject> bindings = customFieldProjectMapper.selectList(
                new LambdaQueryWrapper<CustomFieldProject>().eq(CustomFieldProject::getProjectId, sourceId));

        for (CustomFieldProject src : bindings) {
            CustomFieldProject cfp = new CustomFieldProject();
            cfp.setProjectId(targetId);
            cfp.setCustomFieldId(src.getCustomFieldId());
            cfp.setPosition(src.getPosition());
            cfp.setConditionFieldId(src.getConditionFieldId());
            cfp.setConditionValues(src.getConditionValues());
            cfp.setIsExcluded(src.getIsExcluded());
            customFieldProjectMapper.insert(cfp);
        }
        log.info("Copied {} custom field bindings from project {} to {}", bindings.size(), sourceId, targetId);
    }

    private void copyBoardConfig(Long sourceId, Long targetId, LocalDateTime now) {
        // 1. 看板列配置
        List<BoardColumnConfig> columns = boardColumnConfigMapper.selectList(
                new LambdaQueryWrapper<BoardColumnConfig>().eq(BoardColumnConfig::getProjectId, sourceId));
        for (BoardColumnConfig src : columns) {
            BoardColumnConfig col = new BoardColumnConfig();
            col.setProjectId(targetId);
            col.setStatusId(src.getStatusId());
            col.setVisible(src.getVisible());
            col.setSortOrder(src.getSortOrder());
            col.setCollapsed(src.getCollapsed());
            col.setWipMin(src.getWipMin());
            col.setWipMax(src.getWipMax());
            col.setCreatedAt(now);
            col.setUpdatedAt(now);
            boardColumnConfigMapper.insert(col);
        }

        // 2. 看板列合并配置
        List<BoardColumnMerge> merges = boardColumnMergeMapper.selectList(
                new LambdaQueryWrapper<BoardColumnMerge>().eq(BoardColumnMerge::getProjectId, sourceId));
        for (BoardColumnMerge src : merges) {
            BoardColumnMerge merge = new BoardColumnMerge();
            merge.setProjectId(targetId);
            merge.setMergeGroupId(src.getMergeGroupId());
            merge.setMergeTitle(src.getMergeTitle());
            merge.setStatusId(src.getStatusId());
            merge.setSortOrder(src.getSortOrder());
            merge.setCreatedAt(now);
            boardColumnMergeMapper.insert(merge);
        }

        // 3. 看板卡片配置
        BoardCardConfig cardConfig = boardCardConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardCardConfig>().eq(BoardCardConfig::getProjectId, sourceId));
        if (cardConfig != null) {
            BoardCardConfig card = new BoardCardConfig();
            card.setProjectId(targetId);
            card.setVisibleFields(cardConfig.getVisibleFields());
            card.setColorScheme(cardConfig.getColorScheme());
            card.setCreatedAt(now);
            card.setUpdatedAt(now);
            boardCardConfigMapper.insert(card);
        }

        // 4. 看板基本设置
        BoardGeneralConfig generalConfig = boardGeneralConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardGeneralConfig>().eq(BoardGeneralConfig::getProjectId, sourceId));
        if (generalConfig != null) {
            BoardGeneralConfig gen = new BoardGeneralConfig();
            gen.setProjectId(targetId);
            gen.setName(generalConfig.getName());
            gen.setCanViewRoles(generalConfig.getCanViewRoles());
            gen.setCanEditRoles(generalConfig.getCanEditRoles());
            gen.setCreatedAt(now);
            gen.setUpdatedAt(now);
            boardGeneralConfigMapper.insert(gen);
        }

        // 5. 看板泳道配置
        BoardSwimlaneConfig swimlaneConfig = boardSwimlaneConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardSwimlaneConfig>().eq(BoardSwimlaneConfig::getProjectId, sourceId));
        if (swimlaneConfig != null) {
            BoardSwimlaneConfig swim = new BoardSwimlaneConfig();
            swim.setProjectId(targetId);
            swim.setGroupByField(swimlaneConfig.getGroupByField());
            swim.setCreatedAt(now);
            swim.setUpdatedAt(now);
            boardSwimlaneConfigMapper.insert(swim);
        }

        log.info("Copied board config from project {} to {} (columns={}, merges={}, card={}, general={}, swimlane={})",
                sourceId, targetId, columns.size(), merges.size(),
                cardConfig != null, generalConfig != null, swimlaneConfig != null);
    }

    private void copyTransitionActions(Long sourceId, Long targetId, Long userId, LocalDateTime now) {
        List<TransitionAction> actions = transitionActionMapper.selectList(
                new LambdaQueryWrapper<TransitionAction>().eq(TransitionAction::getProjectId, sourceId));

        for (TransitionAction src : actions) {
            TransitionAction action = new TransitionAction();
            action.setProjectId(targetId);
            action.setIssueType(src.getIssueType());
            action.setOldStatusId(src.getOldStatusId());
            action.setNewStatusId(src.getNewStatusId());
            action.setActionType(src.getActionType());
            action.setActionConfig(src.getActionConfig());
            action.setSortOrder(src.getSortOrder());
            action.setEnabled(src.getEnabled());
            action.setCreatedBy(userId);
            action.setCreatedAt(now);
            action.setUpdatedAt(now);
            transitionActionMapper.insert(action);
        }
        log.info("Copied {} transition actions from project {} to {}", actions.size(), sourceId, targetId);
    }

    private void copyMembers(Long sourceId, Long targetId, Long currentUserId, LocalDateTime now) {
        List<ProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, sourceId));

        int copied = 0;
        for (ProjectMember src : members) {
            // 跳过当前用户（已作为管理员添加）
            if (src.getUserId().equals(currentUserId)) continue;

            ProjectMember member = new ProjectMember();
            member.setProjectId(targetId);
            member.setUserId(src.getUserId());
            member.setRoleId(src.getRoleId());
            member.setJoinedAt(now);
            projectMemberMapper.insert(member);
            copied++;
        }
        log.info("Copied {} members from project {} to {}", copied, sourceId, targetId);
    }

    private void copySavedQueries(Long sourceId, Long targetId, Long currentUserId, LocalDateTime now) {
        // 只复制共享查询（个人查询不复制）
        List<SavedQuery> queries = savedQueryMapper.selectList(
                new LambdaQueryWrapper<SavedQuery>()
                        .eq(SavedQuery::getProjectId, sourceId)
                        .eq(SavedQuery::getShared, true));

        for (SavedQuery src : queries) {
            SavedQuery query = new SavedQuery();
            query.setName(src.getName());
            query.setProjectId(targetId);
            query.setUserId(currentUserId);
            query.setShared(true);
            query.setPinned(src.getPinned());
            query.setFolder(src.getFolder());
            query.setFilters(src.getFilters());
            query.setColumns(src.getColumns());
            query.setSortCriteria(src.getSortCriteria());
            query.setGroupBy(src.getGroupBy());
            query.setIcon(src.getIcon());
            query.setSortOrder(src.getSortOrder());
            query.setCreatedAt(now);
            query.setUpdatedAt(now);
            savedQueryMapper.insert(query);
        }
        log.info("Copied {} shared queries from project {} to {}", queries.size(), sourceId, targetId);
    }
}
