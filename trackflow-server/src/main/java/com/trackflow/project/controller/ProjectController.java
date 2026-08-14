package com.trackflow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.dto.CreateTagDTO;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.service.IssueTagService;
import com.trackflow.issue.vo.IssueTagVO;
import com.trackflow.auth.security.TrackFlowPermissionEvaluator;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.report.service.CustomDashboardService;
import com.trackflow.project.converter.ProjectConverter;
import com.trackflow.project.dto.*;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectActivity;
import com.trackflow.project.service.ProjectActivityService;
import com.trackflow.project.service.ProjectCopyService;
import com.trackflow.project.service.ProjectModuleService;
import com.trackflow.project.service.ProjectService;
import com.trackflow.project.service.ProjectVOAssembler;
import com.trackflow.project.vo.AssignedIssueCountVO;
import com.trackflow.project.vo.FavoriteToggleVO;
import com.trackflow.project.vo.MemberOperationResultVO;
import com.trackflow.project.vo.ProjectActivityVO;
import com.trackflow.project.vo.ProjectCopySummaryVO;
import com.trackflow.project.vo.ProjectDeletePreCheckVO;
import com.trackflow.project.vo.ProjectDetailVO;
import com.trackflow.project.vo.ProjectMemberVO;
import com.trackflow.project.vo.ProjectMembersViewVO;
import com.trackflow.project.vo.ProjectModulesVO;
import com.trackflow.project.vo.ProjectStatisticsVO;
import com.trackflow.project.vo.ProjectTimeTrackingSettingsVO;
import com.trackflow.project.vo.ProjectTrashSettingsVO;
import com.trackflow.project.vo.ProjectVO;
import com.trackflow.project.vo.TimeTrackingDisableImpactVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectCopyService projectCopyService;
    private final ProjectVOAssembler projectVOAssembler;
    private final ProjectConverter projectConverter;
    private final IssueTagService tagService;
    private final IssueConverter issueConverter;
    private final ProjectActivityService projectActivityService;
    private final TrackFlowPermissionEvaluator permissionEvaluator;
    private final ProjectModuleService projectModuleService;
    private final PermissionService permissionService;
    private final CustomDashboardService dashboardService;

    @PostMapping
    @PreAuthorize("@perm.checkGlobal('project:create')")
    public R<ProjectVO> create(@Valid @RequestBody CreateProjectDTO dto) {
        Project project = projectService.create(dto);
        return R.ok(projectConverter.toVO(project));
    }

    @PostMapping("/copy")
    @PreAuthorize("@perm.checkGlobal('project:create')")
    public R<ProjectVO> copyProject(@Valid @RequestBody CopyProjectDTO dto) {
        // 额外验证：需要源项目的 view 权限
        if (!permissionEvaluator.check(dto.getSourceProjectId(), "project:view")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问源项目");
        }
        Project project = projectCopyService.copyProject(dto);
        return R.ok(projectConverter.toVO(project));
    }

    @GetMapping("/{id}/copy-summary")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<ProjectCopySummaryVO> getCopySummary(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectCopyService.getSourceProjectSummary(projectId));
    }

    @GetMapping
    public R<PageResult<ProjectVO>> list(ProjectQuery query) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<Project> result = projectService.list(query.toPage(), query.getKeyword(), query.getStatus(), userId, query.getRequiredPermission());
        List<ProjectVO> voList = projectConverter.toVOList(result.getRecords());
        projectService.populateMemberSummary(voList);
        projectService.populateFavoriteStatus(voList, userId);
        PageResult<ProjectVO> pageResult = new PageResult<>(voList, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<ProjectVO> getById(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectConverter.toVO(projectService.getById(projectId)));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<ProjectDetailVO> getDetail(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(projectVOAssembler.getProjectDetail(projectId, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkProject(#id, 'project:edit')")
    public R<ProjectVO> update(@PathVariable("id") String id, @Valid @RequestBody UpdateProjectDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectConverter.toVO(projectService.update(projectId, dto)));
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("@perm.checkProject(#id, 'project:edit')")
    public R<Void> archive(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        projectService.archive(projectId);
        return R.ok();
    }

    @GetMapping("/{id}/trash-settings")
    @PreAuthorize("@perm.checkProject(#id, 'project:edit')")
    public R<ProjectTrashSettingsVO> getTrashSettings(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.getTrashSettings(projectId));
    }

    @PutMapping("/{id}/trash-settings")
    @PreAuthorize("@perm.checkProject(#id, 'project:edit')")
    public R<Void> updateTrashSettings(@PathVariable("id") String id, @Valid @RequestBody UpdateTrashSettingsDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        projectService.updateTrashSettings(projectId, dto.getTrashRetentionDays());
        return R.ok();
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize("@perm.checkProject(#id, 'project:delete')")
    public R<Void> restore(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        projectService.restore(projectId);
        return R.ok();
    }

    @GetMapping("/{id}/delete-precheck")
    @PreAuthorize("@perm.checkProject(#id, 'project:delete')")
    public R<ProjectDeletePreCheckVO> deletePreCheck(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.preCheckDelete(projectId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkProject(#id, 'project:delete')")
    public R<Void> delete(@PathVariable("id") String id, @RequestParam("confirmKey") String confirmKey) {
        Long projectId = projectService.resolveProjectId(id);
        projectService.deleteProject(projectId, confirmKey);
        return R.ok();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<List<ProjectMemberVO>> listMembers(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.listMembers(projectId));
    }

    /**
     * 按关键词搜索项目成员，用于 @ mention 懒加载场景。
     * keyword 为空时返回前 limit 条（默认10）。
     */
    @GetMapping("/{id}/members/search")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<List<ProjectMemberVO>> searchMembers(
            @PathVariable("id") String id,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "10") int limit) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.searchMembers(projectId, keyword, limit));
    }

    /**
     * 获取可分配工单的成员列表（仅拥有 issue:edit 权限的成员）。
     * 用于负责人选择下拉列表，排除观察者等不可分配角色。
     */
    @GetMapping("/{id}/assignable-members")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<List<ProjectMemberVO>> listAssignableMembers(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.listAssignableMembers(projectId));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@perm.checkProject(#id, 'project:manage_members')")
    public R<Void> addMember(@PathVariable("id") String id, @Valid @RequestBody AddMemberDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        projectService.addMember(projectId, dto);
        return R.ok();
    }

    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize("@perm.checkProject(#id, 'project:manage_members')")
    public R<MemberOperationResultVO> updateMemberRole(@PathVariable("id") String id, @PathVariable("userId") Long userId, @Valid @RequestBody UpdateMemberRoleDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        List<Long> effectiveRoleIds = dto.getEffectiveRoleIds();
        if (effectiveRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要指定一个角色");
        }
        int affectedIssueCount = projectService.updateMemberRoles(projectId, userId, effectiveRoleIds);
        return R.ok(MemberOperationResultVO.of(affectedIssueCount));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@perm.checkProject(#id, 'project:manage_members')")
    public R<MemberOperationResultVO> removeMember(@PathVariable("id") String id, @PathVariable("userId") Long userId) {
        Long projectId = projectService.resolveProjectId(id);
        int affectedCount = projectService.removeMember(projectId, userId);
        return R.ok(MemberOperationResultVO.of(affectedCount));
    }

    @GetMapping("/{id}/members/{userId}/assigned-issue-count")
    @PreAuthorize("@perm.checkProject(#id, 'project:manage_members')")
    public R<AssignedIssueCountVO> getAssignedIssueCount(@PathVariable("id") String id, @PathVariable("userId") Long userId) {
        Long projectId = projectService.resolveProjectId(id);
        int count = projectService.countAssignedIssues(projectId, userId);
        return R.ok(AssignedIssueCountVO.of(count));
    }

    // ========== 项目组成员管理（对标 YouTrack People 页面） ==========

    /**
     * 获取项目成员完整视图（含直接成员 + 用户组成员）
     */
    @GetMapping("/{id}/members-view")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<ProjectMembersViewVO> listMembersView(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.listMembersFullView(projectId));
    }

    /**
     * 添加用户组到项目团队
     */
    @PostMapping("/{id}/group-members")
    @PreAuthorize("@perm.checkProject(#id, 'project:manage_members')")
    public R<Void> addGroupMember(@PathVariable("id") String id, @Valid @RequestBody AddGroupMemberDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        projectService.addGroupMember(projectId, dto);
        return R.ok();
    }

    /**
     * 从项目团队中移除用户组
     */
    @DeleteMapping("/{id}/group-members/{groupId}")
    @PreAuthorize("@perm.checkProject(#id, 'project:manage_members')")
    public R<Void> removeGroupMember(@PathVariable("id") String id, @PathVariable("groupId") Long groupId) {
        Long projectId = projectService.resolveProjectId(id);
        projectService.removeGroupMember(projectId, groupId);
        return R.ok();
    }

    // ========== 项目活动日志 ==========

    @GetMapping("/{id}/activities")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<PageResult<ProjectActivityVO>> listActivities(
            @PathVariable("id") String id,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        Long projectId = projectService.resolveProjectId(id);
        Page<ProjectActivity> pageObj = new Page<>(page, pageSize);
        Page<ProjectActivityVO> result = projectActivityService.listByProject(projectId, pageObj);
        PageResult<ProjectActivityVO> pageResult = new PageResult<>(
                result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    // ========== 项目统计 ==========

    @GetMapping("/{id}/statistics")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<ProjectStatisticsVO> getStatistics(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.getProjectStatistics(projectId));
    }

    // ========== 标签 ==========

    @GetMapping("/{id}/tags")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<List<IssueTagVO>> listProjectTags(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(issueConverter.toTagVOList(tagService.listProjectTags(projectId)));
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("@perm.checkProject(#id, 'issue:create')")
    public R<IssueTagVO> createProjectTag(@PathVariable("id") String id, @Valid @RequestBody CreateTagDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        IssueTag tag = tagService.createTag(projectId, dto);
        return R.ok(issueConverter.toTagVO(tag));
    }

    // ========== 时间追踪设置 ==========

    @GetMapping("/{id}/time-tracking-settings")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<ProjectTimeTrackingSettingsVO> getTimeTrackingSettings(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.getTimeTrackingSettings(projectId));
    }

    @PutMapping("/{id}/time-tracking-settings")
    @PreAuthorize("@perm.checkProject(#id, 'project:edit')")
    public R<ProjectTimeTrackingSettingsVO> updateTimeTrackingSettings(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateTimeTrackingSettingsDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.updateTimeTrackingSettings(projectId, dto));
    }

    /**
     * 获取禁用时间追踪的影响评估（在前端显示确认对话框前调用）
     */
    @GetMapping("/{id}/time-tracking-settings/disable-impact")
    @PreAuthorize("@perm.checkProject(#id, 'project:edit')")
    public R<TimeTrackingDisableImpactVO> getTimeTrackingDisableImpact(
            @PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectVOAssembler.getTimeTrackingDisableImpact(projectId));
    }

    // ========== 项目模块管理 ==========

    /**
     * 获取项目启用的模块列表
     */
    @GetMapping("/{id}/modules")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<ProjectModulesVO> getEnabledModules(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        Set<String> enabledModules = projectModuleService.getEnabledModules(projectId);
        return R.ok(projectModuleService.buildModulesVO(enabledModules));
    }

    /**
     * 更新项目启用的模块列表
     */
    @PutMapping("/{id}/modules")
    @PreAuthorize("@perm.checkProject(#id, 'project:edit')")
    public R<ProjectModulesVO> updateEnabledModules(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateProjectModulesDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        projectModuleService.updateEnabledModules(projectId, dto.getEnabledModules());
        // 更新后清除该项目所有用户的权限缓存
        permissionService.invalidateCacheForProject(projectId);
        // 返回最新状态
        Set<String> enabledModules = projectModuleService.getEnabledModules(projectId);
        return R.ok(projectModuleService.buildModulesVO(enabledModules));
    }

    // ========== 项目收藏 ==========

    /**
     * 切换项目收藏状态（Toggle）
     */
    @PostMapping("/{id}/favorite")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<FavoriteToggleVO> toggleFavorite(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        Long userId = SecurityUtils.getCurrentUserId();
        boolean favorited = projectService.toggleFavorite(projectId, userId);
        return R.ok(FavoriteToggleVO.of(favorited));
    }

    // ========== 项目概览仪表盘 ==========

    /**
     * 获取项目概览仪表盘（含 Widget 列表）
     * 首次访问时自动创建仪表盘，确保每个项目只有一个 project_overview 类型仪表盘。
     * <p>
     * Widget 的 CRUD 复用 /api/v1/dashboards/{dashboardId}/widgets 接口。
     */
    @GetMapping("/{id}/overview-dashboard")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<com.trackflow.report.vo.DashboardDetailVO> getOverviewDashboard(
            @PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(dashboardService.getOrCreateProjectOverviewDashboard(projectId, userId));
    }
}
