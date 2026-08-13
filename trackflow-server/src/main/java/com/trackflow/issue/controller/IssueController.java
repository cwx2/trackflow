package com.trackflow.issue.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.model.PageResult;
import com.trackflow.common.model.R;
import com.trackflow.customfield.converter.CustomFieldConverter;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.issue.converter.IssueConverter;
import com.trackflow.issue.dto.*;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueAttachment;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.mapper.result.ChildIssueRow;
import com.trackflow.issue.mapper.result.IssueDetailRow;
import com.trackflow.issue.mapper.result.TrashRow;
import com.trackflow.issue.service.IssueService;
import com.trackflow.issue.service.IssueDetailVOAssembler;
import com.trackflow.issue.service.IssueExportService;
import com.trackflow.issue.service.IssueLinkService;
import com.trackflow.issue.service.IssueLinkTypeService;
import com.trackflow.issue.service.IssueTagService;
import com.trackflow.issue.service.IssueTypeFieldService;
import com.trackflow.issue.service.IssueVOAssembler;
import com.trackflow.issue.service.PriorityFieldService;
import com.trackflow.issue.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class IssueController {

    private final IssueService issueService;
    private final IssueExportService issueExportService;
    private final IssueConverter issueConverter;
    private final IssueDetailVOAssembler issueDetailVOAssembler;
    private final IssueVOAssembler issueVOAssembler;
    private final CustomFieldConverter customFieldConverter;
    private final IssueLinkService linkService;
    private final IssueLinkTypeService linkTypeService;
    private final IssueTagService tagService;
    private final CustomFieldService customFieldService;
    private final PriorityFieldService priorityFieldService;
    private final IssueTypeFieldService issueTypeFieldService;
    private final com.trackflow.issue.service.IssueCommentService commentService;
    private final com.trackflow.issue.service.IssueAttachmentService attachmentService;
    private final com.trackflow.issue.service.IssueActivityService activityService;
    private final com.trackflow.workflow.service.TransitionActionEngine transitionActionEngine;

    @PostMapping
    @PreAuthorize("@perm.check(#dto.projectId, 'issue:create')")
    public R<IssueDetailVO> create(@P("dto") @Valid @RequestBody CreateIssueDTO dto) {
        Issue issue = issueService.create(dto);
        return R.ok(assembleDetail(issue.getId()));
    }

    @GetMapping
    public R<PageResult<IssueVO>> list(@Valid IssueQuery query) {
        Page<Issue> page = issueService.listIssuesPage(query);
        List<IssueVO> voList = issueConverter.toVOList(page.getRecords());
        issueVOAssembler.assemble(page.getRecords(), voList);
        // Populate matchContext when keyword search is active
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            fillMatchContext(page.getRecords(), voList, query.getKeyword().trim());
        }
        return R.ok(new PageResult<>(voList, page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    /**
     * 获取工单详情 — 同时支持数字 ID 和 Issue Key（如 DE4-1543）。
     * 与 YouTrack API 行为一致：单个端点自动识别参数格式。
     */
    @GetMapping("/{idOrKey}")
    public R<IssueDetailVO> getById(@PathVariable("idOrKey") String idOrKey) {
        IssueDetailRow row = issueService.getDetailRowByIdOrKey(idOrKey);
        return R.ok(assembleDetailFromRow(row));
    }

    /**
     * 通过 Issue Key 获取工单详情（保留向后兼容）。
     */
    @GetMapping("/key/{issueKey}")
    public R<IssueDetailVO> getByKey(@PathVariable("issueKey") String issueKey) {
        IssueDetailRow row = issueService.getDetailRowByIdOrKey(issueKey);
        return R.ok(assembleDetailFromRow(row));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueDetailVO> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateIssueDTO dto) {
        IssueService.UpdateResult result = issueService.update(id, dto);
        IssueDetailVO detail = assembleDetail(id);
        if (result.statusAutoReset()) {
            detail.setStatusAutoReset(true);
        }
        return R.okWithWarnings(detail, result.warnings());
    }

    /**
     * 更新单个自定义字段值（内联编辑）。
     * 单值字段传 { "value": "xxx" }，多值字段传 { "values": ["id1","id2"] }。
     * 如果该字段是其他字段的值过滤源，会级联清除依赖字段中不再有效的值。
     */
    @PutMapping("/{id}/custom-fields/{fieldId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit') or @perm.checkIssue(#id, 'issue:edit_custom_fields')")
    public R<IssueDetailVO> updateCustomFieldValue(
            @PathVariable("id") Long id,
            @PathVariable("fieldId") Long fieldId,
            @Valid @RequestBody com.trackflow.customfield.dto.UpdateCustomFieldValueDTO dto) {
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
        List<String> cascadeCleared = customFieldService.saveSingleValue(id, fieldId, effectiveValue, issue.getIssueType(), issue.getProjectId());

        IssueDetailVO detail = assembleDetail(id);

        if (!cascadeCleared.isEmpty()) {
            List<String> warnings = cascadeCleared.stream()
                    .map(name -> "字段「" + name + "」的值已自动清除（不再匹配过滤条件）")
                    .toList();
            return R.okWithWarnings(detail, warnings);
        }
        return R.ok(detail);
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
        Page<TrashRow> result = issueService.listTrashPage(projectId, page, pageSize);
        List<IssueTrashVO> voList = result.getRecords().stream().map(row -> {
            IssueTrashVO vo = new IssueTrashVO();
            vo.setId(String.valueOf(row.getId()));
            vo.setProjectId(String.valueOf(row.getProjectId()));
            vo.setIssueKey(row.getIssueKey());
            vo.setTitle(row.getTitle());
            vo.setIssueType(row.getIssueType());
            vo.setPriority(row.getPriority());
            vo.setAssigneeName(row.getAssigneeName());
            vo.setDeletedByName(row.getDeletedByName());
            vo.setDeletedAt(row.getDeletedAt());
            return vo;
        }).toList();
        return R.ok(new PageResult<>(voList, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize()));
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
        List<BatchAvailableStatusVO> result = issueVOAssembler.getBatchAvailableTransitions(dto.getIssueIds());
        return R.ok(result);
    }

    @PostMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    public R<BatchOperationResult> batchOperation(@Valid @RequestBody BatchOperationDTO dto) {
        boolean silent = Boolean.TRUE.equals(dto.getSilent());

        // WIP 限制预检查（仅 status 操作）
        if ("status".equals(dto.getOperation()) && dto.getStatusId() != null
                && !Boolean.TRUE.equals(dto.getForceWip())) {
            String wipWarning = issueService.checkBatchWipLimit(dto.getIssueIds(), dto.getStatusId());
            if (wipWarning != null) {
                throw new BusinessException(ErrorCode.WIP_LIMIT_EXCEEDED, wipWarning);
            }
        }

        BatchOperationResult result = switch (dto.getOperation()) {
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
            throw new BusinessException(ErrorCode.INVALID_BATCH_OPERATION);
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
        IssueService.AvailableTransitionsResult data = issueService.getAvailableTransitionsData(id);
        Issue issue = issueService.getById(id);
        List<IssueStatusVO> voList = issueConverter.toStatusVOList(data.statuses());

        // 附加转换显示名
        for (IssueStatusVO vo : voList) {
            String tName = data.transitionNames().get(Long.valueOf(vo.getId()));
            if (tName != null) {
                vo.setTransitionName(tName);
            }
        }

        // 附加强制评论标记
        for (IssueStatusVO vo : voList) {
            if (data.requireCommentStatusIds().contains(Long.valueOf(vo.getId()))) {
                vo.setRequireComment(true);
            }
        }

        // 附加阻塞信息
        if (!data.blockerKeys().isEmpty()) {
            for (IssueStatusVO vo : voList) {
                if (Boolean.TRUE.equals(vo.getIsClosed())) {
                    vo.setBlocked(true);
                    vo.setBlockedBy(data.blockerKeys());
                }
            }
        }

        // 附加每个转换所需的必填字段 ID 列表（来自 require_field 动作配置）
        for (IssueStatusVO vo : voList) {
            List<Long> requiredIds = transitionActionEngine.getRequiredFieldIds(
                    issue.getProjectId(), issue.getIssueType(),
                    issue.getStatusId(), Long.valueOf(vo.getId()));
            if (!requiredIds.isEmpty()) {
                vo.setRequiredFieldIds(requiredIds.stream().map(String::valueOf).toList());
            }
        }

        return R.ok(voList);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:change_status')")
    public R<TransitStatusResult> transitStatus(@PathVariable("id") Long id, @Valid @RequestBody TransitStatusDTO dto) {
        return R.ok(issueService.performTransition(id, dto));
    }

    @PostMapping("/{id}/transitions/undo")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<TransitStatusResult> undoTransitStatus(@PathVariable("id") Long id, @Valid @RequestBody TransitStatusDTO dto) {
        TransitStatusResult result = issueService.undoTransitStatus(id, dto.getStatusId());
        return R.ok(result);
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
        return R.ok(commentService.listCommentsWithUser(id));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:comment')")
    public R<IssueCommentVO> addComment(@PathVariable("id") Long id, @Valid @RequestBody AddCommentDTO dto) {
        IssueComment comment = commentService.addComment(id, dto.getContent(), dto.getVisibleToGroupIds());
        return R.ok(commentService.toCommentVOWithUser(comment));
    }

    @PutMapping("/{id}/comments/{commentId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:comment')")
    public R<IssueCommentVO> updateComment(@PathVariable("id") Long id, @PathVariable("commentId") Long commentId,
                                            @Valid @RequestBody UpdateCommentDTO dto) {
        boolean updateVisibility = dto.getVisibleToGroupIds() != null;
        IssueComment comment = commentService.updateComment(id, commentId, dto.getContent(),
                dto.getVisibleToGroupIds(), updateVisibility);
        return R.ok(commentService.toCommentVOWithUser(comment));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:comment')")
    public R<Void> deleteComment(@PathVariable("id") Long id, @PathVariable("commentId") Long commentId) {
        commentService.deleteComment(id, commentId);
        return R.ok();
    }

    @PostMapping("/{id}/comments/{commentId}/restore")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:comment')")
    public R<Void> restoreComment(@PathVariable("id") Long id, @PathVariable("commentId") Long commentId) {
        commentService.restoreComment(id, commentId);
        return R.ok();
    }

    @DeleteMapping("/{id}/comments/{commentId}/permanent")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:manage_comments')")
    public R<Void> permanentlyDeleteComment(@PathVariable("id") Long id, @PathVariable("commentId") Long commentId) {
        commentService.permanentlyDeleteComment(id, commentId);
        return R.ok();
    }

    // ========== 附件 ==========

    @GetMapping("/{id}/attachments")
    public R<List<IssueAttachmentVO>> listAttachments(@PathVariable("id") Long id) {
        issueService.getByIdWithAccessCheck(id);
        List<IssueAttachment> attachments = attachmentService.listAttachments(id);
        return R.ok(attachmentService.buildAttachmentVOList(attachments));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueAttachmentVO> uploadAttachment(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "visibleToGroupIds", required = false) List<Long> visibleToGroupIds) {
        IssueAttachment attachment = attachmentService.uploadAttachment(id, file, visibleToGroupIds);
        return R.ok(attachmentService.buildAttachmentVO(attachment));
    }

    @PutMapping("/{id}/attachments/{attachmentId}/visibility")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<IssueAttachmentVO> updateAttachmentVisibility(
            @PathVariable("id") Long id,
            @PathVariable("attachmentId") Long attachmentId,
            @Valid @RequestBody com.trackflow.issue.dto.UpdateAttachmentVisibilityDTO dto) {
        IssueAttachment attachment = attachmentService.updateAttachmentVisibility(id, attachmentId, dto.getVisibleToGroupIds());
        return R.ok(attachmentService.buildAttachmentVO(attachment));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> deleteAttachment(@PathVariable("id") Long id, @PathVariable("attachmentId") Long attachmentId) {
        attachmentService.deleteAttachment(id, attachmentId);
        return R.ok();
    }

    // ========== 活动记录 ==========

    @GetMapping("/{id}/activities")
    public R<PageResult<IssueActivityVO>> listActivities(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        issueService.getByIdWithAccessCheck(id);
        return R.ok(activityService.listActivitiesWithUserPaged(id, page, pageSize));
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
        tagService.addTagsToIssue(id, dto.getEffectiveTagIds());
        return R.ok();
    }

    @DeleteMapping("/{id}/tags/{tagId}")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:edit')")
    public R<Void> removeTag(@PathVariable("id") Long id, @PathVariable("tagId") Long tagId) {
        tagService.removeTagFromIssue(id, tagId);
        return R.ok();
    }

    // ========== 关联 ==========

    @GetMapping("/link-types")
    public R<List<IssueLinkTypeVO>> listLinkTypes() {
        return R.ok(linkTypeService.listAllVO());
    }

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
    @PreAuthorize("isAuthenticated()")
    public R<List<IssueStatusVO>> listStatuses() {
        return R.ok(issueConverter.toStatusVOList(issueService.listStatuses()));
    }

    /**
     * 更新单个状态节点在工作流画布中的坐标位置
     */
    @PatchMapping("/statuses/{statusId}/position")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Void> updateStatusPosition(@PathVariable("statusId") Long statusId,
                                        @Valid @RequestBody com.trackflow.issue.dto.UpdateStatusPositionDTO dto) {
        issueService.updateStatusPosition(statusId, dto.getCanvasX(), dto.getCanvasY());
        return R.ok();
    }

    /**
     * 批量更新状态节点在工作流画布中的坐标位置（自动布局等场景）
     */
    @PutMapping("/statuses/positions")
    @PreAuthorize("@perm.checkGlobal('system:admin')")
    public R<Void> batchUpdateStatusPositions(@Valid @RequestBody com.trackflow.issue.dto.BatchUpdateStatusPositionDTO dto) {
        issueService.batchUpdateStatusPositions(dto.getPositions());
        return R.ok();
    }

    // ========== 移动工单 ==========

    @PostMapping("/{id}/move")
    @PreAuthorize("@perm.checkIssue(#id, 'issue:move')")
    public R<IssueDetailVO> move(@PathVariable("id") Long id,
                                 @Valid @RequestBody com.trackflow.issue.dto.MoveIssueDTO dto) {
        Issue moved = issueService.moveToProject(id, dto);
        return R.ok(assembleDetail(moved.getId()));
    }

    // ========== 优先级选项 ==========

    /**
     * 获取项目的优先级选项列表。
     * 优先级已纳入自定义字段体系（V249），本接口返回项目有效的优先级值列表（含颜色）。
     */
    @GetMapping("/priority-options")
    public R<List<com.trackflow.customfield.vo.CustomFieldOptionVO>> getPriorityOptions(
            @RequestParam("projectId") Long projectId) {
        var options = priorityFieldService.getPriorityOptions(projectId);
        return R.ok(customFieldConverter.toOptionVOList(options));
    }

    // ========== 工单类型选项 ==========

    /**
     * 获取项目的工单类型选项列表。
     * 工单类型已纳入自定义字段体系（V251），本接口返回项目有效的工单类型值列表（含颜色）。
     */
    @GetMapping("/issue-type-options")
    public R<List<com.trackflow.customfield.vo.CustomFieldOptionVO>> getIssueTypeOptions(
            @RequestParam("projectId") Long projectId) {
        var options = issueTypeFieldService.getIssueTypeOptions(projectId);
        return R.ok(customFieldConverter.toOptionVOList(options));
    }

    // ========== 内部辅助方法：VO 组装 ==========

    /**
     * 根据 Issue ID 组装完整的 IssueDetailVO（getDetailRow + children + assembler）
     */
    private IssueDetailVO assembleDetail(Long issueId) {
        IssueDetailRow row = issueService.getDetailRowWithAccessCheck(issueId);
        return assembleDetailFromRow(row);
    }

    /**
     * 从 IssueDetailRow 组装完整的 IssueDetailVO
     */
    private IssueDetailVO assembleDetailFromRow(IssueDetailRow row) {
        List<ChildIssueRow> children = issueService.listChildrenRows(row.getId());
        return issueDetailVOAssembler.assemble(row, children);
    }

    /**
     * 为搜索结果填充 matchContext 和 matchSource 字段。
     * 当关键词匹配来自描述（而非标题）时，提取匹配位置附近的上下文片段，
     * 帮助用户理解为什么该工单出现在搜索结果中。
     * matchSource 标识匹配来源（description/issueKey），前端据此展示不同的视觉样式。
     */
    private void fillMatchContext(List<Issue> issues, List<IssueVO> voList, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            IssueVO vo = voList.get(i);
            // Only show matchContext if title does NOT contain the keyword
            // (if title already contains it, the highlight on title is sufficient)
            String title = issue.getTitle();
            if (title != null && title.toLowerCase().contains(lowerKeyword)) {
                continue;
            }
            // Try to extract context from description
            String description = issue.getDescription();
            if (description != null && !description.isBlank()) {
                String context = extractSnippet(description, lowerKeyword, 80);
                if (context != null) {
                    vo.setMatchContext(context);
                    vo.setMatchSource("description");
                    continue;
                }
            }
            // If no match in title or description, it might have matched via issue_key or assignee.
            // Provide a hint for issue_key match
            String issueKey = issue.getIssueKey();
            if (issueKey != null && issueKey.toLowerCase().contains(lowerKeyword)) {
                vo.setMatchContext(issueKey);
                vo.setMatchSource("issueKey");
            }
            // Note: assignee name match context is not easily extractable here without
            // an extra query; the highlight on title is the primary UX improvement.
        }
    }

    /**
     * 从文本中提取关键词匹配位置附近的上下文片段。
     * 先去除 Markdown/HTML 标签简单清理后再匹配。
     */
    private String extractSnippet(String text, String lowerKeyword, int maxLength) {
        // Strip basic markdown/HTML for cleaner snippets
        String cleaned = text.replaceAll("<[^>]+>", "").replaceAll("[#*_~`>]", "").trim();
        if (cleaned.isBlank()) return null;

        String lowerCleaned = cleaned.toLowerCase();
        int idx = lowerCleaned.indexOf(lowerKeyword);
        if (idx == -1) return null;

        int contextBefore = 30;
        int start = Math.max(0, idx - contextBefore);
        int end = Math.min(cleaned.length(), start + maxLength);

        StringBuilder sb = new StringBuilder();
        if (start > 0) sb.append("...");
        sb.append(cleaned, start, end);
        if (end < cleaned.length()) sb.append("...");
        return sb.toString();
    }
}
