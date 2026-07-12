package com.trackflow.sprint.controller;

import com.trackflow.common.model.R;
import com.trackflow.sprint.converter.SprintConverter;
import com.trackflow.sprint.dto.CreateSprintDTO;
import com.trackflow.sprint.service.SprintService;
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
    public R<List<SprintVO>> list(@PathVariable Long projectId) {
        return R.ok(sprintService.listByProjectWithStats(projectId));
    }

    @PostMapping("/api/v1/projects/{projectId}/sprints")
    @PreAuthorize("@perm.check(#projectId, 'sprint:create')")
    public R<SprintVO> create(@PathVariable Long projectId, @Valid @RequestBody CreateSprintDTO dto) {
        return R.ok(sprintConverter.toVO(sprintService.create(projectId, dto)));
    }

    @GetMapping("/api/v1/sprints/{id}")
    @PreAuthorize("@perm.check(@sprintService.getById(#id).projectId, 'sprint:view')")
    public R<SprintVO> getById(@PathVariable Long id) {
        return R.ok(sprintConverter.toVO(sprintService.getById(id)));
    }

    @PutMapping("/api/v1/sprints/{id}/activate")
    @PreAuthorize("@perm.check(@sprintService.getById(#id).projectId, 'sprint:edit')")
    public R<SprintVO> activate(@PathVariable Long id) {
        return R.ok(sprintConverter.toVO(sprintService.activate(id)));
    }

    @PutMapping("/api/v1/sprints/{id}/complete")
    @PreAuthorize("@perm.check(@sprintService.getById(#id).projectId, 'sprint:edit')")
    public R<SprintVO> complete(@PathVariable Long id) {
        return R.ok(sprintConverter.toVO(sprintService.complete(id)));
    }

    @DeleteMapping("/api/v1/sprints/{id}")
    @PreAuthorize("@perm.check(@sprintService.getById(#id).projectId, 'sprint:delete')")
    public R<Void> delete(@PathVariable Long id) {
        sprintService.delete(id);
        return R.ok();
    }
}
