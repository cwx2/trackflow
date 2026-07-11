package com.trackflow.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.dto.AddMemberDTO;
import com.trackflow.project.dto.CreateProjectDTO;
import com.trackflow.project.dto.UpdateProjectDTO;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final SysUserMapper userMapper;
    private final PermissionService permissionService;

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
    }

    /**
     * 递增 Issue 序号并返回新序号
     */
    @Transactional
    public int nextIssueSequence(Long projectId) {
        Project project = getById(projectId);
        int next = project.getIssueSequence() + 1;
        project.setIssueSequence(next);
        projectMapper.updateById(project);
        return next;
    }
}
