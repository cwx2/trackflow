package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.entity.UserRole;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import com.trackflow.system.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Long SYSTEM_ADMIN_ROLE_ID = 1L;

    private final SysUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PermissionService permissionService;
    private final SystemAuditService systemAuditService;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMapper projectMapper;
    private final IssueActivityMapper issueActivityMapper;
    private final IssueMapper issueMapper;

    /**
     * 分页查询用户列表
     */
    public Page<SysUser> list(Page<SysUser> page, String username, String displayName,
                              String email, Long orgId, String status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) {
            wrapper.like(SysUser::getUsername, username);
        }
        if (displayName != null && !displayName.isBlank()) {
            wrapper.like(SysUser::getDisplayName, displayName);
        }
        if (email != null && !email.isBlank()) {
            wrapper.like(SysUser::getEmail, email);
        }
        if (orgId != null) {
            wrapper.eq(SysUser::getOrgId, orgId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);
        return userMapper.selectPage(page, wrapper);
    }

    /**
     * 获取用户详情
     */
    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found");
        }
        return user;
    }

    /**
     * 获取用户的全局角色 ID 列表
     */
    public List<Long> getUserGlobalRoleIds(Long userId) {
        return userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        ).stream().map(UserRole::getRoleId).toList();
    }

    /**
     * 禁用用户
     */
    @Transactional
    public void disable(Long id) {
        SysUser user = getById(id);

        // 保护最后一个系统管理员
        if (isLastSystemAdmin(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot disable the last system administrator");
        }

        user.setStatus("disabled");
        userMapper.updateById(user);
        permissionService.invalidateCache(id);

        // 审计日志
        systemAuditService.log("disable_user", "user", id,
                Map.of("username", user.getUsername(),
                        "displayName", user.getDisplayName() != null ? user.getDisplayName() : ""));
    }

    /**
     * 启用用户
     */
    @Transactional
    public void enable(Long id) {
        SysUser user = getById(id);
        user.setStatus("active");
        userMapper.updateById(user);

        // 审计日志
        systemAuditService.log("enable_user", "user", id,
                Map.of("username", user.getUsername(),
                        "displayName", user.getDisplayName() != null ? user.getDisplayName() : ""));
    }

    /**
     * 分配全局角色
     */
    @Transactional
    public void assignGlobalRole(Long userId, Long roleId) {
        // 检查是否已存在
        Long count = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId)
        );
        if (count > 0) return;

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRoleMapper.insert(userRole);
        permissionService.invalidateCache(userId);

        // 审计日志
        SysRole role = roleMapper.selectById(roleId);
        String roleName = role != null ? role.getName() : String.valueOf(roleId);
        SysUser user = userMapper.selectById(userId);
        String username = user != null ? user.getUsername() : String.valueOf(userId);
        systemAuditService.log("assign_global_role", "user", userId,
                Map.of("roleId", roleId, "roleName", roleName, "username", username));
    }

    /**
     * 移除全局角色
     */
    @Transactional
    public void removeGlobalRole(Long userId, Long roleId) {
        // 保护最后一个系统管理员
        if (SYSTEM_ADMIN_ROLE_ID.equals(roleId) && isLastSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot remove the last system administrator role");
        }

        userRoleMapper.delete(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getRoleId, roleId)
        );
        permissionService.invalidateCache(userId);

        // 审计日志
        SysRole role = roleMapper.selectById(roleId);
        String roleName = role != null ? role.getName() : String.valueOf(roleId);
        SysUser user = userMapper.selectById(userId);
        String username = user != null ? user.getUsername() : String.valueOf(userId);
        systemAuditService.log("remove_global_role", "user", userId,
                Map.of("roleId", roleId, "roleName", roleName, "username", username));
    }

    /**
     * 检查用户是否是最后一个系统管理员
     */
    private boolean isLastSystemAdmin(Long userId) {
        // 查看有多少用户拥有系统管理员角色
        List<Long> adminUserIds = userRoleMapper.selectUserIdsByRoleId(SYSTEM_ADMIN_ROLE_ID);
        return adminUserIds.size() == 1 && adminUserIds.contains(userId);
    }

    /**
     * 获取用户完整档案（基本信息 + 全局角色 + 项目角色 + 最近活动）
     */
    public UserProfileVO getUserProfile(Long userId) {
        SysUser user = getById(userId);

        UserProfileVO profile = new UserProfileVO();
        profile.setId(String.valueOf(user.getId()));
        profile.setUsername(user.getUsername());
        profile.setDisplayName(user.getDisplayName());
        profile.setEmail(user.getEmail());
        profile.setAvatarUrl(user.getAvatarUrl());
        profile.setStatus(user.getStatus());
        profile.setLastLoginAt(user.getLastLoginAt());
        profile.setCreatedAt(user.getCreatedAt());

        // 全局角色
        profile.setGlobalRoles(buildGlobalRoles(userId));

        // 项目角色分布
        profile.setProjectRoles(buildProjectRoles(userId));

        // 最近活动（10条）
        profile.setRecentActivities(buildRecentActivities(userId));

        return profile;
    }

    private List<UserProfileVO.RoleInfo> buildGlobalRoles(Long userId) {
        List<Long> roleIds = getUserGlobalRoleIds(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream().map(role -> {
            UserProfileVO.RoleInfo info = new UserProfileVO.RoleInfo();
            info.setId(String.valueOf(role.getId()));
            info.setName(role.getName());
            info.setCode(role.getCode());
            return info;
        }).toList();
    }

    private List<UserProfileVO.ProjectRoleInfo> buildProjectRoles(Long userId) {
        // 查询用户的所有项目成员关系
        List<ProjectMember> memberships = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId)
        );
        if (memberships.isEmpty()) {
            return List.of();
        }

        // 批量查询项目信息
        List<Long> projectIds = memberships.stream().map(ProjectMember::getProjectId).distinct().toList();
        List<Project> projects = projectMapper.selectBatchIds(projectIds);
        Map<Long, Project> projectMap = projects.stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        // 批量查询角色信息
        List<Long> roleIds = memberships.stream().map(ProjectMember::getRoleId).distinct().toList();
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        Map<Long, SysRole> roleMap = roles.stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));

        List<UserProfileVO.ProjectRoleInfo> result = new ArrayList<>();
        for (ProjectMember membership : memberships) {
            Project project = projectMap.get(membership.getProjectId());
            SysRole role = roleMap.get(membership.getRoleId());
            if (project == null || role == null) continue;

            UserProfileVO.ProjectRoleInfo info = new UserProfileVO.ProjectRoleInfo();
            info.setProjectId(String.valueOf(project.getId()));
            info.setProjectName(project.getName());
            info.setProjectKey(project.getKey());
            info.setRoleName(role.getName());
            info.setRoleCode(role.getCode());
            info.setJoinedAt(membership.getJoinedAt());
            result.add(info);
        }
        return result;
    }

    private List<UserProfileVO.ActivityInfo> buildRecentActivities(Long userId) {
        // 查询最近 10 条活动
        Page<IssueActivity> page = new Page<>(1, 10);
        page.setSearchCount(false);
        Page<IssueActivity> activityPage = issueActivityMapper.selectPage(page,
                new LambdaQueryWrapper<IssueActivity>()
                        .eq(IssueActivity::getUserId, userId)
                        .orderByDesc(IssueActivity::getCreatedAt)
        );

        List<IssueActivity> activities = activityPage.getRecords();
        if (activities.isEmpty()) {
            return List.of();
        }

        // 批量查询关联的 Issue 信息
        List<Long> issueIds = activities.stream().map(IssueActivity::getIssueId).distinct().toList();
        List<Issue> issues = issueMapper.selectBatchIds(issueIds);
        Map<Long, Issue> issueMap = issues.stream()
                .collect(Collectors.toMap(Issue::getId, i -> i));

        return activities.stream().map(activity -> {
            UserProfileVO.ActivityInfo info = new UserProfileVO.ActivityInfo();
            info.setId(String.valueOf(activity.getId()));
            info.setIssueId(String.valueOf(activity.getIssueId()));
            info.setAction(activity.getAction());
            info.setFieldName(activity.getFieldName());
            info.setOldValue(activity.getOldValue());
            info.setNewValue(activity.getNewValue());
            info.setCreatedAt(activity.getCreatedAt());

            Issue issue = issueMap.get(activity.getIssueId());
            if (issue != null) {
                info.setIssueKey(issue.getIssueKey());
                info.setIssueTitle(issue.getTitle());
            }
            return info;
        }).toList();
    }
}
