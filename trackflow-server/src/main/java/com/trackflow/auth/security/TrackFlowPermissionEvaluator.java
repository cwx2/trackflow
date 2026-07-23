package com.trackflow.auth.security;

import com.trackflow.auth.service.PermissionService;
import com.trackflow.board.service.BoardAccessService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.workflow.WorkflowScope;
import com.trackflow.workflow.entity.TransitionAction;
import com.trackflow.workflow.mapper.TransitionActionMapper;
import com.trackflow.sprint.entity.Sprint;
import com.trackflow.sprint.mapper.SprintMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * 自定义权限评估器：集成到 Spring Security @PreAuthorize 中
 * <p>
 * 使用方式:
 * &#064;PreAuthorize("@perm.check(#projectId,  'issue:create')")
 * &#064;PreAuthorize("@perm.checkGlobal('system:manage_users')")
 * <p>
 * API Key scope 过滤逻辑：
 * 当请求通过 API Key 认证且 scope 非空时，实际权限 = 用户角色权限 ∩ apiKeyScope。
 * scope 为空（[]）表示不限制，保持向后兼容。
 */
@Component("perm")
@RequiredArgsConstructor
public class TrackFlowPermissionEvaluator implements PermissionEvaluator {

    private final PermissionService permissionService;
    private final IssueMapper issueMapper;
    private final com.trackflow.project.mapper.ProjectMapper projectMapper;
    private final TransitionActionMapper transitionActionMapper;
    private final SprintMapper sprintMapper;
    private final BoardAccessService boardAccessService;

    /**
     * 检查项目级权限（通过项目标识符：Key 或 ID）
     * <p>
     * 用于 @PreAuthorize("@perm.checkProject(#identifier, 'project:view')")
     * identifier 可以是项目 Key（如 "TF1"）或数字 ID 字符串
     * <p>
     * 如果项目不存在，抛出 BusinessException(RESOURCE_NOT_FOUND) → 返回 404，
     * 而非返回 false 导致 AccessDeniedException → 403（避免泄露项目存在性信息）。
     */
    public boolean checkProject(String identifier, String permission) {
        if (identifier == null || identifier.isBlank()) return false;
        Long projectId = resolveProjectIdForPerm(identifier);
        return check(projectId, permission);
    }

