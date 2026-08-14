package com.trackflow.issue.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trackflow.auth.service.PermissionService;
import com.trackflow.common.event.IssueNotificationEvent;
import com.trackflow.common.event.ReportCacheInvalidationEvent;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import com.trackflow.common.util.SecurityUtils;
import com.trackflow.customfield.service.CustomFieldService;
import com.trackflow.issue.dto.MoveIssueDTO;
import com.trackflow.issue.entity.Issue;
import com.trackflow.issue.entity.IssueKeyHistory;
import com.trackflow.issue.entity.IssueStatus;
import com.trackflow.issue.mapper.IssueKeyHistoryMapper;
import com.trackflow.issue.mapper.IssueMapper;
import com.trackflow.project.service.ProjectService;
import com.trackflow.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工单跨项目移动服务 — 处理 Issue 从一个项目移动到另一个项目的完整业务逻辑。
 * <p>
 * 包括：权限校验、Issue Key 重新生成、字段兼容性清理、关联数据迁移等。
 *
 * @author TrackFlow
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueMoveService {

    private final IssueService issueService;
    private final IssueMapper issueMapper;
    private final IssueKeyHistoryMapper issueKeyHistoryMapper;
    private final ProjectService projectService;
    private final PermissionService permissionService;
    private final WorkflowService workflowService;
    private final StatusCacheHelper statusCacheHelper;
    private final CustomFieldService customFieldService;
    private final AncestorRefreshService ancestorRefreshService;
    private final IssueActivityService activityService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.trackflow.sprint.mapper.SprintMapper sprintMapper;
    private final com.trackflow.timeentry.mapper.TimeEntryMapper timeEntryMapper;
    private final com.trackflow.system.mapper.SysUserMapper sysUserMapper;

    /**
     * 将工单移动到另一个项目。
     * <p>
     * 处理流程：
     * 1. 权限校验（源项目 issue:move + 目标项目 issue:create）
     * 2. 重新生成 Issue Key
     * 3. Sprint 清空（不同项目的 Sprint 不通用）
     * 4. Assignee 兼容性清理
     * 5. 状态兼容性检查（不兼容时重置为默认状态）
     * 6. 自定义字段清理
     * 7. 子工单反向清理
     * 8. 关联数据迁移（time_entry.project_id）
     */
    @Transactional(rollbackFor = Exception.class)
    public Issue moveToProject(Long issueId, MoveIssueDTO dto) {
        Issue issue = issueService.getById(issueId);
        Long sourceProjectId = issue.getProjectId();
        Long targetProjectId = dto.getTargetProjectId();

        if (sourceProjectId.equals(targetProjectId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单已在该项目中，无需移动");
        }

        projectService.assertProjectActive(sourceProjectId);
        projectService.assertProjectActive(targetProjectId);

        Long currentUserId = SecurityUtils.getCurrentUserId();

        if (!permissionService.hasPermission(currentUserId, sourceProjectId, "issue:move")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "您没有在源项目中移动工单的权限");
        }
        if (!permissionService.hasPermission(currentUserId, targetProjectId, "issue:create")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "您没有在目标项目中创建工单的权限");
        }

        var sourceProject = projectService.getById(sourceProjectId);
        var targetProject = projectService.getById(targetProjectId);
        if (targetProject == null) {
            throw BusinessException.notFound("目标项目不存在");
        }

        // 生成新的 issue_key
        String oldIssueKey = issue.getIssueKey();
        int newSeq = projectService.nextIssueSequence(targetProjectId);
        String newIssueKey = targetProject.getKey() + "-" + newSeq;

        // 字段清理：Sprint 置空
        Long oldSprintId = issue.getSprintId();
        issue.setSprintId(null);

        // 字段清理：如果 assignee 不是目标项目成员，置空
        Long oldAssigneeId = issue.getAssigneeId();
        if (oldAssigneeId != null && !projectService.isProjectMember(oldAssigneeId, targetProjectId)) {
            issue.setAssigneeId(null);
        }

        issue.setProjectId(targetProjectId);
        issue.setIssueKey(newIssueKey);

        // 子工单处理：清理 parentId
        if (issue.getParentId() != null) {
            Issue parent = issueMapper.selectById(issue.getParentId());
            if (parent == null || !parent.getProjectId().equals(targetProjectId)) {
                issue.setParentId(null);
            }
        }

        // 保存旧 Key 到历史表
        IssueKeyHistory keyHistory = new IssueKeyHistory();
        keyHistory.setIssueId(issueId);
        keyHistory.setOldKey(oldIssueKey);
        keyHistory.setNewKey(newIssueKey);
        keyHistory.setChangedBy(currentUserId);
        issueKeyHistoryMapper.insert(keyHistory);

        // 状态兼容性检查
        boolean statusValid = workflowService.isStatusInWorkflow(
                targetProjectId, issue.getIssueType(), issue.getStatusId());
        if (!statusValid) {
            var defaultStatus = workflowService.getDefaultStatus();
            if (defaultStatus != null) {
                Long oldStatusId = issue.getStatusId();
                issue.setStatusId(defaultStatus.getId());
                String oldStatusName = statusCacheHelper.getStatusName(oldStatusId);
                String newStatusName = defaultStatus.getLocalizedName();
                activityService.recordActivity(issueId, currentUserId, "status_reset", "status",
                        oldStatusName, newStatusName);
                log.info("Issue {} moved to project {}: status auto-reset from {} to default ({})",
                        issueId, targetProjectId, oldStatusName, newStatusName);
            }
        }

        int rows = issueMapper.updateById(issue);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该工单已被其他人修改，请刷新页面后重试");
        }

        // 自定义字段清理
        customFieldService.removeOrphanValues(issueId, issue.getIssueType(), targetProjectId);

        // 子工单反向清理
        List<Issue> orphanChildren = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getParentId, issueId)
                        .isNull(Issue::getDeletedAt));
        if (!orphanChildren.isEmpty()) {
            issueMapper.clearParentId(issueId);
            for (Issue child : orphanChildren) {
                activityService.recordActivity(child.getId(), currentUserId, "updated", "parent",
                        oldIssueKey, null, oldIssueKey, null);
            }
            ancestorRefreshService.refreshAncestorChain(issueId);
            log.info("Issue {} moved: cleared parent_id on {} child issues in source project",
                    issueId, orphanChildren.size());
        }

        // 关联数据：更新 time_entry 的 project_id
        timeEntryMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.trackflow.timeentry.entity.TimeEntry>()
                        .eq(com.trackflow.timeentry.entity.TimeEntry::getIssueId, issueId)
                        .set(com.trackflow.timeentry.entity.TimeEntry::getProjectId, targetProjectId));

        // 活动记录
        String sourceProjectName = sourceProject != null ? sourceProject.getName() : String.valueOf(sourceProjectId);
        String targetProjectName = targetProject.getName();
        activityService.recordActivity(issueId, currentUserId, "moved_to_project", "project",
                sourceProjectName, targetProjectName);

        if (oldSprintId != null) {
            var oldSprint = sprintMapper.selectById(oldSprintId);
            String oldSprintName = oldSprint != null ? oldSprint.getName() : null;
            activityService.recordActivity(issueId, currentUserId, "updated", "sprint",
                    String.valueOf(oldSprintId), null, oldSprintName, null);
        }

        if (oldAssigneeId != null && issue.getAssigneeId() == null) {
            String oldAssigneeName = getUserDisplayName(oldAssigneeId);
            activityService.recordActivity(issueId, currentUserId, "assigned", "assignee", oldAssigneeName, null);
        }

        activityService.recordActivity(issueId, currentUserId, "updated", "issue_key", oldIssueKey, newIssueKey);

        // 失效两个项目的缓存
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(sourceProjectId, "issue_moved"));
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(targetProjectId, "issue_moved"));

        eventPublisher.publishEvent(new IssueNotificationEvent.Moved(issue, sourceProjectId, targetProjectId, currentUserId));

        log.info("Issue {} moved from project {} ({}) to project {} ({}). Key: {} → {}",
                issueId, sourceProjectId, sourceProjectName, targetProjectId, targetProjectName,
                oldIssueKey, newIssueKey);

        return issue;
    }

    /**
     * 自动化规则使用的内部移动方法——跳过权限检查，使用指定的 operatorId 作为操作者。
     */
    @Transactional(rollbackFor = Exception.class)
    public Issue moveToProjectByAutomation(Long issueId, Long targetProjectId, Long operatorId) {
        Issue issue = issueService.getById(issueId);
        Long sourceProjectId = issue.getProjectId();

        if (sourceProjectId.equals(targetProjectId)) {
            log.warn("[Automation] moveToProject: issue {} already in target project {}", issueId, targetProjectId);
            return issue;
        }

        var sourceProject = projectService.getById(sourceProjectId);
        var targetProject = projectService.getById(targetProjectId);
        if (targetProject == null) {
            throw BusinessException.notFound("目标项目", targetProjectId);
        }

        String oldIssueKey = issue.getIssueKey();
        int newSeq = projectService.nextIssueSequence(targetProjectId);
        String newIssueKey = targetProject.getKey() + "-" + newSeq;

        Long oldSprintId = issue.getSprintId();
        issue.setSprintId(null);

        Long oldAssigneeId = issue.getAssigneeId();
        if (oldAssigneeId != null && !projectService.isProjectMember(oldAssigneeId, targetProjectId)) {
            issue.setAssigneeId(null);
        }

        issue.setProjectId(targetProjectId);
        issue.setIssueKey(newIssueKey);

        if (issue.getParentId() != null) {
            Issue parent = issueMapper.selectById(issue.getParentId());
            if (parent == null || !parent.getProjectId().equals(targetProjectId)) {
                issue.setParentId(null);
            }
        }

        IssueKeyHistory keyHistory = new IssueKeyHistory();
        keyHistory.setIssueId(issueId);
        keyHistory.setOldKey(oldIssueKey);
        keyHistory.setNewKey(newIssueKey);
        keyHistory.setChangedBy(operatorId);
        issueKeyHistoryMapper.insert(keyHistory);

        boolean statusValid = workflowService.isStatusInWorkflow(
                targetProjectId, issue.getIssueType(), issue.getStatusId());
        if (!statusValid) {
            var defaultStatus = workflowService.getDefaultStatus();
            if (defaultStatus != null) {
                Long oldStatusId = issue.getStatusId();
                issue.setStatusId(defaultStatus.getId());
                String oldStatusName = statusCacheHelper.getStatusName(oldStatusId);
                String newStatusName = defaultStatus.getLocalizedName();
                activityService.recordActivity(issueId, operatorId, "status_reset", "status",
                        oldStatusName, newStatusName);
            }
        }

        issueMapper.updateById(issue);

        customFieldService.removeOrphanValues(issueId, issue.getIssueType(), targetProjectId);

        List<Issue> orphanChildren = issueMapper.selectList(
                new LambdaQueryWrapper<Issue>()
                        .eq(Issue::getParentId, issueId)
                        .isNull(Issue::getDeletedAt));
        if (!orphanChildren.isEmpty()) {
            issueMapper.clearParentId(issueId);
            for (Issue child : orphanChildren) {
                activityService.recordActivity(child.getId(), operatorId, "updated", "parent",
                        oldIssueKey, null, oldIssueKey, null);
            }
            ancestorRefreshService.refreshAncestorChain(issueId);
        }

        timeEntryMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.trackflow.timeentry.entity.TimeEntry>()
                        .eq(com.trackflow.timeentry.entity.TimeEntry::getIssueId, issueId)
                        .set(com.trackflow.timeentry.entity.TimeEntry::getProjectId, targetProjectId));

        String sourceProjectName = sourceProject != null ? sourceProject.getName() : String.valueOf(sourceProjectId);
        String targetProjectName = targetProject.getName();
        activityService.recordActivity(issueId, operatorId, "moved_to_project", "project",
                sourceProjectName, targetProjectName);

        if (oldSprintId != null) {
            var oldSprint = sprintMapper.selectById(oldSprintId);
            String oldSprintName = oldSprint != null ? oldSprint.getName() : null;
            activityService.recordActivity(issueId, operatorId, "updated", "sprint",
                    String.valueOf(oldSprintId), null, oldSprintName, null);
        }

        if (oldAssigneeId != null && issue.getAssigneeId() == null) {
            String oldAssigneeName = getUserDisplayName(oldAssigneeId);
            activityService.recordActivity(issueId, operatorId, "assigned", "assignee", oldAssigneeName, null);
        }

        activityService.recordActivity(issueId, operatorId, "updated", "issue_key", oldIssueKey, newIssueKey);

        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(sourceProjectId, "issue_moved"));
        eventPublisher.publishEvent(ReportCacheInvalidationEvent.of(targetProjectId, "issue_moved"));

        log.info("[Automation] Issue {} moved from project {} to project {}. Key: {} → {}",
                issueId, sourceProjectId, targetProjectId, oldIssueKey, newIssueKey);

        return issue;
    }

    private String getUserDisplayName(Long userId) {
        if (userId == null) return null;
        var user = sysUserMapper.selectById(userId);
        return user != null ? user.getDisplayName() : null;
    }
}
