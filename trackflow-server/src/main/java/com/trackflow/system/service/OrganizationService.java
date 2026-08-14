package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.system.dto.AddProjectsToOrgDTO;
import com.trackflow.system.dto.CreateOrgDTO;
import com.trackflow.system.dto.GrantOrgAccessDTO;
import com.trackflow.system.dto.UpdateOrgDTO;
import com.trackflow.system.entity.OrgAccess;
import com.trackflow.system.entity.Organization;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.OrgAccessMapper;
import com.trackflow.system.mapper.OrganizationMapper;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.vo.OrgStatsVO;
import com.trackflow.system.vo.OrgAccessVO;
import com.trackflow.system.vo.OrgProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组织管理服务 - 处理组织 CRUD、项目归属管理、访问控制
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationMapper organizationMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final ProjectMapper projectMapper;
    private final OrgAccessMapper orgAccessMapper;

    /**
     * 获取组织统计数据
     */
    public OrgStatsVO getStats() {
        long total = organizationMapper.selectCount(null);
        long totalProjects = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().isNotNull(Project::getOrgId));
        long orgsWithProjects = total > 0
                ? organizationMapper.selectCount(new LambdaQueryWrapper<Organization>()
                        .inSql(Organization::getId, "SELECT DISTINCT org_id FROM project WHERE org_id IS NOT NULL"))
                : 0;
        return OrgStatsVO.builder()
                .total(total)
                .totalProjects(totalProjects)
                .orgsWithProjects(orgsWithProjects)
                .build();
    }

    /**
     * 创建组织
     */
    @Transactional(rollbackFor = Exception.class)
    public Organization create(CreateOrgDTO dto) {
        // 检查 code 唯一性
        Long count = organizationMapper.selectCount(
                new LambdaQueryWrapper<Organization>().eq(Organization::getCode, dto.getCode())
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.ORG_CODE_DUPLICATE);
        }

        Organization org = new Organization();
        org.setName(dto.getName());
        org.setCode(dto.getCode());
        org.setDescription(dto.getDescription());
        organizationMapper.insert(org);

        // 如果有初始项目列表，设置项目归属
        if (dto.getProjectIds() != null && !dto.getProjectIds().isEmpty()) {
            assignProjectsToOrg(org.getId(), dto.getProjectIds());
        }

        return org;
    }

    /**
     * 分页查询组织列表
     */
    public Page<Organization> list(Page<Organization> page, String keyword) {
        LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Organization::getName, keyword)
                    .or()
                    .like(Organization::getCode, keyword);
        }
        wrapper.orderByAsc(Organization::getCode);
        return organizationMapper.selectPage(page, wrapper);
    }

    /**
     * 获取组织详情
     */
    public Organization getById(Long id) {
        Organization org = organizationMapper.selectById(id);
        if (org == null) {
            throw BusinessException.notFound("Organization not found");
        }
        return org;
    }

    /**
     * 获取组织下项目数量
     */
    public int getProjectCount(Long orgId) {
        return Math.toIntExact(projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getOrgId, orgId)
        ));
    }

    /**
     * 批量获取各组织的项目数量
     */
    public Map<Long, Integer> getProjectCountMap(List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Map.of();
        }
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().in(Project::getOrgId, orgIds).select(Project::getOrgId)
        );
        return projects.stream()
                .collect(Collectors.groupingBy(Project::getOrgId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    /**
     * 更新组织
     */
    @Transactional(rollbackFor = Exception.class)
    public Organization update(Long id, UpdateOrgDTO dto) {
        Organization org = getById(id);
        if (dto.getName() != null) {
            String trimmedName = dto.getName().trim();
            if (trimmedName.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "组织名称不能为空");
            }
            org.setName(trimmedName);
        }
        if (dto.getDescription() != null) {
            org.setDescription(dto.getDescription().trim());
        }
        organizationMapper.updateById(org);
        return org;
    }

    /**
     * 删除组织（有引用时拒绝）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Organization org = getById(id);

        // 检查是否有用户引用
        Long userCount = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getOrgId, id)
        );
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.ORG_HAS_REFERENCES);
        }

        // 检查是否有项目引用
        Long projectCount = projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getOrgId, id)
        );
        if (projectCount > 0) {
            throw new BusinessException(ErrorCode.ORG_HAS_REFERENCES, "该组织下还有项目关联，无法删除");
        }

        // 删除组织级访问控制记录
        orgAccessMapper.delete(
                new LambdaQueryWrapper<OrgAccess>().eq(OrgAccess::getOrgId, id)
        );

        organizationMapper.deleteById(id);
    }

    // ===== 项目归属管理 =====

    /**
     * 获取组织下的项目列表
     */
    @Transactional(readOnly = true)
    public List<OrgProjectVO> getOrgProjects(Long orgId) {
        getById(orgId); // 确认组织存在
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getOrgId, orgId)
                        .orderByAsc(Project::getKey)
        );
        return projects.stream().map(this::toOrgProjectVO).collect(Collectors.toList());
    }

    /**
     * 添加项目到组织
     */
    @Transactional(rollbackFor = Exception.class)
    public void addProjectsToOrg(Long orgId, AddProjectsToOrgDTO dto) {
        getById(orgId); // 确认组织存在
        assignProjectsToOrg(orgId, dto.getProjectIds());
    }

    /**
     * 从组织中移除项目（解除关联，项目变为独立）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeProjectFromOrg(Long orgId, Long projectId) {
        getById(orgId); // 确认组织存在
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw BusinessException.notFound("项目不存在");
        }
        if (!orgId.equals(project.getOrgId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该项目不属于此组织");
        }
        // 使用 LambdaUpdateWrapper 显式将 org_id 设为 null（updateById 默认跳过 null 字段）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Project> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        updateWrapper.eq(Project::getId, projectId)
                .set(Project::getOrgId, null);
        projectMapper.update(null, updateWrapper);
    }

    /**
     * 获取未归属任何组织的项目列表（用于添加项目选择器）
     */
    @Transactional(readOnly = true)
    public List<OrgProjectVO> getUnassignedProjects() {
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .isNull(Project::getOrgId)
                        .orderByAsc(Project::getKey)
        );
        return projects.stream().map(this::toOrgProjectVO).collect(Collectors.toList());
    }

    // ===== 访问控制 =====

    /**
     * 获取组织级访问授权列表
     */
    @Transactional(readOnly = true)
    public List<OrgAccessVO> getOrgAccessList(Long orgId) {
        getById(orgId); // 确认组织存在
        List<OrgAccess> accessList = orgAccessMapper.selectList(
                new LambdaQueryWrapper<OrgAccess>().eq(OrgAccess::getOrgId, orgId)
        );
        if (accessList.isEmpty()) {
            return List.of();
        }

        // 批量查用户和角色信息
        Set<Long> userIds = accessList.stream().map(OrgAccess::getUserId).collect(Collectors.toSet());
        Set<Long> roleIds = accessList.stream().map(OrgAccess::getRoleId).collect(Collectors.toSet());

        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(userIds)
                .stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, SysRole> roleMap = sysRoleMapper.selectBatchIds(roleIds)
                .stream().collect(Collectors.toMap(SysRole::getId, Function.identity()));

        return accessList.stream().map(access -> {
            OrgAccessVO vo = new OrgAccessVO();
            vo.setId(String.valueOf(access.getId()));
            vo.setOrgId(String.valueOf(access.getOrgId()));
            vo.setUserId(String.valueOf(access.getUserId()));
            vo.setRoleId(String.valueOf(access.getRoleId()));
            vo.setCreatedAt(access.getCreatedAt());

            SysUser user = userMap.get(access.getUserId());
            if (user != null) {
                vo.setUserName(user.getUsername());
                vo.setUserDisplayName(user.getDisplayName());
            }
            SysRole role = roleMap.get(access.getRoleId());
            if (role != null) {
                vo.setRoleName(role.getName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 授予组织级访问权限
     */
    @Transactional(rollbackFor = Exception.class)
    public OrgAccessVO grantOrgAccess(Long orgId, GrantOrgAccessDTO dto) {
        getById(orgId); // 确认组织存在

        // 确认用户存在
        SysUser user = sysUserMapper.selectById(dto.getUserId());
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        // 确认角色存在
        SysRole role = sysRoleMapper.selectById(dto.getRoleId());
        if (role == null) {
            throw BusinessException.notFound("角色不存在");
        }

        // 检查是否已有相同的授权
        Long existing = orgAccessMapper.selectCount(
                new LambdaQueryWrapper<OrgAccess>()
                        .eq(OrgAccess::getOrgId, orgId)
                        .eq(OrgAccess::getUserId, dto.getUserId())
                        .eq(OrgAccess::getRoleId, dto.getRoleId())
        );
        if (existing > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该用户已拥有此角色的组织级授权");
        }

        OrgAccess access = new OrgAccess();
        access.setOrgId(orgId);
        access.setUserId(dto.getUserId());
        access.setRoleId(dto.getRoleId());
        access.setCreatedAt(LocalDateTime.now());
        access.setCreatedBy(SecurityUtils.getCurrentUserId());
        orgAccessMapper.insert(access);

        // 构造返回 VO
        OrgAccessVO vo = new OrgAccessVO();
        vo.setId(String.valueOf(access.getId()));
        vo.setOrgId(String.valueOf(orgId));
        vo.setUserId(String.valueOf(dto.getUserId()));
        vo.setUserName(user.getUsername());
        vo.setUserDisplayName(user.getDisplayName());
        vo.setRoleId(String.valueOf(dto.getRoleId()));
        vo.setRoleName(role.getName());
        vo.setCreatedAt(access.getCreatedAt());
        return vo;
    }

    /**
     * 撤销组织级访问授权
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeOrgAccess(Long orgId, Long accessId) {
        OrgAccess access = orgAccessMapper.selectById(accessId);
        if (access == null || !access.getOrgId().equals(orgId)) {
            throw BusinessException.notFound("授权记录不存在");
        }
        orgAccessMapper.deleteById(accessId);
    }

    // ===== Private =====

    private void assignProjectsToOrg(Long orgId, List<Long> projectIds) {
        for (Long projectId : projectIds) {
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                throw BusinessException.notFound("项目", projectId);
            }
            if (project.getOrgId() != null && !project.getOrgId().equals(orgId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "项目 " + project.getKey() + " 已归属于其他组织，需先从原组织移除");
            }
            if (!orgId.equals(project.getOrgId())) {
                project.setOrgId(orgId);
                projectMapper.updateById(project);
            }
        }
    }

    private OrgProjectVO toOrgProjectVO(Project project) {
        OrgProjectVO vo = new OrgProjectVO();
        vo.setId(String.valueOf(project.getId()));
        vo.setName(project.getName());
        vo.setKey(project.getKey());
        vo.setStatus(project.getStatus() != null ? project.getStatus().name() : null);
        // issueCount will be set separately if needed, default to null
        return vo;
    }
}
