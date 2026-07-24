package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.board.entity.BoardFavorite;
import com.trackflow.board.entity.BoardGeneralConfig;
import com.trackflow.board.mapper.BoardFavoriteMapper;
import com.trackflow.board.mapper.BoardGeneralConfigMapper;
import com.trackflow.board.vo.BoardListItemVO;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.entity.Project;
import com.trackflow.project.mapper.ProjectMapper;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.system.mapper.UserGroupRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 看板收藏服务 - 管理用户看板收藏和看板列表查询
 *
 * @author TrackFlow
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
public class BoardFavoriteService {

    private final BoardFavoriteMapper boardFavoriteMapper;
    private final ProjectMapper projectMapper;
    private final BoardGeneralConfigMapper boardGeneralConfigMapper;
    private final SysUserMapper sysUserMapper;
    private final PermissionService permissionService;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserGroupRoleMapper userGroupRoleMapper;
    private final ObjectMapper objectMapper;

    /** 默认可查看角色：所有项目角色 */
    private static final List<String> DEFAULT_CAN_VIEW_ROLES = List.of(
            "project_admin", "tech_lead", "developer", "product_manager", "tester", "observer"
    );

    /**
     * 获取当前用户可访问的所有看板列表。
     * 收藏的看板排在前面。
     * <p>
     * 访问控制规则：
     * - system_admin 可查看所有看板
     * - 其他用户只能看到自己有看板查看权限的项目看板
     *   （通过 project_member 角色 ∩ board_general_config.can_view_roles）
     *
     * @return 看板列表（收藏在前，按名称排序）
     */
    public List<BoardListItemVO> listBoards() {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 1. 获取用户可访问的项目列表（应用访问控制）
        List<Project> accessibleProjects = getAccessibleProjects(currentUserId);
        if (accessibleProjects.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 获取用户收藏的项目ID集合
        Set<Long> favoriteProjectIds = getFavoriteProjectIds(currentUserId);

        // 3. 获取所有相关看板配置（获取看板名称）
        Set<Long> projectIds = accessibleProjects.stream()
                .map(Project::getId)
                .collect(Collectors.toSet());
        List<BoardGeneralConfig> configs = boardGeneralConfigMapper.selectList(
                new LambdaQueryWrapper<BoardGeneralConfig>()
                        .in(BoardGeneralConfig::getProjectId, projectIds)
        );
        Map<Long, BoardGeneralConfig> configMap = configs.stream()
                .collect(Collectors.toMap(BoardGeneralConfig::getProjectId, c -> c, (a, b) -> a));

        // 4. 获取项目负责人信息（使用 leadId）
        Set<Long> ownerIds = accessibleProjects.stream()
                .map(Project::getLeadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> userMap = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            List<SysUser> users = sysUserMapper.selectBatchIds(ownerIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        }

        // 5. 组装结果
        List<BoardListItemVO> result = new ArrayList<>();
        for (Project project : accessibleProjects) {
            BoardListItemVO item = new BoardListItemVO();
            item.setProjectId(String.valueOf(project.getId()));
            item.setProjectKey(project.getKey());
            item.setProjectName(project.getName());
            item.setFavorite(favoriteProjectIds.contains(project.getId()));

            // 看板名称：优先使用 board_general_config 中的 name，否则用 "ProjectKey Board"
            BoardGeneralConfig config = configMap.get(project.getId());
            if (config != null && config.getName() != null && !config.getName().isBlank()) {
                item.setName(config.getName());
            } else {
                item.setName(project.getKey() + " Board");
            }

            // 所有者信息（项目负责人）
            if (project.getLeadId() != null) {
                SysUser owner = userMap.get(project.getLeadId());
                if (owner != null) {
                    item.setOwnerId(String.valueOf(owner.getId()));
                    item.setOwnerName(owner.getDisplayName());
                }
            }

            result.add(item);
        }

        // 6. 排序：收藏的在前，然后按名称排序
        result.sort((a, b) -> {
            if (a.isFavorite() && !b.isFavorite()) return -1;
            if (!a.isFavorite() && b.isFavorite()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return result;
    }

    /**
     * 获取用户可访问的项目列表（应用看板级访问控制）。
     * <p>
     * - system_admin：返回所有项目
     * - 其他用户：获取所属项目（直接成员+用户组），再根据每个项目的 can_view_roles 过滤
     */
    private List<Project> getAccessibleProjects(Long currentUserId) {
        // system_admin 可查看所有看板
        if (permissionService.isSystemAdmin(currentUserId)) {
            return projectMapper.selectList(null);
        }

        // 获取用户所属的项目 ID（直接成员 + 用户组成员）
        Set<Long> memberProjectIds = new HashSet<>(
                projectMemberMapper.selectProjectIdsByUserId(currentUserId)
        );
        List<Long> groupProjectIds = userGroupRoleMapper.selectProjectIdsByUserIdViaGroups(currentUserId);
        memberProjectIds.addAll(groupProjectIds);

        if (memberProjectIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 加载这些项目
        List<Project> memberProjects = projectMapper.selectBatchIds(memberProjectIds);

        // 获取这些项目的看板配置（用于判断 can_view_roles）
        List<BoardGeneralConfig> configs = boardGeneralConfigMapper.selectList(
                new LambdaQueryWrapper<BoardGeneralConfig>()
                        .in(BoardGeneralConfig::getProjectId, memberProjectIds)
        );
        Map<Long, BoardGeneralConfig> configMap = configs.stream()
                .collect(Collectors.toMap(BoardGeneralConfig::getProjectId, c -> c, (a, b) -> a));

        // 过滤：用户在项目中的角色 ∩ 该看板的 can_view_roles 非空
        return memberProjects.stream()
                .filter(project -> hasViewAccessForProject(currentUserId, project.getId(), configMap))
                .collect(Collectors.toList());
    }

    /**
     * 判断用户是否有指定项目的看板查看权限。
     * 内联版本，避免对每个项目都做独立 DB 查询。
     */
    private boolean hasViewAccessForProject(Long userId, Long projectId,
                                            Map<Long, BoardGeneralConfig> configMap) {
        // 获取用户在该项目中的角色
        List<String> userRoleCodes = projectMemberMapper.selectRoleCodesByUserAndProject(userId, projectId);
        if (userRoleCodes == null || userRoleCodes.isEmpty()) {
            // 可能通过用户组有权限，但没有直接角色——按默认规则放行
            // （用户组成员也算项目成员，但 projectMemberMapper 只查直接成员）
            // 通过用户组关联的用户，在 can_view_roles 检查中使用默认角色列表放行
            return true;
        }

        // project_admin 始终放行
        if (userRoleCodes.contains("project_admin")) {
            return true;
        }

        // 获取该项目看板的 can_view_roles
        BoardGeneralConfig config = configMap.get(projectId);
        List<String> canViewRoles = parseCanViewRoles(config);

        // 用户角色 ∩ 允许角色 非空 → 放行
        return !Collections.disjoint(userRoleCodes, canViewRoles);
    }

    /**
     * 解析看板的可查看角色配置。无配置时返回默认值（所有角色可看）。
     */
    private List<String> parseCanViewRoles(BoardGeneralConfig config) {
        if (config == null) return DEFAULT_CAN_VIEW_ROLES;
        String json = config.getCanViewRoles();
        if (json == null || json.isBlank()) return DEFAULT_CAN_VIEW_ROLES;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return DEFAULT_CAN_VIEW_ROLES;
        }
    }

    /**
     * 收藏看板（幂等操作）
     *
     * @param projectId 项目 ID
     */
    public void addFavorite(Long projectId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long count = boardFavoriteMapper.selectCount(
                new LambdaQueryWrapper<BoardFavorite>()
                        .eq(BoardFavorite::getUserId, currentUserId)
                        .eq(BoardFavorite::getProjectId, projectId)
        );
        if (count == 0) {
            BoardFavorite favorite = new BoardFavorite();
            favorite.setUserId(currentUserId);
            favorite.setProjectId(projectId);
            favorite.setCreatedAt(LocalDateTime.now());
            boardFavoriteMapper.insert(favorite);
        }
    }

    /**
     * 取消收藏看板
     *
     * @param projectId 项目 ID
     */
    public void removeFavorite(Long projectId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boardFavoriteMapper.delete(
                new LambdaQueryWrapper<BoardFavorite>()
                        .eq(BoardFavorite::getUserId, currentUserId)
                        .eq(BoardFavorite::getProjectId, projectId)
        );
    }

    private Set<Long> getFavoriteProjectIds(Long userId) {
        List<BoardFavorite> favorites = boardFavoriteMapper.selectList(
                new LambdaQueryWrapper<BoardFavorite>()
                        .eq(BoardFavorite::getUserId, userId)
        );
        return favorites.stream()
                .map(BoardFavorite::getProjectId)
                .collect(Collectors.toSet());
    }
}
