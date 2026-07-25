package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueAttachmentMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.workflow.entity.WorkflowDefinition;
import com.trackflow.workflow.mapper.WorkflowDefinitionMapper;
import com.trackflow.workflow.mapper.ProjectWorkflowMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.entity.SprintStatus;
import com.trackflow.timeentry.entity.TimeEntry;
import com.trackflow.timeentry.mapper.TimeEntryMapper;
import com.trackflow.project.converter.ProjectConverter;
import com.trackflow.project.dto.AddMemberDTO;
import com.trackflow.project.dto.CreateProjectDTO;
import com.trackflow.project.dto.UpdateProjectDTO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.entity.ProjectStatus;
import com.trackflow.project.entity.ProjectVisibility;
import com.trackflow.project.entity.UserProjectFavorite;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.project.mapper.UserProjectFavoriteMapper;
import com.trackflow.project.vo.ProjectDeletePreCheckVO;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trackflow.common.event.ProjectAttachmentCleanupEvent;
import com.trackflow.common.event.ProjectNotificationEvent;
import com.trackflow.integration.entity.NotificationType;

import com.trackflow.project.vo.ProjectDetailVO;
import com.trackflow.project.vo.ProjectGroupMemberVO;
import com.trackflow.project.vo.ProjectMemberVO;
import com.trackflow.project.vo.ProjectMembersViewVO;
import com.trackflow.project.vo.ProjectStatisticsVO;
import com.trackflow.project.vo.ProjectTrashSettingsVO;
import com.trackflow.project.vo.ProjectVO;
import com.trackflow.project.dto.AddGroupMemberDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 项目管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ProjectService {

    private static final Long PROJECT_ADMIN_ROLE_ID = 2L;
    private static final String ACCESSIBLE_PROJECTS_CACHE_PREFIX = "accessible_projects:";

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final UserProjectFavoriteMapper favoriteMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final ProjectConverter projectConverter;
    private final PermissionService permissionService;
    private final StringRedisTemplate redisTemplate;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final IssueAttachmentMapper issueAttachmentMapper;
    private final SprintMapper sprintMapper;
    private final TimeEntryMapper timeEntryMapper;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final ProjectWorkflowMapper projectWorkflowMapper;
    private final ProjectActivityService projectActivityService;
    private final ProjectInitializationService projectInitializationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final com.trackflow.integration.service.MutedThreadService mutedThreadService;
    private final com.trackflow.system.service.GlobalMemberService globalMemberService;
    private final com.trackflow.project.service.ProjectModuleService projectModuleService;
    private final com.trackflow.customfield.mapper.CustomFieldDefinitionMapper customFieldDefinitionMapper;
    private final com.trackflow.customfield.mapper.CustomFieldProjectMapper customFieldProjectMapper;
    private final com.trackflow.system.mapper.UserGroupRoleMapper userGroupRoleMapper;
    private final com.trackflow.system.mapper.UserGroupMapper userGroupMapper;
    private final com.trackflow.system.mapper.UserGroupMemberMapper userGroupMemberMapper;

    /**
     * 创建项目
     */
    @Transactional(rollbackFor = Exception.class)
    public Project create(CreateProjectDTO dto) {
        // Key 唯一性检查（不区分大小写）
        Long count = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getKey, dto.getKey().toUpperCase())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.PROJECT_KEY_DUPLICATE);
        }

        // Name 唯一性检查（不区分大小写，对标 YouTrack: "The project name must be unique."）
        Long nameCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().apply("LOWER(name) = LOWER({0})", dto.getName().trim())
        );
        if (nameCount > 0) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_DUPLICATE);
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // leadId 校验：如果指定了 leadId，校验用户存在且状态为 active
        Long leadId = dto.getLeadId();
        if (leadId != null) {
            SysUser leadUser = userMapper.selectById(leadId);
            if (leadUser == null || !"active".equals(leadUser.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "指定的项目负责人用户不存在或已禁用");
            }
        } else {
            leadId = currentUserId;
        }

        Project project = new Project();
        project.setName(dto.getName());
        project.setKey(dto.getKey().toUpperCase());
        project.setDescription(dto.getDescription());
        project.setLeadId(leadId);
        project.setOrgId(dto.getOrgId());
        project.setStatus(ProjectStatus.ACTIVE);
        project.setVisibility(ProjectVisibility.PRIVATE);
        project.setIssueSequence(dto.getStartingNumber() != null ? dto.getStartingNumber().intValue() : 0);
        projectMapper.insert(project);

        // 自动添加创建者为项目管理员
        if (currentUserId != null) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(project.getId());
            member.setUserId(currentUserId);
            member.setRoleId(PROJECT_ADMIN_ROLE_ID);
            member.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(member);
        }

        // 如果 leadId 不是创建者本人，也将 lead 添加为项目管理员
        if (leadId != null && !leadId.equals(currentUserId)) {
            ProjectMember leadMember = new ProjectMember();
            leadMember.setProjectId(project.getId());
            leadMember.setUserId(leadId);
            leadMember.setRoleId(PROJECT_ADMIN_ROLE_ID);
            leadMember.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(leadMember);
        }

        // 根据模板类型初始化项目（工作流、看板列配置、默认 Saved Query 等）
        projectInitializationService.initialize(project.getId(), dto.getTemplate(),
                project.getKey(), currentUserId);

        // 初始化项目默认启用模块（全部启用）
        projectModuleService.initializeDefaultModules(project.getId());

        // 同步全局分配的成员到新项目
        globalMemberService.syncGlobalMembersToProject(project.getId());

        // 自动附加 isAutoAttach=true 的自定义字段到新项目（YouTrack Auto-attach 行为）
        autoAttachCustomFieldsToProject(project.getId());

        return project;
    }

    /**
     * 自动附加 isAutoAttach=true 的自定义字段到新创建的项目。
     * 参考 YouTrack 行为：Enable auto-attach 使字段自动附加到新创建的项目。
     */
    private void autoAttachCustomFieldsToProject(Long projectId) {
        List<com.trackflow.customfield.entity.CustomFieldDefinition> autoAttachFields =
                customFieldDefinitionMapper.selectList(
                        new LambdaQueryWrapper<com.trackflow.customfield.entity.CustomFieldDefinition>()
                                .eq(com.trackflow.customfield.entity.CustomFieldDefinition::getIsAutoAttach, true)
                                .orderByAsc(com.trackflow.customfield.entity.CustomFieldDefinition::getPosition));

        for (int i = 0; i < autoAttachFields.size(); i++) {
            com.trackflow.customfield.entity.CustomFieldDefinition field = autoAttachFields.get(i);
            // 跳过已经是 isForAll=true 的字段（它们已经通过全局逻辑在项目中可见）
            if (Boolean.TRUE.equals(field.getIsForAll())) {
                continue;
            }
            // 创建 custom_field_project 关联记录
            com.trackflow.customfield.entity.CustomFieldProject mapping = new com.trackflow.customfield.entity.CustomFieldProject();
            mapping.setCustomFieldId(field.getId());
            mapping.setProjectId(projectId);
            mapping.setPosition(i);
            customFieldProjectMapper.insert(mapping);
        }

        if (!autoAttachFields.isEmpty()) {
            log.info("自动附加 {} 个自定义字段到新项目 {}", autoAttachFields.size(), projectId);
        }
    }

    /**
     * 项目列表（只返回用户有权限的项目）
     * 包含：成员项目 + internal/public 项目（对已登录非成员可见）
     */
    public Page<Project> list(Page<Project> page, String keyword, String status, Long userId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();

        // 非系统管理员：直接成员项目 + 通过用户组获得的项目 + internal/public 项目
        if (userId != null && !permissionService.isSystemAdmin(userId)) {
            List<Long> memberProjectIds = memberMapper.selectProjectIdsByUserId(userId);
            List<Long> groupProjectIds = getProjectIdsByUserViaGroups(userId);

            // 合并去重
            Set<Long> allAccessibleIds = new java.util.LinkedHashSet<>(memberProjectIds);
            allAccessibleIds.addAll(groupProjectIds);

            // 显示：用户可访问的项目 OR visibility 为 internal/public 的项目
            wrapper.and(w -> {
                if (!allAccessibleIds.isEmpty()) {
                    w.in(Project::getId, allAccessibleIds)
                     .or()
                     .in(Project::getVisibility, List.of(ProjectVisibility.INTERNAL, ProjectVisibility.PUBLIC));
                } else {
                    w.in(Project::getVisibility, List.of(ProjectVisibility.INTERNAL, ProjectVisibility.PUBLIC));
                }
            });
        }

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Project::getName, keyword).or().like(Project::getKey, keyword));
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(Project::getStatus, ProjectStatus.fromValue(status));
        } else {
            // 默认不显示归档项目
            wrapper.eq(Project::getStatus, ProjectStatus.ACTIVE);
        }

        wrapper.orderByDesc(Project::getCreatedAt);
        return projectMapper.selectPage(page, wrapper);
    }

    /**
     * 批量填充项目 VO 的成员摘要信息（memberCount + topMembers）。
     * 使用 SQL 聚合查询替代全量加载——COUNT(DISTINCT) 统计成员数，
     * ROW_NUMBER() 窗口函数获取每个项目前 5 名成员的 displayName。
     */
    public void populateMemberSummary(List<ProjectVO> voList) {
        if (voList == null || voList.isEmpty()) return;

        List<Long> projectIds = voList.stream()
                .map(vo -> Long.valueOf(vo.getId()))
                .toList();

        // 1. SQL 聚合：一次查询获取每个项目的去重成员数
        Map<Long, Integer> countMap = memberMapper.countDistinctUsersByProjects(projectIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.trackflow.project.mapper.result.MemberCountRow::getProjectId,
                        com.trackflow.project.mapper.result.MemberCountRow::getMemberCount
                ));

        // 2. SQL 窗口函数：一次查询获取每个项目前 5 名成员的 displayName
        Map<Long, List<String>> topMembersMap = memberMapper.selectTopMembersByProjects(projectIds, 5)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.trackflow.project.mapper.result.TopMemberRow::getProjectId,
                        java.util.stream.Collectors.mapping(
                                row -> {
                                    String name = row.getDisplayName();
                                    return (name != null && !name.isBlank()) ? name : "?";
                                },
                                java.util.stream.Collectors.toList()
                        )
                ));

        // 3. 填充 VO
        for (ProjectVO vo : voList) {
            Long pid = Long.valueOf(vo.getId());
            vo.setMemberCount(countMap.getOrDefault(pid, 0));
            vo.setTopMembers(topMembersMap.getOrDefault(pid, List.of()));
        }
    }

    /**
     * 项目详情
     */
    public Project getById(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return project;
    }

    /**
     * 通过标识符（项目 Key 或数字 ID）解析项目 ID。
     * <p>
     * 解析策略（Key 优先）：
     * 1. 如果 identifier 以字母开头（符合项目 Key 格式） → 优先按 Key（不区分大小写）查询
     * 2. 尝试按数字 ID 查询
     * 3. 两者都未命中 → 抛出 404
     * <p>
     * 此策略避免了使用 NumberFormatException 作为控制流的 anti-pattern，
     * 同时消除了纯数字 Key（理论上）无法路由的问题。
     *
     * @param identifier 项目 Key（如 "DE4"）或数字 ID 字符串
     * @return 项目数据库 ID
     * @throws BusinessException 如果项目不存在
     */
    public Long resolveProjectId(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目标识不能为空");
        }

        // Key 优先策略：项目 Key 必须以字母开头（正则: ^[A-Z][A-Z0-9_]*$）
        if (Character.isLetter(identifier.charAt(0))) {
            // 优先按 Key 查询（不区分大小写）
            Project project = projectMapper.selectOne(
                    new LambdaQueryWrapper<Project>()
                            .apply("LOWER(\"key\") = LOWER({0})", identifier)
            );
            if (project != null) {
                return project.getId();
            }
        }

        // 尝试按数字 ID 查询
        try {
            Long id = Long.parseLong(identifier);
            Project project = projectMapper.selectById(id);
            if (project != null) {
                return project.getId();
            }
        } catch (NumberFormatException ignored) {
            // 非数字格式，忽略
        }

        // 两者都未命中
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在: " + identifier);
    }

    /**
     * 通过标识符获取项目实体
     */
    public Project getByIdentifier(String identifier) {
        Long id = resolveProjectId(identifier);
        return projectMapper.selectById(id);
    }

    /**
     * 获取项目详情（含当前用户角色和成员统计）
     */
    public ProjectDetailVO getProjectDetail(Long projectId, Long currentUserId) {
        Project project = getById(projectId);

        // 使用 Converter 映射基础字段
        ProjectDetailVO vo = projectConverter.toDetailVO(project);

        // 查询唯一成员总数（一个用户多角色只计一次）
        int memberCount = memberMapper.countDistinctUsers(projectId);
        vo.setMemberCount(memberCount);

        // 查询当前用户在项目中的角色（支持多角色）
        if (currentUserId != null) {
            if (permissionService.isSystemAdmin(currentUserId)) {
                vo.setMyRoleName("系统管理员");
                vo.setMyRoleCode("system_admin");
                vo.setMyRoleNames(java.util.List.of("系统管理员"));
                vo.setMyRoleCodes(java.util.List.of("system_admin"));
            } else {
                List<Long> roleIds = memberMapper.selectRoleIdsByUserAndProject(currentUserId, projectId);
                if (!roleIds.isEmpty()) {
                    // 批量查询所有角色，支持多角色展示
                    List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
                    if (roles != null && !roles.isEmpty()) {
                        // 向后兼容：myRoleName/myRoleCode 填充第一个角色
                        vo.setMyRoleName(roles.get(0).getName());
                        vo.setMyRoleCode(roles.get(0).getCode());
                        // 多角色列表
                        vo.setMyRoleNames(roles.stream().map(SysRole::getName).toList());
                        vo.setMyRoleCodes(roles.stream().map(SysRole::getCode).toList());
                    }
                } else {
                    // 非成员但项目可见（internal/public）→ 显示 NonMember 角色
                    ProjectVisibility visibility = project.getVisibility();
                    if (ProjectVisibility.INTERNAL == visibility || ProjectVisibility.PUBLIC == visibility) {
                        vo.setMyRoleName("非成员");
                        vo.setMyRoleCode("non_member");
                        vo.setMyRoleNames(java.util.List.of("非成员"));
                        vo.setMyRoleCodes(java.util.List.of("non_member"));
                    }
                }
            }
        }

        // 查询负责人名称和状态
        if (project.getLeadId() != null) {
            SysUser lead = userMapper.selectById(project.getLeadId());
            if (lead != null) {
                vo.setLeadName(lead.getDisplayName());
                vo.setLeadStatus(lead.getStatus());
            }
        }

        return vo;
    }

    /**
     * 更新项目
     */
    @Transactional(rollbackFor = Exception.class)
    public Project update(Long id, UpdateProjectDTO dto) {
        Project project = getById(id);
        // 归档项目不允许修改
        assertProjectActive(id);

        // 项目 Key 创建后不可修改——明确拒绝变更请求
        if (dto.getKey() != null && !dto.getKey().equals(project.getKey())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目标识（Key）创建后不可修改");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 名称变更
        if (dto.getName() != null) {
            String trimmedName = dto.getName().trim();
            if (trimmedName.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "项目名称不能为空");
            }
            if (!trimmedName.equals(project.getName())) {
                // 名称唯一性检查（排除当前项目自身，不区分大小写）
                Long nameCount = projectMapper.selectCount(
                        new LambdaQueryWrapper<Project>()
                                .apply("LOWER(name) = LOWER({0})", trimmedName)
                                .ne(Project::getId, id)
                );
                if (nameCount > 0) {
                    throw new BusinessException(ErrorCode.PROJECT_NAME_DUPLICATE);
                }
                String oldName = project.getName();
                project.setName(trimmedName);
                Map<String, Object> detail = new java.util.LinkedHashMap<>();
                detail.put("field", "name");
                detail.put("old_value", oldName);
                detail.put("new_value", trimmedName);
                projectActivityService.log(id, currentUserId, "update_project", null, detail);
            }
        }

        // 描述变更
        if (dto.getDescription() != null) {
            String trimmedDesc = dto.getDescription().trim();
            if (!trimmedDesc.equals(project.getDescription() != null ? project.getDescription() : "")) {
                String oldDesc = project.getDescription();
                project.setDescription(trimmedDesc);
                Map<String, Object> detail = new java.util.LinkedHashMap<>();
                detail.put("field", "description");
                detail.put("old_value", oldDesc != null ? oldDesc : "");
                detail.put("new_value", trimmedDesc);
                projectActivityService.log(id, currentUserId, "update_project", null, detail);
            }
        }

        // 可见性变更：事务提交后失效所有用户的 accessible_projects 缓存
        if (dto.getVisibility() != null) {
            ProjectVisibility newVisibility = ProjectVisibility.fromValue(dto.getVisibility());
            if (newVisibility != project.getVisibility()) {
                ProjectVisibility oldVisibility = project.getVisibility();
                String oldVisibilityValue = oldVisibility.getValue();
                project.setVisibility(newVisibility);

                // 发布事件，由 ProjectCacheEventListener 在事务提交后执行缓存清理
                eventPublisher.publishEvent(new com.trackflow.common.event.ProjectVisibilityChangedEvent(
                        id, oldVisibilityValue, newVisibility.getValue()));

                // 计算权限影响范围（非成员用户数）
                long totalUsers = userMapper.selectCount(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getStatus, "active"));
                long memberCount = memberMapper.selectCount(
                        new LambdaQueryWrapper<ProjectMember>()
                                .eq(ProjectMember::getProjectId, id));
                long affectedNonMembers = Math.max(0, totalUsers - memberCount);

                Map<String, Object> detail = new java.util.LinkedHashMap<>();
                detail.put("field", "visibility");
                detail.put("old_value", oldVisibilityValue);
                detail.put("new_value", newVisibility.getValue());
                detail.put("affected_non_members", affectedNonMembers);

                // 权限扩散警告：private → internal/public
                if (oldVisibility == ProjectVisibility.PRIVATE
                        && (newVisibility == ProjectVisibility.INTERNAL || newVisibility == ProjectVisibility.PUBLIC)) {
                    detail.put("security_impact", "permissions_expanded");
                    detail.put("permissions_granted", "issue:view,issue:comment,query:create,report:view,sprint:view,project:view");
                    log.warn("Project visibility expanded (projectId={}, {} → {}): {} non-member users now have NonMember permissions",
                            id, oldVisibilityValue, newVisibility.getValue(), affectedNonMembers);
                }
                // 权限收紧：internal/public → private
                else if (newVisibility == ProjectVisibility.PRIVATE) {
                    detail.put("security_impact", "permissions_revoked");
                    log.info("Project visibility restricted (projectId={}, {} → {}): {} non-member users lost access",
                            id, oldVisibilityValue, newVisibility.getValue(), affectedNonMembers);
                }

                projectActivityService.log(id, currentUserId, "change_visibility", null, detail);
            }
        }

        // 组织变更
        if (dto.getOrgId() != null) {
            Long newOrgId = dto.getOrgId();
            // 传 0 表示清除组织关联
            if (newOrgId == 0L) {
                newOrgId = null;
            }
            if ((newOrgId == null && project.getOrgId() != null)
                    || (newOrgId != null && !newOrgId.equals(project.getOrgId()))) {
                Long oldOrgId = project.getOrgId();
                project.setOrgId(newOrgId);
                Map<String, Object> detail = new java.util.LinkedHashMap<>();
                detail.put("field", "orgId");
                detail.put("old_value", oldOrgId != null ? oldOrgId.toString() : null);
                detail.put("new_value", newOrgId != null ? newOrgId.toString() : null);
                projectActivityService.log(id, currentUserId, "update_project", null, detail);
            }
        }

        // 负责人变更：需要完整的业务校验和权限联动
        if (dto.getLeadId() != null && !dto.getLeadId().equals(project.getLeadId())) {
            changeLead(project, dto.getLeadId());
        }

        projectMapper.updateById(project);
        return project;
    }

    /**
     * 变更项目负责人
     * 1. 校验新负责人是项目成员
     * 2. 校验新负责人用户状态为 active
     * 3. 如果新负责人当前角色不是 project_admin，自动升级
     * 4. 记录活动日志
     */
    private void changeLead(Project project, Long newLeadId) {
        Long projectId = project.getId();
        Long oldLeadId = project.getLeadId();

        // 1. 校验新负责人用户存在且状态为 active
        SysUser newLead = userMapper.selectById(newLeadId);
        if (newLead == null || !"active".equals(newLead.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新负责人用户不存在或已禁用");
        }

        // 2. 校验新负责人必须是项目成员
        Long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, newLeadId)
        );
        if (memberCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新负责人必须是该项目的成员");
        }

        // 3. 如果新负责人当前没有 project_admin 角色，自动添加
        List<Long> newLeadRoleIds = memberMapper.selectRoleIdsByUserAndProject(newLeadId, projectId);
        if (!newLeadRoleIds.contains(PROJECT_ADMIN_ROLE_ID)) {
            // 添加 project_admin 角色记录
            ProjectMember adminMember = new ProjectMember();
            adminMember.setProjectId(projectId);
            adminMember.setUserId(newLeadId);
            adminMember.setRoleId(PROJECT_ADMIN_ROLE_ID);
            adminMember.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(adminMember);
            permissionService.invalidateCache(newLeadId);

            // 记录角色自动升级的活动日志
            SysRole adminRole = roleMapper.selectById(PROJECT_ADMIN_ROLE_ID);
            List<String> oldRoleNames = newLeadRoleIds.stream()
                    .map(rid -> roleMapper.selectById(rid))
                    .filter(Objects::nonNull)
                    .map(SysRole::getName)
                    .toList();
            Map<String, Object> roleDetailMap = new java.util.LinkedHashMap<>();
            roleDetailMap.put("old_role_ids", newLeadRoleIds);
            roleDetailMap.put("old_role_names", String.join(", ", oldRoleNames));
            roleDetailMap.put("added_role_id", PROJECT_ADMIN_ROLE_ID);
            roleDetailMap.put("added_role_name", adminRole != null ? adminRole.getName() : "项目管理员");
            roleDetailMap.put("reason", "lead_promotion");
            Long currentUserId = SecurityUtils.getCurrentUserId();
            projectActivityService.log(projectId, currentUserId, "change_role", newLeadId, roleDetailMap);
        }

        // 4. 更新负责人字段
        project.setLeadId(newLeadId);

        // 5. 记录负责人变更活动日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String oldLeadName = "";
        if (oldLeadId != null) {
            SysUser oldLead = userMapper.selectById(oldLeadId);
            if (oldLead != null) {
                oldLeadName = oldLead.getDisplayName() != null ? oldLead.getDisplayName() : oldLead.getUsername();
            }
        }
        String newLeadName = newLead.getDisplayName() != null ? newLead.getDisplayName() : newLead.getUsername();
        Map<String, Object> leadDetailMap = new java.util.LinkedHashMap<>();
        leadDetailMap.put("old_lead_id", oldLeadId);
        leadDetailMap.put("old_lead_name", oldLeadName);
        leadDetailMap.put("new_lead_id", newLeadId);
        leadDetailMap.put("new_lead_name", newLeadName);
        projectActivityService.log(projectId, currentUserId, "change_lead", newLeadId, leadDetailMap);

        // 6. 通知新负责人 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.NewLead(newLeadId, currentUserId, projectId, project.getName()));

        // 7. 通知旧负责人（如果存在且不同）— 事务提交后触发
        if (oldLeadId != null && !oldLeadId.equals(currentUserId)) {
            eventPublisher.publishEvent(new ProjectNotificationEvent.OldLead(oldLeadId, currentUserId, projectId,
                    project.getName(), newLeadName));
        }
    }

    /**
     * 归档项目
     * <p>
     * 归档流程：
     * 1. 校验项目当前状态（必须为 active）
     * 2. 将项目状态设为 archived
     * 3. 将项目内 ACTIVE 状态的 Sprint 自动变为 PLANNED
     * 4. 记录项目活动日志
     * 5. 通知所有项目成员
     */
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        Project project = getById(id);
        if (ProjectStatus.ARCHIVED == project.getStatus()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目已处于归档状态");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 归档项目
        project.setStatus(ProjectStatus.ARCHIVED);
        projectMapper.updateById(project);

        // 2. 暂停活跃 Sprint（ACTIVE → PLANNED）
        int suspendedSprintCount = suspendActiveSprintsForProject(id);

        // 3. 记录活动日志
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        if (suspendedSprintCount > 0) {
            detail.put("suspended_sprint_count", suspendedSprintCount);
        }
        projectActivityService.log(id, currentUserId, "archive_project", null,
                detail.isEmpty() ? null : detail);

        // 4. 通知所有项目成员 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.LifecycleEvent(id, currentUserId,
                "项目归档通知", "项目「" + project.getName() + "」已被归档", NotificationType.project_archived));
    }

    /**
     * 恢复项目
     * <p>
     * 恢复流程：
     * 1. 校验项目当前状态（必须为 archived）
     * 2. 将项目状态设为 active
     * 3. 记录项目活动日志
     * 4. 通知所有项目成员
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        Project project = getById(id);
        if (ProjectStatus.ARCHIVED != project.getStatus()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能恢复归档状态的项目");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 恢复项目
        project.setStatus(ProjectStatus.ACTIVE);
        projectMapper.updateById(project);

        // 2. 记录活动日志
        projectActivityService.log(id, currentUserId, "restore_project", null, null);

        // 3. 通知所有项目成员 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.LifecycleEvent(id, currentUserId,
                "项目恢复通知", "项目「" + project.getName() + "」已从归档状态恢复", NotificationType.project_restored));
    }

    /**
     * 暂停项目内所有活跃 Sprint（ACTIVE → PLANNED）
     *
     * @return 受影响的 Sprint 数量
     */
    private int suspendActiveSprintsForProject(Long projectId) {
        LambdaUpdateWrapper<Sprint> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Sprint::getProjectId, projectId)
                .eq(Sprint::getStatus, SprintStatus.ACTIVE)
                .set(Sprint::getStatus, SprintStatus.PLANNED)
                .set(Sprint::getUpdatedAt, LocalDateTime.now());
        return sprintMapper.update(null, updateWrapper);
    }

    // ========== 回收站保留策略设置 ==========

    /**
     * 获取项目回收站保留策略
     */
    public ProjectTrashSettingsVO getTrashSettings(Long id) {
        Project project = getById(id);
        ProjectTrashSettingsVO vo = new ProjectTrashSettingsVO();
        vo.setTrashRetentionDays(parseRetentionDays(project.getSettings()));
        return vo;
    }

    /**
     * 更新项目回收站保留策略
     * @param days 保留天数，0 表示永久保留
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTrashSettings(Long id, int days) {
        updateProjectSetting(id, "trashRetentionDays", days);
    }

    /**
     * 从 settings JSON 解析保留天数
     */
    public int parseRetentionDays(String settingsJson) {
        if (settingsJson == null || settingsJson.isBlank() || "{}".equals(settingsJson)) {
            return 30;
        }
        try {
            var node = objectMapper.readTree(settingsJson);
            var retentionNode = node.get("trashRetentionDays");
            if (retentionNode == null) return 30;
            if (retentionNode.isTextual() && "forever".equalsIgnoreCase(retentionNode.asText())) return 0;
            int days = retentionNode.asInt(-1);
            return days >= 0 ? days : 30;
        } catch (Exception e) {
            return 30;
        }
    }

    // ========== 成员管理 ==========

    /**
     * 获取项目成员列表（包含用户信息）
     */
    public List<ProjectMemberVO> listMembersVO(Long projectId) {
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId)
        );
        if (members.isEmpty()) return List.of();

        // 获取用户信息
        List<Long> userIds = members.stream().map(ProjectMember::getUserId).distinct().toList();
        var users = userMapper.selectBatchIds(userIds);
        Map<Long, com.trackflow.system.entity.SysUser> userMap = users.stream()
                .collect(java.util.stream.Collectors.toMap(com.trackflow.system.entity.SysUser::getId, u -> u));

        // 获取角色信息（用于填充 roleNames）
        List<Long> roleIds = members.stream().map(ProjectMember::getRoleId).distinct().toList();
        var roles = roleMapper.selectBatchIds(roleIds);
        Map<Long, String> roleNameMap = roles.stream()
                .collect(java.util.stream.Collectors.toMap(SysRole::getId, SysRole::getName));

        // 按 userId 聚合（一个用户一条 VO，含多角色）
        Map<Long, List<ProjectMember>> membersByUser = members.stream()
                .collect(java.util.stream.Collectors.groupingBy(ProjectMember::getUserId));

        return membersByUser.entrySet().stream().map(entry -> {
            Long userId = entry.getKey();
            List<ProjectMember> userMembers = entry.getValue();
            ProjectMember first = userMembers.get(0);

            ProjectMemberVO vo = new ProjectMemberVO();
            vo.setId(first.getId() != null ? first.getId().toString() : null);
            vo.setProjectId(first.getProjectId() != null ? first.getProjectId().toString() : null);
            vo.setUserId(userId.toString());
            // 向后兼容：roleId 取第一个角色
            vo.setRoleId(first.getRoleId() != null ? first.getRoleId().toString() : null);
            // 多角色列表
            List<String> allRoleIds = userMembers.stream()
                    .map(m -> m.getRoleId().toString())
                    .toList();
            vo.setRoleIds(allRoleIds);
            List<String> allRoleNames = userMembers.stream()
                    .map(m -> roleNameMap.getOrDefault(m.getRoleId(), ""))
                    .filter(name -> !name.isEmpty())
                    .toList();
            vo.setRoleNames(allRoleNames);
            // 取最早的 joinedAt
            vo.setJoinedAt(userMembers.stream()
                    .map(ProjectMember::getJoinedAt)
                    .filter(java.util.Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null));
            var user = userMap.get(userId);
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setDisplayName(user.getDisplayName());
                vo.setEmail(user.getEmail());
            }
            return vo;
        }).toList();
    }

    /**
     * 获取项目中通过用户组获得访问权的组成员列表。
     * 查询 user_group_role 表中 project_id 匹配的组角色记录，
     * 并展开组成员信息。
     */
    public List<ProjectGroupMemberVO> listProjectGroupMembers(Long projectId) {
        // 1. 查询绑定到该项目的组角色分配
        List<com.trackflow.system.entity.UserGroupRole> groupRoles =
                userGroupRoleMapper.selectGroupRolesByProjectId(projectId);
        if (groupRoles.isEmpty()) return List.of();

        // 2. 收集组ID和角色ID
        List<Long> groupIds = groupRoles.stream()
                .map(com.trackflow.system.entity.UserGroupRole::getGroupId)
                .distinct().toList();
        List<Long> roleIds = groupRoles.stream()
                .map(com.trackflow.system.entity.UserGroupRole::getRoleId)
                .distinct().toList();

        // 3. 批量查询组信息
        var groups = userGroupMapper.selectBatchIds(groupIds);
        Map<Long, com.trackflow.system.entity.UserGroup> groupMap = groups.stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.trackflow.system.entity.UserGroup::getId, g -> g));

        // 4. 批量查询角色名称
        var roles = roleMapper.selectBatchIds(roleIds);
        Map<Long, String> roleNameMap = roles.stream()
                .collect(java.util.stream.Collectors.toMap(SysRole::getId, SysRole::getName));

        // 5. 查询组成员 - 避免 N+1
        Map<Long, List<Long>> groupMembersMap = new java.util.HashMap<>();
        Set<Long> allUserIds = new java.util.HashSet<>();
        for (Long groupId : groupIds) {
            List<com.trackflow.system.entity.UserGroupMember> members =
                    userGroupMemberMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.trackflow.system.entity.UserGroupMember>()
                                    .eq(com.trackflow.system.entity.UserGroupMember::getGroupId, groupId));
            List<Long> userIds = members.stream()
                    .map(com.trackflow.system.entity.UserGroupMember::getUserId).toList();
            groupMembersMap.put(groupId, userIds);
            allUserIds.addAll(userIds);
        }

        // 6. 批量查询用户信息
        Map<Long, com.trackflow.system.entity.SysUser> userMap = new java.util.HashMap<>();
        if (!allUserIds.isEmpty()) {
            var users = userMapper.selectBatchIds(allUserIds);
            userMap = users.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            com.trackflow.system.entity.SysUser::getId, u -> u));
        }

        // 7. 组装 VO
        final Map<Long, com.trackflow.system.entity.SysUser> finalUserMap = userMap;
        return groupRoles.stream().map(gr -> {
            ProjectGroupMemberVO vo = new ProjectGroupMemberVO();
            vo.setGroupId(gr.getGroupId().toString());
            vo.setProjectId(projectId.toString());
            vo.setRoleId(gr.getRoleId().toString());
            vo.setRoleName(roleNameMap.getOrDefault(gr.getRoleId(), ""));
            vo.setAssignedAt(gr.getCreatedAt());

            var group = groupMap.get(gr.getGroupId());
            if (group != null) {
                vo.setGroupName(group.getName());
            }

            // 展开组成员
            List<Long> memberUserIds = groupMembersMap.getOrDefault(gr.getGroupId(), List.of());
            List<ProjectGroupMemberVO.GroupUserVO> userVOs = memberUserIds.stream().map(uid -> {
                ProjectGroupMemberVO.GroupUserVO userVO = new ProjectGroupMemberVO.GroupUserVO();
                userVO.setUserId(uid.toString());
                var user = finalUserMap.get(uid);
                if (user != null) {
                    userVO.setUsername(user.getUsername());
                    userVO.setDisplayName(user.getDisplayName());
                    userVO.setEmail(user.getEmail());
                }
                return userVO;
            }).toList();
            vo.setUsers(userVOs);

            return vo;
        }).toList();
    }

    /**
     * 获取项目成员完整视图（对标 YouTrack People 页面）
     * 包含直接成员和组成员两部分
     */
    public ProjectMembersViewVO listMembersFullView(Long projectId) {
        ProjectMembersViewVO view = new ProjectMembersViewVO();
        view.setDirectMembers(listMembersVO(projectId));
        view.setGroupMembers(listProjectGroupMembers(projectId));
        return view;
    }

    /**
     * 添加用户组到项目团队
     */
    @Transactional(rollbackFor = Exception.class)
    public void addGroupMember(Long projectId, AddGroupMemberDTO dto) {
        // 检查组是否存在
        var group = userGroupMapper.selectById(dto.getGroupId());
        if (group == null) {
            throw new com.trackflow.common.exception.BusinessException(
                    com.trackflow.common.exception.ErrorCode.RESOURCE_NOT_FOUND, "用户组不存在: " + dto.getGroupId());
        }
        // 检查角色是否存在
        var role = roleMapper.selectById(dto.getRoleId());
        if (role == null) {
            throw new com.trackflow.common.exception.BusinessException(
                    com.trackflow.common.exception.ErrorCode.RESOURCE_NOT_FOUND, "角色不存在: " + dto.getRoleId());
        }
        // 检查是否已经分配
        var existing = userGroupRoleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.trackflow.system.entity.UserGroupRole>()
                        .eq(com.trackflow.system.entity.UserGroupRole::getGroupId, dto.getGroupId())
                        .eq(com.trackflow.system.entity.UserGroupRole::getProjectId, projectId)
        );
        if (!existing.isEmpty()) {
            throw new com.trackflow.common.exception.BusinessException(
                    com.trackflow.common.exception.ErrorCode.BAD_REQUEST, "该用户组已在项目团队中");
        }
        // 插入记录
        com.trackflow.system.entity.UserGroupRole ugr = new com.trackflow.system.entity.UserGroupRole();
        ugr.setGroupId(dto.getGroupId());
        ugr.setRoleId(dto.getRoleId());
        ugr.setProjectId(projectId);
        userGroupRoleMapper.insert(ugr);

        // 失效组内所有用户的权限缓存
        List<Long> userIds = userGroupRoleMapper.selectUserIdsByGroupId(dto.getGroupId());
        for (Long userId : userIds) {
            permissionService.invalidateCache(userId);
        }

        log.info("添加用户组 {} 到项目 {} 团队，角色: {}", dto.getGroupId(), projectId, dto.getRoleId());
    }

    /**
     * 从项目团队中移除用户组
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeGroupMember(Long projectId, Long groupId) {
        // 查找并删除
        var records = userGroupRoleMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.trackflow.system.entity.UserGroupRole>()
                        .eq(com.trackflow.system.entity.UserGroupRole::getGroupId, groupId)
                        .eq(com.trackflow.system.entity.UserGroupRole::getProjectId, projectId)
        );
        if (records.isEmpty()) {
            throw new com.trackflow.common.exception.BusinessException(
                    com.trackflow.common.exception.ErrorCode.RESOURCE_NOT_FOUND, "该用户组不在项目团队中");
        }
        for (var record : records) {
            userGroupRoleMapper.deleteById(record.getId());
        }

        // 失效组内所有用户的权限缓存
        List<Long> userIds = userGroupRoleMapper.selectUserIdsByGroupId(groupId);
        for (Long userId : userIds) {
            permissionService.invalidateCache(userId);
        }

        log.info("从项目 {} 团队中移除用户组 {}", projectId, groupId);
    }

    /**
     * 获取项目中可被分配工单的成员列表。
     * 仅返回拥有 issue:edit 权限的成员（排除观察者、测试人员等不具备编辑能力的角色）。
     * 系统管理员即使不是项目成员也可被分配（拥有全部权限）。
     *
     * 同时返回"已离开项目但仍有工单被分配"的历史用户（标记为 formerMember），
     * 参照 YouTrack 行为：移除成员不自动清除 Assignee 候选值，保留历史有效性。
     */
    public List<ProjectMemberVO> listAssignableMembersVO(Long projectId) {
        // 查询项目中拥有 issue:edit 权限的成员 user_id
        List<Long> assignableUserIds = memberMapper.selectUserIdsWithPermission(projectId, "issue:edit");

        // 查询已离开项目但仍有工单被分配的历史 assignee
        List<Long> formerAssigneeIds = memberMapper.selectFormerAssigneeUserIds(projectId);

        // 合并活跃成员和历史 assignee 用户 ID（去重）
        Set<Long> allUserIds = new java.util.LinkedHashSet<>(assignableUserIds);
        allUserIds.addAll(formerAssigneeIds);

        if (allUserIds.isEmpty()) return List.of();

        // 获取用户信息
        var users = userMapper.selectBatchIds(new java.util.ArrayList<>(allUserIds));
        Map<Long, SysUser> userMap = users.stream()
                .filter(u -> !"disabled".equals(u.getStatus()))
                .collect(java.util.stream.Collectors.toMap(SysUser::getId, u -> u));

        if (userMap.isEmpty()) return List.of();

        // 获取活跃成员的成员记录（用于填充角色信息）
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .in(ProjectMember::getUserId, assignableUserIds.isEmpty() ? List.of(0L) : assignableUserIds)
        );

        // 获取角色名称
        List<Long> roleIds = members.stream().map(ProjectMember::getRoleId).distinct().toList();
        Map<Long, String> roleNameMap = java.util.Collections.emptyMap();
        if (!roleIds.isEmpty()) {
            var roles = roleMapper.selectBatchIds(roleIds);
            roleNameMap = roles.stream()
                    .collect(java.util.stream.Collectors.toMap(SysRole::getId, SysRole::getName));
        }

        // 按 userId 聚合活跃成员
        Map<Long, List<ProjectMember>> membersByUser = members.stream()
                .collect(java.util.stream.Collectors.groupingBy(ProjectMember::getUserId));

        // 构建结果列表
        List<ProjectMemberVO> result = new java.util.ArrayList<>();

        // 1. 添加活跃成员
        Map<Long, String> finalRoleNameMap = roleNameMap;
        membersByUser.entrySet().stream()
                .filter(entry -> userMap.containsKey(entry.getKey()))
                .forEach(entry -> {
                    Long userId = entry.getKey();
                    List<ProjectMember> userMembers = entry.getValue();
                    ProjectMember first = userMembers.get(0);

                    ProjectMemberVO vo = new ProjectMemberVO();
                    vo.setId(first.getId() != null ? first.getId().toString() : null);
                    vo.setProjectId(first.getProjectId() != null ? first.getProjectId().toString() : null);
                    vo.setUserId(userId.toString());
                    vo.setRoleId(first.getRoleId() != null ? first.getRoleId().toString() : null);
                    List<String> allRoleIds = userMembers.stream()
                            .map(m -> m.getRoleId().toString())
                            .toList();
                    vo.setRoleIds(allRoleIds);
                    List<String> allRoleNames = userMembers.stream()
                            .map(m -> finalRoleNameMap.getOrDefault(m.getRoleId(), ""))
                            .filter(name -> !name.isEmpty())
                            .toList();
                    vo.setRoleNames(allRoleNames);
                    vo.setJoinedAt(userMembers.stream()
                            .map(ProjectMember::getJoinedAt)
                            .filter(Objects::nonNull)
                            .min(LocalDateTime::compareTo)
                            .orElse(null));
                    var user = userMap.get(userId);
                    if (user != null) {
                        vo.setUsername(user.getUsername());
                        vo.setDisplayName(user.getDisplayName());
                        vo.setEmail(user.getEmail());
                    }
                    vo.setFormerMember(false);
                    result.add(vo);
                });

        // 2. 添加已离开项目的历史 assignee（标记 formerMember = true）
        for (Long formerUserId : formerAssigneeIds) {
            // 跳过已经在活跃成员列表中的用户
            if (membersByUser.containsKey(formerUserId)) continue;

            var user = userMap.get(formerUserId);
            if (user == null) continue;

            ProjectMemberVO vo = new ProjectMemberVO();
            vo.setProjectId(projectId.toString());
            vo.setUserId(formerUserId.toString());
            vo.setUsername(user.getUsername());
            vo.setDisplayName(user.getDisplayName());
            vo.setEmail(user.getEmail());
            vo.setRoleIds(List.of());
            vo.setRoleNames(List.of());
            vo.setFormerMember(true);
            result.add(vo);
        }

        return result;
    }

    /**
     * 检查指定用户在项目中是否可被分配工单（拥有 issue:edit 权限或为历史有效 assignee）。
     */
    public boolean isAssignableMember(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        // 系统管理员始终可被分配
        if (permissionService.isSystemAdmin(userId)) return true;
        // 检查是否在当前可分配成员列表中
        List<Long> assignableUserIds = memberMapper.selectUserIdsWithPermission(projectId, "issue:edit");
        if (assignableUserIds.contains(userId)) return true;
        // 检查是否为已离开项目但仍有工单被分配的历史 assignee
        List<Long> formerAssigneeIds = memberMapper.selectFormerAssigneeUserIds(projectId);
        return formerAssigneeIds.contains(userId);
    }

    /**
     * 添加项目成员（支持多角色）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long projectId, AddMemberDTO dto) {
        // 1. 校验项目状态（归档项目不允许添加成员）
        Project project = getById(projectId);
        if (ProjectStatus.ACTIVE != project.getStatus()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归档项目不允许管理成员");
        }

        // 2. 校验用户存在且状态为 active
        SysUser user = userMapper.selectById(dto.getUserId());
        if (user == null || !"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在或已禁用");
        }

        // 3. 获取有效角色列表
        List<Long> effectiveRoleIds = dto.getEffectiveRoleIds();
        if (effectiveRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要指定一个角色");
        }

        // 4. 校验所有角色类型必须为 project
        List<SysRole> roles = roleMapper.selectBatchIds(effectiveRoleIds);
        if (roles.size() != effectiveRoleIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效的角色ID");
        }
        for (SysRole role : roles) {
            if (!"project".equals(role.getRoleType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "只能分配项目角色，角色「" + role.getName() + "」不是项目角色");
            }
        }

        // 5. 查询该用户已有的角色（多角色场景下过滤已存在的）
        List<Long> existingRoleIds = memberMapper.selectRoleIdsByUserAndProject(dto.getUserId(), projectId);
        List<Long> newRoleIds = effectiveRoleIds.stream()
                .filter(rid -> !existingRoleIds.contains(rid))
                .toList();

        if (newRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户已拥有所有指定角色");
        }

        // 6. 插入新角色记录
        LocalDateTime now = LocalDateTime.now();
        List<String> addedRoleNames = new java.util.ArrayList<>();
        for (Long roleId : newRoleIds) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(projectId);
            member.setUserId(dto.getUserId());
            member.setRoleId(roleId);
            member.setJoinedAt(now);
            memberMapper.insert(member);
            // 记录角色名称
            roles.stream().filter(r -> r.getId().equals(roleId)).findFirst()
                    .ifPresent(r -> addedRoleNames.add(r.getName()));
        }

        // 失效权限缓存
        permissionService.invalidateCache(dto.getUserId());
        // 失效项目列表缓存
        redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + dto.getUserId());

        // 审计日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String roleNamesStr = String.join(", ", addedRoleNames);
        projectActivityService.log(projectId, currentUserId, "add_member", dto.getUserId(),
                Map.of("role_ids", newRoleIds, "role_names", roleNamesStr));

        // 通知被添加的用户 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.MemberAdded(dto.getUserId(), currentUserId, projectId,
                project.getName(), roleNamesStr));
    }

    /**
     * 更新成员角色（全量替换：设置用户在项目中的角色列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateMemberRole(Long projectId, Long userId, Long roleId) {
        return updateMemberRoles(projectId, userId, List.of(roleId));
    }

    /**
     * 更新成员角色（多角色版本：全量替换）
     * @return 因角色降级而被清空 assignee 的工单数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateMemberRoles(Long projectId, Long userId, List<Long> newRoleIds) {
        // 校验项目状态（归档项目不允许管理成员）
        Project project = getById(projectId);
        if (ProjectStatus.ACTIVE != project.getStatus()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归档项目不允许管理成员");
        }

        if (newRoleIds == null || newRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要指定一个角色");
        }

        // 去重（前端多选可能传入重复值）
        List<Long> distinctRoleIds = newRoleIds.stream().distinct().toList();

        // 校验所有角色类型必须为 project
        List<SysRole> newRoles = roleMapper.selectBatchIds(distinctRoleIds);
        if (newRoles.size() != distinctRoleIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效的角色ID");
        }
        for (SysRole role : newRoles) {
            if (!"project".equals(role.getRoleType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "只能分配项目角色");
            }
        }

        // 查询用户当前的成员记录
        List<ProjectMember> existingMembers = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (existingMembers.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该用户不是项目成员");
        }

        List<Long> oldRoleIds = existingMembers.stream().map(ProjectMember::getRoleId).toList();

        // 保护最后一个项目管理员：如果用户当前有 project_admin 角色，且新角色列表中没有，
        // 需要确认还有其他 project_admin
        if (oldRoleIds.contains(PROJECT_ADMIN_ROLE_ID) && !distinctRoleIds.contains(PROJECT_ADMIN_ROLE_ID)) {
            long adminCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, projectId)
                            .eq(ProjectMember::getRoleId, PROJECT_ADMIN_ROLE_ID)
            );
            // 如果所有 admin 记录都属于当前用户，则不允许移除 admin 角色
            long userAdminCount = existingMembers.stream()
                    .filter(m -> m.getRoleId().equals(PROJECT_ADMIN_ROLE_ID))
                    .count();
            if (adminCount <= userAdminCount) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移除项目中最后一个管理员的管理员角色");
            }
        }

        // 计算需要删除和新增的角色
        List<Long> toRemove = oldRoleIds.stream().filter(rid -> !distinctRoleIds.contains(rid)).toList();
        List<Long> toAdd = distinctRoleIds.stream().filter(rid -> !oldRoleIds.contains(rid)).toList();

        // 删除不再需要的角色记录
        if (!toRemove.isEmpty()) {
            memberMapper.delete(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, projectId)
                            .eq(ProjectMember::getUserId, userId)
                            .in(ProjectMember::getRoleId, toRemove)
            );
        }

        // 新增角色记录
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : toAdd) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(projectId);
            member.setUserId(userId);
            member.setRoleId(roleId);
            member.setJoinedAt(now);
            memberMapper.insert(member);
        }

        // 失效权限缓存
        permissionService.invalidateCache(userId);

        // 审计日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<String> oldRoleNames = oldRoleIds.stream()
                .map(rid -> roleMapper.selectById(rid))
                .filter(java.util.Objects::nonNull)
                .map(SysRole::getName)
                .toList();
        List<String> newRoleNames = newRoles.stream().map(SysRole::getName).toList();
        projectActivityService.log(projectId, currentUserId, "change_role", userId,
                Map.of("old_role_ids", oldRoleIds, "old_role_names", String.join(", ", oldRoleNames),
                       "new_role_ids", distinctRoleIds, "new_role_names", String.join(", ", newRoleNames)));

        // 通知角色变更的用户 — 事务提交后触发
        if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
            eventPublisher.publishEvent(new ProjectNotificationEvent.RoleChanged(userId, currentUserId, projectId,
                    project.getName(), String.join(", ", newRoleNames)));
        }

        // === 级联处理：如果用户失去 issue:edit 权限，清空其作为 assignee 的工单 ===
        int affectedIssueCount = 0;
        if (!toRemove.isEmpty()) {
            // 检查用户在新角色下是否仍有 issue:edit 权限
            List<Long> usersWithEditPermission = memberMapper.selectUserIdsWithPermission(projectId, "issue:edit");
            if (!usersWithEditPermission.contains(userId)) {
                // 用户已失去 issue:edit 权限 — 清空其在该项目中的 assignee 引用
                List<Issue> assignedIssues = issueMapper.selectList(
                        new LambdaQueryWrapper<Issue>()
                                .select(Issue::getId)
                                .eq(Issue::getProjectId, projectId)
                                .eq(Issue::getAssigneeId, userId)
                                .isNull(Issue::getDeletedAt)
                );
                affectedIssueCount = assignedIssues.size();
                if (affectedIssueCount > 0) {
                    LocalDateTime clearTime = LocalDateTime.now();
                    // 批量更新 assignee_id 为 null
                    issueMapper.update(null,
                            new LambdaUpdateWrapper<Issue>()
                                    .eq(Issue::getProjectId, projectId)
                                    .eq(Issue::getAssigneeId, userId)
                                    .isNull(Issue::getDeletedAt)
                                    .set(Issue::getAssigneeId, null)
                                    .set(Issue::getUpdatedAt, clearTime)
                                    .set(Issue::getUpdatedBy, currentUserId)
                    );
                    // 批量插入活动日志
                    List<IssueActivity> activities = assignedIssues.stream().map(issue -> {
                        IssueActivity activity = new IssueActivity();
                        activity.setIssueId(issue.getId());
                        activity.setUserId(currentUserId);
                        activity.setAction("field_change");
                        activity.setFieldName("assignee_id");
                        activity.setOldValue(userId.toString());
                        activity.setNewValue(null);
                        activity.setDetail("{\"reason\":\"role_downgrade\"}");
                        activity.setCreatedAt(clearTime);
                        return activity;
                    }).toList();
                    com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch(activities);
                    log.info("角色降级清空 assignee：projectId={}, userId={}, affectedIssues={}",
                            projectId, userId, affectedIssueCount);
                }
            }
        }

        return affectedIssueCount;
    }

    /**
     * 查询指定用户在项目中被分配的工单数量（用于移除前确认提示）
     */
    public int countAssignedIssues(Long projectId, Long userId) {
        Long count = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .eq(Issue::getAssigneeId, userId)
                        .isNull(Issue::getDeletedAt)
        );
        return count.intValue();
    }

    /**
     * 移除项目成员（含级联清理：清空该成员被分配的工单负责人）
     *
     * @return 受影响的工单数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int removeMember(Long projectId, Long userId) {
        // 校验项目状态（归档项目不允许管理成员）
        Project project = getById(projectId);
        if (ProjectStatus.ACTIVE != project.getStatus()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归档项目不允许管理成员");
        }

        // 保护项目负责人：不能将负责人从成员中移除
        if (userId.equals(project.getLeadId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不能移除项目负责人。请先在项目设置中转让负责人后再移除该成员。");
        }

        // 保护最后一个项目管理员
        List<ProjectMember> admins = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getRoleId, PROJECT_ADMIN_ROLE_ID)
        );
        // 如果该用户拥有 project_admin 角色，检查是否是唯一的 admin
        boolean userHasAdmin = admins.stream().anyMatch(a -> a.getUserId().equals(userId));
        long otherAdminCount = admins.stream().filter(a -> !a.getUserId().equals(userId)).count();
        if (userHasAdmin && otherAdminCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移除项目中最后一个管理员");
        }

        // === 级联处理：清空该用户在此项目中所有工单的 assignee_id ===
        List<Issue> assignedIssues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId)
                        .eq(Issue::getProjectId, projectId)
                        .eq(Issue::getAssigneeId, userId)
                        .isNull(Issue::getDeletedAt)
        );

        int affectedCount = assignedIssues.size();
        if (affectedCount > 0) {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            LocalDateTime now = LocalDateTime.now();

            // 批量更新 assignee_id 为 null
            issueMapper.update(null,
                    new LambdaUpdateWrapper<Issue>()
                            .eq(Issue::getProjectId, projectId)
                            .eq(Issue::getAssigneeId, userId)
                            .isNull(Issue::getDeletedAt)
                            .set(Issue::getAssigneeId, null)
                            .set(Issue::getUpdatedAt, now)
                            .set(Issue::getUpdatedBy, currentUserId)
            );

            // 批量插入活动日志
            List<IssueActivity> activities = assignedIssues.stream().map(issue -> {
                IssueActivity activity = new IssueActivity();
                activity.setIssueId(issue.getId());
                activity.setUserId(currentUserId);
                activity.setAction("field_change");
                activity.setFieldName("assignee_id");
                activity.setOldValue(userId.toString());
                activity.setNewValue(null);
                activity.setDetail("{\"reason\":\"member_removed\"}");
                activity.setCreatedAt(now);
                return activity;
            }).toList();
            com.baomidou.mybatisplus.extension.toolkit.Db.saveBatch(activities);
        }

        // === 级联处理：停止该用户在此项目中的所有活跃计时器 ===
        stopActiveTimersForUser(projectId, userId);

        // 删除成员记录
        memberMapper.delete(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        permissionService.invalidateCache(userId);
        // 失效项目列表缓存
        redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + userId);

        // 审计日志
        Long operatorId = SecurityUtils.getCurrentUserId();
        Map<String, Object> detailMap = affectedCount > 0
                ? Map.of("affected_issue_count", affectedCount)
                : null;
        projectActivityService.log(projectId, operatorId, "remove_member", userId, detailMap);

        // 通知被移除的用户 — 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.MemberRemoved(userId, operatorId, projectId, project.getName()));

        return affectedCount;
    }

    // ========== 项目成员校验（数据隔离核心方法） ==========

    /**
     * 校验用户是否为项目成员。系统管理员不受限制。
     * 如果不是成员且不是系统管理员，抛出 403 异常。
     *
     * @param userId    当前用户 ID
     * @param projectId 目标项目 ID
     */
    public void assertProjectMember(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目");
        }
        // 系统管理员：仅需校验项目存在性
        if (permissionService.isSystemAdmin(userId)) {
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
            }
            return;
        }
        // 检查是否为项目成员
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (count == 0) {
            // 区分"项目不存在"和"无权限"：提供更准确的错误信息
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
            }
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目");
        }
    }

    /**
     * 校验用户是否可访问项目（读操作用）。
     * 与 assertProjectMember 的区别：本方法兼容项目可见性（visibility）。
     * - 系统管理员：直接放行
     * - 项目成员：直接放行
     * - 非成员但项目 visibility = internal/public：放行（已登录用户）
     * - 非成员且项目 visibility = private：拒绝
     *
     * 适用场景：查看报表、查看工单列表、执行查询等只读操作。
     * 写操作（创建工单/工时/Webhook）仍应使用 assertProjectMember。
     *
     * @param userId    当前用户 ID
     * @param projectId 目标项目 ID
     */
    public void assertProjectAccessible(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目");
        }
        // 系统管理员：仅需校验项目存在性
        if (permissionService.isSystemAdmin(userId)) {
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
            }
            return;
        }
        // 检查是否为项目成员
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (count > 0) {
            return;
        }
        // 非成员：检查项目可见性
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        ProjectVisibility visibility = project.getVisibility();
        if (visibility == ProjectVisibility.INTERNAL || visibility == ProjectVisibility.PUBLIC) {
            return; // 非成员但项目对已登录用户可见
        }
        throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目");
    }

    /**
     * 校验项目是否处于 active 状态。归档项目不允许写操作。
     *
     * @param projectId 目标项目 ID
     * @throws BusinessException 如果项目已归档
     */
    public void assertProjectActive(Long projectId) {
        if (projectId == null) return;
        Project project = getById(projectId);
        if (ProjectStatus.ACTIVE != project.getStatus()) {
            throw new BusinessException(ErrorCode.PROJECT_ARCHIVED, "归档项目不允许此操作");
        }
    }

    /**
     * 判断用户是否为项目成员（不抛异常版本）。系统管理员返回 true。
     */
    public boolean isProjectMember(Long userId, Long projectId) {
        if (userId == null || projectId == null) {
            return false;
        }
        if (permissionService.isSystemAdmin(userId)) {
            return true;
        }
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        return count > 0;
    }

    /**
     * 获取用户可访问的所有项目 ID 列表。系统管理员返回 null（表示不限制）。
     * 包含：成员项目 ID + internal/public 项目 ID。
     * 结果缓存在 Redis 中（TTL 30s），避免同一请求内多次查库。
     */
    public List<Long> getAccessibleProjectIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        if (permissionService.isSystemAdmin(userId)) {
            return null; // null 表示无限制
        }

        // 短 TTL Redis 缓存（30s），减少同一用户短时间内重复查库
        String cacheKey = ACCESSIBLE_PROJECTS_CACHE_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if ("[]".equals(cached)) {
                return List.of();
            }
            return java.util.Arrays.stream(cached.split(",")).map(Long::parseLong).toList();
        }

        // 成员项目
        List<Long> memberProjectIds = memberMapper.selectProjectIdsByUserId(userId);

        // 通过用户组获得的项目
        List<Long> groupProjectIds = getProjectIdsByUserViaGroups(userId);

        // internal/public 项目
        List<Long> visibleProjectIds = projectMapper.selectProjectIdsByVisibility(
                List.of(ProjectVisibility.INTERNAL.getValue(), ProjectVisibility.PUBLIC.getValue()));

        // 合并去重
        Set<Long> allIds = new java.util.LinkedHashSet<>(memberProjectIds);
        allIds.addAll(groupProjectIds);
        allIds.addAll(visibleProjectIds);
        List<Long> projectIds = new java.util.ArrayList<>(allIds);

        // 原子写入（set 自带 TTL，即使并发重复写入也只是覆盖相同值）
        String value = projectIds.isEmpty() ? "[]" : projectIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        redisTemplate.opsForValue().set(cacheKey, value, java.time.Duration.ofSeconds(30));

        return projectIds;
    }

    /**
     * 获取用户通过用户组获得的项目 ID 列表。
     * 路径: user_group_member(user_id) → user_group_role(group_id, project_id IS NOT NULL)
     * 使用已有 Mapper 方法组合查询，避免依赖未编译的新 Mapper 方法。
     */
    private List<Long> getProjectIdsByUserViaGroups(Long userId) {
        // 获取用户所属的所有组
        List<Long> groupIds = userGroupMemberMapper.selectGroupIdsByUserId(userId);
        if (groupIds.isEmpty()) {
            return List.of();
        }
        // 查询这些组的项目角色分配中有 project_id 的记录
        List<com.trackflow.system.entity.UserGroupRole> groupRoles = userGroupRoleMapper.selectList(
                new LambdaQueryWrapper<com.trackflow.system.entity.UserGroupRole>()
                        .in(com.trackflow.system.entity.UserGroupRole::getGroupId, groupIds)
                        .isNotNull(com.trackflow.system.entity.UserGroupRole::getProjectId)
        );
        return groupRoles.stream()
                .map(com.trackflow.system.entity.UserGroupRole::getProjectId)
                .distinct()
                .toList();
    }

    /**
     * 获取所有活跃项目的 ID 列表。
     * 用于系统管理员全项目模式，确保 SQL 使用 IN 条件走索引，而非无 WHERE 全表扫描。
     */
    public List<Long> getAllActiveProjectIds() {
        return projectMapper.selectAllActiveProjectIds();
    }

    /**
     * 递增 Issue 序号并返回新序号。
     * 使用 FOR UPDATE 锁防止并发冲突。
     * 如果发现实际 max 序号高于项目记录的 sequence（数据不一致），自动校正。
     */
    @Transactional(rollbackFor = Exception.class)
    public int nextIssueSequence(Long projectId) {
        // 使用 FOR UPDATE 悲观锁锁定项目行，防止并发生成重复序号
        Project project = projectMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Project>()
                        .eq(Project::getId, projectId)
                        .last("FOR UPDATE")
        );
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }

        int currentSeq = project.getIssueSequence();

        // 查询数据库中该项目实际的最大序号，防止 sequence 落后导致唯一约束冲突
        Integer actualMax = projectMapper.selectMaxIssueSequence(projectId);
        if (actualMax != null && actualMax > currentSeq) {
            currentSeq = actualMax;
        }

        int next = currentSeq + 1;
        project.setIssueSequence(next);
        projectMapper.updateById(project);
        return next;
    }

    // ========== 项目删除 ==========

    /**
     * 删除前预检查 — 返回受影响数据量供前端确认弹窗展示
     */
    public ProjectDeletePreCheckVO preCheckDelete(Long projectId) {
        Project project = getById(projectId);

        ProjectDeletePreCheckVO vo = new ProjectDeletePreCheckVO();
        vo.setProjectName(project.getName());
        vo.setProjectKey(project.getKey());

        // 工单总数
        long issueCount = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .isNull(Issue::getDeletedAt));
        vo.setIssueCount((int) issueCount);

        // 未关闭工单数
        List<IssueStatus> statuses = issueStatusMapper.selectList(null);
        Set<Long> doneStatusIds = statuses.stream()
                .filter(s -> "done".equals(s.getCategory()) || "cancelled".equals(s.getCategory()))
                .map(IssueStatus::getId)
                .collect(java.util.stream.Collectors.toSet());
        long openIssueCount = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .isNull(Issue::getDeletedAt)
                        .notIn(!doneStatusIds.isEmpty(), Issue::getStatusId, doneStatusIds));
        vo.setOpenIssueCount((int) openIssueCount);

        // Sprint 数量
        long sprintCount = sprintMapper.selectCount(
                new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId));
        vo.setSprintCount((int) sprintCount);

        // 成员数量（唯一用户数）
        int memberCount = memberMapper.countDistinctUsers(projectId);
        vo.setMemberCount(memberCount);

        // 工时记录数量
        long timeEntryCount = timeEntryMapper.selectCount(
                new LambdaQueryWrapper<TimeEntry>()
                        .eq(TimeEntry::getProjectId, projectId));
        vo.setTimeEntryCount((int) timeEntryCount);

        // 判断是否可删除
        vo.setDeletable(true);
        vo.setReason(null);

        return vo;
    }

    /**
     * 删除项目 — 级联删除所有关联数据（物理删除）
     *
     * 流程参考 OpenProject DeleteService：
     * 1. 通知所有项目成员
     * 2. 失效所有成员的权限缓存
     * 3. 查询附件路径列表（事务内，因为 CASCADE 会删除 issue_attachment 记录）
     * 4. 物理删除项目（FK CASCADE 自动清理 issue/sprint/member/tag 等）
     * 5. 事务提交后，异步清理 MinIO 中的附件物理文件（通过事件机制）
     *
     * @param projectId         项目 ID
     * @param confirmProjectKey 前端传入的项目 Key 用于二次确认
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long projectId, String confirmProjectKey) {
        Project project = getById(projectId);

        // 二次校验：前端传入的 key 必须匹配
        if (!project.getKey().equalsIgnoreCase(confirmProjectKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目标识不匹配，请输入正确的项目标识确认删除");
        }

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 查询所有成员用于通知和缓存清理
        List<ProjectMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId));

        List<Long> memberUserIds = members.stream()
                .map(ProjectMember::getUserId)
                .distinct()
                .toList();

        // 2. 失效所有成员的权限缓存和项目列表缓存
        for (Long memberId : memberUserIds) {
            permissionService.invalidateCache(memberId);
            redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + memberId);
        }

        // 3. 通知所有成员（强制通知，不受偏好控制——不可逆操作）— 事务提交后触发
        eventPublisher.publishEvent(new ProjectNotificationEvent.ProjectDeleted(projectId, memberUserIds, currentUserId,
                project.getName(), project.getKey()));

        // 4. 查询附件路径（必须在 DB 删除之前，因为 CASCADE 会删除 issue_attachment 记录）
        //    发布清理事件，由 @TransactionalEventListener(AFTER_COMMIT) 在事务成功后执行 MinIO 删除
        List<String> attachmentPaths = issueAttachmentMapper.selectFilePathsByProjectId(projectId);
        if (attachmentPaths != null && !attachmentPaths.isEmpty()) {
            eventPublisher.publishEvent(new ProjectAttachmentCleanupEvent(projectId, attachmentPaths));
        }

        // 4.5 清理项目下所有工单的通知静音记录（notification_muted_thread 无 FK，不会被 CASCADE 删除）
        List<Long> projectIssueIds = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getId)
                        .eq(Issue::getProjectId, projectId)
        ).stream().map(Issue::getId).toList();
        if (!projectIssueIds.isEmpty()) {
            mutedThreadService.deleteByResources("issue", projectIssueIds);
        }

        // 4.6 清理项目专属 WorkflowDefinition（非系统默认工作流，project_workflow 无 FK → workflow_definition，不会被 CASCADE 删除）
        List<Long> defIds = projectWorkflowMapper.selectDefinitionIdsByProjectId(projectId);
        if (!defIds.isEmpty()) {
            // 只删除非系统默认工作流（项目初始化时为该项目单独创建的工作流定义）
            List<Long> defIdsToDelete = defIds.stream()
                    .filter(defId -> {
                        WorkflowDefinition def = workflowDefinitionMapper.selectById(defId);
                        return def != null && !Boolean.TRUE.equals(def.getIsDefault());
                    })
                    .toList();
            if (!defIdsToDelete.isEmpty()) {
                workflowDefinitionMapper.deleteBatchIds(defIdsToDelete);
                log.info("Deleted {} workflow definitions for project {}", defIdsToDelete.size(), projectId);
            }
        }

        // 5. 物理删除项目（FK CASCADE 自动删除所有关联数据）
        projectMapper.deleteById(projectId);
    }

    /**
     * 获取项目概览统计数据
     */
    @Transactional(readOnly = true)
    public ProjectStatisticsVO getProjectStatistics(Long projectId) {
        var vo = new ProjectStatisticsVO();

        // 1. 查询各状态工单数量（聚合查询，不加载全量数据）
        List<IssueStatus> allStatuses = issueStatusMapper.selectList(null);
        Map<Long, IssueStatus> statusMap = allStatuses.stream()
                .collect(java.util.stream.Collectors.toMap(IssueStatus::getId, s -> s));

        // 按状态分组统计
        LambdaQueryWrapper<Issue> baseWrapper = new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .isNull(Issue::getDeletedAt);
        Long totalCount = issueMapper.selectCount(baseWrapper);
        int total = totalCount != null ? totalCount.intValue() : 0;

        // 统计各状态数量（用 selectMaps 做 GROUP BY）
        Map<Long, Integer> statusCountMap = new java.util.HashMap<>();
        if (total > 0) {
            var groupWrapper = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Issue>();
            groupWrapper.select("status_id", "count(*) as cnt")
                        .eq("project_id", projectId)
                        .isNull("deleted_at")
                        .groupBy("status_id");
            List<Map<String, Object>> groupResults = issueMapper.selectMaps(groupWrapper);
            for (Map<String, Object> row : groupResults) {
                Long statusId = ((Number) row.get("status_id")).longValue();
                int cnt = ((Number) row.get("cnt")).intValue();
                statusCountMap.put(statusId, cnt);
            }
        }

        // 计算已关闭/未关闭
        int closed = 0;
        for (Map.Entry<Long, Integer> entry : statusCountMap.entrySet()) {
            IssueStatus status = statusMap.get(entry.getKey());
            if (status != null && Boolean.TRUE.equals(status.getIsClosed())) {
                closed += entry.getValue();
            }
        }
        int open = total - closed;
        double completionRate = total > 0 ? Math.round((closed * 100.0 / total) * 10) / 10.0 : 0;

        vo.setTotalIssues(total);
        vo.setOpenIssues(open);
        vo.setClosedIssues(closed);
        vo.setCompletionRate(completionRate);

        // 4. 本周新建/关闭（用 COUNT 查询）
        LocalDateTime weekStart = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay();

        Long createdCount = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .isNull(Issue::getDeletedAt)
                .ge(Issue::getCreatedAt, weekStart));
        vo.setCreatedThisWeek(createdCount != null ? createdCount.intValue() : 0);

        Long closedCount = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getProjectId, projectId)
                .isNull(Issue::getDeletedAt)
                .ge(Issue::getResolvedAt, weekStart));
        vo.setClosedThisWeek(closedCount != null ? closedCount.intValue() : 0);

        // 5. 状态分布
        List<ProjectStatisticsVO.StatusDistribution> distributions = statusCountMap.entrySet().stream()
                .map(entry -> {
                    IssueStatus status = statusMap.get(entry.getKey());
                    if (status == null) return null;
                    var dist = new ProjectStatisticsVO.StatusDistribution();
                    dist.setStatusId(entry.getKey().toString());
                    dist.setStatusName(status.getName());
                    dist.setStatusColor(status.getColor());
                    dist.setCategory(status.getCategory());
                    dist.setClosed(Boolean.TRUE.equals(status.getIsClosed()));
                    dist.setCount(entry.getValue());
                    return dist;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                .toList();

        vo.setStatusDistribution(distributions);

        // 6. 当前活跃 Sprint
        LambdaQueryWrapper<Sprint> sprintWrapper = new LambdaQueryWrapper<>();
        sprintWrapper.eq(Sprint::getProjectId, projectId)
                     .eq(Sprint::getStatus, com.trackflow.sprint.entity.SprintStatus.ACTIVE);
        Sprint activeSprint = sprintMapper.selectOne(sprintWrapper);

        if (activeSprint != null) {
            var sprintInfo = new ProjectStatisticsVO.ActiveSprintInfo();
            sprintInfo.setId(activeSprint.getId().toString());
            sprintInfo.setName(activeSprint.getName());
            sprintInfo.setStartDate(activeSprint.getStartDate() != null ? activeSprint.getStartDate().toString() : null);
            sprintInfo.setEndDate(activeSprint.getEndDate() != null ? activeSprint.getEndDate().toString() : null);

            // Sprint 中的工单统计（用 COUNT 代替 selectList）
            Long sprintTotal = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getProjectId, projectId)
                    .eq(Issue::getSprintId, activeSprint.getId())
                    .isNull(Issue::getDeletedAt));
            sprintInfo.setTotalIssues(sprintTotal != null ? sprintTotal.intValue() : 0);

            // Sprint 中已关闭工单（获取所有 closed 状态 ID，用 IN 查询）
            List<Long> closedStatusIds = allStatuses.stream()
                    .filter(s -> Boolean.TRUE.equals(s.getIsClosed()))
                    .map(IssueStatus::getId)
                    .toList();
            if (!closedStatusIds.isEmpty()) {
                Long sprintCompleted = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getProjectId, projectId)
                        .eq(Issue::getSprintId, activeSprint.getId())
                        .isNull(Issue::getDeletedAt)
                        .in(Issue::getStatusId, closedStatusIds));
                sprintInfo.setCompletedIssues(sprintCompleted != null ? sprintCompleted.intValue() : 0);
            } else {
                sprintInfo.setCompletedIssues(0);
            }

            // 剩余天数
            if (activeSprint.getEndDate() != null) {
                long remaining = java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), activeSprint.getEndDate());
                sprintInfo.setRemainingDays((int) Math.max(0, remaining));
            }

            vo.setActiveSprint(sprintInfo);
        }

        return vo;
    }

    /**
     * 原子更新项目 settings JSONB 中的指定字段。
     * 使用 PostgreSQL jsonb_set 在数据库层完成更新，避免 Read-Modify-Write 竞态条件。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectSetting(Long projectId, String key, Object value) {
        // 确保项目存在
        getById(projectId);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (value == null) {
            projectMapper.removeSettingKey(projectId, key, currentUserId);
        } else {
            // 将 value 转为 JSON 字面值（PostgreSQL jsonb_set 需要 JSON 格式的 value）
            String jsonValue;
            if (value instanceof Boolean) {
                jsonValue = value.toString();
            } else if (value instanceof Number) {
                jsonValue = value.toString();
            } else {
                // 字符串值需要加引号
                jsonValue = "\"" + value.toString().replace("\"", "\\\"") + "\"";
            }
            projectMapper.updateSettingKey(projectId, key, jsonValue, currentUserId);
        }
    }

    /**
     * 从项目 settings JSONB 中读取指定 key 的 Long 值。
     * 如果不存在或解析失败，返回 null。
     */
    public Long getProjectSettingAsLong(Long projectId, String key) {
        Project project = getById(projectId);
        String settingsJson = project.getSettings();
        if (settingsJson == null || settingsJson.isBlank() || "{}".equals(settingsJson)) {
            return null;
        }
        try {
            var node = objectMapper.readTree(settingsJson);
            var valueNode = node.get(key);
            if (valueNode == null || valueNode.isNull()) return null;
            return valueNode.asLong();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查项目是否启用了时间追踪功能。
     * 默认启用（兼容已有项目）。
     */
    public boolean isTimeTrackingEnabled(Long projectId) {
        Project project = getById(projectId);
        String settingsJson = project.getSettings();
        if (settingsJson == null || settingsJson.isBlank() || "{}".equals(settingsJson)) {
            return true; // 默认启用
        }
        try {
            var node = objectMapper.readTree(settingsJson);
            var enabledNode = node.get("timeTrackingEnabled");
            if (enabledNode == null || enabledNode.isNull()) return true;
            return enabledNode.asBoolean(true);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 获取禁用时间追踪功能的影响评估。
     */
    public com.trackflow.project.vo.TimeTrackingDisableImpactVO getTimeTrackingDisableImpact(Long projectId) {
        // 确保项目存在
        getById(projectId);
        var vo = new com.trackflow.project.vo.TimeTrackingDisableImpactVO();
        vo.setTotalTimeEntries(timeEntryMapper.countByProjectId(projectId));
        vo.setAffectedUsers(timeEntryMapper.countDistinctUsersByProjectId(projectId));
        vo.setActiveTimers(timeEntryMapper.countActiveTimersByProjectId(projectId));
        return vo;
    }

    /**
     * 更新项目时间追踪启用/禁用设置。
     * 禁用时自动停止该项目所有活跃计时器。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTimeTrackingEnabled(Long projectId, boolean enabled) {
        if (!enabled) {
            // 禁用时：自动停止所有活跃计时器
            stopActiveTimersForProject(projectId);
        }
        updateProjectSetting(projectId, "timeTrackingEnabled", enabled);
    }

    /**
     * 停止项目中所有活跃计时器，计算已用时长并保存。
     */
    private void stopActiveTimersForProject(Long projectId) {
        List<TimeEntry> activeTimers = timeEntryMapper.selectActiveTimersByProjectId(projectId);
        doStopActiveTimers(activeTimers, "项目 " + projectId + " 禁用时间追踪");
    }

    /**
     * 停止指定用户在指定项目中的所有活跃计时器。
     * 用于成员移除时的级联清理。
     */
    private void stopActiveTimersForUser(Long projectId, Long userId) {
        List<TimeEntry> activeTimers = timeEntryMapper.selectList(
                new LambdaQueryWrapper<TimeEntry>()
                        .eq(TimeEntry::getProjectId, projectId)
                        .eq(TimeEntry::getUserId, userId)
                        .eq(TimeEntry::getOngoing, true)
        );
        doStopActiveTimers(activeTimers, "用户 " + userId + " 被移出项目 " + projectId);
    }

    /**
     * 通用：停止一批活跃计时器，计算已用时长并保存。
     */
    private void doStopActiveTimers(List<TimeEntry> activeTimers, String reason) {
        if (activeTimers == null || activeTimers.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (TimeEntry timer : activeTimers) {
            // 计算从创建时间到现在的分钟数
            int elapsedMinutes = (int) java.time.Duration.between(timer.getCreatedAt(), now).toMinutes();
            int duration = Math.max(1, elapsedMinutes); // 至少 1 分钟

            // 跨天处理：保留原始 workDate，清除 startTime（与 TimeEntryService.stopTimer 逻辑一致）
            boolean crossDay = timer.getWorkDate() != null && !timer.getWorkDate().equals(java.time.LocalDate.now());
            if (crossDay) {
                timer.setStartTime(null);
            }
            // 同天：workDate 和 startTime 均保持不变

            timer.setDuration(duration);
            timer.setOngoing(false);
            timer.setUpdatedAt(now);
            timeEntryMapper.updateById(timer);

            // 更新工单的 spent_hours
            if (timer.getIssueId() != null) {
                timeEntryMapper.atomicRefreshSpentHours(timer.getIssueId());
            }
        }
        log.info("{}，自动停止 {} 个活跃计时器", reason, activeTimers.size());
    }

    // ========== 项目收藏 ==========

    /**
     * 切换项目收藏状态（Toggle）
     * @return true=已收藏，false=已取消收藏
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long projectId, Long userId) {
        // 确保项目存在
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }

        int existing = favoriteMapper.countByUserAndProject(userId, projectId);
        if (existing > 0) {
            // 已收藏 → 取消
            favoriteMapper.deleteFavorite(userId, projectId);
            return false;
        } else {
            // 未收藏 → 添加
            UserProjectFavorite fav = new UserProjectFavorite();
            fav.setUserId(userId);
            fav.setProjectId(projectId);
            fav.setCreatedAt(LocalDateTime.now());
            favoriteMapper.insert(fav);
            return true;
        }
    }

    /**
     * 查询用户收藏的项目ID集合
     */
    public Set<Long> getUserFavoriteProjectIds(Long userId) {
        List<Long> ids = favoriteMapper.selectFavoriteProjectIds(userId);
        return new java.util.HashSet<>(ids);
    }

    /**
     * 为项目 VO 列表填充当前用户的收藏状态
     */
    public void populateFavoriteStatus(List<ProjectVO> voList, Long userId) {
        if (voList == null || voList.isEmpty() || userId == null) return;

        Set<Long> favoriteIds = getUserFavoriteProjectIds(userId);
        for (ProjectVO vo : voList) {
            vo.setFavorited(favoriteIds.contains(Long.valueOf(vo.getId())));
        }
    }
}
