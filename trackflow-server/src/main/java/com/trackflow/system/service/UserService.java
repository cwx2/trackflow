package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.KeycloakAdminService;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.dto.CreateUserDTO;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.entity.UserRole;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import com.trackflow.system.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 */
@Slf4j
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
    private final KeycloakAdminService keycloakAdminService;
    private final ApiKeyService apiKeyService;

    /**
     * 创建新用户（同步到 Keycloak + 本地 sys_user）
     * <p>
     * 流程：
     * 1. 检查本地是否已存在同名用户/同邮箱用户
     * 2. 在 Keycloak 中创建用户（含临时密码）
     * 3. 在本地 sys_user 中创建记录
     *
     * @return 新创建的用户实体
     */
    @Transactional
    public SysUser createUser(CreateUserDTO dto) {
        // 1. 本地重复性校验
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername())
        );
        if (usernameCount > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "用户名已存在: " + dto.getUsername());
        }

        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, dto.getEmail())
        );
        if (emailCount > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "邮箱已被使用: " + dto.getEmail());
        }

        // 2. 解析 displayName 为 firstName + lastName（用于 Keycloak）
        String[] nameParts = parseDisplayName(dto.getDisplayName());
        String firstName = nameParts[0];
        String lastName = nameParts[1];

        // 3. 在 Keycloak 中创建用户
        String keycloakId = keycloakAdminService.createUser(
                dto.getUsername(), dto.getEmail(), firstName, lastName, dto.getPassword()
        );

        // 4. 在本地创建 sys_user 记录
        SysUser user = new SysUser();
        user.setKeycloakId(keycloakId);
        user.setUsername(dto.getUsername());
        user.setDisplayName(dto.getDisplayName());
        user.setEmail(dto.getEmail());
        user.setStatus("active");
        userMapper.insert(user);

        // 5. 审计日志
        systemAuditService.log("create_user", "user", user.getId(),
                Map.of("username", dto.getUsername(),
                        "displayName", dto.getDisplayName(),
                        "email", dto.getEmail()));

        log.info("User created: username={}, keycloakId={}, localId={}",
                dto.getUsername(), keycloakId, user.getId());
        return user;
    }

    /**
     * 将显示名称解析为 firstName（名）和 lastName（姓）。
     * <p>
     * 规则：
     * - 包含 CJK 字符：第一个字符为姓，其余为名
     * - 西方名字：按空格分割，最后一个词为 lastName，其余为 firstName
     * - 无空格的单词：整个作为 firstName
     */
    private String[] parseDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return new String[]{"", ""};
        }

        // 检查是否包含 CJK 字符
        boolean hasCjk = displayName.chars().anyMatch(c ->
                Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);

        if (hasCjk) {
            // CJK 姓名：第一个字符为姓，其余为名
            // 处理复姓暂不支持，按单字姓处理
            if (displayName.length() >= 2) {
                return new String[]{displayName.substring(1), displayName.substring(0, 1)};
            }
            return new String[]{displayName, ""};
        } else {
            // 西方姓名：按空格分割
            String[] parts = displayName.trim().split("\\s+");
            if (parts.length == 1) {
                return new String[]{parts[0], ""};
            }
            // firstName = 除最后一个之外的所有部分，lastName = 最后一个
            String lastName = parts[parts.length - 1];
            String firstName = String.join(" ", java.util.Arrays.copyOfRange(parts, 0, parts.length - 1));
            return new String[]{firstName, lastName};
        }
    }

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

        // 禁止禁用自己
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (id.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能禁用自己的账号");
        }

        // 保护最后一个系统管理员
        if (isLastSystemAdmin(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统至少需要保留一个活跃管理员");
        }

        user.setStatus("disabled");
        userMapper.updateById(user);
        permissionService.invalidateCache(id);

        // 级联吊销用户所有 API Key（防止禁用后仍可通过已有 Key 访问）
        int revokedKeys = apiKeyService.revokeAllByUser(id);
        if (revokedKeys > 0) {
            log.info("禁用用户 {} 时级联吊销 {} 个 API Key", user.getUsername(), revokedKeys);
        }

        // 审计日志
        systemAuditService.log("disable_user", "user", id,
                Map.of("username", user.getUsername(),
                        "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                        "revoked_api_keys", revokedKeys));
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
        // 禁止移除自己的系统管理员角色
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (SYSTEM_ADMIN_ROLE_ID.equals(roleId) && userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移除自己的系统管理员角色");
        }

        // 保护最后一个系统管理员
        if (SYSTEM_ADMIN_ROLE_ID.equals(roleId) && isLastSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统至少需要保留一个活跃管理员");
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