    /**
     * 解析项目标识符为数据库 ID（用于权限检查）。
     * <p>
     * 与 ProjectService.resolveProjectId 行为一致：验证存在性，不存在则抛 404。
     * 这避免了权限检查返回 403 而实际应为 404 的问题。
     *
     * @throws BusinessException RESOURCE_NOT_FOUND 当项目不存在时
     */
    private Long resolveProjectIdForPerm(String identifier) {
        // 尝试按数字 ID 解析
        try {
            Long id = Long.parseLong(identifier);
            // 必须验证存在性，防止对不存在的 ID 返回 403（应为 404）
            Long count = projectMapper.selectCount(
                    new LambdaQueryWrapper<com.trackflow.project.entity.Project>()
                            .eq(com.trackflow.project.entity.Project::getId, id)
            );
            if (count == null || count == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
            }
            return id;
        } catch (NumberFormatException e) {
            // 非数字，按 key 查询
        }
        com.trackflow.project.entity.Project project = projectMapper.selectOne(
                new LambdaQueryWrapper<com.trackflow.project.entity.Project>()
                        .select(com.trackflow.project.entity.Project::getId)
                        .apply("LOWER(\"key\") = LOWER({0})", identifier)
        );
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在: " + identifier);
        }
        return project.getId();
    }

    /**
     * 检查项目级权限
     */
    public boolean check(Long projectId, String permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // Scope 过滤已下沉到 PermissionService 内部，此处无需额外检查
        return permissionService.hasPermission(userId, projectId, permission);
    }

    /**
     * 检查 Issue 资源级权限（项目级 + reporter/assignee 额外权限）
     * 用于 @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
     *
     * @param issueId    Issue 的数据库 ID
     * @param permission 要检查的权限码
     * @return true 如果用户有权限（项目级或资源级）
     * @throws BusinessException RESOURCE_NOT_FOUND 当 Issue 不存在时（返回 404）
     */
    public boolean checkIssue(Long issueId, String permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // Scope 过滤已下沉到 PermissionService 内部

        Issue issue = issueMapper.selectOne(
                new LambdaQueryWrapper<Issue>()
                        .select(Issue::getProjectId, Issue::getReporterId, Issue::getAssigneeId)
                        .eq(Issue::getId, issueId)
                        .isNull(Issue::getDeletedAt)
        );
        if (issue == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }

        return permissionService.hasIssuePermission(userId, issue, permission);
    }

    /**
     * 检查转换动作的工作流管理权限。
     * <p>
     * 全局动作 → 要求 system:admin；项目级动作 → 要求 project:manage_workflow。
     * 用于 @PreAuthorize("@perm.checkWorkflowAction(#id)")
     *
     * @param actionId 转换动作 ID
     * @return true 如果用户有权限管理此转换动作
     * @throws BusinessException RESOURCE_NOT_FOUND 当动作不存在时
     */
    public boolean checkWorkflowAction(Long actionId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        TransitionAction action = transitionActionMapper.selectById(actionId);
        if (action == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "转换动作不存在");
        }

        // Scope 过滤已下沉到 PermissionService 内部
        Long projectId = WorkflowScope.toApi(action.getProjectId());
        if (WorkflowScope.isGlobal(projectId)) {
            return permissionService.hasGlobalPermission(userId, "system:admin");
        } else {
            return permissionService.hasPermission(userId, projectId, "project:manage_workflow");
        }
    }

    /**
     * 检查已删除 Issue 的项目级权限（用于恢复/永久删除操作）。
     * <p>
     * 与 checkIssue 不同，此方法查询含 soft-delete 标记的 Issue。
     * 用于 @PreAuthorize("@perm.checkDeletedIssue(#id, 'issue:delete')")
     *
     * @param issueId    Issue ID
     * @param permission 要检查的权限码
     * @return true 如果用户有权限
     * @throws BusinessException RESOURCE_NOT_FOUND 当 Issue 不存在时
     */
    public boolean checkDeletedIssue(Long issueId, String permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // Scope 过滤已下沉到 PermissionService 内部

        com.trackflow.issue.mapper.result.DeletedIssueRow row = issueMapper.selectByIdIgnoreDeleted(issueId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Issue not found");
        }
        Long projectId = row.getProjectId();
        return permissionService.hasPermission(userId, projectId, permission);
    }

    /**
     * 检查 Sprint 资源的项目级权限。
     * <p>
     * 用于 @PreAuthorize("@perm.checkSprint(#id, 'sprint:edit')")
     *
     * @param sprintId   Sprint ID
     * @param permission 要检查的权限码
     * @return true 如果用户有权限
     * @throws BusinessException RESOURCE_NOT_FOUND 当 Sprint 不存在时
     */
    public boolean checkSprint(Long sprintId, String permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // Scope 过滤已下沉到 PermissionService 内部

        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Sprint not found");
        }
        return permissionService.hasPermission(userId, sprint.getProjectId(), permission);
    }

    /**
     * 检查看板查看权限。
     * <p>
     * 基于 board_general_config 中的 can_view_roles 动态判断，
     * 而非静态 permission code，因为看板的访问控制粒度比项目级更细。
     * <p>
     * 用于 @PreAuthorize("@perm.checkBoardView(#projectId)")
     *
     * @param projectId 项目 ID
     * @return true 如果当前用户有看板查看权限
     */
    public boolean checkBoardView(Long projectId) {
        if (projectId == null) return false;
        return boardAccessService.hasViewAccess(projectId);
    }

    /**
     * 检查看板编辑权限。
     * <p>
     * 基于 board_general_config 中的 can_edit_roles 动态判断。
     * 用于 @PreAuthorize("@perm.checkBoardEdit(#projectId)")
     *
     * @param projectId 项目 ID
     * @return true 如果当前用户有看板编辑权限
     */
    public boolean checkBoardEdit(Long projectId) {
        if (projectId == null) return false;
        return boardAccessService.hasEditAccess(projectId);
    }

    /**
     * 检查全局权限
     */
    public boolean checkGlobal(String permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // Scope 过滤已下沉到 PermissionService 内部
        return permissionService.hasGlobalPermission(userId, permission);
    }

    // PermissionEvaluator 接口方法（供 hasPermission SpEL 使用）
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        String perm = permission.toString();
        // Scope 过滤已下沉到 PermissionService 内部

        Long projectId = null;
        if (targetDomainObject instanceof Long) {
            projectId = (Long) targetDomainObject;
        }
        return permissionService.hasPermission(userId, projectId, perm);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        String perm = permission.toString();
        // Scope 过滤已下沉到 PermissionService 内部

        Long projectId = targetId instanceof Long ? (Long) targetId : null;
        return permissionService.hasPermission(userId, projectId, perm);
    }

    /**
     * 检查用户是否有报表查看权限（用于报表列表/执行/克隆/收藏等读操作）。
     * <p>
     * 规则：系统管理员 或 在任意项目中拥有 report:view 权限
     * 用于 @PreAuthorize("@perm.canViewReports()")
     */
    public boolean canViewReports() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // Scope 过滤已下沉到 PermissionService 内部
        return permissionService.isSystemAdmin(userId)
                || permissionService.hasPermissionInAnyProject(userId, "report:view");
    }

    /**
     * 检查用户是否有报表创建权限。
     * <p>
     * 规则：系统管理员 或 在任意项目中拥有 report:create 权限
     * 用于 @PreAuthorize("@perm.canCreateReports()")
     */
    public boolean canCreateReports() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // Scope 过滤已下沉到 PermissionService 内部
        return permissionService.isSystemAdmin(userId)
                || permissionService.hasPermissionInAnyProject(userId, "report:create");
    }

    /**
     * 检查用户对指定项目是否有报表查看权限。
     * <p>
     * 用于 @PreAuthorize("@perm.check(#projectId, 'report:view')")
     * 当 projectId 为 null 时回退到 canViewReports() 逻辑。
     */
    public boolean checkReportView(Long projectId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return false;

        // Scope 过滤已下沉到 PermissionService 内部

        if (permissionService.isSystemAdmin(userId)) return true;

        if (projectId != null) {
            return permissionService.hasPermission(userId, projectId, "report:view");
        }
        return permissionService.hasPermissionInAnyProject(userId, "report:view");
    }

}
