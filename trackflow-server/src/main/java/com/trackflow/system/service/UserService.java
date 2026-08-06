package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.KeycloakAdminService;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.mapper.IssueActivityMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.entity.ProjectStatus;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.dto.CreateUserDTO;
import com.trackflow.system.dto.DisableUserDTO;
import com.trackflow.system.converter.UserConverter;
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
import com.trackflow.system.vo.UserDataExportVO;
import com.trackflow.system.vo.UserProfileVO;
import com.trackflow.system.vo.UserVO;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.mapper.IssueAttachmentMapper;
import com.trackflow.issue.mapper.IssueCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final IssueCommentMapper issueCommentMapper;
    private final IssueAttachmentMapper issueAttachmentMapper;
    private final KeycloakAdminService keycloakAdminService;
    private final ApiKeyService apiKeyService;
    private final StringRedisTemplate redisTemplate;
    private final UserGroupMemberMapper userGroupMemberMapper;
    private final UserGroupRoleMapper userGroupRoleMapper;
    private final UserGroupMapper userGroupMapper;
    private final UserConverter userConverter;

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
    @Transactional(rollbackFor = Exception.class)
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
     * 分页查询用户列表（含全局角色信息）
     * <p>
     * 封装完整的查询 + VO 转换 + 批量角色填充逻辑，避免 Controller 承担业务编排职责。
     *
     * @return 包含全局角色信息的用户分页结果
     */
    public PageResult<UserVO> listUsersWithRoles(Page<SysUser> page, String keyword, String username,
                                                  String displayName, String email, Long orgId,
                                                  String status, String banStatus, Long roleId) {
        Page<SysUser> result = list(page, keyword, username, displayName, email, orgId, status, banStatus, roleId);

        List<UserVO> voList = userConverter.toVOList(result.getRecords());

        // 批量填充全局角色信息（避免 N+1）
        if (!result.getRecords().isEmpty()) {
            List<Long> userIds = result.getRecords().stream().map(SysUser::getId).toList();
            Map<Long, List<UserVO.GlobalRoleInfo>> rolesMap = batchGetGlobalRoles(userIds);
            for (int i = 0; i < voList.size(); i++) {
                Long userId = result.getRecords().get(i).getId();
                voList.get(i).setGlobalRoles(rolesMap.getOrDefault(userId, List.of()));
            }
        }

        return new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 分页查询用户列表
     */
    public Page<SysUser> list(Page<SysUser> page, String keyword, String username, String displayName,
                              String email, Long orgId, String status, String banStatus, Long roleId) {
        // 如果指定了角色筛选，先查出该角色对应的用户 ID 列表
        List<Long> roleUserIds = null;
        if (roleId != null) {
            roleUserIds = userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, roleId)
            ).stream().map(UserRole::getUserId).toList();
            if (roleUserIds.isEmpty()) {
                // 该角色无用户，直接返回空结果
                return page;
            }
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();

        // roleId 筛选
        if (roleUserIds != null) {
            wrapper.in(SysUser::getId, roleUserIds);
        }

        // keyword: unified search across username, displayName, email (OR condition)
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w
                    .like(SysUser::getUsername, kw)
                    .or().like(SysUser::getDisplayName, kw)
                    .or().like(SysUser::getEmail, kw)
            );
        }

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
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
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
     * 批量获取多个用户的全局角色映射
     * <p>
     * 用于列表页展示，避免 N+1 查询。
     *
     * @param userIds 用户 ID 列表
     * @return Map: userId → List<GlobalRoleInfo>
     */
    public Map<Long, List<UserVO.GlobalRoleInfo>> batchGetGlobalRoles(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        // 1. 批量查询 user_role 表
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds)
        );
        if (userRoles.isEmpty()) {
            return Map.of();
        }

        // 2. 收集所有涉及的 role_id，一次性查出角色信息
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).distinct().toList();
        Map<Long, SysRole> roleMap = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));

        // 3. 按 userId 分组组装结果
        Map<Long, List<UserVO.GlobalRoleInfo>> result = new java.util.HashMap<>();
        for (UserRole ur : userRoles) {
            SysRole role = roleMap.get(ur.getRoleId());
            if (role != null) {
                result.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>())
                        .add(new UserVO.GlobalRoleInfo(
                                String.valueOf(role.getId()),
                                role.getName(),
                                role.getCode()
                        ));
            }
        }
        return result;
    }

    /**
     * 禁用用户
     */
    @Transactional(rollbackFor = Exception.class)
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

        // 保护项目负责人：禁用前检查该用户是否为任何活跃项目的 lead
        List<Project> ledProjects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getLeadId, id)
                        .eq(Project::getStatus, ProjectStatus.ACTIVE));
        if (ledProjects != null && !ledProjects.isEmpty()) {
            String projectNames = ledProjects.stream()
                    .map(Project::getName)
                    .collect(Collectors.joining("、"));
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该用户是以下项目的负责人，请先转让负责人后再禁用：" + projectNames);
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
    @Transactional(rollbackFor = Exception.class)
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
     * 仅允许 role_type='global' 的角色被分配为全局角色（防御性校验）
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignGlobalRole(Long userId, Long roleId) {
        // 校验角色存在且为 global 类型（防御性校验，Controller 已有前置校验）
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色不存在: " + roleId);
        }
        if (!"global".equals(role.getRoleType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "只有全局角色可以通过此路径分配，「" + role.getName() + "」是项目角色");
        }

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
        userRole.setSource(UserRole.SOURCE_MANUAL);
        userRoleMapper.insert(userRole);
        permissionService.invalidateCache(userId);

        // 审计日志
        String roleName = role.getName();
        SysUser user = userMapper.selectById(userId);
        String username = user != null ? user.getUsername() : String.valueOf(userId);
        systemAuditService.log("assign_global_role", "user", userId,
                Map.of("roleId", roleId, "roleName", roleName, "username", username));
    }

    /**
     * 移除全局角色
     */
    @Transactional(rollbackFor = Exception.class)
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
     * 批量替换用户的全局角色集合
     * 
     * 语义：传入期望的完整角色 ID 列表，计算差异后执行增删
     * - 已有但不在列表中的角色会被移除
     * - 不在已有中但在列表中的角色会被添加
     * - 空列表表示清空所有全局角色（受最后管理员保护）
     * 
     * @param userId 用户 ID
     * @param targetRoleIds 期望的角色 ID 列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceGlobalRoles(Long userId, List<Long> targetRoleIds) {
        // 校验用户存在
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + userId);
        }

        // 去重并过滤 null
        List<Long> distinctTargetIds = targetRoleIds == null ? List.of() :
                targetRoleIds.stream().filter(id -> id != null).distinct().toList();

        // 校验所有目标角色存在且为 global 类型
        for (Long roleId : distinctTargetIds) {
            SysRole role = roleMapper.selectById(roleId);
            if (role == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "角色不存在: " + roleId);
            }
            if (!"global".equals(role.getRoleType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "只有全局角色可以通过此路径分配，「" + role.getName() + "」是项目角色");
            }
        }

        // 获取当前全局角色
        List<Long> currentRoleIds = getUserGlobalRoleIds(userId);

        // 计算差异
        List<Long> toAdd = distinctTargetIds.stream()
                .filter(id -> !currentRoleIds.contains(id))
                .toList();
        List<Long> toRemove = currentRoleIds.stream()
                .filter(id -> !distinctTargetIds.contains(id))
                .toList();

        // 检查是否会移除自己的系统管理员角色
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (toRemove.contains(SYSTEM_ADMIN_ROLE_ID) && userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能移除自己的系统管理员角色");
        }

        // 保护最后一个系统管理员
        if (toRemove.contains(SYSTEM_ADMIN_ROLE_ID) && isLastSystemAdmin(userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统至少需要保留一个活跃管理员");
        }

        // 执行删除
        if (!toRemove.isEmpty()) {
            userRoleMapper.delete(
                    new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getUserId, userId)
                            .in(UserRole::getRoleId, toRemove)
            );
        }

        // 执行添加
        for (Long roleId : toAdd) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setSource(UserRole.SOURCE_MANUAL);
            userRoleMapper.insert(userRole);
        }

        // 刷新权限缓存
        if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
            permissionService.invalidateCache(userId);
        }

        // 审计日志
        if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
            List<String> addedNames = toAdd.stream()
                    .map(id -> {
                        SysRole r = roleMapper.selectById(id);
                        return r != null ? r.getName() : String.valueOf(id);
                    }).toList();
            List<String> removedNames = toRemove.stream()
                    .map(id -> {
                        SysRole r = roleMapper.selectById(id);
                        return r != null ? r.getName() : String.valueOf(id);
                    }).toList();

            systemAuditService.log("replace_global_roles", "user", userId,
                    Map.of(
                            "username", user.getUsername(),
                            "addedRoles", addedNames,
                            "removedRoles", removedNames,
                            "finalRoleIds", distinctTargetIds
                    ));
        }
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
        profile.setKeycloakId(user.getKeycloakId());
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

    /**
     * 导出指定用户的所有个人数据（GDPR 数据可携权）
     *
     * @param userId 目标用户ID
     * @return 包含用户所有数据的导出对象
     * @throws BusinessException 当用户不存在时
     */
    public UserDataExportVO exportUserData(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: " + userId);
        }

        UserDataExportVO export = new UserDataExportVO();
        export.setExportDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        export.setExportedBy(SecurityUtils.getCurrentUsername());

        // 1. 用户基本信息
        export.setUserInfo(buildUserBasicInfo(user));

        // 2. 全局角色
        export.setGlobalRoles(buildExportGlobalRoles(userId));

        // 3. 项目成员关系
        export.setProjectMemberships(buildProjectMemberships(userId));

        // 4. 创建的工单
        export.setCreatedIssues(buildIssuesByReporter(userId));

        // 5. 分配给该用户的工单
        export.setAssignedIssues(buildIssuesByAssignee(userId));

        // 6. 评论
        export.setComments(buildComments(userId));

        // 7. 活动记录
        export.setActivities(buildActivities(userId));

        // 8. 附件
        export.setAttachments(buildAttachments(userId));

        log.info("用户数据导出完成: userId={}, exportedBy={}", userId, export.getExportedBy());
        return export;
    }

    private UserDataExportVO.UserBasicInfo buildUserBasicInfo(SysUser user) {
        UserDataExportVO.UserBasicInfo info = new UserDataExportVO.UserBasicInfo();
        info.setId(String.valueOf(user.getId()));
        info.setUsername(user.getUsername());
        info.setDisplayName(user.getDisplayName());
        info.setEmail(user.getEmail());
        info.setPhone(user.getPhone());
        info.setAvatarUrl(user.getAvatarUrl());
        info.setStatus(user.getStatus());
        info.setBanStatus(user.getBanStatus());
        info.setBanReason(user.getBanReason());
        info.setBannedAt(user.getBannedAt());
        info.setLastLoginAt(user.getLastLoginAt());
        info.setCreatedAt(user.getCreatedAt());
        info.setUpdatedAt(user.getUpdatedAt());
        return info;
    }

    private List<String> buildExportGlobalRoles(Long userId) {
        List<Long> roleIds = getUserGlobalRoleIds(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream().map(SysRole::getName).toList();
    }

    private List<UserDataExportVO.ProjectMembership> buildProjectMemberships(Long userId) {
        List<ProjectMember> members = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId)
        );
        if (members.isEmpty()) {
            return List.of();
        }

        List<Long> projectIds = members.stream().map(ProjectMember::getProjectId).distinct().toList();
        List<Long> roleIds = members.stream().map(ProjectMember::getRoleId).distinct().toList();

        Map<Long, Project> projectMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));
        Map<Long, SysRole> roleMap = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));

        return members.stream().map(member -> {
            UserDataExportVO.ProjectMembership pm = new UserDataExportVO.ProjectMembership();
            pm.setProjectId(String.valueOf(member.getProjectId()));
            pm.setJoinedAt(member.getJoinedAt());

            Project project = projectMap.get(member.getProjectId());
            if (project != null) {
                pm.setProjectName(project.getName());
                pm.setProjectKey(project.getKey());
            }

            SysRole role = roleMap.get(member.getRoleId());
            if (role != null) {
                pm.setRoleName(role.getName());
            }
            return pm;
        }).toList();
    }

    private List<UserDataExportVO.IssueData> buildIssuesByReporter(Long userId) {
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getReporterId, userId)
                        .isNull(Issue::getDeletedAt)
                        .orderByDesc(Issue::getCreatedAt)
        );
        return issues.stream().map(this::toIssueData).toList();
    }

    private List<UserDataExportVO.IssueData> buildIssuesByAssignee(Long userId) {
        List<Issue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getAssigneeId, userId)
                        .isNull(Issue::getDeletedAt)
                        .orderByDesc(Issue::getCreatedAt)
        );
        return issues.stream().map(this::toIssueData).toList();
    }

    private UserDataExportVO.IssueData toIssueData(Issue issue) {
        UserDataExportVO.IssueData data = new UserDataExportVO.IssueData();
        data.setId(String.valueOf(issue.getId()));
        data.setIssueKey(issue.getIssueKey());
        data.setTitle(issue.getTitle());
        data.setIssueType(issue.getIssueType());
        data.setPriority(issue.getPriority());
        data.setCreatedAt(issue.getCreatedAt());
        data.setUpdatedAt(issue.getUpdatedAt());
        return data;
    }

    private List<UserDataExportVO.CommentData> buildComments(Long userId) {
        List<IssueComment> comments = issueCommentMapper.selectList(
                new LambdaQueryWrapper<IssueComment>()
                        .eq(IssueComment::getUserId, userId)
                        .isNull(IssueComment::getDeletedAt)
                        .orderByDesc(IssueComment::getCreatedAt)
        );
        return comments.stream().map(comment -> {
            UserDataExportVO.CommentData data = new UserDataExportVO.CommentData();
            data.setId(String.valueOf(comment.getId()));
            data.setIssueId(String.valueOf(comment.getIssueId()));
            data.setContent(comment.getContent());
            data.setSource(comment.getSource());
            data.setCreatedAt(comment.getCreatedAt());
            return data;
        }).toList();
    }

    private List<UserDataExportVO.ActivityData> buildActivities(Long userId) {
        List<IssueActivity> activities = issueActivityMapper.selectList(
                new LambdaQueryWrapper<IssueActivity>()
                        .eq(IssueActivity::getUserId, userId)
                        .orderByDesc(IssueActivity::getCreatedAt)
        );
        return activities.stream().map(activity -> {
            UserDataExportVO.ActivityData data = new UserDataExportVO.ActivityData();
            data.setId(String.valueOf(activity.getId()));
            data.setIssueId(String.valueOf(activity.getIssueId()));
            data.setAction(activity.getAction());
            data.setFieldName(activity.getFieldName());
            data.setOldValue(activity.getOldValue());
            data.setNewValue(activity.getNewValue());
            data.setCreatedAt(activity.getCreatedAt());
            return data;
        }).toList();
    }

    private List<UserDataExportVO.AttachmentData> buildAttachments(Long userId) {
        List<IssueAttachment> attachments = issueAttachmentMapper.selectList(
                new LambdaQueryWrapper<IssueAttachment>()
                        .eq(IssueAttachment::getUploadedBy, userId)
                        .orderByDesc(IssueAttachment::getCreatedAt)
        );
        return attachments.stream().map(attachment -> {
            UserDataExportVO.AttachmentData data = new UserDataExportVO.AttachmentData();
            data.setId(String.valueOf(attachment.getId()));
            data.setIssueId(String.valueOf(attachment.getIssueId()));
            data.setFileName(attachment.getFileName());
            data.setFileSize(attachment.getFileSize());
            data.setContentType(attachment.getContentType());
            data.setCreatedAt(attachment.getCreatedAt());
            return data;
        }).toList();
    }
}
