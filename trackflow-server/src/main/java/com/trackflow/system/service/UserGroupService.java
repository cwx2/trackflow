package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.system.dto.CreateGroupDTO;
import com.trackflow.system.dto.GroupRoleDTO;
import com.trackflow.system.dto.UpdateGroupDTO;
import com.trackflow.system.entity.*;
import com.trackflow.system.mapper.*;
import com.trackflow.system.vo.UserGroupDetailVO;
import com.trackflow.system.vo.UserGroupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户组管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserGroupMapper groupMapper;
    private final UserGroupMemberMapper memberMapper;
    private final UserGroupRoleMapper groupRoleMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final ProjectMapper projectMapper;
    private final PermissionService permissionService;
    private final SystemAuditService systemAuditService;

    /**
     * 分页查询用户组列表
     */
    public Page<UserGroupVO> list(Page<UserGroup> page, String keyword) {
        LambdaQueryWrapper<UserGroup> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(UserGroup::getName, keyword)
                    .or().like(UserGroup::getDescription, keyword);
        }
        wrapper.orderByAsc(UserGroup::getName);
        Page<UserGroup> result = groupMapper.selectPage(page, wrapper);

        // 转换为 VO 并填充统计信息
        List<UserGroupVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .toList();

        Page<UserGroupVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 获取用户组详情（含成员列表 + 角色分配）
     */
    public UserGroupDetailVO getDetail(Long groupId) {
        UserGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户组不存在");
        }

        UserGroupDetailVO detail = new UserGroupDetailVO();
        detail.setId(String.valueOf(group.getId()));
        detail.setName(group.getName());
        detail.setDescription(group.getDescription());
        detail.setCreatedAt(group.getCreatedAt());
        detail.setUpdatedAt(group.getUpdatedAt());

        // 加载成员
        detail.setMembers(loadMembers(groupId));

        // 加载角色分配
        detail.setRoles(loadRoleAssignments(groupId));

        return detail;
    }

    /**
     * 创建用户组
     */
    @Transactional
    public UserGroup create(CreateGroupDTO dto) {
        // 校验名称唯一
        Long count = groupMapper.selectCount(
                new LambdaQueryWrapper<UserGroup>().eq(UserGroup::getName, dto.getName())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "用户组名称已存在: " + dto.getName());
        }

        UserGroup group = new UserGroup();
        group.setName(dto.getName());
        group.setDescription(dto.getDescription());
        groupMapper.insert(group);

        systemAuditService.log("create_group", "user_group", group.getId(),
                Map.of("name", dto.getName()));

        log.info("用户组已创建: id={}, name={}", group.getId(), dto.getName());
        return group;
    }

    /**
     * 更新用户组
     */
    @Transactional
    public UserGroup update(Long groupId, UpdateGroupDTO dto) {
        UserGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户组不存在");
        }

        // 名称唯一性校验（排除自身）
        Long count = groupMapper.selectCount(
                new LambdaQueryWrapper<UserGroup>()
                        .eq(UserGroup::getName, dto.getName())
                        .ne(UserGroup::getId, groupId)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "用户组名称已存在: " + dto.getName());
        }

        String oldName = group.getName();
        group.setName(dto.getName());
        group.setDescription(dto.getDescription());
        groupMapper.updateById(group);

        systemAuditService.log("update_group", "user_group", groupId,
                Map.of("oldName", oldName, "newName", dto.getName()));

        return group;
    }

    /**
     * 删除用户组
     */
    @Transactional
    public void delete(Long groupId) {
        UserGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户组不存在");
        }

        // 删除前先失效所有组成员的权限缓存
        List<Long> memberUserIds = memberMapper.selectUserIdsByGroupId(groupId);
        memberUserIds.forEach(permissionService::invalidateCache);

        // 级联删除（数据库外键 ON DELETE CASCADE 会清理关联表）
        groupMapper.deleteById(groupId);

        systemAuditService.log("delete_group", "user_group", groupId,
                Map.of("name", group.getName(), "memberCount", memberUserIds.size()));

        log.info("用户组已删除: id={}, name={}, memberCount={}", groupId, group.getName(), memberUserIds.size());
    }

    /**
     * 添加成员到组
     */
    @Transactional
    public void addMembers(Long groupId, List<Long> userIds) {
        UserGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户组不存在");
        }

        // 查出已存在的成员
        List<Long> existingUserIds = memberMapper.selectUserIdsByGroupId(groupId);
        Set<Long> existingSet = new HashSet<>(existingUserIds);

        List<Long> addedUserIds = new ArrayList<>();
        for (Long userId : userIds) {
            if (existingSet.contains(userId)) {
                continue; // 幂等：已在组中的跳过
            }
            // 校验用户存在
            SysUser user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在: ID=" + userId);
            }

            UserGroupMember member = new UserGroupMember();
            member.setGroupId(groupId);
            member.setUserId(userId);
            member.setCreatedAt(LocalDateTime.now());
            memberMapper.insert(member);
            addedUserIds.add(userId);
        }

        // 失效新成员的权限缓存（他们现在可能继承了组的角色）
        addedUserIds.forEach(permissionService::invalidateCache);

        if (!addedUserIds.isEmpty()) {
            systemAuditService.log("add_group_members", "user_group", groupId,
                    Map.of("groupName", group.getName(), "addedCount", addedUserIds.size()));
            log.info("向用户组 {}({}) 添加了 {} 名成员", group.getName(), groupId, addedUserIds.size());
        }
    }

    /**
     * 从组中移除成员
     */
    @Transactional
    public void removeMembers(Long groupId, List<Long> userIds) {
        UserGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户组不存在");
        }

        memberMapper.delete(
                new LambdaQueryWrapper<UserGroupMember>()
                        .eq(UserGroupMember::getGroupId, groupId)
                        .in(UserGroupMember::getUserId, userIds)
        );

        // 失效被移除成员的权限缓存
        userIds.forEach(permissionService::invalidateCache);

        systemAuditService.log("remove_group_members", "user_group", groupId,
                Map.of("groupName", group.getName(), "removedCount", userIds.size()));
        log.info("从用户组 {}({}) 移除了 {} 名成员", group.getName(), groupId, userIds.size());
    }

    /**
     * 为组分配角色（支持全局作用域、多项目批量分配）
     */
    @Transactional
    public void assignRole(Long groupId, GroupRoleDTO dto) {
        UserGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户组不存在");
        }

        // 校验角色存在
        SysRole role = roleMapper.selectById(dto.getRoleId());
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }

        // 规范化参数：兼容旧接口的 projectId 字段
        List<Long> projectIds = resolveProjectIds(dto);
        boolean isGlobalScope = Boolean.TRUE.equals(dto.getGlobalScope());

        if ("global".equals(role.getRoleType())) {
            // 全局角色类型：只能以全局方式分配（projectId=null）
            if (!projectIds.isEmpty() || isGlobalScope) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "全局角色不需要指定项目，直接分配即可");
            }
            insertGroupRoleIfNotExists(groupId, dto.getRoleId(), null);
        } else if ("project".equals(role.getRoleType())) {
            if (isGlobalScope) {
                // 项目角色 + 全局作用域：project_id=null 表示对所有项目生效
                insertGroupRoleIfNotExists(groupId, dto.getRoleId(), null);
            } else if (!projectIds.isEmpty()) {
                // 项目角色 + 指定项目列表：逐个校验并插入
                for (Long projectId : projectIds) {
                    Project project = projectMapper.selectById(projectId);
                    if (project == null) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在: ID=" + projectId);
                    }
                    insertGroupRoleIfNotExists(groupId, dto.getRoleId(), projectId);
                }
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "项目角色必须指定项目或选择全局作用域");
            }
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未知的角色类型: " + role.getRoleType());
        }

        // 失效所有组成员的权限缓存
        List<Long> memberUserIds = memberMapper.selectUserIdsByGroupId(groupId);
        memberUserIds.forEach(permissionService::invalidateCache);

        String scopeDesc = isGlobalScope ? "全局(所有项目)" :
                projectIds.isEmpty() ? "全局" : "项目:" + projectIds;
        systemAuditService.log("assign_group_role", "user_group", groupId,
                Map.of("groupName", group.getName(), "roleName", role.getName(), "scope", scopeDesc));

        log.info("为用户组 {}({}) 分配角色 {}({}), scope={}",
                group.getName(), groupId, role.getName(), dto.getRoleId(), scopeDesc);
    }

    /**
     * 解析项目 ID 列表（兼容 projectId 和 projectIds 两种传参方式）
     */
    private List<Long> resolveProjectIds(GroupRoleDTO dto) {
        if (dto.getProjectIds() != null && !dto.getProjectIds().isEmpty()) {
            return dto.getProjectIds();
        }
        if (dto.getProjectId() != null) {
            return List.of(dto.getProjectId());
        }
        return List.of();
    }

    /**
     * 插入组角色分配记录（幂等：已存在则跳过）
     */
    private void insertGroupRoleIfNotExists(Long groupId, Long roleId, Long projectId) {
        LambdaQueryWrapper<UserGroupRole> checkWrapper = new LambdaQueryWrapper<UserGroupRole>()
                .eq(UserGroupRole::getGroupId, groupId)
                .eq(UserGroupRole::getRoleId, roleId);
        if (projectId != null) {
            checkWrapper.eq(UserGroupRole::getProjectId, projectId);
        } else {
            checkWrapper.isNull(UserGroupRole::getProjectId);
        }
        Long existing = groupRoleMapper.selectCount(checkWrapper);
        if (existing > 0) {
            return; // 已存在，幂等返回
        }

        UserGroupRole groupRole = new UserGroupRole();
        groupRole.setGroupId(groupId);
        groupRole.setRoleId(roleId);
        groupRole.setProjectId(projectId);
        groupRole.setCreatedAt(LocalDateTime.now());
        groupRoleMapper.insert(groupRole);
    }

    /**
     * 移除组的角色分配
     */
    @Transactional
    public void removeRole(Long groupId, Long groupRoleId) {
        UserGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户组不存在");
        }

        UserGroupRole groupRole = groupRoleMapper.selectById(groupRoleId);
        if (groupRole == null || !groupRole.getGroupId().equals(groupId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "角色分配记录不存在");
        }

        groupRoleMapper.deleteById(groupRoleId);

        // 失效所有组成员的权限缓存
        List<Long> memberUserIds = memberMapper.selectUserIdsByGroupId(groupId);
        memberUserIds.forEach(permissionService::invalidateCache);

        systemAuditService.log("remove_group_role", "user_group", groupId,
                Map.of("groupName", group.getName(), "roleId", groupRole.getRoleId()));

        log.info("从用户组 {}({}) 移除角色分配 id={}", group.getName(), groupId, groupRoleId);
    }

    // ==================== Private Helper Methods ====================

    private UserGroupVO toVO(UserGroup group) {
        UserGroupVO vo = new UserGroupVO();
        vo.setId(String.valueOf(group.getId()));
        vo.setName(group.getName());
        vo.setDescription(group.getDescription());
        vo.setCreatedAt(group.getCreatedAt());
        vo.setUpdatedAt(group.getUpdatedAt());

        // 统计成员数
        Long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<UserGroupMember>().eq(UserGroupMember::getGroupId, group.getId())
        );
        vo.setMemberCount(memberCount.intValue());

        // 统计角色分配数
        Long roleCount = groupRoleMapper.selectCount(
                new LambdaQueryWrapper<UserGroupRole>().eq(UserGroupRole::getGroupId, group.getId())
        );
        vo.setRoleCount(roleCount.intValue());

        return vo;
    }

    private List<UserGroupDetailVO.MemberInfo> loadMembers(Long groupId) {
        List<UserGroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<UserGroupMember>()
                        .eq(UserGroupMember::getGroupId, groupId)
                        .orderByAsc(UserGroupMember::getCreatedAt)
        );
        if (members.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = members.stream().map(UserGroupMember::getUserId).toList();
        List<SysUser> users = userMapper.selectBatchIds(userIds);
        Map<Long, SysUser> userMap = users.stream().collect(Collectors.toMap(SysUser::getId, u -> u));

        return members.stream().map(m -> {
            UserGroupDetailVO.MemberInfo info = new UserGroupDetailVO.MemberInfo();
            info.setUserId(String.valueOf(m.getUserId()));
            info.setJoinedAt(m.getCreatedAt());
            SysUser user = userMap.get(m.getUserId());
            if (user != null) {
                info.setUsername(user.getUsername());
                info.setDisplayName(user.getDisplayName());
                info.setEmail(user.getEmail());
                info.setAvatarUrl(user.getAvatarUrl());
            }
            return info;
        }).toList();
    }

    private List<UserGroupDetailVO.RoleAssignment> loadRoleAssignments(Long groupId) {
        List<UserGroupRole> groupRoles = groupRoleMapper.selectList(
                new LambdaQueryWrapper<UserGroupRole>()
                        .eq(UserGroupRole::getGroupId, groupId)
                        .orderByAsc(UserGroupRole::getCreatedAt)
        );
        if (groupRoles.isEmpty()) {
            return List.of();
        }

        // 批量加载角色
        List<Long> roleIds = groupRoles.stream().map(UserGroupRole::getRoleId).distinct().toList();
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        Map<Long, SysRole> roleMap = roles.stream().collect(Collectors.toMap(SysRole::getId, r -> r));

        // 批量加载项目
        List<Long> projectIds = groupRoles.stream()
                .map(UserGroupRole::getProjectId)
                .filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, Project> projectMap = projectIds.isEmpty() ? Map.of() :
                projectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(Project::getId, p -> p));

        return groupRoles.stream().map(gr -> {
            UserGroupDetailVO.RoleAssignment assignment = new UserGroupDetailVO.RoleAssignment();
            assignment.setId(String.valueOf(gr.getId()));
            assignment.setRoleId(String.valueOf(gr.getRoleId()));
            assignment.setCreatedAt(gr.getCreatedAt());

            SysRole role = roleMap.get(gr.getRoleId());
            if (role != null) {
                assignment.setRoleName(role.getName());
                assignment.setRoleCode(role.getCode());
                assignment.setRoleType(role.getRoleType());
            }

            // 确定作用域
            if (gr.getProjectId() != null) {
                assignment.setProjectId(String.valueOf(gr.getProjectId()));
                assignment.setScope("project");
                Project project = projectMap.get(gr.getProjectId());
                if (project != null) {
                    assignment.setProjectName(project.getName());
                    assignment.setProjectKey(project.getKey());
                }
            } else if (role != null && "project".equals(role.getRoleType())) {
                // 项目角色但 project_id=null → 全局作用域（所有项目）
                assignment.setScope("all_projects");
            } else {
                // 全局角色类型
                assignment.setScope("global");
            }

            return assignment;
        }).toList();
    }
}
