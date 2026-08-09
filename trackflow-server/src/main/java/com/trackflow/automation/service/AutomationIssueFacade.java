package com.trackflow.automation.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.customfield.entity.CustomFieldDefinition;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.customfield.service.CustomFieldValueService;
import com.trackflow.issue.dto.IssueQuery;
import com.trackflow.issue.dto.UpdateIssueDTO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.entity.IssueTag;
import com.trackflow.issue.mapper.IssueStatusMapper;
import com.trackflow.issue.service.IssueCommentService;
import com.trackflow.issue.service.IssueService;
import com.trackflow.issue.service.IssueTagService;
import com.trackflow.query.service.SavedQueryService;
import com.trackflow.system.entity.SysUser;
import com.trackflow.system.mapper.SysUserMapper;
import com.trackflow.workflow.service.WorkflowService;
import com.trackflow.workflow.vo.ActionExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** TrackFlow 工单原生节点的安全业务门面。 */
@Service
@RequiredArgsConstructor
public class AutomationIssueFacade {
    private final IssueService issueService;
    private final IssueCommentService commentService;
    private final IssueTagService tagService;
    private final IssueStatusMapper statusMapper;
    private final WorkflowService workflowService;
    private final PermissionService permissionService;
    private final SysUserMapper sysUserMapper;
    private final CustomFieldValueService customFieldValueService;
    private final CustomFieldService customFieldService;
    private final SavedQueryService savedQueryService;

    /**
     * 获取工单的丰富信息（包含状态名称、负责人姓名、评论、标签、自定义字段）。
     */
    public Map<String, Object> getIssue(Long actorUserId, Object identifier) {
        Issue issue = identifier != null && identifier.toString().matches("\\d+")
                ? issueService.getByIdWithAccessCheck(Long.valueOf(identifier.toString()))
                : issueService.getByKeyWithAccessCheck(String.valueOf(identifier));
        requirePermission(actorUserId, issue, "issue:view");
        return toRichMap(issue);
    }

    /**
     * 搜索工单，支持优先级、工单类型、标签、排序等多维度筛选。
     */
    public List<Map<String, Object>> search(Long actorUserId, Long projectId, String statusIds,
                                             String keyword, boolean assignedToMe, int limit,
                                             String priorityIds, String issueTypes,
                                             String tagIds, String sort) {
        IssueQuery query = new IssueQuery();
        query.setProjectId(projectId);
        query.setStatusId(blankToNull(statusIds));
        query.setKeyword(blankToNull(keyword));
        query.setAssignedToMe(assignedToMe ? "true" : null);
        query.setPriority(blankToNull(priorityIds));
        query.setIssueType(blankToNull(issueTypes));
        query.setTagId(blankToNull(tagIds));
        query.setPage(1);
        query.setPageSize(Math.max(1, Math.min(limit, 100)));
        query.setSort(blankToNull(sort) != null ? sort : "-priority,-created_at");
        Page<Issue> page = issueService.listByQuery(query);
        return page.getRecords().stream().map(this::toMap).toList();
    }

    /**
     * 执行需求列表中已有的完整保存筛选。
     *
     * <p>保存筛选由同一个 QueryExecutor 执行，因此状态、负责人、日期、标签、Sprint 和
     * 自定义字段等条件与需求列表完全一致；查询结果仍按自动化执行身份可访问的项目收敛。</p>
     */
    public List<Map<String, Object>> searchSavedQuery(Long actorUserId, Long savedQueryId, int limit) {
        Page<Issue> page = savedQueryService.executeByIdWithAccessCheck(
                savedQueryId, 1, Math.max(1, Math.min(limit, 100)), actorUserId, false, null);
        return page.getRecords().stream().map(this::toMap).toList();
    }

    /** 向后兼容的 search 方法签名。 */
    public List<Map<String, Object>> search(Long actorUserId, Long projectId, String statusIds,
                                             String keyword, boolean assignedToMe, int limit) {
        return search(actorUserId, projectId, statusIds, keyword, assignedToMe, limit,
                null, null, null, null);
    }

