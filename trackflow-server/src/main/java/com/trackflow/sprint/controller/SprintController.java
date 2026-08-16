package com.trackflow.sprint.controller;

import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.sprint.converter.SprintConverter;
import com.trackflow.sprint.dto.CompleteSprintDTO;
import com.trackflow.sprint.dto.CreateSprintDTO;
import com.trackflow.sprint.dto.DeleteSprintDTO;
import com.trackflow.sprint.dto.UpdateSprintDTO;
import com.trackflow.sprint.service.SprintService;
import com.trackflow.sprint.service.SprintStatsService;
import com.trackflow.sprint.vo.BurndownVO;
import com.trackflow.sprint.vo.CompletionPreviewVO;
import com.trackflow.sprint.vo.CreationPreviewVO;
import com.trackflow.sprint.vo.DeletionPreviewVO;
import com.trackflow.sprint.vo.LingeringIssuesVO;
import com.trackflow.sprint.vo.SprintAssigneeDistributionVO;
import com.trackflow.sprint.vo.SprintCompleteResultVO;
import com.trackflow.sprint.vo.SprintVO;
import com.trackflow.sprint.vo.SprintVelocityVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;
    private final SprintStatsService sprintStatsService;
    private final SprintConverter sprintConverter;


    @GetMapping("/api/v1/projects/{projectId}/sprints")
    @PreAuthorize("@perm.check(#projectId, 'sprint:view')")
    public R<PageResult<SprintVO>> list(@PathVariable("projectId") Long projectId,
                                        @RequestParam(value = "page", defaultValue = "1") int page,
                                        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        var result = sprintService.listByProjectWithStatsPage(projectId, page, pageSize);
        return R.ok(new PageResult<>(sprintConverter.statsRowToVOList(result.getList()),
                result.getPagination().getTotal(), result.getPagination().getPage(), result.getPagination().getPageSize()));
    }

    /**
     * 跨项目 Sprint 概览列表。
     * 返回当前用户有权限访问的所有项目的 Sprint（可选按项目过滤）。
     * 不要求指定项目 ID，权限通过 getAccessibleProjectIds 在 Service 层内部处理。
     */
    @GetMapping("/api/v1/sprints")
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<SprintVO>> listAll(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
        var result = sprintService.listAllWithStats(projectId, page, pageSize);
        return R.ok(new PageResult<>(sprintConverter.statsRowToVOList(result.getList()),
                result.getPagination().getTotal(), result.getPagination().getPage(), result.getPagination().getPageSize()));
    }

    @PostMapping("/api/v1/projects/{projectId}/sprints")
    @PreAuthorize("@perm.check(#projectId, 'sprint:create')")
    public R<SprintVO> create(@PathVariable("projectId") Long projectId, @Valid @RequestBody CreateSprintDTO dto) {
        return R.ok(sprintConverter.toVO(sprintService.create(projectId, dto)));
    }

    @GetMapping("/api/v1/projects/{projectId}/sprints/creation-preview")
    @PreAuthorize("@perm.check(#projectId, 'sprint:create')")
    public R<CreationPreviewVO> creationPreview(@PathVariable("projectId") Long projectId) {
        return R.ok(sprintService.getCreationPreview(projectId));
    }

    @GetMapping("/api/v1/projects/{projectId}/sprints/lingering-issues")
    @PreAuthorize("@perm.check(#projectId, 'sprint:view')")
    public R<LingeringIssuesVO> lingeringIssues(@PathVariable("projectId") Long projectId) {
        return R.ok(sprintService.getLingeringIssues(projectId));
    }

    @GetMapping("/api/v1/sprints/{id}")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:view')")
    public R<SprintVO> getById(@PathVariable("id") Long id) {
        return R.ok(sprintConverter.statsRowToVO(sprintService.getByIdWithStats(id)));
    }

    @PutMapping("/api/v1/sprints/{id}")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:edit')")
    public R<SprintVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateSprintDTO dto) {
        return R.ok(sprintConverter.toVO(sprintService.update(id, dto)));
    }

    @PutMapping("/api/v1/sprints/{id}/activate")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:edit')")
    public R<SprintVO> activate(@PathVariable("id") Long id) {
        return R.ok(sprintConverter.toVO(sprintService.activate(id)));
    }

    @GetMapping("/api/v1/sprints/{id}/completion-preview")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:edit')")
    public R<CompletionPreviewVO> completionPreview(@PathVariable("id") Long id) {
        return R.ok(sprintService.getCompletionPreview(id));
    }

    @PutMapping("/api/v1/sprints/{id}/complete")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:edit')")
    public R<SprintCompleteResultVO> complete(@PathVariable("id") Long id, @RequestBody(required = false) @Valid CompleteSprintDTO dto) {
        return R.ok(sprintService.complete(id, dto));
    }

    @GetMapping("/api/v1/sprints/{id}/deletion-preview")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:delete')")
    public R<DeletionPreviewVO> deletionPreview(@PathVariable("id") Long id) {
        return R.ok(sprintService.getDeletionPreview(id));
    }

    @DeleteMapping("/api/v1/sprints/{id}")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:delete')")
    public R<Void> delete(@PathVariable("id") Long id, @RequestBody(required = false) @Valid DeleteSprintDTO dto) {
        sprintService.delete(id, dto);
        return R.ok();
    }

    @GetMapping("/api/v1/sprints/{id}/burndown")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:view')")
    public R<BurndownVO> burndown(@PathVariable("id") Long id,
                                  @RequestParam(value = "mode", defaultValue = "issue_count") String mode) {
        return R.ok(sprintStatsService.getBurndownData(id, mode));
    }

    @PutMapping("/api/v1/sprints/{id}/archive")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:edit')")
    public R<SprintVO> archive(@PathVariable("id") Long id) {
        return R.ok(sprintConverter.toVO(sprintService.archive(id)));
    }

    @PutMapping("/api/v1/sprints/{id}/restore")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:edit')")
    public R<SprintVO> restore(@PathVariable("id") Long id) {
        return R.ok(sprintConverter.toVO(sprintService.restore(id)));
    }

    @PutMapping("/api/v1/sprints/{id}/revert-to-planned")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:edit')")
    public R<SprintVO> revertToPlanned(@PathVariable("id") Long id) {
        return R.ok(sprintConverter.toVO(sprintService.revertToPlanned(id)));
    }

    @GetMapping("/api/v1/sprints/{id}/assignee-distribution")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:view')")
    public R<SprintAssigneeDistributionVO> assigneeDistribution(@PathVariable("id") Long id) {
        return R.ok(sprintStatsService.getAssigneeDistribution(id));
    }

    /**
     * 获取项目最近已完成 Sprint 的速率统计数据。
     * 用于 Sprint 规划页展示历史速率参考（帮助团队合理规划工作量）。
     *
     * @param projectId 项目 ID
     * @param limit     最多统计几个 Sprint，默认 5，最大 10
     */
    @GetMapping("/api/v1/projects/{projectId}/sprint-velocity")
    @PreAuthorize("@perm.check(#projectId, 'sprint:view')")
    public R<SprintVelocityVO> sprintVelocity(
            @PathVariable("projectId") Long projectId,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        // 安全限制：最多查询 10 个 Sprint，防止超大查询
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        return R.ok(sprintStatsService.getSprintVelocity(projectId, safeLimit));
    }
}
