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
import com.trackflow.project.converter.ProjectConverter;
import com.trackflow.project.dto.*;
import com.trackflow.project.entity.Project;
import com.trackflow.project.entity.ProjectActivity;
import com.trackflow.project.service.ProjectActivityService;
import com.trackflow.project.service.ProjectCopyService;
import com.trackflow.project.service.ProjectService;
import com.trackflow.project.vo.ProjectActivityVO;
import com.trackflow.project.vo.ProjectDeletePreCheckVO;
import com.trackflow.project.vo.ProjectDetailVO;
import com.trackflow.project.vo.ProjectMemberVO;
import com.trackflow.project.vo.ProjectStatisticsVO;
import com.trackflow.project.vo.ProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectCopyService projectCopyService;
    private final ProjectConverter projectConverter;
    private final IssueTagService tagService;
    private final IssueConverter issueConverter;
    private final ProjectActivityService projectActivityService;
    private final TrackFlowPermissionEvaluator permissionEvaluator;

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
    public R<Map<String, Integer>> getCopySummary(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectCopyService.getSourceProjectSummary(projectId));
    }

    @GetMapping
    public R<PageResult<ProjectVO>> list(ProjectQuery query) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<Project> result = projectService.list(query.toPage(), query.getKeyword(), query.getStatus(), userId);
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
        return R.ok(projectService.getProjectDetail(projectId, userId));
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
    public R<Map<String, Object>> getTrashSettings(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        return R.ok(projectService.getTrashSettings(projectId));
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
        return R.ok(projectService.preCheckDelete(projectId));
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
        return R.ok(projectService.listMembersVO(projectId));
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
    public R<Void> updateMemberRole(@PathVariable("id") String id, @PathVariable("userId") Long userId, @Valid @RequestBody UpdateMemberRoleDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        List<Long> effectiveRoleIds = dto.getEffectiveRoleIds();
        if (effectiveRoleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要指定一个角色");
        }
        projectService.updateMemberRoles(projectId, userId, effectiveRoleIds);
        return R.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@perm.checkProject(#id, 'project:manage_members')")
    public R<Map<String, Object>> removeMember(@PathVariable("id") String id, @PathVariable("userId") Long userId) {
        Long projectId = projectService.resolveProjectId(id);
        int affectedCount = projectService.removeMember(projectId, userId);
        return R.ok(Map.of("affectedIssueCount", affectedCount));
    }

    @GetMapping("/{id}/members/{userId}/assigned-issue-count")
    @PreAuthorize("@perm.checkProject(#id, 'project:manage_members')")
    public R<Map<String, Object>> getAssignedIssueCount(@PathVariable("id") String id, @PathVariable("userId") Long userId) {
        Long projectId = projectService.resolveProjectId(id);
        int count = projectService.countAssignedIssues(projectId, userId);
        return R.ok(Map.of("count", count));
    }

    // ========== 项目活动日志 ==========

    @GetMapping("/{id}/activities")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<PageResult<ProjectActivityVO>> listActivities(
            @PathVariable("id") String id,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
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
        return R.ok(projectService.getProjectStatistics(projectId));
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
    public R<com.trackflow.project.vo.ProjectTimeTrackingSettingsVO> getTimeTrackingSettings(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        var vo = new com.trackflow.project.vo.ProjectTimeTrackingSettingsVO();
        vo.setEnabled(projectService.isTimeTrackingEnabled(projectId));
        return R.ok(vo);
    }

    @PutMapping("/{id}/time-tracking-settings")
    @PreAuthorize("@perm.checkProject(#id, 'project:edit')")
    public R<com.trackflow.project.vo.ProjectTimeTrackingSettingsVO> updateTimeTrackingSettings(
            @PathVariable("id") String id,
            @Valid @RequestBody com.trackflow.project.dto.UpdateTimeTrackingSettingsDTO dto) {
        Long projectId = projectService.resolveProjectId(id);
        if (dto.getEnabled() != null) {
            projectService.updateTimeTrackingEnabled(projectId, dto.getEnabled());
        }
        var vo = new com.trackflow.project.vo.ProjectTimeTrackingSettingsVO();
        vo.setEnabled(projectService.isTimeTrackingEnabled(projectId));
        return R.ok(vo);
    }

    // ========== 项目收藏 ==========

    /**
     * 切换项目收藏状态（Toggle）
     * 返回 { favorited: true/false }
     */
    @PostMapping("/{id}/favorite")
    @PreAuthorize("@perm.checkProject(#id, 'project:view')")
    public R<Map<String, Boolean>> toggleFavorite(@PathVariable("id") String id) {
        Long projectId = projectService.resolveProjectId(id);
        Long userId = SecurityUtils.getCurrentUserId();
        boolean favorited = projectService.toggleFavorite(projectId, userId);
        return R.ok(Map.of("favorited", favorited));
    }
}
