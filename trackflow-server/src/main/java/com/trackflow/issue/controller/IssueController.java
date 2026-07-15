package com.trackflow.issue.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.dto.*;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.service.IssueService;
import com.trackflow.issue.service.IssueLinkService;
import com.trackflow.issue.service.IssueTagService;
import com.trackflow.issue.vo.*;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final PermissionService permissionService;
    private final IssueConverter issueConverter;
    private final WorkflowService workflowService;
    private final IssueLinkService linkService;
    private final IssueTagService tagService;
    private final SysUserMapper sysUserMapper;
    private final IssueStatusMapper issueStatusMapper;

    @PostMapping
    @PreAuthorize("@perm.check(#dto.projectId, 'issue:create')")
    public R<IssueDetailVO> create(@Valid @RequestBody CreateIssueDTO dto) {
        Issue issue = issueService.create(dto);
        return R.ok(issueService.getDetail(issue.getId()));
    }

    @GetMapping
    public R<PageResult<IssueVO>> list(@Valid IssueQuery query) {
        // listByQuery 内部已做项目成员校验
        Page<Issue> result = issueService.listByQuery(query);
        List<IssueVO> voList = issueConverter.toVOList(result.getRecords());

        // 批量填充 assigneeName
        List<Long> assigneeIds = result.getRecords().stream()
                .map(Issue::getAssigneeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!assigneeIds.isEmpty()) {
            Map<Long, String> userNameMap = sysUserMapper.selectBatchIds(assigneeIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
            for (int i = 0; i < result.getRecords().size(); i++) {
                Issue issue = result.getRecords().get(i);
                if (issue.getAssigneeId() != null) {
                    voList.get(i).setAssigneeName(userNameMap.get(issue.getAssigneeId()));
                }
            }
        }

        PageResult<IssueVO> pageResult = new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        return R.ok(pageResult);
    }

    @GetMapping("/{id}")
    public R<IssueDetailVO> getById(@PathVariable Long id) {
        // 校验项目成员权限并获取详情
        return R.ok(issueService.getDetailWithAccessCheck(id));
    }

    @GetMapping("/key/{issueKey}")
    public R<IssueDetailVO> getByKey(@PathVariable String issueKey) {
        // 先校验项目成员权限
        Issue issue = issueService.getByKeyWithAccessCheck(issueKey);
        return R.ok(issueService.getDetail(issue.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.check(@issueService.getProjectId(#id), 'issue:edit')")
    public R<IssueDetailVO> update(@PathVariable Long id, @Valid @RequestBody UpdateIssueDTO dto) {
        issueService.update(id, dto);
        return R.ok(issueService.getDetail(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.check(@issueService.getProjectId(#id), 'issue:delete')")
    public R<Void> delete(@PathVariable Long id) {
        issueService.delete(id);
        return R.ok();
    }

    // ========== 回收站 ==========

    @GetMapping("/trash")
    @PreAuthorize("@perm.check(#projectId, 'issue:delete')")
    public R<PageResult<IssueTrashVO>> listTrash(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(issueService.listTrash(projectId, page, pageSize));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("@perm.check(@issueService.getDeletedIssueProjectId(#id), 'issue:delete')")
    public R<Void> restore(@PathVariable Long id) {
        issueService.restore(id);
        return R.ok();
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("@perm.check(@issueService.getDeletedIssueProjectId(#id), 'project:admin')")
    public R<Void> permanentDelete(@PathVariable Long id) {
        issueService.permanentDelete(id);
        return R.ok();
    }

    // ========== 批量操作 ==========

    @PostMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    public R<BatchOperationResultVO> batchOperation(@Valid @RequestBody BatchOperationDTO dto) {
        BatchOperationResultVO result = switch (dto.getOperation()) {
            case "status" -> {
                if (dto.getStatusId() == null) {
                    yield null;
                }
                yield issueService.batchTransitStatus(dto.getIssueIds(), dto.getStatusId());
            }
            case "assign" -> {
                if (dto.getAssigneeId() == null) {
                    yield null;
                }
                yield issueService.batchAssign(dto.getIssueIds(), dto.getAssigneeId());
            }
            case "sprint" -> {
                if (dto.getSprintId() == null) {
                    yield null;
                }
                yield issueService.batchUpdateSprint(dto.getIssueIds(), dto.getSprintId());
            }
            case "priority" -> {
                if (dto.getPriority() == null || dto.getPriority().isBlank()) {
                    yield null;
                }
                yield issueService.batchUpdatePriority(dto.getIssueIds(), dto.getPriority());
            }
            case "delete" -> issueService.batchDelete(dto.getIssueIds());
            default -> null;
        };

        if (result == null) {
            return R.fail(40000, "无效的批量操作类型或缺少必填参数");
        }
        return R.ok(result);
    }

    // ========== 状态转换 ==========

    @GetMapping("/{id}/available-transitions")
    @PreAuthorize("@perm.check(@issueService.getProjectId(#id), 'issue:change_status')")
    public R<List<IssueStatusVO>> getAvailableTransitions(@PathVariable Long id) {
        Issue issue = issueService.getByIdWithAccessCheck(id);
        Long userId = SecurityUtils.getCurrentUserId();
        List<IssueStatus> statuses = workflowService.getAvailableTransitions(issue, userId);
        return R.ok(issueConverter.toStatusVOList(statuses));
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("@perm.check(@issueService.getProjectId(#id), 'issue:change_status')")
    public R<Void> transitStatus(@PathVariable Long id, @Valid @RequestBody TransitStatusDTO dto) {
        // 校验项目成员权限 + 工作流规则（含所有权检查）
        Issue issue = issueService.getByIdWithAccessCheck(id);
        Long userId = SecurityUtils.getCurrentUserId();
        if (!workflowService.isTransitionAllowed(issue, dto.getStatusId(), userId)) {
            // 区分错误原因：所有权问题 vs 工作流规则限制
            // 系统管理员和特权角色不受所有权限制，错误必然是工作流规则
            if (!permissionService.isSystemAdmin(userId) && !workflowService.isIssueOwner(issue, userId)) {
                return R.fail(40300, "只能修改分配给自己或由自己创建的工单状态");
            }
            return R.fail(40300, "当前角色不允许执行此状态转换");
        }

        // 关闭父工单时检查子任务状态（非强制模式下返回警告）
        IssueStatus targetStatus = issueStatusMapper.selectById(dto.getStatusId());
        if (targetStatus != null && targetStatus.getIsClosed() && !Boolean.TRUE.equals(dto.getForce())) {
            long openChildren = issueService.countOpenChildren(id);
            if (openChildren > 0) {
                return R.fail(40910, "该工单有 " + openChildren + " 个未完成的子任务，确认要关闭吗？");
            }
        }

        issueService.transitStatus(id, dto.getStatusId(), dto.getComment(),
                dto.getAssigneeId(), Boolean.TRUE.equals(dto.getAssigneeExplicit()),
                dto.getVersion());
        return R.ok();
    }

    @PostMapping("/{id}/transitions/undo")
    @PreAuthorize("@perm.check(@issueService.getProjectId(#id), 'issue:edit')")
    public R<Void> undoTransitStatus(@PathVariable Long id, @Valid @RequestBody TransitStatusDTO dto) {
        // 撤销操作：绕过工作流校验，但仍需验证用户有该项目的 issue:edit 权限
        issueService.transitStatus(id, dto.getStatusId(), "撤销状态变更");
        return R.ok();
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("@perm.check(@issueService.getProjectId(#id), 'issue:assign')")
    public R<Void> assign(@PathVariable Long id, @Valid @RequestBody AssignIssueDTO dto) {
        issueService.assign(id, dto.getAssigneeId());
        return R.ok();
    }

    // ========== 评论 ==========

    @GetMapping("/{id}/comments")
    public R<List<IssueCommentVO>> listComments(@PathVariable Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(issueService.listCommentsWithUser(id));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("@perm.check(@issueService.getProjectId(#id), 'issue:comment')")
    public R<IssueCommentVO> addComment(@PathVariable Long id, @Valid @RequestBody AddCommentDTO dto) {
        return R.ok(issueConverter.toCommentVO(issueService.addComment(id, dto.getContent())));
    }

    // ========== 附件 ==========

    @GetMapping("/{id}/attachments")
    public R<List<IssueAttachmentVO>> listAttachments(@PathVariable Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(issueConverter.toAttachmentVOList(issueService.listAttachments(id)));
    }

    @PostMapping("/{id}/attachments")
    public R<IssueAttachmentVO> uploadAttachment(@PathVariable Long id,
                                                  @RequestParam("file") MultipartFile file) {
        issueService.getByIdWithAccessCheck(id);
        IssueAttachment attachment = issueService.uploadAttachment(id, file);
        return R.ok(issueConverter.toAttachmentVO(attachment));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public R<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        issueService.getByIdWithAccessCheck(id);
        issueService.deleteAttachment(id, attachmentId);
        return R.ok();
    }

    // ========== 活动记录 ==========

    @GetMapping("/{id}/activities")
    public R<List<IssueActivityVO>> listActivities(@PathVariable Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(issueService.listActivitiesWithUser(id));
    }

    // ========== 标签 ==========

    @GetMapping("/{id}/tags")
    public R<List<IssueTagVO>> listIssueTags(@PathVariable Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(issueConverter.toTagVOList(tagService.listIssueTags(id)));
    }

    @PostMapping("/{id}/tags")
    public R<Void> addTag(@PathVariable Long id, @Valid @RequestBody AddTagDTO dto) {
        issueService.getByIdWithAccessCheck(id);
        tagService.addTagToIssue(id, dto.getTagId());
        return R.ok();
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    public R<Void> removeTag(@PathVariable Long id, @PathVariable Long tagId) {
        issueService.getByIdWithAccessCheck(id);
        tagService.removeTagFromIssue(id, tagId);
        return R.ok();
    }

    // ========== 关联 ==========

    @GetMapping("/{id}/links")
    public R<List<IssueLinkVO>> listLinks(@PathVariable Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(linkService.listIssueLinks(id));
    }

    @PostMapping("/{id}/links")
    public R<Void> createLink(@PathVariable Long id, @Valid @RequestBody CreateIssueLinkDTO dto) {
        issueService.getByIdWithAccessCheck(id);
        linkService.createIssueLink(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}/links/{linkId}")
    public R<Void> deleteLink(@PathVariable Long id, @PathVariable Long linkId) {
        issueService.getByIdWithAccessCheck(id);
        linkService.deleteIssueLink(linkId);
        return R.ok();
    }

    // ========== 状态列表 ==========

    @GetMapping("/statuses")
    public R<List<IssueStatusVO>> listStatuses() {
        return R.ok(issueConverter.toStatusVOList(issueService.listStatuses()));
    }
}
