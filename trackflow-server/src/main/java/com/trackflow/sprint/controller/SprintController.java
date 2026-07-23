package com.trackflow.sprint.controller;

import com.trackflow.common.model.R;
import com.trackflow.sprint.converter.SprintConverter;
import com.trackflow.sprint.dto.CompleteSprintDTO;
import com.trackflow.sprint.dto.CreateSprintDTO;
import com.trackflow.sprint.dto.DeleteSprintDTO;
import com.trackflow.sprint.dto.UpdateSprintDTO;
import com.trackflow.sprint.service.SprintService;
import com.trackflow.sprint.vo.BurndownVO;
import com.trackflow.sprint.vo.CompletionPreviewVO;
import com.trackflow.sprint.vo.CreationPreviewVO;
import com.trackflow.sprint.vo.DeletionPreviewVO;
import com.trackflow.sprint.vo.SprintAssigneeDistributionVO;
import com.trackflow.sprint.vo.SprintVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;
    private final SprintConverter sprintConverter;


    @GetMapping("/api/v1/projects/{projectId}/sprints")
    @PreAuthorize("@perm.check(#projectId, 'sprint:view')")
    public R<List<SprintVO>> list(@PathVariable("projectId") Long projectId) {
        return R.ok(sprintService.listByProjectWithStats(projectId));
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

    @GetMapping("/api/v1/sprints/{id}")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:view')")
    public R<SprintVO> getById(@PathVariable("id") Long id) {
        return R.ok(sprintService.getByIdWithStats(id));
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
    public R<SprintVO> complete(@PathVariable("id") Long id, @RequestBody(required = false) @Valid CompleteSprintDTO dto) {
        return R.ok(sprintConverter.toVO(sprintService.complete(id, dto)));
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
        return R.ok(sprintService.getBurndownData(id, mode));
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

    @GetMapping("/api/v1/sprints/{id}/assignee-distribution")
    @PreAuthorize("@perm.checkSprint(#id, 'sprint:view')")
    public R<SprintAssigneeDistributionVO> assigneeDistribution(@PathVariable("id") Long id) {
        return R.ok(sprintService.getAssigneeDistribution(id));
    }
}
