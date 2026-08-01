package com.trackflow.automation.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.issue.dto.IssueQuery;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueComment;
import com.trackflow.issue.service.IssueService;
import com.trackflow.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TrackFlow 工单原生节点的安全业务门面。 */
@Service
@RequiredArgsConstructor
public class AutomationIssueFacade {
    private final IssueService issueService;
    private final WorkflowService workflowService;
    private final PermissionService permissionService;

    public Map<String, Object> getIssue(Long actorUserId, Object identifier) {
        Issue issue = identifier != null && identifier.toString().matches("\\d+")
                ? issueService.getByIdWithAccessCheck(Long.valueOf(identifier.toString()))
                : issueService.getByKeyWithAccessCheck(String.valueOf(identifier));
        requirePermission(actorUserId, issue, "issue:view");
        return toMap(issue);
    }

    public List<Map<String, Object>> search(Long actorUserId, Long projectId, String statusIds,
                                             String keyword, boolean assignedToMe, int limit) {
        IssueQuery query = new IssueQuery();
        query.setProjectId(projectId);
        query.setStatusId(blankToNull(statusIds));
        query.setKeyword(blankToNull(keyword));
        query.setAssignedToMe(assignedToMe ? "true" : null);
        query.setPage(1);
        query.setPageSize(Math.max(1, Math.min(limit, 100)));
        query.setSort("-priority,-created_at");
        Page<Issue> page = issueService.listByQuery(query);
        return page.getRecords().stream().map(this::toMap).toList();
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
        issueService.transitStatus(issueId, statusId, comment, null, false, expectedVersion, true);
        return toMap(issueService.getById(issueId));
    }

    public Map<String, Object> comment(Long actorUserId, Long issueId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "评论内容不能为空");
        }
        Issue issue = issueService.getByIdWithAccessCheck(issueId);
        requirePermission(actorUserId, issue, "issue:comment");
        IssueComment comment = issueService.addComment(issueId, content);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", comment.getId());
        result.put("issueId", issueId);
        result.put("content", comment.getContent());
        result.put("createdAt", comment.getCreatedAt());
        return result;
    }

    private void requirePermission(Long actorUserId, Issue issue, String permission) {
        if (!permissionService.hasPermission(actorUserId, issue.getProjectId(), permission)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "自动化执行身份缺少权限: " + permission);
        }
    }

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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
