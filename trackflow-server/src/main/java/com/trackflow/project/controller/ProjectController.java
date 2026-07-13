package com.trackflow.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.dto.CreateTagDTO;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.service.IssueTagService;
import com.trackflow.issue.vo.IssueTagVO;
import com.trackflow.project.converter.ProjectConverter;
import com.trackflow.project.dto.*;
import com.trackflow.project.entity.Project;
import com.trackflow.project.service.ProjectService;
import com.trackflow.project.vo.ProjectDetailVO;
import com.trackflow.project.vo.ProjectMemberVO;
import com.trackflow.project.vo.ProjectVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectConverter projectConverter;
    private final IssueTagService tagService;
    private final IssueConverter issueConverter;

    @PostMapping
    @PreAuthorize("@perm.checkGlobal('project:create')")
    public R<ProjectVO> create(@Valid @RequestBody CreateProjectDTO dto) {
        Project project = projectService.create(dto);
        return R.ok(projectConverter.toVO(project));
    }

    @GetMapping
    public R<PageResult<ProjectVO>> list(ProjectQuery query) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<Project> result = projectService.list(query.toPage(), query.getKeyword(), query.getStatus(), userId);
        List<ProjectVO> voList = projectConverter.toVOList(result.getRecords());
        PageResult<ProjectVO> pageResult = new PageResult<>(voList, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.check(#id, 'project:view')")
    public R<ProjectVO> getById(@PathVariable Long id) {
        return R.ok(projectConverter.toVO(projectService.getById(id)));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("@perm.check(#id, 'project:view')")
    public R<ProjectDetailVO> getDetail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(projectService.getProjectDetail(id, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.check(#id, 'project:edit')")
    public R<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody UpdateProjectDTO dto) {
        return R.ok(projectConverter.toVO(projectService.update(id, dto)));
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("@perm.check(#id, 'project:edit')")
    public R<Void> archive(@PathVariable Long id) {
        projectService.archive(id);
        return R.ok();
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize("@perm.check(#id, 'project:edit')")
    public R<Void> restore(@PathVariable Long id) {
        projectService.restore(id);
        return R.ok();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("@perm.check(#id, 'project:view')")
    public R<List<ProjectMemberVO>> listMembers(@PathVariable Long id) {
        return R.ok(projectService.listMembersVO(id));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("@perm.check(#id, 'project:manage_members')")
    public R<Void> addMember(@PathVariable Long id, @Valid @RequestBody AddMemberDTO dto) {
        projectService.addMember(id, dto);
        return R.ok();
    }

    @PutMapping("/{id}/members/{userId}")
    @PreAuthorize("@perm.check(#id, 'project:manage_members')")
    public R<Void> updateMemberRole(@PathVariable Long id, @PathVariable Long userId, @Valid @RequestBody UpdateMemberRoleDTO dto) {
        projectService.updateMemberRole(id, userId, dto.getRoleId());
        return R.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("@perm.check(#id, 'project:manage_members')")
    public R<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
        return R.ok();
    }

    // ========== 标签 ==========

    @GetMapping("/{id}/tags")
    @PreAuthorize("@perm.check(#id, 'project:view')")
    public R<List<IssueTagVO>> listProjectTags(@PathVariable Long id) {
        return R.ok(issueConverter.toTagVOList(tagService.listProjectTags(id)));
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("@perm.check(#id, 'issue:create')")
    public R<IssueTagVO> createProjectTag(@PathVariable Long id, @Valid @RequestBody CreateTagDTO dto) {
        IssueTag tag = tagService.createTag(id, dto);
        return R.ok(issueConverter.toTagVO(tag));
    }
}
