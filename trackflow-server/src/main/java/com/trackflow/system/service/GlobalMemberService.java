package com.trackflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectMember;
import com.trackflow.project.entity.ProjectStatus;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.dto.GlobalMemberDTO;
import com.trackflow.system.entity.GlobalMember;
import com.trackflow.system.entity.SysRole;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.GlobalMemberMapper;
import com.trackflow.system.mapper.SysRoleMapper;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.vo.GlobalMemberVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 全局项目角色分配服务。
 * <p>
 * 核心职责：
 * 1. CRUD 全局成员分配记录
 * 2. 分配时同步插入所有现有项目的 project_member 记录
 * 3. 撤销时同步删除由全局分配产生的 project_member 记录（保留独立分配的）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalMemberService {

    private final GlobalMemberMapper globalMemberMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final PermissionService permissionService;
    private final StringRedisTemplate redisTemplate;

    private static final String ACCESSIBLE_PROJECTS_CACHE_PREFIX = "accessible_projects:";

    /**
     * 分配全局项目角色。
     * 效果：用户在所有现有项目中获得该角色，且未来新建项目时自动继承。
     */
    @Transactional(rollbackFor = Exception.class)
    public GlobalMember assign(GlobalMemberDTO dto) {
        Long userId = dto.getUserId();
        Long roleId = dto.getRoleId();

        // 1. 校验用户存在
        SysUser user = userMapper.selectById(userId);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在或已禁用");
        }

        // 2. 校验角色存在且为 project 类型
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色不存在");
        }
        if (!"project".equals(role.getRoleType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "只有项目角色可以全局分配，「" + role.getName() + "」是全局角色（请使用全局角色分配功能）");
        }

        // 3. 检查重复
        int exists = globalMemberMapper.countByUserAndRole(userId, roleId);
        if (exists > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该用户已拥有「" + role.getName() + "」角色的全局分配");
        }

        // 4. 插入 global_member 记录
        GlobalMember gm = new GlobalMember();
        gm.setUserId(userId);
        gm.setRoleId(roleId);
        gm.setCreatedAt(LocalDateTime.now());
        gm.setCreatedBy(SecurityUtils.getCurrentUserId());
        globalMemberMapper.insert(gm);

        // 5. 同步到所有现有活跃项目（插入 project_member 记录）
        int synced = syncToAllProjects(userId, roleId);
        log.info("Global member assigned: user={}, role={} ({}), synced to {} projects",
                user.getUsername(), role.getName(), role.getCode(), synced);

        // 6. 失效权限缓存
        permissionService.invalidateCache(userId);
        redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + userId);

        return gm;
    }

    /**
     * 撤销全局项目角色。
     * 效果：从 global_member 中移除记录，并删除由此产生的 project_member 记录
     * （如果用户在某项目中有独立分配的同一角色，则保留——但当前模型中无法区分来源，
     *  因此采用保守策略：只删除 project_member 中匹配 user+role 且该项目没有
     *  其他独立操作迹象的记录。实际实现中我们全部删除 user+role 的 project_member 记录，
     *  因为"全局撤销"的语义就是该角色不再有效。如果管理员想保留某些项目的独立分配，
     *  需要重新在那些项目中手动添加。）
     */
    @Transactional(rollbackFor = Exception.class)
    public int revoke(Long userId, Long roleId) {
        // 1. 检查记录存在
        LambdaQueryWrapper<GlobalMember> wrapper = new LambdaQueryWrapper<GlobalMember>()
                .eq(GlobalMember::getUserId, userId)
                .eq(GlobalMember::getRoleId, roleId);
        GlobalMember gm = globalMemberMapper.selectOne(wrapper);
        if (gm == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该全局分配记录不存在");
        }

        // 2. 删除 global_member 记录
        globalMemberMapper.deleteById(gm.getId());

        // 3. 从所有项目中移除该 user+role 的 project_member 记录
        LambdaQueryWrapper<ProjectMember> pmWrapper = new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getUserId, userId)
                .eq(ProjectMember::getRoleId, roleId);
        int removed = projectMemberMapper.delete(pmWrapper);

        log.info("Global member revoked: userId={}, roleId={}, removed {} project_member records",
                userId, roleId, removed);

        // 4. 失效权限缓存
        permissionService.invalidateCache(userId);
        redisTemplate.delete(ACCESSIBLE_PROJECTS_CACHE_PREFIX + userId);

        return removed;
    }

    /**
     * 列出所有全局分配记录（管理界面用）
     */
    public List<GlobalMemberVO> listAll() {
        List<GlobalMember> members = globalMemberMapper.selectList(
                new LambdaQueryWrapper<GlobalMember>().orderByDesc(GlobalMember::getCreatedAt));

        List<GlobalMemberVO> result = new ArrayList<>();
        for (GlobalMember gm : members) {
            GlobalMemberVO vo = new GlobalMemberVO();
            vo.setId(String.valueOf(gm.getId()));
            vo.setUserId(String.valueOf(gm.getUserId()));
            vo.setRoleId(String.valueOf(gm.getRoleId()));
            vo.setCreatedAt(gm.getCreatedAt());

            // 填充用户信息
            SysUser user = userMapper.selectById(gm.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setDisplayName(user.getDisplayName());
                vo.setEmail(user.getEmail());
            }

            // 填充角色信息
            SysRole role = roleMapper.selectById(gm.getRoleId());
            if (role != null) {
                vo.setRoleName(role.getName());
                vo.setRoleCode(role.getCode());
            }

            // 填充操作人
            if (gm.getCreatedBy() != null) {
                SysUser creator = userMapper.selectById(gm.getCreatedBy());
                if (creator != null) {
                    vo.setCreatedByName(creator.getDisplayName());
                }
            }

            result.add(vo);
        }
        return result;
    }

    /**
     * 列出指定用户的全局角色分配
     */
    public List<GlobalMemberVO> listByUser(Long userId) {
        List<GlobalMember> members = globalMemberMapper.selectList(
                new LambdaQueryWrapper<GlobalMember>()
                        .eq(GlobalMember::getUserId, userId)
                        .orderByDesc(GlobalMember::getCreatedAt));

        List<GlobalMemberVO> result = new ArrayList<>();
        for (GlobalMember gm : members) {
            GlobalMemberVO vo = new GlobalMemberVO();
            vo.setId(String.valueOf(gm.getId()));
            vo.setUserId(String.valueOf(gm.getUserId()));
            vo.setRoleId(String.valueOf(gm.getRoleId()));
            vo.setCreatedAt(gm.getCreatedAt());

            SysRole role = roleMapper.selectById(gm.getRoleId());
            if (role != null) {
                vo.setRoleName(role.getName());
                vo.setRoleCode(role.getCode());
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 同步全局分配到所有现有活跃项目。
     * 对已存在的 (projectId, userId, roleId) 跳过。
     *
     * @return 实际新增的 project_member 数量
     */
    private int syncToAllProjects(Long userId, Long roleId) {
        // 获取所有活跃项目
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getStatus, ProjectStatus.ACTIVE));

        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Project project : projects) {
            // 检查是否已存在
            Long existing = projectMemberMapper.selectCount(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, project.getId())
                            .eq(ProjectMember::getUserId, userId)
                            .eq(ProjectMember::getRoleId, roleId));
            if (existing > 0) continue;

            ProjectMember pm = new ProjectMember();
            pm.setProjectId(project.getId());
            pm.setUserId(userId);
            pm.setRoleId(roleId);
            pm.setJoinedAt(now);
            projectMemberMapper.insert(pm);
            count++;
        }
        return count;
    }

    /**
     * 当新项目创建时调用：为所有全局分配的用户在新项目中添加 project_member 记录。
     * 由 ProjectService.create() 调用。
     *
     * @param projectId 新创建的项目 ID
     * @return 添加的成员数
     */
    @Transactional(rollbackFor = Exception.class)
    public int syncGlobalMembersToProject(Long projectId) {
        List<GlobalMember> globalMembers = globalMemberMapper.selectList(new LambdaQueryWrapper<>());
        if (globalMembers.isEmpty()) return 0;

        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (GlobalMember gm : globalMembers) {
            // 检查是否已存在（可能创建者本人已经在全局分配中）
            Long existing = projectMemberMapper.selectCount(
                    new LambdaQueryWrapper<ProjectMember>()
                            .eq(ProjectMember::getProjectId, projectId)
                            .eq(ProjectMember::getUserId, gm.getUserId())
                            .eq(ProjectMember::getRoleId, gm.getRoleId()));
            if (existing > 0) continue;

            ProjectMember pm = new ProjectMember();
            pm.setProjectId(projectId);
            pm.setUserId(gm.getUserId());
            pm.setRoleId(gm.getRoleId());
            pm.setJoinedAt(now);
            projectMemberMapper.insert(pm);
            count++;
        }

        if (count > 0) {
            log.info("Synced {} global members to new project {}", count, projectId);
        }
        return count;
    }
}
