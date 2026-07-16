package com.trackflow.issue.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.dto.*;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueActivity;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.service.IssueService;
import com.trackflow.issue.service.IssueLinkService;
import com.trackflow.issue.service.IssueTagService;
import com.trackflow.issue.service.precheck.ClosePreCheckChain;
import com.trackflow.issue.vo.*;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final IssueConverter issueConverter;
    private final WorkflowService workflowService;
    private final IssueLinkService linkService;
    private final IssueTagService tagService;
    private final SysUserMapper sysUserMapper;
    private final IssueStatusMapper issueStatusMapper;
    private final ClosePreCheckChain closePreCheckChain;
    private final CustomFieldService customFieldService;

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

        // 批量填充 assigneeName + assigneeAvatarUrl
        List<Long> assigneeIds = result.getRecords().stream()
                .map(Issue::getAssigneeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!assigneeIds.isEmpty()) {
            Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(assigneeIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
            for (int i = 0; i < result.getRecords().size(); i++) {
                Issue issue = result.getRecords().get(i);
                if (issue.getAssigneeId() != null) {
                    SysUser user = userMap.get(issue.getAssigneeId());
                    if (user != null) {
                        voList.get(i).setAssigneeName(user.getDisplayName());
                        voList.get(i).setAssigneeAvatarUrl(user.getAvatarUrl());
                    }
                }
            }
        }

        // 填充子任务进度字段（childCount / childClosedCount）
        for (int i = 0; i < result.getRecords().size(); i++) {
            Issue issue = result.getRecords().get(i);
            voList.get(i).setChildCount(issue.getChildCount());
            voList.get(i).setChildClosedCount(issue.getChildClosedCount());
        }

        // 批量填充自定义字段展示值
        List<Long> issueIds = result.getRecords().stream()
                .map(Issue::getId)
                .toList();
        if (!issueIds.isEmpty()) {
            Map<Long, Map<String, String>> cfValuesMap = customFieldService.getBatchDisplayValues(issueIds);
            for (int i = 0; i < result.getRecords().size(); i++) {
                Long issueId = result.getRecords().get(i).getId();
                Map<String, String> cfValues = cfValuesMap.get(issueId);
                if (cfValues != null && !cfValues.isEmpty()) {
                    voList.get(i).setCustomFieldValues(cfValues);
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
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueDetailVO> update(@PathVariable Long id, @Valid @RequestBody UpdateIssueDTO dto) {
        IssueService.UpdateResult result = issueService.update(id, dto);
        IssueDetailVO detail = issueService.getDetail(id);
        if (result.statusAutoReset()) {
            detail.setStatusAutoReset(true);
        }
        return R.ok(detail);
    }

    /**
     * 更新单个自定义字段值（内联编辑）
     */
    @PutMapping("/{id}/custom-fields/{fieldId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueDetailVO> updateCustomFieldValue(
            @PathVariable Long id,
            @PathVariable Long fieldId,
            @RequestBody com.trackflow.customfield.dto.UpdateCustomFieldValueDTO dto) {
        Issue issue = issueService.getById(id);
        customFieldService.saveSingleValue(id, fieldId, dto.getValue(), issue.getIssueType(), issue.getProjectId());
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
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
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

    /**
     * 获取批量状态转换的可用状态列表（带可达性信息）
     */
    @PostMapping("/batch-available-transitions")
    @PreAuthorize("isAuthenticated()")
    public R<List<BatchAvailableStatusVO>> getBatchAvailableTransitions(
            @RequestBody @Valid BatchAvailableTransitionsDTO dto) {
        List<BatchAvailableStatusVO> result = issueService.getBatchAvailableTransitions(dto.getIssueIds());
        return R.ok(result);
    }

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
            case "restore" -> issueService.batchRestore(dto.getIssueIds());
            default -> null;
        };

        if (result == null) {
            return R.fail(ErrorCode.INVALID_BATCH_OPERATION);
        }
        return R.ok(result);
    }

    // ========== 状态转换 ==========

    @GetMapping("/{id}/available-transitions")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:change_status')")
    public R<List<IssueStatusVO>> getAvailableTransitions(@PathVariable Long id) {
        Issue issue = issueService.getByIdWithAccessCheck(id);
        Long userId = SecurityUtils.getCurrentUserId();
        List<IssueStatus> statuses = workflowService.getAvailableTransitions(issue, userId);
        List<IssueStatusVO> voList = issueConverter.toStatusVOList(statuses);

        // 检查阻塞关系：如果有未解决的 blocker，标注关闭状态为 blocked
        List<String> blockerKeys = linkService.getUnresolvedBlockerKeys(id);
        if (!blockerKeys.isEmpty()) {
            for (IssueStatusVO vo : voList) {
                if (Boolean.TRUE.equals(vo.getIsClosed())) {
                    vo.setBlocked(true);
                    vo.setBlockedBy(blockerKeys);
                }
            }
        }

        return R.ok(voList);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:change_status')")
    public R<Void> transitStatus(@PathVariable Long id, @Valid @RequestBody TransitStatusDTO dto) {
        Issue issue = issueService.getByIdWithAccessCheck(id);
        Long userId = SecurityUtils.getCurrentUserId();
        if (!workflowService.isTransitionAllowed(issue, dto.getStatusId(), userId)) {
            return R.fail(ErrorCode.WORKFLOW_TRANSITION_DENIED, "当前角色不允许执行此状态转换");
        }

        // 关闭状态时的前置检查链（非强制模式下返回警告）
        IssueStatus targetStatus = issueStatusMapper.selectById(dto.getStatusId());
        if (targetStatus != null && targetStatus.getIsClosed() && !Boolean.TRUE.equals(dto.getForce())) {
            List<String> warnings = closePreCheckChain.execute(issue);
            if (!warnings.isEmpty()) {
                String message = "此工单" + String.join("，且", warnings) + "，确定要强制关闭吗？";
                return R.fail(ErrorCode.CLOSE_CONFIRMATION_REQUIRED, message);
            }
        }

        issueService.transitStatus(id, dto.getStatusId(), dto.getComment(),
                dto.getAssigneeId(), Boolean.TRUE.equals(dto.getAssigneeExplicit()),
                dto.getVersion());
        return R.ok();
    }

    @PostMapping("/{id}/transitions/undo")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> undoTransitStatus(@PathVariable Long id, @Valid @RequestBody TransitStatusDTO dto) {
        // 撤销操作：验证目标状态必须是上一个状态（从活动记录获取），防止任意跳转
        IssueActivity lastStatusChange = issueService.getLastStatusChange(id);
        if (lastStatusChange == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该工单没有状态变更记录，无法撤销");
        }

        // 时间窗口校验：撤销仅在 30 秒内有效
        long elapsedSeconds = Duration.between(lastStatusChange.getCreatedAt(), LocalDateTime.now()).getSeconds();
        if (elapsedSeconds > 30) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "撤销窗口已过期（30秒内有效）");
        }

        // 操作人校验：只能撤销自己的操作
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!currentUserId.equals(lastStatusChange.getUserId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能撤销自己的操作");
        }

        // 并发修改检测：检查该工单在状态变更之后是否有其他人做过修改
        Issue currentIssue = issueService.getById(id);
        if (currentIssue.getUpdatedBy() != null && !currentUserId.equals(currentIssue.getUpdatedBy())) {
            // 如果最后修改人不是自己，说明有人在中间做了修改
            if (currentIssue.getUpdatedAt() != null && currentIssue.getUpdatedAt().isAfter(lastStatusChange.getCreatedAt())) {
                throw new BusinessException(ErrorCode.CONFLICT, "工单已被其他人修改，无法撤销");
            }
        }

        // 验证目标状态名称与上一次变更的旧状态一致
        IssueStatus targetStatus = issueStatusMapper.selectById(dto.getStatusId());
        if (targetStatus == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标状态不存在");
        }
        if (!targetStatus.getName().equals(lastStatusChange.getOldValue())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "撤销操作只能回退到上一个状态（" + lastStatusChange.getOldValue() + "），不允许任意跳转");
        }

        // 目标状态已验证为上一个状态，跳过工作流校验执行撤销
        issueService.transitStatusSkipWorkflow(id, dto.getStatusId(), "撤销状态变更");
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

    @PutMapping("/{id}/comments/{commentId}")
    @PreAuthorize("@perm.check(@issueService.getProjectId(#id), 'issue:comment')")
    public R<IssueCommentVO> updateComment(@PathVariable Long id, @PathVariable Long commentId,
                                            @Valid @RequestBody UpdateCommentDTO dto) {
        return R.ok(issueConverter.toCommentVO(issueService.updateComment(id, commentId, dto.getContent())));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @PreAuthorize("@perm.check(@issueService.getProjectId(#id), 'issue:comment')")
    public R<Void> deleteComment(@PathVariable Long id, @PathVariable Long commentId) {
        issueService.deleteComment(id, commentId);
        return R.ok();
    }

    // ========== 附件 ==========

    @GetMapping("/{id}/attachments")
    public R<List<IssueAttachmentVO>> listAttachments(@PathVariable Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(issueConverter.toAttachmentVOList(issueService.listAttachments(id)));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueAttachmentVO> uploadAttachment(@PathVariable Long id,
                                                  @RequestParam("file") MultipartFile file) {
        IssueAttachment attachment = issueService.uploadAttachment(id, file);
        return R.ok(issueConverter.toAttachmentVO(attachment));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
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
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> addTag(@PathVariable Long id, @Valid @RequestBody AddTagDTO dto) {
        tagService.addTagToIssue(id, dto.getTagId());
        return R.ok();
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> removeTag(@PathVariable Long id, @PathVariable Long tagId) {
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
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> createLink(@PathVariable Long id, @Valid @RequestBody CreateIssueLinkDTO dto) {
        linkService.createIssueLink(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}/links/{linkId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> deleteLink(@PathVariable Long id, @PathVariable Long linkId) {
        linkService.deleteIssueLink(linkId);
        return R.ok();
    }

    // ========== 状态列表 ==========

    @GetMapping("/statuses")
    public R<List<IssueStatusVO>> listStatuses() {
        return R.ok(issueConverter.toStatusVOList(issueService.listStatuses()));
    }
}