    public Map<String, Object> transition(Long actorUserId, Long issueId, Long statusId,
                                           String comment, Integer expectedVersion) {
        Issue issue = issueService.getByIdWithAccessCheck(issueId);
        requirePermission(actorUserId, issue, "issue:change_status");
        if (!workflowService.isTransitionAllowed(issue, statusId, actorUserId)) {
            throw new BusinessException(ErrorCode.WORKFLOW_TRANSITION_DENIED,
                    "当前自动化角色不允许执行此状态转换");
        }
        if (workflowService.isCommentRequired(issue.getStatusId(), statusId)
                && (comment == null || comment.isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "此状态转换需要填写理由");
        }
        IssueService.TransitPreCheckResult preCheck = issueService.checkTransitPreConditions(
                issue, statusId, false, false);
        if (preCheck.wipWarning() != null) {
            throw new BusinessException(ErrorCode.WIP_LIMIT_EXCEEDED, preCheck.wipWarning());
        }
        if (preCheck.closeWarning() != null) {
            throw new BusinessException(ErrorCode.CLOSE_CONFIRMATION_REQUIRED, preCheck.closeWarning());
        }
        ActionExecutionResult transitResult = issueService.transitStatus(
                issueId, statusId, comment, null, false, expectedVersion, true);
        if (transitResult != null
                && transitResult.getOutcome() == ActionExecutionResult.Outcome.FIELD_VALIDATION_FAILED) {
            String warningMessage = transitResult.getWarningMessage();
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    warningMessage != null && !warningMessage.isBlank()
                            ? warningMessage
                            : "字段校验失败，无法完成状态转换");
        }
        return toMap(issueService.getById(issueId));
    }

    public Map<String, Object> comment(Long actorUserId, Long issueId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评论内容不能为空");
        }
        Issue issue = issueService.getByIdWithAccessCheck(issueId);
        requirePermission(actorUserId, issue, "issue:comment");
        IssueComment issueComment = commentService.addComment(issueId, content);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", issueComment.getId());
        result.put("issueId", issueId);
        result.put("content", issueComment.getContent());
        result.put("createdAt", issueComment.getCreatedAt());
        return result;
    }

    /**
     * 更新工单字段（优先级、负责人、标签等）。
     */
    public Map<String, Object> update(Long actorUserId, Long issueId, String priority,
                                       Long assigneeId, String tagIds,
                                       Map<String, String> customFields) {
        Issue issue = issueService.getByIdWithAccessCheck(issueId);
        requirePermission(actorUserId, issue, "issue:edit");

        UpdateIssueDTO dto = new UpdateIssueDTO();
        if (priority != null && !priority.isBlank()) {
            dto.setPriority(priority);
        }
        if (assigneeId != null) {
            dto.setAssigneeId(assigneeId);
        }
        if (customFields != null && !customFields.isEmpty()) {
            dto.setCustomFields(customFields);
        }
        dto.setVersion(issue.getVersion());

        issueService.update(issueId, dto);

        // 处理标签更新（全量替换：先移除旧标签，再添加新标签）
        if (tagIds != null) {
            List<Long> newTagIdList = tagIds.isBlank()
                    ? List.of()
                    : java.util.Arrays.stream(tagIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Long::valueOf)
                        .toList();
            // 获取当前标签
            List<IssueTag> currentTags = tagService.listIssueTags(issueId);
            java.util.Set<Long> currentTagIds = currentTags.stream()
                    .map(IssueTag::getId).collect(Collectors.toSet());
            java.util.Set<Long> targetTagIds = new java.util.HashSet<>(newTagIdList);
            // 移除不再需要的标签
            for (Long oldTagId : currentTagIds) {
                if (!targetTagIds.contains(oldTagId)) {
                    tagService.removeTagFromIssue(issueId, oldTagId);
                }
            }
            // 添加新标签（addTagsToIssue 内部幂等）
            List<Long> toAdd = newTagIdList.stream()
                    .filter(id -> !currentTagIds.contains(id)).toList();
            if (!toAdd.isEmpty()) {
                tagService.addTagsToIssue(issueId, toAdd);
            }
        }

        return toMap(issueService.getById(issueId));
    }

    private void requirePermission(Long actorUserId, Issue issue, String permission) {
        if (!permissionService.hasPermission(actorUserId, issue.getProjectId(), permission)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "自动化执行身份缺少权限: " + permission);
        }
    }

    /**
     * 基础 toMap：不包含评论、标签等重数据，用于列表搜索结果。
     */
    private Map<String, Object> toMap(Issue issue) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", issue.getId());
        result.put("key", issue.getIssueKey());
        result.put("projectId", issue.getProjectId());
        result.put("title", issue.getTitle());
        result.put("description", issue.getDescription());
        result.put("issueType", issue.getIssueType());
        result.put("statusId", issue.getStatusId());
        result.put("priority", issue.getPriority());
        result.put("assigneeId", issue.getAssigneeId());
        result.put("reporterId", issue.getReporterId());
        result.put("version", issue.getVersion());
        result.put("createdAt", issue.getCreatedAt());
        result.put("updatedAt", issue.getUpdatedAt());
        return result;
    }

    /**
     * 丰富的 toMap：包含状态名称、用户姓名、评论、标签、自定义字段。
     * 用于 IssueGetNode 的输出（后续喂给 IssueContextNode）。
     */
    private Map<String, Object> toRichMap(Issue issue) {
        Map<String, Object> result = toMap(issue);

        // 状态名称和颜色
        IssueStatus status = statusMapper.selectById(issue.getStatusId());
        if (status != null) {
            result.put("statusName", status.getLocalizedName());
            result.put("statusColor", status.getColor());
            result.put("statusCategory", status.getCategory());
        }

        // 负责人和报告人显示名
        result.put("assigneeName", getUserDisplayName(issue.getAssigneeId()));
        result.put("reporterName", getUserDisplayName(issue.getReporterId()));

        // 最近20条评论（简化格式）
        List<IssueComment> comments = commentService.listComments(issue.getId());
        int start = Math.max(0, comments.size() - 20);
        List<Map<String, Object>> commentList = comments.subList(start, comments.size()).stream()
                .map(c -> {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("id", c.getId());
                    cm.put("authorName", getUserDisplayName(c.getUserId()));
                    cm.put("content", c.getContent());
                    cm.put("createdAt", c.getCreatedAt());
                    return cm;
                }).toList();
        result.put("comments", commentList);

        // 标签列表
        List<IssueTag> tags = tagService.listIssueTags(issue.getId());
        List<Map<String, Object>> tagList = tags.stream().map(t -> {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("id", t.getId());
            tm.put("name", t.getName());
            tm.put("color", t.getColor());
            return tm;
        }).toList();
        result.put("tags", tagList);

        // 自定义字段值（key=字段名, value=字段值）
        Map<Long, String> fieldValues = customFieldValueService.getValues(issue.getId());
        if (!fieldValues.isEmpty()) {
            List<CustomFieldDefinition> fields = customFieldService.listByProject(
                    issue.getProjectId(), issue.getIssueType());
            Map<Long, String> fieldNameMap = fields.stream()
                    .collect(Collectors.toMap(CustomFieldDefinition::getId, CustomFieldDefinition::getName));
            Map<String, String> customFieldDisplay = new LinkedHashMap<>();
            fieldValues.forEach((fieldId, value) -> {
                String fieldName = fieldNameMap.getOrDefault(fieldId, "field_" + fieldId);
                customFieldDisplay.put(fieldName, value);
            });
            result.put("customFields", customFieldDisplay);
        } else {
            result.put("customFields", Map.of());
        }

        return result;
    }

    private String getUserDisplayName(Long userId) {
        if (userId == null) return null;
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? user.getDisplayName() : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
