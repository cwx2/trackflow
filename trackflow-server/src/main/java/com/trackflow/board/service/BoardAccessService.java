package com.trackflow.board.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.board.entity.BoardGeneralConfig;
import com.trackflow.board.mapper.BoardGeneralConfigMapper;
import com.trackflow.common.constant.ProjectRoleCodes;
import com.trackflow.common.exception.BusinessException;
import static com.trackflow.common.exception.ErrorCode.ACCESS_DENIED;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.project.mapper.ProjectMemberMapper;
import com.trackflow.system.mapper.UserGroupRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * 看板访问控制服务。
 * <p>
 * 在基线权限（project:view）通过后，根据 board_general_config 中的
 * can_view_roles / can_edit_roles 动态判断当前用户是否有看板级别的访问权限。
 * <p>
 * 规则：
 * - system_admin 始终放行
 * - project_admin 始终放行（项目管理员不受看板角色限制）
 * - 其他角色：用户在项目中的角色 ∩ 允许角色列表 非空 → 放行
 * - 如果 board_general_config 不存在，使用默认值（所有人可看，admin+tech_lead 可编辑）
 * <p>
 * 性能优化：提供接受已查到的 {@link BoardGeneralConfig} 对象的重载方法，
 * 避免调用方（如 getGeneralConfig）重复查询同一张表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardAccessService {

    /**
     * 合并后的权限结果，用于一次性返回 canView + canEdit，减少重复查询。
     */
    public record BoardPermissions(boolean canView, boolean canEdit) {}


    /** 默认可查看角色：所有项目角色 */
    private static final List<String> DEFAULT_CAN_VIEW_ROLES = ProjectRoleCodes.ALL_PROJECT_ROLES;

    /** 默认可编辑角色：项目管理员 + 技术负责人 */
    private static final List<String> DEFAULT_CAN_EDIT_ROLES = ProjectRoleCodes.DEFAULT_EDIT_ROLES;

    private final BoardGeneralConfigMapper boardGeneralConfigMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserGroupRoleMapper userGroupRoleMapper;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    /**
     * 检查当前用户是否有看板查看权限。无权限时抛出 403 异常。
     */
    public void checkViewAccess(Long projectId) {
        if (!hasViewAccess(projectId)) {
            throw new BusinessException(ACCESS_DENIED, "您没有查看此看板的权限");
        }
    }

    /**
     * 检查当前用户是否有看板编辑权限。无权限时抛出 403 异常。
     */
    public void checkEditAccess(Long projectId) {
        if (!hasEditAccess(projectId)) {
            throw new BusinessException(ACCESS_DENIED, "您没有编辑此看板的权限");
        }
    }

    /**
     * 判断当前用户是否有看板查看权限（不抛异常，返回布尔值）。
     */
    public boolean hasViewAccess(Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // system_admin 始终放行
        if (permissionService.isSystemAdmin(userId)) return true;

        // 获取用户在项目中的角色代码
        List<String> userRoleCodes = getUserProjectRoleCodes(userId, projectId);
        if (userRoleCodes.isEmpty()) return false;

        // project_admin 始终放行
        if (userRoleCodes.contains(ProjectRoleCodes.PROJECT_ADMIN)) return true;

        // 获取看板配置的 canViewRoles
        List<String> canViewRoles = getCanViewRoles(projectId);

        // 用户角色 ∩ 允许角色 非空 → 放行
        return !Collections.disjoint(userRoleCodes, canViewRoles);
    }

    /**
     * 判断当前用户是否有看板编辑权限（不抛异常，返回布尔值）。
     */
    public boolean hasEditAccess(Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // system_admin 始终放行
        if (permissionService.isSystemAdmin(userId)) return true;

        // 获取用户在项目中的角色代码
        List<String> userRoleCodes = getUserProjectRoleCodes(userId, projectId);
        if (userRoleCodes.isEmpty()) return false;

        // project_admin 始终放行
        if (userRoleCodes.contains(ProjectRoleCodes.PROJECT_ADMIN)) return true;

        // 获取看板配置的 canEditRoles
        List<String> canEditRoles = getCanEditRoles(projectId);

        // 用户角色 ∩ 允许角色 非空 → 放行
        return !Collections.disjoint(userRoleCodes, canEditRoles);
    }

    /**
     * 一次性计算当前用户在指定项目看板的查看和编辑权限。
     * <p>
     * 调用方已经查到了 {@link BoardGeneralConfig}，传入此方法可避免重复查表，
     * 同时角色查询只执行一次，canView 和 canEdit 共享结果。
     *
     * @param projectId    项目 ID
     * @param existingConfig 调用方已查到的看板配置；为 null 时使用默认权限规则
     * @return 包含 canView + canEdit 的权限结果
     */
    public BoardPermissions computePermissions(Long projectId, BoardGeneralConfig existingConfig) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return new BoardPermissions(false, false);
        }

        // system_admin 始终放行
        if (permissionService.isSystemAdmin(userId)) {
            return new BoardPermissions(true, true);
        }

        // 获取用户在项目中的角色代码（只查一次，供 canView 和 canEdit 共享）
        List<String> userRoleCodes = getUserProjectRoleCodes(userId, projectId);
        if (userRoleCodes.isEmpty()) {
            return new BoardPermissions(false, false);
        }

        // project_admin 始终放行
        if (userRoleCodes.contains(ProjectRoleCodes.PROJECT_ADMIN)) {
            return new BoardPermissions(true, true);
        }

        // 从已有 config 中读取允许角色列表（避免重复查表）
        List<String> canViewRoles;
        List<String> canEditRoles;
        if (existingConfig == null) {
            canViewRoles = DEFAULT_CAN_VIEW_ROLES;
            canEditRoles = DEFAULT_CAN_EDIT_ROLES;
        } else {
            canViewRoles = parseRoles(existingConfig.getCanViewRoles(), DEFAULT_CAN_VIEW_ROLES);
            canEditRoles = parseRoles(existingConfig.getCanEditRoles(), DEFAULT_CAN_EDIT_ROLES);
        }

        boolean canView = !Collections.disjoint(userRoleCodes, canViewRoles);
        boolean canEdit = !Collections.disjoint(userRoleCodes, canEditRoles);
        return new BoardPermissions(canView, canEdit);
    }

    /**
     * 获取用户在指定项目中的角色代码列表（直接成员 + 用户组成员，合并去重）。
     * <p>
     * 路径 1：project_member → sys_role（直接成员角色）
     * 路径 2：user_group_member → user_group_role → sys_role（通过用户组获得的角色）
     */
    private List<String> getUserProjectRoleCodes(Long userId, Long projectId) {
        // 路径 1：直接成员角色
        List<String> directRoles = projectMemberMapper.selectRoleCodesByUserAndProject(userId, projectId);

        // 路径 2：通过用户组获得的角色
        List<String> groupRoles = userGroupRoleMapper.selectRoleCodesByUserAndProject(userId, projectId);

        // 合并去重
        Set<String> allRoles = new HashSet<>();
        if (directRoles != null) allRoles.addAll(directRoles);
        if (groupRoles != null) allRoles.addAll(groupRoles);

        return allRoles.isEmpty() ? Collections.emptyList() : new ArrayList<>(allRoles);
    }

    /**
     * 获取看板的可查看角色列表。无配置时返回默认值。
     */
    private List<String> getCanViewRoles(Long projectId) {
        BoardGeneralConfig config = getConfig(projectId);
        if (config == null) return DEFAULT_CAN_VIEW_ROLES;
        return parseRoles(config.getCanViewRoles(), DEFAULT_CAN_VIEW_ROLES);
    }

    /**
     * 获取看板的可编辑角色列表。无配置时返回默认值。
     */
    private List<String> getCanEditRoles(Long projectId) {
        BoardGeneralConfig config = getConfig(projectId);
        if (config == null) return DEFAULT_CAN_EDIT_ROLES;
        return parseRoles(config.getCanEditRoles(), DEFAULT_CAN_EDIT_ROLES);
    }

    private BoardGeneralConfig getConfig(Long projectId) {
        return boardGeneralConfigMapper.selectOne(
                new LambdaQueryWrapper<BoardGeneralConfig>()
                        .eq(BoardGeneralConfig::getProjectId, projectId)
        );
    }

    private List<String> parseRoles(String json, List<String> defaultValue) {
        if (json == null || json.isBlank()) return defaultValue;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse board roles JSON: {}", json, e);
            return defaultValue;
        }
    }
}
