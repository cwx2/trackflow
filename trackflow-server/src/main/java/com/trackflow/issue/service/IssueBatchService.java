package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.context.NotificationContext;
import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.event.ReportCacheInvalidationEvent;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.issue.dto.BatchOperationResult;
import com.trackflow.issue.dto.UpdateIssueDTO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.issue.mapper.result.DeletedIssueRow;
import com.trackflow.project.service.ProjectService;
import com.trackflow.workflow.service.WorkflowService;
import com.trackflow.workflow.vo.ActionExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 工单批量操作服务 — 处理批量状态转换、批量分配、批量 Sprint 变更等。
 * <p>
 * 从 IssueService 拆分，遵循职责单一原则：每个批量操作都是对 IssueService 单条操作的迭代封装。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueBatchService {

    private final IssueService issueService;
    private final IssueMapper issueMapper;
    private final IssueTagService tagService;
    private final ProjectService projectService;
    private final PermissionService permissionService;
    private final WorkflowService workflowService;
    private final ApplicationEventPublisher eventPublisher;
    private final IssueActivityService activityService;

    /**
     * 批量状态转换（带乐观锁 + 备注支持）。
     *
     * @param issueIds 要转换的工单 ID 列表
     * @param statusId 目标状态 ID
     * @param comment  可选备注（记录到活动日志）
     * @param versions 乐观锁版本映射（issueId → version），为 null 时跳过版本校验
     * @param silent   静默模式，为 true 时不发送通知
     */
    public BatchOperationResult batchTransitStatus(List<Long> issueIds, Long statusId,
                                                   String comment, Map<Long, Integer> versions, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:change_status", (issue, userId) -> {
            if (!workflowService.isTransitionAllowed(issue, statusId, userId)) {
                return "工作流不允许此状态转换";
            }
            Integer expectedVersion = versions != null ? versions.get(issue.getId()) : null;
            ActionExecutionResult transitResult = issueService.transitStatus(
                    issue.getId(), statusId, comment, null, false, expectedVersion, true);
            if (transitResult != null
                    && transitResult.getOutcome() == ActionExecutionResult.Outcome.FIELD_VALIDATION_FAILED) {
                String warningMessage = transitResult.getWarningMessage();
                return warningMessage != null && !warningMessage.isBlank()
                        ? warningMessage
                        : "字段校验失败，无法完成状态转换";
            }
            return null;
        }, "状态转换", silent);
    }

    /**
     * 批量分配
     */
    public BatchOperationResult batchAssign(List<Long> issueIds, Long assigneeId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:assign", (issue, userId) -> {
            issueService.assign(issue.getId(), assigneeId);
            return null;
        }, "分配", silent);
    }

    /**
     * 批量更新 Sprint
     */
    public BatchOperationResult batchUpdateSprint(List<Long> issueIds, Long sprintId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            UpdateIssueDTO dto = new UpdateIssueDTO();
            dto.setSprintId(sprintId);
            issueService.update(issue.getId(), dto);
            return null;
        }, "Sprint移动", silent);
    }

    /**
     * 批量更新优先级
     */
    public BatchOperationResult batchUpdatePriority(List<Long> issueIds, String priority, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            UpdateIssueDTO dto = new UpdateIssueDTO();
            dto.setPriority(priority);
            issueService.update(issue.getId(), dto);
            return null;
        }, "优先级变更", silent);
    }

    /**
     * 批量添加标签
     */
    public BatchOperationResult batchAddTag(List<Long> issueIds, Long tagId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            tagService.addTagToIssue(issue.getId(), tagId);
            return null;
        }, "添加标签", silent);
    }

    /**
     * 批量移除标签
     */
    public BatchOperationResult batchRemoveTag(List<Long> issueIds, Long tagId, boolean silent) {
        return executeBatchWithSilent(issueIds, "issue:edit", (issue, userId) -> {
            tagService.removeTagFromIssue(issue.getId(), tagId);
            return null;
        }, "移除标签", silent);
    }

    /**
     * 批量删除
     */
    public BatchOperationResult batchDelete(List<Long> issueIds) {
        return executeBatch(issueIds, "issue:delete", (issue, userId) -> {
            issueService.delete(issue.getId());
            return null;
        }, "删除");
    }

    /**
     * 批量恢复（从回收站还原）。
     * 注意：此方法不使用 executeBatch 模板，因为已删除工单 getById() 会抛异常。
     */
    public BatchOperationResult batchRestore(List<Long> issueIds) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();
        result.setTotal(issueIds.size());

        for (Long issueId : issueIds) {
            try {
                DeletedIssueRow row = issueMapper.selectByIdIgnoreDeleted(issueId);
                if (row == null || row.getDeletedAt() == null) {
                    result.addFailure(issueId, "?", "工单不在回收站中");
                    continue;
                }
                Long projectId = row.getProjectId();
                if (!permissionService.hasPermission(currentUserId, projectId, "issue:delete")) {
                    String key = row.getIssueKey() != null ? row.getIssueKey() : "?";
                    result.addFailure(issueId, key, "无恢复权限");
                    continue;
                }
                issueMapper.restoreById(issueId);
                activityService.recordActivity(issueId, currentUserId, "restored", null, null, null);
                // 发布恢复通知事件
                Issue restoredIssue = issueMapper.selectById(issueId);
                if (restoredIssue != null) {
                    eventPublisher.publishEvent(new IssueNotificationEvent.Restored(restoredIssue, currentUserId));
                }
                // 失效 Dashboard + 看板缓存
                eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(projectId, "issue_restored"));
                result.addSuccess();
            } catch (Exception e) {
                result.addFailure(issueId, "?", "恢复失败");
                log.warn("批量恢复失败 issueId={}", issueId, e);
            }
        }
        return result;
    }

    // ========== 内部方法 ==========

    private BatchOperationResult executeBatch(
            List<Long> issueIds,
            String permissionCode,
            BatchIssueAction action,
            String operationName) {
        return executeBatchWithSilent(issueIds, permissionCode, action, operationName, false);
    }

    /**
     * 支持静默模式的批量操作执行引擎。
     * silent=true 时通知事件监听器会跳过通知发送。
     */
    private BatchOperationResult executeBatchWithSilent(
            List<Long> issueIds,
            String permissionCode,
            BatchIssueAction action,
            String operationName,
            boolean silent) {

        if (silent) {
            NotificationContext.setSilent(true);
        }
        try {
            return doExecuteBatch(issueIds, permissionCode, action, operationName);
        } finally {
            if (silent) {
                NotificationContext.clear();
            }
        }
    }

    private BatchOperationResult doExecuteBatch(
            List<Long> issueIds,
            String permissionCode,
            BatchIssueAction action,
            String operationName) {

        Long currentUserId = SecurityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();
        result.setTotal(issueIds.size());

        for (Long issueId : issueIds) {
            Issue issue = null;
            try {
                issue = issueService.getById(issueId);
                projectService.assertProjectActive(issue.getProjectId());
                if (!permissionService.hasIssuePermission(currentUserId, issue, permissionCode)) {
                    result.addFailure(issueId, issue.getIssueKey(), "无" + operationName + "权限");
                    continue;
                }
                String rejectReason = action.execute(issue, currentUserId);
                if (rejectReason != null) {
                    result.addFailure(issueId, issue.getIssueKey(), rejectReason);
                } else {
                    result.addSuccess();
                }
            } catch (BusinessException e) {
                String key = issue != null ? issue.getIssueKey() : "?";
                result.addFailure(issueId, key, e.getMessage());
            } catch (Exception e) {
                String key = issue != null ? issue.getIssueKey() : "?";
                result.addFailure(issueId, key, "操作失败");
                log.warn("批量{}失败 issueId={}", operationName, issueId, e);
            }
        }
        return result;
    }

    @FunctionalInterface
    private interface BatchIssueAction {
        String execute(Issue issue, Long currentUserId) throws Exception;
    }
}
