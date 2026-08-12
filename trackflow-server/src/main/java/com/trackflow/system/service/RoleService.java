package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.constant.RoleTypes;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.annotation.AuditLog;
import com.trackflow.common.audit.AuditContext;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.workflow.entity.WorkflowTransition;
import com.trackflow.workflow.mapper.WorkflowTransitionMapper;
import com.trackflow.system.converter.UserConverter;
import com.trackflow.system.dto.CreateRoleDTO;
import com.trackflow.system.dto.UpdateRoleDTO;
import com.trackflow.system.entity.RolePermission;
import com.trackflow.system.entity.SysPermission;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.entity.UserRole;
import com.trackflow.system.mapper.RolePermissionMapper;
import com.trackflow.system.mapper.SysPermissionMapper;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserRoleMapper;
import com.trackflow.system.vo.PermissionGroupVO;
import com.trackflow.system.vo.PermissionVO;
import com.trackflow.system.vo.RoleStatsVO;
import com.trackflow.system.vo.RoleUsersVO;
import com.trackflow.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理服务
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserMapper userMapper;
    private final PermissionService permissionService;
    private final SystemAuditService systemAuditService;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMapper projectMapper;
    private final UserConverter userConverter;
    private final WorkflowTransitionMapper workflowTransitionMapper;
    private final PermissionImplicationService permissionImplicationService;

    /**
     * 获取角色管理统计数据
     */
    public RoleStatsVO getStats() {
        long total = roleMapper.selectCount(null);
        long globalRoles = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleType, "global"));
        long projectRoles = total - globalRoles;
        // 有用户分配的角色数（user_role 表中有记录的 role_id 去重）
        long rolesInUse = userRoleMapper.selectCount(null) > 0
                ? roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                        .inSql(SysRole::getId, "SELECT DISTINCT role_id FROM user_role"))
                : 0;
        return RoleStatsVO.builder()
                .total(total)
                .globalRoles(globalRoles)
                .projectRoles(projectRoles)
                .rolesInUse(rolesInUse)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public SysRole create(CreateRoleDTO dto) {
        // 检查 code 唯一性
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, dto.getCode())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.ROLE_CODE_DUPLICATE);
        }

        SysRole role = new SysRole();
        role.setName(dto.getName());
        role.setCode(dto.getCode());
        role.setDescription(dto.getDescription());
        role.setRoleType(dto.getRoleType());
        role.setBuiltin(false);
        role.setSortOrder(0);
        roleMapper.insert(role);
        return role;
    }

    public Page<SysRole> list(Page<SysRole> page, String roleType) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (roleType != null && !roleType.isBlank()) {
            wrapper.eq(SysRole::getRoleType, roleType);
        }
        wrapper.orderByAsc(SysRole::getSortOrder);
        return roleMapper.selectPage(page, wrapper);
    }

    public SysRole getById(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found");
        }
        return role;
    }

    @Transactional(rollbackFor = Exception.class)
    public SysRole update(Long id, UpdateRoleDTO dto) {
        SysRole role = getById(id);

        // 内置角色不允许编辑
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            throw new BusinessException(ErrorCode.BUILTIN_ROLE_PROTECTED);
        }

        if (dto.getName() != null) {
            String trimmedName = dto.getName().trim();
            if (trimmedName.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "角色名称不能为空");
            }
            role.setName(trimmedName);
        }
        if (dto.getDescription() != null) role.setDescription(dto.getDescription().trim());
        if (dto.getSortOrder() != null) role.setSortOrder(dto.getSortOrder());
        roleMapper.updateById(role);
        return role;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = getById(id);

        // 内置角色不能删除
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            throw new BusinessException(ErrorCode.BUILTIN_ROLE_PROTECTED);
        }

        // 检查是否有全局用户关联（user_role 表）
        Long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id)
        );
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE,
                    "该角色已分配给 " + userCount + " 位用户（全局角色），请先移除用户的角色分配后再删除");
        }

        // 检查是否有项目成员关联（project_member 表）
        Long memberCount = projectMemberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getRoleId, id)
        );
        if (memberCount > 0) {
            // 查询具体有多少项目使用了该角色
            List<ProjectMember> members = projectMemberMapper.selectList(
                    new LambdaQueryWrapper<ProjectMember>()
                            .select(ProjectMember::getProjectId)
                            .eq(ProjectMember::getRoleId, id)
                            .groupBy(ProjectMember::getProjectId)
            );
            long projectCount = members.size();
            throw new BusinessException(ErrorCode.ROLE_IN_USE,
                    "该角色已分配给 " + projectCount + " 个项目中的 " + memberCount + " 位成员，请先重新分配角色后再删除");
        }

        // 检查是否有工作流转换规则关联（workflow_transition 表）
        Long transitionCount = workflowTransitionMapper.selectCount(
                new LambdaQueryWrapper<WorkflowTransition>().eq(WorkflowTransition::getRoleId, id)
        );
        if (transitionCount > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE,
                    "该角色在 " + transitionCount + " 条工作流转换规则中被引用，请先删除相关工作流规则后再删除角色");
        }

        // 删除角色及其权限
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)
        );
        roleMapper.deleteById(id);
    }

    /**
     * 启用或禁用角色
     */
    @Transactional(rollbackFor = Exception.class)
    public SysRole setEnabled(Long id, boolean enabled) {
        SysRole role = getById(id);
        if (Boolean.TRUE.equals(role.getBuiltin()) && !enabled) {
            throw new BusinessException(ErrorCode.BUILTIN_ROLE_PROTECTED, "内置角色不能被禁用");
        }
        role.setEnabled(enabled);
        roleMapper.updateById(role);
        return role;
    }

    /**
     * 克隆角色（复制角色定义 + 权限）
     */
    @AuditLog(action = "clone_role", targetType = "role", targetId = "#result.id")
    @Transactional(rollbackFor = Exception.class)
    public SysRole clone(Long sourceId, String newName, String newCode) {
        SysRole source = getById(sourceId);

        // 检查新编码唯一性
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, newCode)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.ROLE_CODE_DUPLICATE);
        }

        // 创建新角色（继承类型，标记为非内置）
        SysRole newRole = new SysRole();
        newRole.setName(newName);
        newRole.setCode(newCode);
        newRole.setDescription(source.getDescription());
        newRole.setRoleType(source.getRoleType());
        newRole.setBuiltin(false);
        newRole.setSortOrder(0);
        roleMapper.insert(newRole);

        // 复制权限
        List<RolePermission> sourcePerms = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, sourceId)
        );
        for (RolePermission sp : sourcePerms) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(newRole.getId());
            rp.setPermission(sp.getPermission());
            rolePermissionMapper.insert(rp);
        }

        AuditContext.put("sourceName", source.getName());
        AuditContext.put("sourceId", source.getId());
        AuditContext.put("newName", newName);
        AuditContext.put("newCode", newCode);
        AuditContext.put("permissionCount", sourcePerms.size());

        return newRole;
    }

    /**
     * 合并角色：将源角色的权限与用户分配合并到目标角色，然后删除源角色。
     * <p>
     * 业务流程（参考 YouTrack Merge Roles）：
     * 1. 目标角色继承所有源角色的权限（并集）
     * 2. 所有源角色的用户/组分配被迁移到目标角色
     * 3. 源角色被删除
     * 4. 失效所有受影响用户的权限缓存
     * </p>
     *
     * @param sourceRoleIds 源角色 ID 列表（将被合并删除）
     * @param targetRoleId  目标角色 ID（保留）
     * @return 合并后的目标角色
     */
    @AuditLog(action = "merge_roles", targetType = "role", targetId = "#targetRoleId")
    @Transactional(rollbackFor = Exception.class)
    public SysRole mergeRoles(List<Long> sourceRoleIds, Long targetRoleId) {
        // 1. 校验：目标角色不能在源角色列表中
        if (sourceRoleIds.contains(targetRoleId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标角色不能同时是源角色");
        }

        // 2. 查询目标角色
        SysRole targetRole = getById(targetRoleId);

        // 3. 查询所有源角色，并验证
        List<SysRole> sourceRoles = new ArrayList<>();
        for (Long sourceId : sourceRoleIds) {
            SysRole sourceRole = getById(sourceId);
            // 内置角色不能作为源角色被合并删除
            if (Boolean.TRUE.equals(sourceRole.getBuiltin())) {
                throw new BusinessException(ErrorCode.BUILTIN_ROLE_PROTECTED,
                        "内置角色 '" + sourceRole.getName() + "' 不能作为源角色被合并删除");
            }
            sourceRoles.add(sourceRole);
        }

        // 4. 提权保护：计算合并后的最终权限集，检查操作者是否持有所有权限
        Set<String> targetPermissions = new HashSet<>(getPermissions(targetRoleId));
        Set<String> allSourcePermissions = new HashSet<>();
        for (Long sourceId : sourceRoleIds) {
            allSourcePermissions.addAll(getPermissions(sourceId));
        }
        // 合并后的完整权限集 = 目标权限 ∪ 所有源权限
        Set<String> mergedPermissions = new HashSet<>(targetPermissions);
        mergedPermissions.addAll(allSourcePermissions);

        // 检查操作者是否有权授予所有最终权限
        Long currentUserId = SecurityUtils.getCurrentUserId();
        checkPrivilegeEscalation(currentUserId,
                new ArrayList<>(targetPermissions),
                new ArrayList<>(mergedPermissions));

        // 5. 合并权限（并集）：将源角色中有但目标角色中没有的权限添加到目标角色
        Set<String> newPermissions = new HashSet<>(allSourcePermissions);
        newPermissions.removeAll(targetPermissions); // 只保留目标角色缺少的
        for (String permission : newPermissions) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(targetRoleId);
            rp.setPermission(permission);
            rolePermissionMapper.insert(rp);
        }

        // 6. 迁移 project_member 的 role_id
        Set<Long> affectedUserIds = new HashSet<>();
        for (Long sourceId : sourceRoleIds) {
            // 获取受影响的用户
            List<ProjectMember> members = projectMemberMapper.selectList(
                    new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getRoleId, sourceId)
            );
            for (ProjectMember member : members) {
                affectedUserIds.add(member.getUserId());
                // 检查是否已存在相同 user+project+targetRole 的记录（避免重复）
                Long existCount = projectMemberMapper.selectCount(
                        new LambdaQueryWrapper<ProjectMember>()
                                .eq(ProjectMember::getUserId, member.getUserId())
                                .eq(ProjectMember::getProjectId, member.getProjectId())
                                .eq(ProjectMember::getRoleId, targetRoleId)
                );
                if (existCount == 0) {
                    // 更新为目标角色
                    member.setRoleId(targetRoleId);
                    projectMemberMapper.updateById(member);
                } else {
                    // 已存在则直接删除源记录
                    projectMemberMapper.deleteById(member.getId());
                }
            }
        }

        // 7. 迁移 user_role（全局角色分配）
        for (Long sourceId : sourceRoleIds) {
            List<UserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, sourceId)
            );
            for (UserRole userRole : userRoles) {
                affectedUserIds.add(userRole.getUserId());
                Long existCount = userRoleMapper.selectCount(
                        new LambdaQueryWrapper<UserRole>()
                                .eq(UserRole::getUserId, userRole.getUserId())
                                .eq(UserRole::getRoleId, targetRoleId)
                );
                if (existCount == 0) {
                    userRole.setRoleId(targetRoleId);
                    userRoleMapper.updateById(userRole);
                } else {
                    userRoleMapper.deleteById(userRole.getId());
                }
            }
        }

        // 8. 删除源角色及其权限记录
        for (Long sourceId : sourceRoleIds) {
            rolePermissionMapper.delete(
                    new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, sourceId)
            );
            roleMapper.deleteById(sourceId);
        }

        // 9. 失效所有受影响用户的权限缓存
        for (Long userId : affectedUserIds) {
            permissionService.invalidateCache(userId);
        }

        // 10. 审计日志
        List<String> sourceNames = sourceRoles.stream()
                .map(SysRole::getName)
                .collect(Collectors.toList());
        AuditContext.put("targetName", targetRole.getName());
        AuditContext.put("sourceNames", String.join(", ", sourceNames));
        AuditContext.put("sourceRoleIds", sourceRoleIds.toString());
        AuditContext.put("mergedPermissionCount", newPermissions.size());
        AuditContext.put("migratedUserCount", affectedUserIds.size());

        return getById(targetRoleId);
    }

    /**
     * 替换角色的所有权限。
     * <p>
     * 权限提权保护（参考 YouTrack）：
     * - 操作者只能添加自己已持有的权限到角色中
     * - 移除权限不受此限制（只要有 manage_roles 权限即可移除任何权限）
     * - system_admin 用户不受此限制（拥有所有权限）
     */
    @AuditLog(action = "update_role_permissions", targetType = "role", targetId = "#id")
    @Transactional(rollbackFor = Exception.class)
    public void replacePermissions(Long id, List<String> permissions) {
        SysRole role = getById(id); // 确保存在

        // 内置角色不允许修改权限
        if (Boolean.TRUE.equals(role.getBuiltin())) {
            throw new BusinessException(ErrorCode.BUILTIN_ROLE_PROTECTED);
        }

        // 记录旧权限（审计用 + 提权检查用）
        List<String> oldPermissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)
        ).stream().map(RolePermission::getPermission).collect(Collectors.toList());

        // 提权保护：检查操作者是否有权授予新增的权限
        Long currentUserId = SecurityUtils.getCurrentUserId();
        checkPrivilegeEscalation(currentUserId, oldPermissions, permissions);

        // 解析隐含/依赖权限关系
        Set<String> oldSet = new HashSet<>(oldPermissions);
        Set<String> newSet = new HashSet<>(permissions);
        Set<String> addedPermissions = newSet.stream().filter(p -> !oldSet.contains(p)).collect(Collectors.toSet());
        Set<String> removedPermissions = oldSet.stream().filter(p -> !newSet.contains(p)).collect(Collectors.toSet());

        // 添加上层权限时，自动包含其隐含的底层权限
        if (!addedPermissions.isEmpty()) {
            Set<String> impliedPermissions = permissionImplicationService.resolveImpliedPermissions(addedPermissions);
            newSet.addAll(impliedPermissions);
        }

        // 移除底层权限时，自动级联移除依赖它的上层权限
        if (!removedPermissions.isEmpty()) {
            Set<String> dependentRemovals = permissionImplicationService.resolveDependentRemovals(removedPermissions, newSet);
            newSet.removeAll(dependentRemovals);
        }

        List<String> resolvedPermissions = new ArrayList<>(newSet);

        // 删除旧权限
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)
        );

        // 插入新权限
        for (String perm : resolvedPermissions) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(id);
            rp.setPermission(perm);
            rolePermissionMapper.insert(rp);
        }

        // 失效缓存
        permissionService.invalidateCacheForRole(id);

        // 审计日志：记录旧权限和新权限
        AuditContext.put("roleName", role.getName());
        AuditContext.put("oldPermissions", oldPermissions);
        AuditContext.put("newPermissions", resolvedPermissions);
    }

    /**
     * 提权保护检查：操作者只能添加自己已持有的权限。
     * <p>
     * 规则（参考 YouTrack privilege escalation protection）：
     * - 找出相对于旧权限"新增的权限"（newPerms - oldPerms）
     * - 如果新增的权限中有操作者不持有的 → 拒绝并记录审计
     * - 移除权限不受此限制
     * - system_admin 不受此限制
     *
     * @param operatorId     操作者用户 ID
     * @param oldPermissions 角色当前已有权限
     * @param newPermissions 请求设置的目标权限
     */
    private void checkPrivilegeEscalation(Long operatorId, List<String> oldPermissions, List<String> newPermissions) {
        // system_admin 不受限制
        if (permissionService.isSystemAdmin(operatorId)) {
            return;
        }

        // 计算新增的权限（新列表中有、旧列表中没有的）
        Set<String> oldSet = new HashSet<>(oldPermissions);
        Set<String> addedPermissions = newPermissions.stream()
                .filter(p -> !oldSet.contains(p))
                .collect(Collectors.toSet());

        if (addedPermissions.isEmpty()) {
            return; // 没有新增权限（只是移除），允许
        }

        // 获取操作者持有的所有权限（全局 + 所有项目级）
        Set<String> operatorPermissions = new HashSet<>(permissionService.getPermissions(operatorId));
        operatorPermissions.addAll(permissionService.getAllProjectPermissionsForUser(operatorId));

        // 检查新增权限是否都在操作者的权限集中
        Set<String> unauthorizedPermissions = addedPermissions.stream()
                .filter(p -> !operatorPermissions.contains(p))
                .collect(Collectors.toSet());

        if (!unauthorizedPermissions.isEmpty()) {
            // 记录失败的提权尝试
            systemAuditService.log("privilege_escalation_attempt", "role", null,
                    Map.of("operatorId", operatorId,
                            "attemptedPermissions", new ArrayList<>(unauthorizedPermissions)));

            throw new BusinessException(ErrorCode.PRIVILEGE_ESCALATION_DENIED,
                    "您不能授予自己不持有的权限: " + String.join(", ", unauthorizedPermissions));
        }
    }

    /**
     * 获取角色的权限列表
     */
    public List<String> getPermissions(Long id) {
        getById(id); // 确保存在
        return rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id)
        ).stream().map(RolePermission::getPermission).collect(Collectors.toList());
    }

    /**
     * 获取所有可用权限（按分类分组，含元数据）
     */
    public List<PermissionGroupVO> getAllPermissionGroups() {
        List<SysPermission> allPerms = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getEnabled, true)
                        .orderByAsc(SysPermission::getCategory)
                        .orderByAsc(SysPermission::getSortOrder)
        );

        // 按 category 分组，保持插入顺序
        Map<String, List<PermissionVO>> grouped = new LinkedHashMap<>();
        for (SysPermission perm : allPerms) {
            PermissionVO vo = new PermissionVO();
            vo.setCode(perm.getCode());
            vo.setName(perm.getName());
            vo.setDescription(perm.getDescription());
            vo.setScope(perm.getScope());
            grouped.computeIfAbsent(perm.getCategory(), k -> new ArrayList<>()).add(vo);
        }

        // 转为 List<PermissionGroupVO>
        List<PermissionGroupVO> result = new ArrayList<>();
        for (Map.Entry<String, List<PermissionVO>> entry : grouped.entrySet()) {
            PermissionGroupVO group = new PermissionGroupVO();
            group.setCategory(entry.getKey());
            group.setPermissions(entry.getValue());
            result.add(group);
        }
        return result;
    }

    /**
     * 获取所有可用权限（按分类，兼容旧格式：Map<category, List<code>>）
     */
    public Map<String, List<String>> getAllPermissions() {
        List<SysPermission> allPerms = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getEnabled, true)
                        .orderByAsc(SysPermission::getCategory)
                        .orderByAsc(SysPermission::getSortOrder)
        );

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (SysPermission perm : allPerms) {
            result.computeIfAbsent(perm.getCategory(), k -> new ArrayList<>()).add(perm.getCode());
        }
        return result;
    }

    /**
     * 获取当前操作者可授予的权限集合。
     * - system_admin 返回含 "*" 的特殊集合（表示可授予所有权限）
     * - 其他用户返回其全局权限 + 所有项目级权限的合集
     */
    public Set<String> getGrantablePermissions() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (permissionService.isSystemAdmin(userId)) {
            return Set.of("*");
        }
        Set<String> permissions = new HashSet<>(permissionService.getPermissions(userId));
        permissions.addAll(permissionService.getAllProjectPermissionsForUser(userId));
        return permissions;
    }

    /**
     * 获取角色已分配用户详情（区分全局角色和项目角色）
     */
    public RoleUsersVO getRoleUsers(Long roleId) {
        SysRole role = getById(roleId);

        RoleUsersVO vo = new RoleUsersVO();
        vo.setRoleId(String.valueOf(role.getId()));
        vo.setRoleName(role.getName());
        vo.setRoleType(role.getRoleType());

        Set<Long> allUserIds = new HashSet<>();

        if (RoleTypes.GLOBAL.equals(role.getRoleType())) {
            // 全局角色：从 user_role 表查询
            List<Long> userIds = userRoleMapper.selectUserIdsByRoleId(roleId);
            allUserIds.addAll(userIds);

            List<SysUser> users = userIds.isEmpty() ? List.of() :
                    userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .in(SysUser::getId, userIds)
                            .orderByAsc(SysUser::getUsername));
            vo.setGlobalUsers(userConverter.toVOList(users));
            vo.setProjectGroups(List.of());
        } else {
            // 项目角色：从 project_member 表查询，按项目分组
            vo.setGlobalUsers(List.of());

            List<ProjectMember> members = projectMemberMapper.selectList(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getRoleId, roleId));

            if (members.isEmpty()) {
                vo.setProjectGroups(List.of());
            } else {
                // 按项目分组
                Map<Long, List<Long>> projectUserMap = new LinkedHashMap<>();
                for (ProjectMember m : members) {
                    projectUserMap.computeIfAbsent(m.getProjectId(), k -> new ArrayList<>())
                            .add(m.getUserId());
                    allUserIds.add(m.getUserId());
                }

                // 批量查询项目信息
                List<Long> projectIds = new ArrayList<>(projectUserMap.keySet());
                List<Project> projects = projectMapper.selectList(
                        new LambdaQueryWrapper<Project>().in(Project::getId, projectIds));
                Map<Long, Project> projectMap = projects.stream()
                        .collect(Collectors.toMap(Project::getId, p -> p));

                // 批量查询用户信息
                List<SysUser> allUsers = allUserIds.isEmpty() ? List.of() :
                        userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                                .in(SysUser::getId, allUserIds));
                Map<Long, SysUser> userMap = allUsers.stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

                // 组装分组数据
                List<RoleUsersVO.ProjectRoleGroup> groups = new ArrayList<>();
                for (Map.Entry<Long, List<Long>> entry : projectUserMap.entrySet()) {
                    Long projectId = entry.getKey();
                    List<Long> userIds = entry.getValue();

                    Project project = projectMap.get(projectId);
                    if (project == null) continue;

                    RoleUsersVO.ProjectRoleGroup group = new RoleUsersVO.ProjectRoleGroup();
                    group.setProjectId(String.valueOf(projectId));
                    group.setProjectName(project.getName());
                    group.setProjectKey(project.getKey());

                    List<SysUser> groupUsers = userIds.stream()
                            .map(userMap::get)
                            .filter(u -> u != null)
                            .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                            .collect(Collectors.toList());
                    group.setUsers(userConverter.toVOList(groupUsers));
                    groups.add(group);
                }
                // 按项目名称排序
                groups.sort((a, b) -> a.getProjectName().compareToIgnoreCase(b.getProjectName()));
                vo.setProjectGroups(groups);
            }
        }

        vo.setTotalUserCount(allUserIds.size());
        return vo;
    }

    /**
     * 批量获取角色的用户计数
     */
    public Map<Long, Integer> getUserCountsByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> countMap = new LinkedHashMap<>();
        for (Long roleId : roleIds) {
            countMap.put(roleId, 0);
        }

        // 全局角色计数：从 user_role 表
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().in(UserRole::getRoleId, roleIds));
        for (UserRole ur : userRoles) {
            countMap.merge(ur.getRoleId(), 1, Integer::sum);
        }

        // 项目角色计数：从 project_member 表（同一用户在不同项目算不同分配，但计入唯一用户数）
        List<ProjectMember> projectMembers = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().in(ProjectMember::getRoleId, roleIds));

        // 按角色分组，统计唯一用户数
        Map<Long, Set<Long>> projectRoleUsers = new LinkedHashMap<>();
        for (ProjectMember pm : projectMembers) {
            projectRoleUsers.computeIfAbsent(pm.getRoleId(), k -> new HashSet<>())
                    .add(pm.getUserId());
        }
        for (Map.Entry<Long, Set<Long>> entry : projectRoleUsers.entrySet()) {
            countMap.put(entry.getKey(), entry.getValue().size());
        }

        return countMap;
    }
}
