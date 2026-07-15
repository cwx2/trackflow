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
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.integration.service.NotificationService;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.project.converter.ProjectConverter;
import com.trackflow.project.dto.AddMemberDTO;
import com.trackflow.project.dto.CreateProjectDTO;
import com.trackflow.project.dto.UpdateProjectDTO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.project.vo.ProjectDeletePreCheckVO;
import com.trackflow.sprint.mapper.SprintMapper;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trackflow.project.vo.ProjectDetailVO;
import com.trackflow.project.vo.ProjectMemberVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目管理服务
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final Long PROJECT_ADMIN_ROLE_ID = 2L;
    private static final String ACCESSIBLE_PROJECTS_CACHE_PREFIX = "accessible_projects:";

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final ProjectConverter projectConverter;
    private final PermissionService permissionService;
    private final StringRedisTemplate redisTemplate;
    private final IssueMapper issueMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final SprintMapper sprintMapper;
    private final ProjectActivityService projectActivityService;
    private final NotificationService notificationService;
    private final ProjectInitializationService projectInitializationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 创建项目
     */
    @Transactional
    public Project create(CreateProjectDTO dto) {
        // Key 唯一性检查（不区分大小写）
        Long count = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getKey, dto.getKey().toUpperCase())
        );
        if (count > 0) {
            throw new BusinessException(40902, 409, "Project key already exists");
        }

        Project project = new Project();
        project.setName(dto.getName());
        project.setKey(dto.getKey().toUpperCase());
        project.setDescription(dto.getDescription());
        project.setLeadId(dto.getLeadId() != null ? dto.getLeadId() : SecurityUtils.getCurrentUserId());
        project.setStatus("active");
        project.setIssueSequence(0);
        projectMapper.insert(project);

        // 自动添加创建者为项目管理员
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(project.getId());
            member.setUserId(currentUserId);
            member.setRoleId(PROJECT_ADMIN_ROLE_ID);
            member.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(member);
        }

        // 根据模板类型初始化项目（工作流、看板列配置等）
        projectInitializationService.initialize(project.getId(), dto.getTemplate());

        return project;
    }

    /**
     * 项目列表（只返回用户有权限的项目）
     */
    public Page<Project> list(Page<Project> page, String keyword, String status, Long userId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();

        // 非系统管理员只能看到自己参与的项目
        if (userId != null && !permissionService.isSystemAdmin(userId)) {
            List<Long> projectIds = memberMapper.selectProjectIdsByUserId(userId);
            if (projectIds.isEmpty()) {
                return new Page<>();
            }
            wrapper.in(Project::getId, projectIds);
        }

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Project::getName, keyword).or().like(Project::getKey, keyword));
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(Project::getStatus, status);
        } else {
            // 默认不显示归档项目
            wrapper.eq(Project::getStatus, "active");
        }

        wrapper.orderByDesc(Project::getCreatedAt);
        return projectMapper.selectPage(page, wrapper);
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
     * 获取项目详情（含当前用户角色和成员统计）
     */
    public ProjectDetailVO getProjectDetail(Long projectId, Long currentUserId) {
        Project project = getById(projectId);

        // 使用 Converter 映射基础字段
        ProjectDetailVO vo = projectConverter.toDetailVO(project);

        // 查询成员总数
        Long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId)
        );
        vo.setMemberCount(memberCount.intValue());

        // 查询当前用户在项目中的角色（当前模型：一个用户在一个项目中只有唯一角色）
        if (currentUserId != null) {
            if (permissionService.isSystemAdmin(currentUserId)) {
                vo.setMyRoleName("系统管理员");
                vo.setMyRoleCode("system_admin");
            } else {
                List<Long> roleIds = memberMapper.selectRoleIdsByUserAndProject(currentUserId, projectId);
                if (!roleIds.isEmpty()) {
                    SysRole role = roleMapper.selectById(roleIds.get(0));
                    if (role != null) {
                        vo.setMyRoleName(role.getName());
                        vo.setMyRoleCode(role.getCode());
                    }
                }
            }
        }

        // 查询负责人名称
        if (project.getLeadId() != null) {
            SysUser lead = userMapper.selectById(project.getLeadId());
            if (lead != null) {
                vo.setLeadName(lead.getDisplayName());
            }
        }

        return vo;
    }

    /**
     * 更新项目
     */
    @Transactional
    public Project update(Long id, UpdateProjectDTO dto) {
        Project project = getById(id);
        // 归档项目不允许修改
        assertProjectActive(id);

        if (dto.getName() != null) project.setName(dto.getName());
        if (dto.getDescription() != null) project.setDescription(dto.getDescription());

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

        // 3. 如果新负责人当前角色不是 project_admin，自动升级
        ProjectMember newLeadMember = memberMapper.selectOne(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, newLeadId)
        );
        if (newLeadMember != null && !PROJECT_ADMIN_ROLE_ID.equals(newLeadMember.getRoleId())) {
            Long oldRoleId = newLeadMember.getRoleId();
            SysRole oldRole = roleMapper.selectById(oldRoleId);
            newLeadMember.setRoleId(PROJECT_ADMIN_ROLE_ID);
            memberMapper.updateById(newLeadMember);
            permissionService.invalidateCache(newLeadId);

            // 记录角色自动升级的活动日志
            SysRole adminRole = roleMapper.selectById(PROJECT_ADMIN_ROLE_ID);
            Map<String, Object> roleDetailMap = new java.util.LinkedHashMap<>();
            roleDetailMap.put("old_role_id", oldRoleId);
            roleDetailMap.put("old_role_name", oldRole != null ? oldRole.getName() : "");
            roleDetailMap.put("new_role_id", PROJECT_ADMIN_ROLE_ID);
            roleDetailMap.put("new_role_name", adminRole != null ? adminRole.getName() : "项目管理员");
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

        // 6. 通知新负责人
        notificationService.notify(
                newLeadId,
                "你已成为项目负责人",
                String.format("你已成为项目「%s」的负责人", project.getName()),
                "lead_changed",
                "project",
                projectId
        );

        // 7. 通知旧负责人（如果存在且不同）
        if (oldLeadId != null && !oldLeadId.equals(currentUserId)) {
            notificationService.notify(
                    oldLeadId,
                    "项目负责人已变更",
                    String.format("项目「%s」的负责人已变更为「%s」", project.getName(), newLeadName),
                    "lead_changed",
                    "project",
                    projectId
            );
        }
    }

    /**
     * 归档项目
     */
    @Transactional
    public void archive(Long id) {
        Project project = getById(id);
        project.setStatus("archived");
        projectMapper.updateById(project);
    }

    /**
     * 恢复项目
     */
    @Transactional
    public void restore(Long id) {
        Project project = getById(id);
        project.setStatus("active");
        projectMapper.updateById(project);
    }

    // ========== 回收站保留策略设置 ==========

    /**
     * 获取项目回收站保留策略
     */
    public Map<String, Object> getTrashSettings(Long id) {
        Project project = getById(id);
        return Map.of("trashRetentionDays", parseRetentionDays(project.getSettings()));
    }

    /**
     * 更新项目回收站保留策略
     * @param days 保留天数，0 表示永久保留
     */
    @Transactional
    public void updateTrashSettings(Long id, int days) {
        Project project = getById(id);
        String existingSettings = project.getSettings();
        try {
            var root = (existingSettings != null && !existingSettings.isBlank())
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(existingSettings)
                    : objectMapper.createObjectNode();
            root.put("trashRetentionDays", days);
            project.setSettings(objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            project.setSettings("{\"trashRetentionDays\":" + days + "}");
        }
        projectMapper.updateById(project);
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

        List<Long> userIds = members.stream().map(ProjectMember::getUserId).toList();
        var users = userMapper.selectBatchIds(userIds);
        Map<Long, com.trackflow.system.entity.SysUser> userMap = users.stream()
                .collect(java.util.stream.Collectors.toMap(com.trackflow.system.entity.SysUser::getId, u -> u));

        return members.stream().map(m -> {
            ProjectMemberVO vo = new ProjectMemberVO();
            vo.setId(m.getId() != null ? m.getId().toString() : null);
            vo.setProjectId(m.getProjectId() != null ? m.getProjectId().toString() : null);
            vo.setUserId(m.getUserId() != null ? m.getUserId().toString() : null);
            vo.setRoleId(m.getRoleId() != null ? m.getRoleId().toString() : null);
            vo.setJoinedAt(m.getJoinedAt());
            var user = userMap.get(m.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setDisplayName(user.getDisplayName());
                vo.setEmail(user.getEmail());
            }
            return vo;
        }).toList();
    }

    /**
     * 添加项目成员
     */
    @Transactional
    public void addMember(Long projectId, AddMemberDTO dto) {
        // 1. 校验项目状态（归档项目不允许添加成员）
        Project project = getById(projectId);
        if (!"active".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归档项目不允许管理成员");
        }

        // 2. 校验用户存在且状态为 active
        SysUser user = userMapper.selectById(dto.getUserId());
        if (user == null || !"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在或已禁用");
        }

        // 3. 校验角色类型必须为 project
        SysRole role = roleMapper.selectById(dto.getRoleId());
        if (role == null || !"project".equals(role.getRoleType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能分配项目角色");
        }

        // 4. 检查是否已经是成员
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, dto.getUserId())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户已是项目成员");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(dto.getUserId());
        member.setRoleId(dto.getRoleId());
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(member);

        // 失效权限缓存
        permissionService.invalidateCache(dto.getUserId());
        // 失效项目列表缓存
        redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + dto.getUserId());

        // 审计日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        projectActivityService.log(projectId, currentUserId, "add_member", dto.getUserId(),
                Map.of("role_id", role.getId(), "role_name", role.getName()));

        // 通知被添加的用户
        String targetName = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
        notificationService.notify(
                dto.getUserId(),
                "你已被添加到项目",
                String.format("你已被添加到项目「%s」，角色为「%s」", project.getName(), role.getName()),
                "member_added",
                "project",
                projectId
        );
    }

    /**
     * 更新成员角色
     */
    @Transactional
    public void updateMemberRole(Long projectId, Long userId, Long roleId) {
        // 校验项目状态（归档项目不允许管理成员）
        Project project = getById(projectId);
        if (!"active".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归档项目不允许管理成员");
        }

        // 校验角色类型必须为 project
        SysRole newRole = roleMapper.selectById(roleId);
        if (newRole == null || !"project".equals(newRole.getRoleType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能分配项目角色");
        }

        ProjectMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (member == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Member not found");
        }

        Long oldRoleId = member.getRoleId();
        SysRole oldRole = roleMapper.selectById(oldRoleId);

        member.setRoleId(roleId);
        memberMapper.updateById(member);
        permissionService.invalidateCache(userId);

        // 审计日志
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String oldRoleName = oldRole != null ? oldRole.getName() : "";
        String newRoleName = newRole.getName();
        projectActivityService.log(projectId, currentUserId, "change_role", userId,
                Map.of("old_role_id", oldRoleId, "old_role_name", oldRoleName,
                       "new_role_id", roleId, "new_role_name", newRoleName));

        // 通知角色变更的用户
        notificationService.notify(
                userId,
                "你的项目角色已变更",
                String.format("你在项目「%s」中的角色已从「%s」变更为「%s」", project.getName(), oldRoleName, newRoleName),
                "role_changed",
                "project",
                projectId
        );
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
    @Transactional
    public int removeMember(Long projectId, Long userId) {
        // 校验项目状态（归档项目不允许管理成员）
        Project project = getById(projectId);
        if (!"active".equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "归档项目不允许管理成员");
        }

        // 保护最后一个项目管理员
        List<ProjectMember> admins = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getRoleId, PROJECT_ADMIN_ROLE_ID)
        );
        if (admins.size() == 1 && admins.get(0).getUserId().equals(userId)) {
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

        // 通知被移除的用户
        notificationService.notify(
                userId,
                "你已被移出项目",
                String.format("你已被移出项目「%s」", project.getName()),
                "member_removed",
                "project",
                projectId
        );

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
        // 系统管理员跳过校验
        if (permissionService.isSystemAdmin(userId)) {
            return;
        }
        // 检查是否为项目成员
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (count == 0) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目");
        }
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
        if (!"active".equals(project.getStatus())) {
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
     * 获取用户所属的所有项目 ID 列表。系统管理员返回 null（表示不限制）。
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

        List<Long> projectIds = memberMapper.selectProjectIdsByUserId(userId);

        // 原子写入（set 自带 TTL，即使并发重复写入也只是覆盖相同值）
        String value = projectIds.isEmpty() ? "[]" : projectIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        redisTemplate.opsForValue().set(cacheKey, value, java.time.Duration.ofSeconds(30));

        return projectIds;
    }

    /**
     * 递增 Issue 序号并返回新序号。
     * 使用 FOR UPDATE 锁防止并发冲突。
     * 如果发现实际 max 序号高于项目记录的 sequence（数据不一致），自动校正。
     */
    @Transactional
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

        // 成员数量
        long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId));
        vo.setMemberCount((int) memberCount);

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
     * 3. 物理删除项目（FK CASCADE 自动清理 issue/sprint/member/tag 等）
     *
     * @param projectId         项目 ID
     * @param confirmProjectKey 前端传入的项目 Key 用于二次确认
     */
    @Transactional
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

        // 3. 通知所有成员（排除执行者自己）
        for (Long memberId : memberUserIds) {
            if (!memberId.equals(currentUserId)) {
                notificationService.notify(
                        memberId,
                        "项目已被删除",
                        String.format("项目「%s」(%s) 已被删除，相关工单和数据已清除。", project.getName(), project.getKey()),
                        "project_deleted",
                        "project",
                        projectId
                );
            }
        }

        // 4. 物理删除项目（FK CASCADE 自动删除所有关联数据）
        projectMapper.deleteById(projectId);
    }
}
