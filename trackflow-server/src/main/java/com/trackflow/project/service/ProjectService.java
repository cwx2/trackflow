package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.converter.ProjectConverter;
import com.trackflow.project.dto.AddMemberDTO;
import com.trackflow.project.dto.CreateProjectDTO;
import com.trackflow.project.dto.UpdateProjectDTO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
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
        if (dto.getName() != null) project.setName(dto.getName());
        if (dto.getDescription() != null) project.setDescription(dto.getDescription());
        if (dto.getLeadId() != null) project.setLeadId(dto.getLeadId());
        projectMapper.updateById(project);
        return project;
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
        // 检查是否已经是成员
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, dto.getUserId())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "User is already a member of this project");
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
    }

    /**
     * 更新成员角色
     */
    @Transactional
    public void updateMemberRole(Long projectId, Long userId, Long roleId) {
        ProjectMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (member == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Member not found");
        }
        member.setRoleId(roleId);
        memberMapper.updateById(member);
        permissionService.invalidateCache(userId);
    }

    /**
     * 移除项目成员
     */
    @Transactional
    public void removeMember(Long projectId, Long userId) {
        // 保护最后一个项目管理员
        List<ProjectMember> admins = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getRoleId, PROJECT_ADMIN_ROLE_ID)
        );
        if (admins.size() == 1 && admins.get(0).getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot remove the last project admin");
        }

        memberMapper.delete(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        permissionService.invalidateCache(userId);
        // 失效项目列表缓存
        redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + userId);
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
}
