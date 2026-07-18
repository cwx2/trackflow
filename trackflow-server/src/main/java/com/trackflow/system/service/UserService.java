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
import com.trackflow.system.dto.DisableUserDTO;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.entity.UserGroup;
import com.trackflow.system.entity.UserGroupMember;
import com.trackflow.system.entity.UserGroupRole;
import com.trackflow.system.entity.UserRole;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserGroupMapper;
import com.trackflow.system.mapper.UserGroupMemberMapper;
import com.trackflow.system.mapper.UserGroupRoleMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import com.trackflow.system.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
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

    /** Redis 黑名单 key 前缀：被禁用用户的即时拦截 */
    private static final String DISABLED_USER_KEY_PREFIX = "auth:disabled:";

    /** 黑名单 TTL：JWT 最大有效期 + 缓冲（Keycloak 默认 access_token 5分钟 + refresh_token 30分钟 + 5分钟缓冲） */
    private static final Duration DISABLED_USER_TTL = Duration.ofMinutes(40);

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
    private final StringRedisTemplate redisTemplate;
    private final UserGroupMemberMapper userGroupMemberMapper;
    private final UserGroupRoleMapper userGroupRoleMapper;
    private final UserGroupMapper userGroupMapper;

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
                              String email, Long orgId, String status, String banStatus) {
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
        if (banStatus != null && !banStatus.isBlank()) {
            wrapper.eq(SysUser::getBanStatus, banStatus);
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
    public void disable(Long id, DisableUserDTO dto) {
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
        user.setBanStatus(dto.getBanStatus());
        user.setBanReason(dto.getBanReason());
        user.setBannedAt(java.time.LocalDateTime.now());
        user.setBannedBy(currentUserId);
        userMapper.updateById(user);
        permissionService.invalidateCache(id);

        // 1. 终止 Keycloak session（使 refresh_token 立即失效）
        keycloakAdminService.logoutUser(user.getKeycloakId());

        // 2. Redis 黑名单：即时拦截已签发的 JWT（O(1) 快速检查）
        redisTemplate.opsForValue().set(
                DISABLED_USER_KEY_PREFIX + id, "1", DISABLED_USER_TTL);

        // 3. 级联吊销用户所有 API Key（防止禁用后仍可通过已有 Key 访问）
        int revokedKeys = apiKeyService.revokeAllByUser(id);
        if (revokedKeys > 0) {
            log.info("禁用用户 {} 时级联吊销 {} 个 API Key", user.getUsername(), revokedKeys);
        }

        // 审计日志
        systemAuditService.log("disable_user", "user", id,
                Map.of("username", user.getUsername(),
                        "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                        "banStatus", dto.getBanStatus(),
                        "banReason", dto.getBanReason() != null ? dto.getBanReason() : "",
                        "revoked_api_keys", revokedKeys));

        log.info("用户 {} 已禁用(状态:{}，原因:{})：Keycloak session 已终止, Redis 黑名单已写入(TTL={}min), API Key 已吊销({}个)",
                user.getUsername(), dto.getBanStatus(), dto.getBanReason(),
                DISABLED_USER_TTL.toMinutes(), revokedKeys);
    }

    /**
     * 启用用户
     */
    @Transactional
    public void enable(Long id) {
        SysUser user = getById(id);

        // 使用 UpdateWrapper 明确将 ban 字段设为 null
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        updateWrapper.eq(SysUser::getId, id)
                .set(SysUser::getStatus, "active")
                .set(SysUser::getBanStatus, null)
                .set(SysUser::getBanReason, null)
                .set(SysUser::getBannedAt, null)
                .set(SysUser::getBannedBy, null);
        userMapper.update(null, updateWrapper);

        // 清除 Redis 黑名单（允许用户重新登录）
        redisTemplate.delete(DISABLED_USER_KEY_PREFIX + id);

        // 审计日志
        systemAuditService.log("enable_user", "user", id,
                Map.of("username", user.getUsername(),
                        "displayName", user.getDisplayName() != null ? user.getDisplayName() : ""));

        log.info("用户 {} 已启用：Redis 黑名单已清除", user.getUsername());
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
        profile.setBanStatus(user.getBanStatus());
        profile.setBanReason(user.getBanReason());
        profile.setBannedAt(user.getBannedAt());
        if (user.getBannedBy() != null) {
            SysUser bannedByUser = userMapper.selectById(user.getBannedBy());
            profile.setBannedByName(bannedByUser != null ? bannedByUser.getDisplayName() : null);
        }
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
        List<UserProfileVO.RoleInfo> result = new ArrayList<>();

        // 1. 直接分配的全局角色
        List<Long> directRoleIds = getUserGlobalRoleIds(userId);
        if (!directRoleIds.isEmpty()) {
            List<SysRole> directRoles = roleMapper.selectBatchIds(directRoleIds);
            for (SysRole role : directRoles) {
                UserProfileVO.RoleInfo info = new UserProfileVO.RoleInfo();
                info.setId(String.valueOf(role.getId()));
                info.setName(role.getName());
                info.setCode(role.getCode());
                info.setSource("direct");
                result.add(info);
            }
        }

        // 2. 通过用户组继承的全局角色
        List<Long> groupIds = userGroupMemberMapper.selectGroupIdsByUserId(userId);
        if (!groupIds.isEmpty()) {
            // 批量加载组信息
            List<UserGroup> groups = userGroupMapper.selectBatchIds(groupIds);
            Map<Long, UserGroup> groupMap = groups.stream()
                    .collect(Collectors.toMap(UserGroup::getId, g -> g));

            // 查询每个组的全局角色分配
            for (Long groupId : groupIds) {
                List<UserGroupRole> groupRoles = userGroupRoleMapper.selectList(
                        new LambdaQueryWrapper<UserGroupRole>()
                                .eq(UserGroupRole::getGroupId, groupId)
                                .isNull(UserGroupRole::getProjectId)
                );
                for (UserGroupRole gr : groupRoles) {
                    SysRole role = roleMapper.selectById(gr.getRoleId());
                    if (role == null) continue;
                    // 避免重复（已通过直接分配存在的）
                    boolean alreadyAdded = result.stream()
                            .anyMatch(r -> r.getId().equals(String.valueOf(role.getId())));
                    if (alreadyAdded) continue;

                    UserProfileVO.RoleInfo info = new UserProfileVO.RoleInfo();
                    info.setId(String.valueOf(role.getId()));
                    info.setName(role.getName());
                    info.setCode(role.getCode());
                    info.setSource("group");
                    UserGroup group = groupMap.get(groupId);
                    info.setGroupName(group != null ? group.getName() : null);
                    result.add(info);
                }
            }
        }

        return result;
    }

    private List<UserProfileVO.ProjectRoleInfo> buildProjectRoles(Long userId) {
        List<UserProfileVO.ProjectRoleInfo> result = new ArrayList<>();

        // 1. 直接项目成员角色
        List<ProjectMember> memberships = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId)
        );
        if (!memberships.isEmpty()) {
            List<Long> projectIds = memberships.stream().map(ProjectMember::getProjectId).distinct().toList();
            List<Project> projects = projectMapper.selectBatchIds(projectIds);
            Map<Long, Project> projectMap = projects.stream()
                    .collect(Collectors.toMap(Project::getId, p -> p));

            List<Long> roleIds = memberships.stream().map(ProjectMember::getRoleId).distinct().toList();
            List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
            Map<Long, SysRole> roleMap = roles.stream()
                    .collect(Collectors.toMap(SysRole::getId, r -> r));

            for (ProjectMember membership : memberships) {
                Project project = projectMap.get(membership.getProjectId());
                SysRole role = roleMap.get(membership.getRoleId());
                if (project == null || role == null) continue;

                UserProfileVO.ProjectRoleInfo info = new UserProfileVO.ProjectRoleInfo();
                info.setProjectId(String.valueOf(project.getId()));
                info.setProjectName(project.getName());
                info.setProjectKey(project.getKey());
                info.setRoleId(String.valueOf(role.getId()));
                info.setRoleName(role.getName());
                info.setRoleCode(role.getCode());
                info.setJoinedAt(membership.getJoinedAt());
                info.setSource("direct");
                result.add(info);
            }
        }

        // 2. 通过用户组继承的项目角色
        List<Long> groupIds = userGroupMemberMapper.selectGroupIdsByUserId(userId);
        if (!groupIds.isEmpty()) {
            List<UserGroup> groups = userGroupMapper.selectBatchIds(groupIds);
            Map<Long, UserGroup> groupMap = groups.stream()
                    .collect(Collectors.toMap(UserGroup::getId, g -> g));

            for (Long groupId : groupIds) {
                List<UserGroupRole> groupRoles = userGroupRoleMapper.selectList(
                        new LambdaQueryWrapper<UserGroupRole>()
                                .eq(UserGroupRole::getGroupId, groupId)
                                .isNotNull(UserGroupRole::getProjectId)
                );
                for (UserGroupRole gr : groupRoles) {
                    SysRole role = roleMapper.selectById(gr.getRoleId());
                    Project project = projectMapper.selectById(gr.getProjectId());
                    if (role == null || project == null) continue;

                    // 避免重复（同项目同角色已通过直接成员存在的）
                    boolean alreadyAdded = result.stream().anyMatch(r ->
                            r.getProjectId().equals(String.valueOf(project.getId()))
                                    && r.getRoleName().equals(role.getName()));
                    if (alreadyAdded) continue;

                    UserProfileVO.ProjectRoleInfo info = new UserProfileVO.ProjectRoleInfo();
                    info.setProjectId(String.valueOf(project.getId()));
                    info.setProjectName(project.getName());
                    info.setProjectKey(project.getKey());
                    info.setRoleId(String.valueOf(role.getId()));
                    info.setRoleName(role.getName());
                    info.setRoleCode(role.getCode());
                    info.setSource("group");
                    UserGroup group = groupMap.get(groupId);
                    info.setGroupName(group != null ? group.getName() : null);
                    result.add(info);
                }
            }
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
