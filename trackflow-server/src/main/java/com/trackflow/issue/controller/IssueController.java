package com.trackflow.issue.controller;

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
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.service.IssueService;
import com.trackflow.issue.service.IssueExportService;
import com.trackflow.issue.service.IssueLinkService;
import com.trackflow.issue.service.IssueTagService;
import com.trackflow.issue.vo.*;
import com.trackflow.workflow.service.WorkflowService;
import com.trackflow.workflow.vo.ActionExecutionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class IssueController {

    private final IssueService issueService;
    private final IssueExportService issueExportService;
    private final IssueConverter issueConverter;
    private final WorkflowService workflowService;
    private final IssueLinkService linkService;
    private final IssueTagService tagService;
    private final CustomFieldService customFieldService;
    private final com.trackflow.system.mapper.UserGroupMapper userGroupMapper;

    @PostMapping
    @PreAuthorize("@perm.check(#dto.projectId, 'issue:create')")
    public R<IssueDetailVO> create(@Valid @RequestBody CreateIssueDTO dto) {
        Issue issue = issueService.create(dto);
        return R.ok(issueService.getDetail(issue.getId()));
    }

    @GetMapping
    public R<PageResult<IssueVO>> list(@Valid IssueQuery query) {
        return R.ok(issueService.listWithDetails(query));
    }

    @GetMapping("/{id}")
    public R<IssueDetailVO> getById(@PathVariable("id") Long id) {
        // 校验项目成员权限并获取详情
        return R.ok(issueService.getDetailWithAccessCheck(id));
    }

    @GetMapping("/key/{issueKey}")
    public R<IssueDetailVO> getByKey(@PathVariable("issueKey") String issueKey) {
        // 先校验项目成员权限
        Issue issue = issueService.getByKeyWithAccessCheck(issueKey);
        return R.ok(issueService.getDetail(issue.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueDetailVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateIssueDTO dto) {
        IssueService.UpdateResult result = issueService.update(id, dto);
        IssueDetailVO detail = issueService.getDetail(id);
        if (result.statusAutoReset()) {
            detail.setStatusAutoReset(true);
        }
        return R.ok(detail);
    }

    /**
     * 更新单个自定义字段值（内联编辑）。
     * 单值字段传 { "value": "xxx" }，多值字段传 { "values": ["id1","id2"] }。
     */
    @PutMapping("/{id}/custom-fields/{fieldId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueDetailVO> updateCustomFieldValue(
            @PathVariable("id") Long id,
            @PathVariable("fieldId") Long fieldId,
            @RequestBody com.trackflow.customfield.dto.UpdateCustomFieldValueDTO dto) {
        Issue issue = issueService.getById(id);

        // Check field-level editability (role-based)
        customFieldService.checkFieldEditable(issue.getProjectId(), fieldId);

        // 多值字段使用 values 数组（逗号连接后传入 service 层解析为多行）
        String effectiveValue;
        if (dto.getValues() != null && !dto.getValues().isEmpty()) {
            effectiveValue = String.join(",", dto.getValues());
        } else {
            effectiveValue = dto.getValue();
        }
        customFieldService.saveSingleValue(id, fieldId, effectiveValue, issue.getIssueType(), issue.getProjectId());
        return R.ok(issueService.getDetail(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:delete')")
    public R<Void> delete(@PathVariable("id") Long id) {
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
    @PreAuthorize("@perm.checkDeletedIssue(#id, 'issue:delete')")
    public R<Void> restore(@PathVariable("id") Long id) {
        issueService.restore(id);
        return R.ok();
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("@perm.checkDeletedIssue(#id, 'project:admin')")
    public R<Void> permanentDelete(@PathVariable("id") Long id) {
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
        boolean silent = Boolean.TRUE.equals(dto.getSilent());

        // WIP 限制预检查（仅 status 操作）
        if ("status".equals(dto.getOperation()) && dto.getStatusId() != null
                && !Boolean.TRUE.equals(dto.getForceWip())) {
            String wipWarning = issueService.checkBatchWipLimit(dto.getIssueIds(), dto.getStatusId());
            if (wipWarning != null) {
                return R.fail(ErrorCode.WIP_LIMIT_EXCEEDED, wipWarning);
            }
        }

        BatchOperationResultVO result = switch (dto.getOperation()) {
            case "status" -> {
                if (dto.getStatusId() == null) {
                    yield null;
                }
                yield issueService.batchTransitStatus(dto.getIssueIds(), dto.getStatusId(),
                        dto.getComment(), dto.getVersions(), silent);
            }
            case "assign" -> {
                if (dto.getAssigneeId() == null) {
                    yield null;
                }
                yield issueService.batchAssign(dto.getIssueIds(), dto.getAssigneeId(), silent);
            }
            case "sprint" -> {
                if (dto.getSprintId() == null) {
                    yield null;
                }
                yield issueService.batchUpdateSprint(dto.getIssueIds(), dto.getSprintId(), silent);
            }
            case "priority" -> {
                if (dto.getPriority() == null || dto.getPriority().isBlank()) {
                    yield null;
                }
                yield issueService.batchUpdatePriority(dto.getIssueIds(), dto.getPriority(), silent);
            }
            case "tag_add" -> {
                if (dto.getTagId() == null) {
                    yield null;
                }
                yield issueService.batchAddTag(dto.getIssueIds(), dto.getTagId(), silent);
            }
            case "tag_remove" -> {
                if (dto.getTagId() == null) {
                    yield null;
                }
                yield issueService.batchRemoveTag(dto.getIssueIds(), dto.getTagId(), silent);
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

    // ========== 导出 ==========

    @PostMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public void exportIssues(@Valid @RequestBody IssueExportDTO dto,
                             jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        IssueExportService.ExportResult result = issueExportService.export(dto);

        response.setContentType(result.contentType());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + java.net.URLEncoder.encode(result.filename(), java.nio.charset.StandardCharsets.UTF_8) + "\"");
        response.setContentLength(result.content().length);
        response.getOutputStream().write(result.content());
        response.getOutputStream().flush();
    }

    // ========== 状态转换 ==========

    @GetMapping("/{id}/available-transitions")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:change_status')")
    public R<List<IssueStatusVO>> getAvailableTransitions(@PathVariable("id") Long id) {
        Issue issue = issueService.getByIdWithAccessCheck(id);
        Long userId = SecurityUtils.getCurrentUserId();
        List<IssueStatus> statuses = workflowService.getAvailableTransitions(issue, userId);
        List<IssueStatusVO> voList = issueConverter.toStatusVOList(statuses);

        // 查询哪些目标状态的转换需要强制评论
        List<Long> targetStatusIds = statuses.stream().map(IssueStatus::getId).toList();
        Set<Long> requireCommentIds = workflowService.getRequireCommentStatusIds(
                issue.getStatusId(), targetStatusIds);
        for (IssueStatusVO vo : voList) {
            if (requireCommentIds.contains(Long.valueOf(vo.getId()))) {
                vo.setRequireComment(true);
            }
        }

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
    public R<TransitStatusResultVO> transitStatus(@PathVariable("id") Long id, @Valid @RequestBody TransitStatusDTO dto) {
        Issue issue = issueService.getByIdWithAccessCheck(id);
        Long userId = SecurityUtils.getCurrentUserId();
        if (!workflowService.isTransitionAllowed(issue, dto.getStatusId(), userId)) {
            return R.fail(ErrorCode.WORKFLOW_TRANSITION_DENIED, "当前角色不允许执行此状态转换");
        }

        // 强制评论校验：如果转换规则要求必须填写评论
        if (workflowService.isCommentRequired(issue.getStatusId(), dto.getStatusId())) {
            if (dto.getComment() == null || dto.getComment().isBlank()) {
                return R.fail(ErrorCode.BAD_REQUEST, "此状态转换需要填写理由");
            }
        }

        // WIP 限制 + 关闭前置检查（业务逻辑在 Service 层）
        IssueService.TransitPreCheckResult preCheck = issueService.checkTransitPreConditions(
                issue, dto.getStatusId(),
                Boolean.TRUE.equals(dto.getForceWip()),
                Boolean.TRUE.equals(dto.getForce()));

        if (preCheck.wipWarning() != null) {
            return R.fail(ErrorCode.WIP_LIMIT_EXCEEDED, preCheck.wipWarning());
        }
        if (preCheck.closeWarning() != null) {
            return R.fail(ErrorCode.CLOSE_CONFIRMATION_REQUIRED, preCheck.closeWarning());
        }

        // Controller 已完成工作流校验，传入 skipWorkflowCheck=true 避免 Service 重复校验
        ActionExecutionResult actionResult = issueService.transitStatus(id, dto.getStatusId(), dto.getComment(),
                dto.getAssigneeId(), Boolean.TRUE.equals(dto.getAssigneeExplicit()),
                dto.getVersion(), true);

        // 返回更新后的版本号 + 动作执行结果
        Issue updated = issueService.getById(id);
        return R.ok(TransitStatusResultVO.of(updated.getVersion(), actionResult));
    }

    @PostMapping("/{id}/transitions/undo")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<TransitStatusResultVO> undoTransitStatus(@PathVariable("id") Long id, @Valid @RequestBody TransitStatusDTO dto) {
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
            if (currentIssue.getUpdatedAt() != null && currentIssue.getUpdatedAt().isAfter(lastStatusChange.getCreatedAt())) {
                throw new BusinessException(ErrorCode.CONFLICT, "工单已被其他人修改，无法撤销");
            }
        }

        // 验证目标状态名称与上一次变更的旧状态一致（业务校验在 Service 层）
        issueService.validateUndoTargetStatus(dto.getStatusId(), lastStatusChange);

        // 目标状态已验证为上一个状态，跳过工作流校验执行撤销
        ActionExecutionResult actionResult = issueService.transitStatusSkipWorkflow(id, dto.getStatusId(), "撤销状态变更");

        // 返回更新后的版本号
        Issue updated = issueService.getById(id);
        return R.ok(TransitStatusResultVO.of(updated.getVersion(), actionResult));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:assign')")
    public R<Void> assign(@PathVariable("id") Long id, @Valid @RequestBody AssignIssueDTO dto) {
        issueService.assign(id, dto.getAssigneeId());
        return R.ok();
    }

    // ========== 评论 ==========

    @GetMapping("/{id}/comments")
    public R<List<IssueCommentVO>> listComments(@PathVariable("id") Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(issueService.listCommentsWithUser(id));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:comment')")
    public R<IssueCommentVO> addComment(@PathVariable("id") Long id, @Valid @RequestBody AddCommentDTO dto) {
        IssueComment comment = issueService.addComment(id, dto.getContent(), dto.getVisibleToGroupIds());
        return R.ok(issueService.toCommentVOWithUser(comment));
    }

    @PutMapping("/{id}/comments/{commentId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:comment')")
    public R<IssueCommentVO> updateComment(@PathVariable("id") Long id, @PathVariable("commentId") Long commentId,
                                            @Valid @RequestBody UpdateCommentDTO dto) {
        boolean updateVisibility = dto.getVisibleToGroupIds() != null;
        IssueComment comment = issueService.updateComment(id, commentId, dto.getContent(),
                dto.getVisibleToGroupIds(), updateVisibility);
        return R.ok(issueService.toCommentVOWithUser(comment));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:comment')")
    public R<Void> deleteComment(@PathVariable("id") Long id, @PathVariable("commentId") Long commentId) {
        issueService.deleteComment(id, commentId);
        return R.ok();
    }

    // ========== 附件 ==========

    @GetMapping("/{id}/attachments")
    public R<List<IssueAttachmentVO>> listAttachments(@PathVariable("id") Long id) {
        issueService.getByIdWithAccessCheck(id);
        List<IssueAttachment> attachments = issueService.listAttachments(id);
        return R.ok(buildAttachmentVOList(attachments));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueAttachmentVO> uploadAttachment(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibleToGroupIds", required = false) List<Long> visibleToGroupIds) {
        IssueAttachment attachment = issueService.uploadAttachment(id, file, visibleToGroupIds);
        return R.ok(buildAttachmentVO(attachment));
    }

    @PutMapping("/{id}/attachments/{attachmentId}/visibility")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueAttachmentVO> updateAttachmentVisibility(
            @PathVariable("id") Long id,
            @PathVariable("attachmentId") Long attachmentId,
            @RequestBody com.trackflow.issue.dto.UpdateAttachmentVisibilityDTO dto) {
        IssueAttachment attachment = issueService.updateAttachmentVisibility(id, attachmentId, dto.getVisibleToGroupIds());
        return R.ok(buildAttachmentVO(attachment));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> deleteAttachment(@PathVariable("id") Long id, @PathVariable("attachmentId") Long attachmentId) {
        issueService.deleteAttachment(id, attachmentId);
        return R.ok();
    }

    /**
     * 构建附件 VO（含可见性信息）
     */
    private IssueAttachmentVO buildAttachmentVO(IssueAttachment attachment) {
        IssueAttachmentVO vo = issueConverter.toAttachmentVO(attachment);
        vo.setIsPrivate(attachment.getVisibleToGroupIds() != null && !attachment.getVisibleToGroupIds().isEmpty());
        if (Boolean.TRUE.equals(vo.getIsPrivate())) {
            vo.setVisibleToGroupIds(attachment.getVisibleToGroupIds().stream()
                    .map(String::valueOf).toList());
            // 查询组名称
            vo.setVisibleToGroupNames(resolveGroupNames(attachment.getVisibleToGroupIds()));
        }
        return vo;
    }

    private List<IssueAttachmentVO> buildAttachmentVOList(List<IssueAttachment> attachments) {
        return attachments.stream().map(this::buildAttachmentVO).toList();
    }

    private List<String> resolveGroupNames(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) return List.of();
        return userGroupMapper.selectBatchIds(groupIds).stream()
                .map(g -> g.getName())
                .toList();
    }

    // ========== 活动记录 ==========

    @GetMapping("/{id}/activities")
    public R<List<IssueActivityVO>> listActivities(@PathVariable("id") Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(issueService.listActivitiesWithUser(id));
    }

    // ========== 标签 ==========

    @GetMapping("/{id}/tags")
    public R<List<IssueTagVO>> listIssueTags(@PathVariable("id") Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(issueConverter.toTagVOList(tagService.listIssueTags(id)));
    }

    @PostMapping("/{id}/tags")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> addTag(@PathVariable("id") Long id, @Valid @RequestBody AddTagDTO dto) {
        tagService.addTagToIssue(id, dto.getTagId());
        return R.ok();
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> removeTag(@PathVariable("id") Long id, @PathVariable("tagId") Long tagId) {
        tagService.removeTagFromIssue(id, tagId);
        return R.ok();
    }

    // ========== 关联 ==========

    @GetMapping("/{id}/links")
    public R<List<IssueLinkVO>> listLinks(@PathVariable("id") Long id) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(linkService.listIssueLinks(id));
    }

    @PostMapping("/{id}/links")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> createLink(@PathVariable("id") Long id, @Valid @RequestBody CreateIssueLinkDTO dto) {
        linkService.createIssueLink(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}/links/{linkId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> deleteLink(@PathVariable("id") Long id, @PathVariable("linkId") Long linkId) {
        linkService.deleteIssueLink(linkId);
        return R.ok();
    }

    // ========== 状态列表 ==========

    @GetMapping("/statuses")
    public R<List<IssueStatusVO>> listStatuses() {
        return R.ok(issueConverter.toStatusVOList(issueService.listStatuses()));
    }

    // ========== 移动工单 ==========

    @PostMapping("/{id}/move")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:move')")
    public R<IssueDetailVO> move(@PathVariable("id") Long id,
                                 @Valid @RequestBody com.trackflow.issue.dto.MoveIssueDTO dto) {
        Issue moved = issueService.moveToProject(id, dto);
        return R.ok(issueService.getDetail(moved.getId()));
    }
}
